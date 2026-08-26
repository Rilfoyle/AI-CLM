# create-bpm-model.ps1
# Idempotently create/update + deploy the SIMPLE-designer BPM model defined in
# clm_contract_approval_v1.model.json against a running RuoYi-Vue-Pro backend.
# PowerShell 5.1 compatible.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File D:\dev\clm\scripts\create-bpm-model.ps1
#   (optional) -BaseUrl http://127.0.0.1:48080 -Username admin -Password admin123 -TenantName 芋道源码 -TenantId 1

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:48080',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [string]$TenantName = '芋道源码',
    [string]$TenantId = '1',
    [string]$ModelFile = ''
)

$ErrorActionPreference = 'Stop'
if (-not $ModelFile) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $ModelFile = Join-Path $scriptDir 'clm_contract_approval_v1.model.json'
}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Make console output UTF-8 so Chinese names print correctly (best effort).
try { [Console]::OutputEncoding = $Utf8NoBom } catch {}

# ---------- helpers ----------
function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body = $null,          # hashtable/object -> JSON, or raw JSON string
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $h = @{ 'tenant-id' = $TenantId; 'Accept' = 'application/json' }
    foreach ($k in $Headers.Keys) { $h[$k] = $Headers[$k] }
    $args = @{ Method = $Method; Uri = $uri; Headers = $h; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 50 -Compress }
        $args['Body'] = $Utf8NoBom.GetBytes($json)   # send as UTF-8 bytes (PS 5.1 would otherwise send Latin-1)
        $args['ContentType'] = 'application/json;charset=UTF-8'
    }
    try {
        $resp = Invoke-WebRequest @args
        $raw = $resp.Content
    } catch {
        $raw = $null
        if ($_.Exception.Response) {
            $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream(), $Utf8NoBom)
            $raw = $sr.ReadToEnd(); $sr.Close()
        }
        if (-not $raw) { throw "HTTP error calling $Method $Path : $($_.Exception.Message)" }
    }
    # Invoke-WebRequest decodes content as ISO-8859-1 unless charset is declared; re-decode if needed.
    if ($raw -is [string] -and $resp -and $resp.RawContentStream) {
        $bytes = $resp.RawContentStream.ToArray()
        $raw = $Utf8NoBom.GetString($bytes)
    }
    $result = $raw | ConvertFrom-Json
    $codeText = if ($null -ne $result.code) { $result.code } else { '?' }
    Write-Host ("[{0} {1}] code={2} msg={3}" -f $Method, $Path, $codeText, $result.msg)
    return $result
}

function Assert-Ok {
    param([object]$Result, [string]$What)
    if ($null -eq $Result -or $Result.code -ne 0) {
        throw ("{0} failed: code={1} msg={2}" -f $What, $Result.code, $Result.msg)
    }
}

# ---------- 0. load model payload ----------
if (-not (Test-Path $ModelFile)) { throw "Model file not found: $ModelFile" }
$payloadText = [IO.File]::ReadAllText($ModelFile, $Utf8NoBom)
$payload = $payloadText | ConvertFrom-Json
$modelKey = $payload.key
Write-Host "Model key: $modelKey  (payload: $ModelFile)"

# ---------- 1. login ----------
$login = Invoke-Api -Method POST -Path '/admin-api/system/auth/login' -Body @{
    username = $Username; password = $Password; tenantName = $TenantName
}
Assert-Ok $login 'login'
$token = $login.data.accessToken
if (-not $token) { throw 'login returned no accessToken' }
$auth = @{ 'Authorization' = "Bearer $token" }

# ---------- 2. find existing model by key ----------
$list = Invoke-Api -Method GET -Path '/admin-api/bpm/model/list' -Headers $auth
Assert-Ok $list 'model/list'
$existing = @($list.data | Where-Object { $_.key -eq $modelKey }) | Select-Object -First 1

# ---------- 3. create or update ----------
$modelId = $null
if ($existing) {
    $modelId = $existing.id
    Write-Host "Model '$modelKey' exists (id=$modelId) -> update"
    # re-serialize payload with id; ConvertTo-Json keeps nested objects (depth 50)
    $payload | Add-Member -NotePropertyName id -NotePropertyValue $modelId -Force
    $update = Invoke-Api -Method PUT -Path '/admin-api/bpm/model/update' -Headers $auth -Body $payload
    Assert-Ok $update 'model/update'
} else {
    Write-Host "Model '$modelKey' not found -> create"
    $create = Invoke-Api -Method POST -Path '/admin-api/bpm/model/create' -Headers $auth -Body $payloadText
    Assert-Ok $create 'model/create'
    $modelId = $create.data
    Write-Host "Created model id=$modelId"
}
if (-not $modelId) { throw 'no model id' }

# ---------- 4. deploy ----------
$deploy = Invoke-Api -Method POST -Path "/admin-api/bpm/model/deploy?id=$modelId" -Headers $auth
Assert-Ok $deploy 'model/deploy'

# ---------- 5. verify model meta ----------
$get = Invoke-Api -Method GET -Path "/admin-api/bpm/model/get?id=$modelId" -Headers $auth
Assert-Ok $get 'model/get'
$m = $get.data
Write-Host ("model/get: id={0} key={1} name={2} type={3} formType={4} createPath={5} viewPath={6} category={7} deploymentId={8}" -f `
    $m.id, $m.key, $m.name, $m.type, $m.formType, $m.formCustomCreatePath, $m.formCustomViewPath, $m.category, $m.deploymentId)
if ($m.formType -ne 20) { throw "formType expected 20, got $($m.formType)" }
if ($m.formCustomCreatePath -ne $payload.formCustomCreatePath) { throw 'formCustomCreatePath mismatch' }
if ($m.formCustomViewPath -ne $payload.formCustomViewPath) { throw 'formCustomViewPath mismatch' }

# ---------- 6. verify simple model round-trip ----------
$simple = Invoke-Api -Method GET -Path "/admin-api/bpm/model/simple/get?id=$modelId" -Headers $auth
Assert-Ok $simple 'model/simple/get'
$chain = @()
$n = $simple.data
while ($null -ne $n) {
    $chain += ("{0}[type={1},id={2},strategy={3},param={4},method={5},reject={6}]" -f $n.name, $n.type, $n.id, $n.candidateStrategy, $n.candidateParam, $n.approveMethod, $n.rejectHandler.type)
    $n = $n.childNode
}
Write-Host ("simple/get chain: " + ($chain -join ' -> '))
if ($chain.Count -ne 4) { throw "expected 4 nodes in simple model chain, got $($chain.Count)" }

# ---------- 7. verify process definition ----------
$defs = Invoke-Api -Method GET -Path '/admin-api/bpm/process-definition/list?suspensionState=1' -Headers $auth
Assert-Ok $defs 'process-definition/list'
$def = @($defs.data | Where-Object { $_.key -eq $modelKey }) | Select-Object -First 1
if (-not $def) { throw "process definition with key $modelKey not found in active list" }
Write-Host ("DEPLOYED: definitionId={0} key={1} version={2} name={3} deploymentId={4} formType={5}" -f `
    $def.id, $def.key, $def.version, $def.name, $def.deploymentId, $def.formType)

# machine-readable summary (last line)
Write-Output (@{ modelId = $modelId; definitionId = $def.id; key = $def.key; version = $def.version; name = $def.name } | ConvertTo-Json -Compress)
