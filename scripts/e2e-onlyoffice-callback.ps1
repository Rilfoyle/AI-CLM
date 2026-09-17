# End-to-end check of the CLM ONLYOFFICE integration (docs/CLM_ONLYOFFICE_SPEC.md §5) against a running backend
# (127.0.0.1:48080). Works WITHOUT a Document Server: the script plays the Document Server role itself
# (pulls the file URL without auth, then posts a signed save callback).
# Flow: login -> type+publish -> parties -> contract -> upload v1 (content A) + v2 (content B)
#       -> GET /config?mode=edit (v2) -> GET document.url with no auth (bytes) -> tampered token rejected
#       -> callback {status:2,url:<v1 file url>} signed with HS256 JWT -> new version v3 (ONLINE_EDIT, sha == v1)
#       -> same callback again -> idempotent (still 3 versions) -> bad JWT -> error 1
#       -> submit approval -> /config?mode=edit on current version is rejected (locked)
# Usage: pwsh ./scripts/e2e-onlyoffice-callback.ps1
#        powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\scripts\e2e-onlyoffice-callback.ps1
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Base = 'http://127.0.0.1:48080/admin-api'
$LocalOrigin = 'http://127.0.0.1:48080'
$ClmRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path (Join-Path $ClmRoot 'infra') '.env'
$script:Failures = 0

function Check($cond, $msg) {
  if ($cond) { Write-Host "  PASS  $msg" -ForegroundColor Green } else { Write-Host "  FAIL  $msg" -ForegroundColor Red; $script:Failures++ }
}
function Get-HttpErrorText($errorRecord) {
  $txt = $errorRecord.ErrorDetails.Message
  if ($txt) { return $txt }
  $response = $errorRecord.Exception.Response
  if ($null -eq $response) { return $null }
  if ($response.PSObject.Methods['GetResponseStream']) {
    $sr = New-Object IO.StreamReader($response.GetResponseStream())
    try { return $sr.ReadToEnd() } finally { $sr.Close() }
  }
  $contentProperty = $response.PSObject.Properties['Content']
  $content = if ($contentProperty) { $contentProperty.Value } else { $null }
  if ($content -and $content.PSObject.Methods['ReadAsStringAsync']) {
    $readTask = $content.ReadAsStringAsync()
    return $readTask.GetAwaiter().GetResult()
  }
  return $null
}
function Login($username, $password) {
  $body = [Text.Encoding]::UTF8.GetBytes((@{ username = $username; password = $password; tenantName = 'TuriX' } | ConvertTo-Json -Compress))
  $r = Invoke-WebRequest -Uri "$Base/system/auth/login" -Method POST -ContentType 'application/json;charset=UTF-8' -Headers @{ 'tenant-id' = '1' } -Body $body -UseBasicParsing
  $j = $r.Content | ConvertFrom-Json
  if ($j.code -ne 0) { throw "login failed for $username : $($j.msg)" }
  return @{ Authorization = "Bearer $($j.data.accessToken)"; 'tenant-id' = '1' }
}
function Api($headers, $method, $path, $data) {
  $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
  if ($PSVersionTable.PSVersion.Major -ge 7) { $params['SkipHttpErrorCheck'] = $true }
  if ($null -ne $data) {
    $params.ContentType = 'application/json;charset=UTF-8'
    $params.Body = [Text.Encoding]::UTF8.GetBytes(($data | ConvertTo-Json -Compress -Depth 10))
  }
  try { $r = Invoke-WebRequest @params } catch {
    $txt = Get-HttpErrorText $_
    if (-not $txt) { throw }
    return ($txt | ConvertFrom-Json)
  }
  return ($r.Content | ConvertFrom-Json)
}
function Upload($headers, $path, $fields, $filePath, $fileField = 'file') {
  $boundary = [Guid]::NewGuid().ToString()
  $LF = "`r`n"
  $ms = New-Object IO.MemoryStream
  $enc = [Text.Encoding]::UTF8
  foreach ($k in $fields.Keys) {
    $part = "--$boundary$LF" + "Content-Disposition: form-data; name=`"$k`"$LF$LF" + "$($fields[$k])$LF"
    $b = $enc.GetBytes($part); $ms.Write($b, 0, $b.Length)
  }
  $fileName = [IO.Path]::GetFileName($filePath)
  $head = "--$boundary$LF" + "Content-Disposition: form-data; name=`"$fileField`"; filename=`"$fileName`"$LF" + "Content-Type: application/octet-stream$LF$LF"
  $b = $enc.GetBytes($head); $ms.Write($b, 0, $b.Length)
  $fb = [IO.File]::ReadAllBytes($filePath); $ms.Write($fb, 0, $fb.Length)
  $tail = "$LF--$boundary--$LF"; $b = $enc.GetBytes($tail); $ms.Write($b, 0, $b.Length)
  $r = Invoke-WebRequest -Uri "$Base$path" -Method POST -Headers $headers -ContentType "multipart/form-data; boundary=$boundary" -Body $ms.ToArray() -UseBasicParsing
  return ($r.Content | ConvertFrom-Json)
}
# Raw GET with NO auth headers (plays the Document Server). Returns @{ status; contentType; bytes; json }
function RawGet($url) {
  try {
    $params = @{ Uri = $url; Method = 'GET'; UseBasicParsing = $true }
    if ($PSVersionTable.PSVersion.Major -ge 7) { $params['SkipHttpErrorCheck'] = $true }
    $r = Invoke-WebRequest @params
    $ct = [string]$r.Headers['Content-Type']
    $res = @{ status = [int]$r.StatusCode; contentType = $ct; bytes = 0; json = $null }
    if ($ct -like 'application/json*') {
      $txt = $r.Content; if ($txt -is [byte[]]) { $txt = [Text.Encoding]::UTF8.GetString($txt) }
      $res.json = $txt | ConvertFrom-Json
    } else {
      if ($r.Content -is [byte[]]) { $res.bytes = $r.Content.Length } else { $res.bytes = [Text.Encoding]::UTF8.GetByteCount([string]$r.Content) }
    }
    return $res
  } catch {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { return @{ status = -1; contentType = ''; bytes = 0; json = $null; error = $_.Exception.Message } }
    $contentType = if ($resp.PSObject.Properties['ContentType']) { [string]$resp.ContentType } elseif ($resp.Content) { [string]$resp.Content.Headers.ContentType } else { '' }
    return @{ status = [int]$resp.StatusCode; contentType = $contentType; bytes = 0; json = $null }
  }
}
# Raw POST JSON with NO auth headers (plays the Document Server). Returns parsed JSON (or @{ error = -1 } on transport failure)
function RawPostJson($url, $obj) {
  $body = [Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Compress -Depth 10))
  try {
    $params = @{ Uri = $url; Method = 'POST'; ContentType = 'application/json;charset=UTF-8'; Body = $body; UseBasicParsing = $true }
    if ($PSVersionTable.PSVersion.Major -ge 7) { $params['SkipHttpErrorCheck'] = $true }
    $r = Invoke-WebRequest @params
    $txt = $r.Content; if ($txt -is [byte[]]) { $txt = [Text.Encoding]::UTF8.GetString($txt) }
    return ($txt | ConvertFrom-Json)
  } catch {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { return @{ error = -1; message = $_.Exception.Message } }
    try { $txt = Get-HttpErrorText $_; return ($txt | ConvertFrom-Json) } catch { return @{ error = -1; message = "HTTP $([int]$resp.StatusCode)" } }
  }
}
# Document Server URLs are built from clm.onlyoffice.callback-base-url (e.g. http://host.docker.internal:48080);
# this script runs on the host, so point them at the local backend instead.
function Localize($url) { return ($url -replace '^https?://[^/]+', $LocalOrigin) }
function Redact-TokenUrl($url) {
  $uri = [Uri]$url
  return "$($uri.GetLeftPart([UriPartial]::Path))?token=<redacted>"
}
function B64Url([byte[]]$bytes) { return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
function HS256Jwt($payloadObj, $secret) {
  $header = B64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
  $payload = B64Url ([Text.Encoding]::UTF8.GetBytes(($payloadObj | ConvertTo-Json -Compress -Depth 10)))
  $hmac = New-Object System.Security.Cryptography.HMACSHA256
  $hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
  $sig = B64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$header.$payload")))
  return "$header.$payload.$sig"
}
function ReadEnvValue($file, $key, $default) {
  if (-not (Test-Path $file)) { return $default }
  foreach ($line in (Get-Content $file)) {
    if ($line -match "^\s*$key\s*=\s*(.*)$") { $v = $Matches[1].Trim(); if ($v.Length -gt 0) { return $v } }
  }
  return $default
}
function Test-TcpPortInUse($hostName, $port) {
  $client = New-Object Net.Sockets.TcpClient
  try {
    $connect = $client.BeginConnect($hostName, $port, $null, $null)
    return $connect.AsyncWaitHandle.WaitOne(300) -and $client.Connected
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}
function Start-DocumentStub($origin, $fileMap) {
  $originUri = [Uri]$origin
  if ($originUri.Scheme -ne 'http' -or $originUri.Host -notin @('127.0.0.1', 'localhost')) {
    throw "local callback stub requires an http loopback documentServerUrl, got: $origin"
  }
  $port = $originUri.Port
  $job = Start-Job -ArgumentList $port, $fileMap -ScriptBlock {
    param($listenPort, $files)
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $listenPort)
    try {
      $listener.Start()
      while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
          $stream = $client.GetStream()
          $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::ASCII)
          $requestLine = $reader.ReadLine()
          if (-not $requestLine) { continue }
          while (($headerLine = $reader.ReadLine()) -ne $null -and $headerLine.Length -gt 0) {}
          $parts = $requestLine.Split(' ')
          $requestPath = if ($parts.Count -ge 2) { $parts[1].Split('?')[0] } else { '' }
          $shouldStop = $requestPath -eq '/__clm_stub_stop__'
          if ($shouldStop) {
            $bytes = [byte[]]@()
            $header = "HTTP/1.1 204 No Content`r`nContent-Length: 0`r`nConnection: close`r`n`r`n"
          } elseif ($parts[0] -eq 'GET' -and $files.ContainsKey($requestPath) -and (Test-Path $files[$requestPath])) {
            $bytes = [IO.File]::ReadAllBytes($files[$requestPath])
            $header = "HTTP/1.1 200 OK`r`nContent-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`r`nContent-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n"
          } else {
            $bytes = [Text.Encoding]::UTF8.GetBytes('not found')
            $header = "HTTP/1.1 404 Not Found`r`nContent-Type: text/plain`r`nContent-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n"
          }
          $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
          $stream.Write($headerBytes, 0, $headerBytes.Length)
          $stream.Write($bytes, 0, $bytes.Length)
          $stream.Flush()
          if ($shouldStop) { break }
        } finally {
          $client.Close()
        }
      }
    } finally {
      $listener.Stop()
    }
  }

  $ready = $false
  for ($attempt = 0; $attempt -lt 30 -and -not $ready; $attempt++) {
    if ($job.State -eq 'Failed') { break }
    Start-Sleep -Milliseconds 100
    $probe = New-Object Net.Sockets.TcpClient
    try {
      $probe.Connect($originUri.Host, $port)
      $ready = $true
    } catch {
      $ready = $false
    } finally {
      $probe.Close()
    }
  }
  if (-not $ready -or $job.State -eq 'Failed') {
    $details = (Receive-Job $job -ErrorAction SilentlyContinue | Out-String).Trim()
    Stop-Job $job -ErrorAction SilentlyContinue
    Remove-Job $job -Force -ErrorAction SilentlyContinue
    throw "unable to start callback content stub at $origin $(if ($details) { ": $details" })"
  }
  $job | Add-Member -NotePropertyName StubStopUrl -NotePropertyValue "$($origin.TrimEnd('/'))/__clm_stub_stop__"
  return $job
}
function Stop-DocumentStub($job) {
  if ($null -eq $job) { return }
  $stopUrlProperty = $job.PSObject.Properties['StubStopUrl']
  if ($stopUrlProperty) {
    try { Invoke-WebRequest -Uri $stopUrlProperty.Value -UseBasicParsing -TimeoutSec 2 | Out-Null } catch {}
  }
  Wait-Job $job -Timeout 3 -ErrorAction SilentlyContinue | Out-Null
  if ($job.State -eq 'Running') { Stop-Job $job -ErrorAction SilentlyContinue }
  Remove-Job $job -Force -ErrorAction SilentlyContinue
}
function MakeDocx($path, $text) {
  # minimal but valid DOCX (zip with [Content_Types].xml + _rels/.rels + word/document.xml)
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $tmp = Join-Path ([IO.Path]::GetDirectoryName($path)) ("docx_src_" + [IO.Path]::GetFileNameWithoutExtension($path))
  if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
  $wordDir = Join-Path $tmp 'word'; New-Item -ItemType Directory -Force $wordDir | Out-Null
  $relsDir = Join-Path $tmp '_rels'; New-Item -ItemType Directory -Force $relsDir | Out-Null
  $utf8 = New-Object Text.UTF8Encoding $false
  [IO.File]::WriteAllText((Join-Path $tmp '[Content_Types].xml'), '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>', $utf8)
  [IO.File]::WriteAllText((Join-Path $relsDir '.rels'), '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>', $utf8)
  [IO.File]::WriteAllText((Join-Path $wordDir 'document.xml'), ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>' + $text + '</w:t></w:r></w:p></w:body></w:document>'), $utf8)
  if (Test-Path $path) { Remove-Item -Force $path }
  [IO.Compression.ZipFile]::CreateFromDirectory($tmp, $path)
}

$JwtSecret = ReadEnvValue $EnvFile 'CLM_ONLYOFFICE_JWT_SECRET' 'change-me-local-only'
Write-Host "== jwt secret source: $(if (Test-Path $EnvFile) { $EnvFile } else { 'default' }) (length $($JwtSecret.Length))"
$configuredDocumentServerUrl = ReadEnvValue $EnvFile 'CLM_ONLYOFFICE_URL' 'http://127.0.0.1:8090'
$configuredDocumentServerUri = [Uri]$configuredDocumentServerUrl
if (Test-TcpPortInUse $configuredDocumentServerUri.Host $configuredDocumentServerUri.Port) {
  throw "callback simulation needs exclusive use of $($configuredDocumentServerUri.GetLeftPart([UriPartial]::Authority)); stop clm-onlyoffice first"
}

Write-Host "== login"
$admin = Login 'admin' 'admin123'     # user 1, super_admin, contract owner
$legal = Login 'legal' 'admin123'     # user 200002, legal collaborator
Check ($null -ne $admin -and $null -ne $legal) 'admin and legal collaborator logged in'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'

Write-Host "== contract type (create + publish)"
$typeId = (Api $admin POST '/clm/contract-type/create' @{ code = "oo_$stamp"; name = "在线编辑合同 $stamp"; description = 'e2e onlyoffice'; status = 0; sort = 1; processDefinitionKey = 'clm_contract_approval_v1' }).data
Check ($typeId -gt 0) "type created id=$typeId"
$versions = (Api $admin GET "/clm/contract-type/version/list?typeId=$typeId").data
$draft = $versions | Where-Object { $_.status -eq 0 } | Select-Object -First 1
$fields = @(
  '{"type":"input","field":"projectName","title":"项目名称","$required":true,"props":{},"_fc_id":"id_p1","name":"ref_p1","_fc_drag_tag":"input","hidden":false,"display":true}'
)
$r = Api $admin PUT '/clm/contract-type/version/update' @{ id = $draft.id; formConf = '{"form":{"labelWidth":"120px"}}'; formFields = $fields; processDefinitionKey = 'clm_contract_approval_v1'; remark = 'v1' }
Check ($r.code -eq 0) 'draft schema saved'
$r = Api $admin POST "/clm/contract-type/version/publish?id=$($draft.id)"
Check ($r.code -eq 0) "type version published (msg=$($r.msg))"
$routeId = (Api $admin POST '/clm/governance/routing/save-draft' @{ ruleCode = "onlyoffice-e2e-route-$stamp"; name = "ONLYOFFICE e2e route $stamp"; contractTypeId = $typeId; priority = 100; condition = @{}; processDefinitionKey = 'clm_contract_approval_v1' }).data
$routePublish = Api $admin PUT "/clm/governance/routing/publish?id=$routeId" $null
Check ($routeId -gt 0 -and $routePublish.code -eq 0) 'published an exact contract-type approval route'

Write-Host "== parties + contract"
$ourId = (Api $admin POST '/clm/party/create' @{ partyType = 1; name = "我方科技有限公司 $stamp"; shortName = '网编方'; unifiedCreditCode = '91310000MA1K3W0001'; internalFlag = $true; status = 0 }).data
$cpId  = (Api $admin POST '/clm/party/create' @{ partyType = 1; name = "客户集团股份有限公司 $stamp"; unifiedCreditCode = '91440300MA5D0002'; internalFlag = $false; status = 0 }).data
Check (($ourId -gt 0) -and ($cpId -gt 0)) "parties created our=$ourId cp=$cpId"
$contractBody = @{ title = "e2e 在线编辑合同 $stamp"; typeId = $typeId; amount = 1000; currency = 'CNY'; signDate = '2026-08-24'; effectiveDate = '2026-09-01'; expiryDate = '2027-08-31'; description = 'e2e onlyoffice'; customData = @{ projectName = 'ONLYOFFICE POC' }; parties = @(@{ partyId = $ourId; roleCode = 'OUR_SIDE'; sort = 1 }, @{ partyId = $cpId; roleCode = 'COUNTERPARTY'; sort = 2 }) }
$r = Api $admin POST '/clm/contract/create' $contractBody
Check ($r.code -eq 0) "contract created (msg=$($r.msg))"
$contractId = $r.data

Write-Host "== documents: upload v1 (content A) and v2 (content B)"
$docDir = Join-Path ([IO.Path]::GetTempPath()) 'clm-e2e'; New-Item -ItemType Directory -Force $docDir | Out-Null
$docxA = Join-Path $docDir 'oo-contract-v1.docx'; MakeDocx $docxA "在线编辑 e2e 正文 A $stamp"
$docxB = Join-Path $docDir 'oo-contract-v2.docx'; MakeDocx $docxB "在线编辑 e2e 正文 B $stamp"
$up1 = Upload $admin '/clm/contract/document/upload' @{ contractId = $contractId; roleCode = 'MAIN'; remark = 'v1 content A' } $docxA
Check ($up1.code -eq 0) "upload v1 ok (msg=$($up1.msg))"
$up2 = Upload $admin '/clm/contract/document/upload' @{ contractId = $contractId; roleCode = 'MAIN'; remark = 'v2 content B' } $docxB
Check ($up2.code -eq 0 -and $up2.data -ne $up1.data) 'upload v2 ok (new version id)'
$docs = (Api $admin GET "/clm/contract/document/list?contractId=$contractId").data
$main = $docs | Where-Object { $_.roleCode -eq 'MAIN' }
$ver1 = $main.versions | Where-Object { $_.versionNo -eq 1 }; $ver2 = $main.versions | Where-Object { $_.versionNo -eq 2 }
Check (@($main.versions).Count -eq 2 -and $ver1.checksumSha256 -ne $ver2.checksumSha256) 'MAIN has v1/v2 with different checksums'
Check ($main.currentVersionId -eq $ver2.id) 'document current version = v2'

Write-Host "== GET /clm/online-edit/config?mode=edit for v2"
$cfgResp = Api $admin GET "/clm/online-edit/config?versionId=$($ver2.id)&mode=edit"
Check ($cfgResp.code -eq 0) "config ok (code=$($cfgResp.code) msg=$($cfgResp.msg))"
$cfg = $cfgResp.data
if ($cfg.enabled -ne $true) { Write-Host "  FAIL  onlyoffice is disabled on the server (clm.onlyoffice.enabled=false) - cannot continue" -ForegroundColor Red; exit 1 }
Check ($cfg.enabled -eq $true) 'enabled=true'
Check ($cfg.documentServerUrl -like 'http*') "documentServerUrl=$($cfg.documentServerUrl)"
Check ($cfg.config.documentType -eq 'word') 'documentType=word'
Check ($cfg.config.document.fileType -eq 'docx') 'document.fileType=docx'
$expectedKey = "clm-v$($ver2.id)-" + $ver2.checksumSha256.Substring(0, 12)
Check ($cfg.config.document.key -eq $expectedKey) "document.key=$($cfg.config.document.key)"
Check ($cfg.config.document.url -like '*/admin-api/clm/online-edit/file?token=*') "document.url=$(Redact-TokenUrl $cfg.config.document.url)"
Check ($cfg.config.document.permissions.edit -eq $true -and $cfg.config.document.permissions.download -eq $true) 'permissions.edit/download true for owner'
Check ($cfg.config.editorConfig.mode -eq 'edit' -and $cfg.config.editorConfig.lang -eq 'zh-CN') 'editorConfig.mode=edit lang=zh-CN'
Check ($cfg.config.editorConfig.callbackUrl -like '*/admin-api/clm/online-edit/callback?token=*') "callbackUrl=$(Redact-TokenUrl $cfg.config.editorConfig.callbackUrl)"
Check ($cfg.config.editorConfig.user.id -eq '1') "editorConfig.user.id=$($cfg.config.editorConfig.user.id) name=$($cfg.config.editorConfig.user.name)"
Check ($cfg.config.editorConfig.customization.forcesave -eq $true) 'customization.forcesave=true'
Check (-not [string]::IsNullOrEmpty($cfg.token) -and @($cfg.token.Split('.')).Count -eq 3) 'HS256 JWT token over config present'
# the JWT must verify with the shared secret (proves backend uses the same CLM_ONLYOFFICE_JWT_SECRET as infra/.env)
$jwtParts = $cfg.token.Split('.')
$hm = New-Object System.Security.Cryptography.HMACSHA256; $hm.Key = [Text.Encoding]::UTF8.GetBytes($JwtSecret)
$expectedSig = B64Url ($hm.ComputeHash([Text.Encoding]::UTF8.GetBytes("$($jwtParts[0]).$($jwtParts[1])")))
Check ($expectedSig -eq $jwtParts[2]) 'config JWT signature verifies with CLM_ONLYOFFICE_JWT_SECRET'
# same version again -> same stable key
$cfgAgain = (Api $admin GET "/clm/online-edit/config?versionId=$($ver2.id)&mode=edit").data
Check ($cfgAgain.config.document.key -eq $expectedKey) 'document.key stable for the same version'

# The hardened callback downloader only accepts URLs on the configured Document Server origin.
# This e2e runs without a real Document Server, so serve v1/v2 from a tiny loopback HTTP stub.
$documentServerUri = [Uri]$cfg.documentServerUrl
$stubOrigin = $documentServerUri.GetLeftPart([UriPartial]::Authority)
$stubV1Url = "$stubOrigin/clm-e2e/v1.docx"
$stubV2Url = "$stubOrigin/clm-e2e/v2.docx"
$stubFiles = @{}
$stubFiles[([Uri]$stubV1Url).AbsolutePath] = $docxA
$stubFiles[([Uri]$stubV2Url).AbsolutePath] = $docxB
Write-Host "== local Document Server content stub: $stubOrigin"
$stubJob = Start-DocumentStub $stubOrigin $stubFiles
try {
  $stubProbe = RawGet $stubV1Url
  if ($stubProbe.status -ne 200 -or $stubProbe.bytes -ne (Get-Item $docxA).Length) {
    throw "callback content stub probe failed: status=$($stubProbe.status) bytes=$($stubProbe.bytes)"
  }

Write-Host "== open file endpoint (no auth headers, plays the Document Server)"
$fileUrlV2 = Localize $cfg.config.document.url
$g = RawGet $fileUrlV2
Check ($g.status -eq 200 -and $g.bytes -gt 0 -and $g.contentType -notlike 'application/json*') "GET document.url -> $($g.bytes) bytes (content-type $($g.contentType))"
Check ($g.bytes -eq $ver2.fileSize) "byte count equals v2.fileSize ($($ver2.fileSize))"
$tampered = $fileUrlV2.Substring(0, $fileUrlV2.Length - 2) + $(if ($fileUrlV2.EndsWith('AA')) { 'BB' } else { 'AA' })
$t = RawGet $tampered
Check (($t.status -ne 200) -or ($null -ne $t.json -and $t.json.code -ne 0)) "tampered token rejected (status=$($t.status) code=$($t.json.code))"
$callbackAsFile = Localize $cfg.config.editorConfig.callbackUrl
$fileWithCallbackToken = $fileUrlV2.Substring(0, $fileUrlV2.IndexOf('?token=')) + $callbackAsFile.Substring($callbackAsFile.IndexOf('?token='))
$p = RawGet $fileWithCallbackToken
Check (($p.status -ne 200) -or ($null -ne $p.json -and $p.json.code -ne 0)) "callback token cannot be used as file token (purpose check, code=$($p.json.code))"

Write-Host "== view config for v1 -> validate the historic backend file endpoint"
$cfgV1 = Api $admin GET "/clm/online-edit/config?versionId=$($ver1.id)&mode=view"
Check ($cfgV1.code -eq 0 -and $cfgV1.data.config.editorConfig.mode -eq 'view') 'view config ok for v1 (historic version)'
Check ($null -eq $cfgV1.data.config.editorConfig.callbackUrl) 'view config has no callbackUrl'
Check ($cfgV1.data.config.document.permissions.edit -eq $false) 'view permissions.edit=false'
$fileUrlV1 = Localize $cfgV1.data.config.document.url
$g1 = RawGet $fileUrlV1
Check ($g1.status -eq 200 -and $g1.bytes -eq $ver1.fileSize) "GET v1 file url -> $($g1.bytes) bytes"
$editV1 = Api $admin GET "/clm/online-edit/config?versionId=$($ver1.id)&mode=edit"
Check ($editV1.code -ne 0) "edit mode on a non-current version rejected (code=$($editV1.code) msg=$($editV1.msg))"

Write-Host "== save callback (status=2) signed with HS256 JWT -> new version v3"
$callbackUrl = Localize $cfg.config.editorConfig.callbackUrl
$cb = [ordered]@{ key = $cfg.config.document.key; status = 2; url = $stubV1Url; users = @('1') }
$cb.token = HS256Jwt $cb $JwtSecret
$cbResp = RawPostJson $callbackUrl $cb
Check ($cbResp.error -eq 0) "callback accepted error=$($cbResp.error) $($cbResp.message)"
$docs = (Api $admin GET "/clm/contract/document/list?contractId=$contractId").data
$main = $docs | Where-Object { $_.roleCode -eq 'MAIN' }
$ver3 = $main.versions | Where-Object { $_.versionNo -eq 3 }
Check ($null -ne $ver3) 'version 3 exists'
Check ($ver3.sourceType -eq 'ONLINE_EDIT') "v3.sourceType=$($ver3.sourceType)"
Check ($ver3.checksumSha256 -eq $ver1.checksumSha256) 'v3 checksum equals v1 checksum (content pulled from url)'
Check ($ver3.parentVersionId -eq $ver2.id) 'v3.parentVersionId = v2 (token.v)'
Check ($ver3.creator -eq '1') "v3.creator = token user ($($ver3.creator))"
Check ($ver3.remark -like 'ONLYOFFICE*status=2*') "v3.remark=$($ver3.remark)"
Check ($main.currentVersionId -eq $ver3.id) 'document current version = v3'
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.currentDocumentVersionId -eq $ver3.id) 'contract.currentDocumentVersionId = v3'
$cfgV3 = (Api $admin GET "/clm/online-edit/config?versionId=$($ver3.id)&mode=edit").data
Check ($cfgV3.config.document.key -ne $expectedKey -and $cfgV3.config.document.key -like "clm-v$($ver3.id)-*") "new version -> new document.key ($($cfgV3.config.document.key))"

Write-Host "== same callback again -> idempotent (no 4th version)"
$cbResp2 = RawPostJson $callbackUrl $cb
Check ($cbResp2.error -eq 0) "second callback error=$($cbResp2.error)"
$docs = (Api $admin GET "/clm/contract/document/list?contractId=$contractId").data
$main = $docs | Where-Object { $_.roleCode -eq 'MAIN' }
Check (@($main.versions).Count -eq 3) "still 3 versions ($(@($main.versions).Count))"

Write-Host "== negative callbacks"
$badJwt = [ordered]@{ key = $cfg.config.document.key; status = 2; url = $stubV1Url; users = @('1') }
$badJwt.token = HS256Jwt $badJwt 'definitely-the-wrong-secret'
$bj = RawPostJson $callbackUrl $badJwt
Check ($bj.error -eq 1) "wrong JWT secret -> error=$($bj.error)"
$noJwt = [ordered]@{ key = $cfg.config.document.key; status = 2; url = $stubV1Url; users = @('1') }
$nj = RawPostJson $callbackUrl $noJwt
Check ($nj.error -eq 1) "missing JWT -> error=$($nj.error)"
$badToken = $callbackUrl.Substring(0, $callbackUrl.Length - 2) + $(if ($callbackUrl.EndsWith('AA')) { 'BB' } else { 'AA' })
$bt = RawPostJson $badToken $cb
Check ($bt.error -eq 1) "tampered callback token -> error=$($bt.error)"
$status4 = [ordered]@{ key = $cfg.config.document.key; status = 4; users = @('1') }   # 4 = closed without changes
$status4.token = HS256Jwt $status4 $JwtSecret
$s4 = RawPostJson $callbackUrl $status4
Check ($s4.error -eq 0) "status=4 (no changes) -> error=$($s4.error)"
$docs = (Api $admin GET "/clm/contract/document/list?contractId=$contractId").data
$main = $docs | Where-Object { $_.roleCode -eq 'MAIN' }
Check (@($main.versions).Count -eq 3) 'negative callbacks created no versions'
$audit = (Api $admin GET "/clm/contract/audit-event/page?contractId=$contractId&pageNo=1&pageSize=100").data.list
$actions = @($audit | ForEach-Object { $_.action })
Check (($actions -contains 'ONLINE_EDIT_OPEN') -and ($actions -contains 'ONLINE_EDIT_SAVE')) "audit has ONLINE_EDIT_OPEN / ONLINE_EDIT_SAVE ($(@($actions | Where-Object { $_ -eq 'ONLINE_EDIT_SAVE' }).Count) save events)"
$saveEvt = $audit | Where-Object { $_.action -eq 'ONLINE_EDIT_SAVE' } | Select-Object -First 1
Check ($saveEvt.actorUserId -eq 1) "ONLINE_EDIT_SAVE actorUserId=$($saveEvt.actorUserId) actorName=$($saveEvt.actorName)"

Write-Host "== submit approval -> edit locked"
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
$confirmedNone = Api $admin PUT "/clm/contract/commitment/confirm-none?contractId=$contractId" $null
Check ($confirmedNone.code -eq 0) 'explicitly confirmed no major commitments'
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
$collaborationId = (Api $admin POST '/clm/collaboration/start' @{ contractId = $contractId; revisionId = $c.currentRevisionId; legalUserId = 200002; reason = 'online-edited nonstandard revision requires legal collaboration' }).data
$collaborationComplete = Api $legal PUT '/clm/collaboration/complete' @{ caseId = $collaborationId; revisionId = $c.currentRevisionId; conclusion = '法务确认在线编辑后的当前修订可以提审' }
Check ($collaborationId -gt 0 -and $collaborationComplete.code -eq 0) 'current online-edited revision completed legal collaboration'
$r = Api $admin POST '/clm/contract/submit' @{ id = $contractId; baseRevisionId = $c.currentRevisionId; submitRequestId = "onlyoffice-e2e-$stamp-submit"; remark = 'e2e onlyoffice submit' }
Check ($r.code -eq 0) "submit ok (msg=$($r.msg))"
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.approvalStatus -eq 1) 'contract approval RUNNING'
$locked = Api $admin GET "/clm/online-edit/config?versionId=$($c.currentDocumentVersionId)&mode=edit"
Check ($locked.code -ne 0) "edit config rejected while approval RUNNING (code=$($locked.code) msg=$($locked.msg))"
$viewLocked = Api $admin GET "/clm/online-edit/config?versionId=$($c.currentDocumentVersionId)&mode=view"
Check ($viewLocked.code -eq 0 -and $viewLocked.data.config.editorConfig.mode -eq 'view') 'view config still allowed while approval RUNNING'
Write-Host "== late save: callback for the already-opened edit session still lands (audit lateSave=true)"
$late = [ordered]@{ key = $cfg.config.document.key; status = 6; url = $stubV2Url; users = @('1'); forcesavetype = 1 }
$late.token = HS256Jwt $late $JwtSecret
$lr = RawPostJson $callbackUrl $late
Check ($lr.error -eq 0) "late forcesave callback error=$($lr.error)"
$docs = (Api $admin GET "/clm/contract/document/list?contractId=$contractId").data
$main = $docs | Where-Object { $_.roleCode -eq 'MAIN' }
$ver4 = $main.versions | Where-Object { $_.versionNo -eq 4 }
Check ($null -ne $ver4 -and $ver4.sourceType -eq 'ONLINE_EDIT' -and $ver4.checksumSha256 -eq $ver2.checksumSha256) 'v4 created from late save (content B)'
$audit = (Api $admin GET "/clm/contract/audit-event/page?contractId=$contractId&pageNo=1&pageSize=100").data.list
$lateEvt = @($audit | Where-Object { $_.action -eq 'ONLINE_EDIT_SAVE' -and $_.detailJson -like '*"lateSave":true*' })
Check ($lateEvt.Count -ge 1) 'late save audited with lateSave=true'
} finally {
  Stop-DocumentStub $stubJob
}

Write-Host ""
if ($script:Failures -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green } else { Write-Host "$($script:Failures) CHECK(S) FAILED" -ForegroundColor Red; exit 1 }
