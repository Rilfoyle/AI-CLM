# End-to-end API test of the CLM vertical slice against a running backend (127.0.0.1:48080).
# Flow: type -> publish -> parties -> contract -> upload v1 -> submit -> approve (yudao) -> approve (admin)
#       -> status checks -> ACL checks (unauthorized user denied; approver can download bound version)
#       -> reject path on a second contract.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\scripts\e2e-api.ps1
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Base = 'http://127.0.0.1:48080/admin-api'
$script:Failures = 0

function Check($cond, $msg) {
  if ($cond) { Write-Host "  PASS  $msg" -ForegroundColor Green } else { Write-Host "  FAIL  $msg" -ForegroundColor Red; $script:Failures++ }
}
function Login($username, $password) {
  $body = [Text.Encoding]::UTF8.GetBytes((@{ username = $username; password = $password; tenantName = '芋道源码' } | ConvertTo-Json -Compress))
  $r = Invoke-WebRequest -Uri "$Base/system/auth/login" -Method POST -ContentType 'application/json;charset=UTF-8' -Headers @{ 'tenant-id' = '1' } -Body $body -UseBasicParsing
  $j = $r.Content | ConvertFrom-Json
  if ($j.code -ne 0) { throw "login failed for $username : $($j.msg)" }
  return @{ Authorization = "Bearer $($j.data.accessToken)"; 'tenant-id' = '1' }
}
function Api($headers, $method, $path, $data) {
  $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
  if ($null -ne $data) {
    $params.ContentType = 'application/json;charset=UTF-8'
    $params.Body = [Text.Encoding]::UTF8.GetBytes(($data | ConvertTo-Json -Compress -Depth 10))
  }
  try { $r = Invoke-WebRequest @params } catch { $r = $_.Exception.Response; if ($null -eq $r) { throw }; $sr = New-Object IO.StreamReader($r.GetResponseStream()); $txt = $sr.ReadToEnd(); return ($txt | ConvertFrom-Json) }
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
function Merge($base, $extra) { $h = @{}; foreach ($k in $base.Keys) { $h[$k] = $base[$k] }; foreach ($k in $extra.Keys) { $h[$k] = $extra[$k] }; return $h }
function DownloadStatus($headers, $versionId) {
  try {
    $r = Invoke-WebRequest -Uri "$Base/clm/document/version/download?id=$versionId" -Headers $headers -UseBasicParsing
    if ($r.Headers['Content-Type'] -like 'application/json*') { $j = $r.Content | ConvertFrom-Json; return @{ code = $j.code; bytes = 0; msg = $j.msg } }
    return @{ code = 0; bytes = $r.RawContentLength; msg = '' }
  } catch { return @{ code = -1; bytes = 0; msg = $_.Exception.Message } }
}

Write-Host "== login users"
$admin = Login 'admin' 'admin123'     # user 1, super_admin
# local POC only: the seeded "test" user's password hash differs from admin's — reset it to a known value
$null = Api $admin PUT '/system/user/update-password' @{ id = 104; password = 'admin123' }
$yudao = Login 'yudao' 'admin123'     # user 100, super_admin+common — used as 法务审批 approver
$test  = Login 'test'  'admin123'     # user 104, common — no access to contracts
Check ($admin -and $yudao -and $test) 'three users logged in'

$stamp = Get-Date -Format 'yyyyMMddHHmmss'

Write-Host "== contract type"
$typeId = (Api $admin POST '/clm/contract-type/create' @{ code = "sales_$stamp"; name = "销售合同 $stamp"; description = 'e2e'; status = 0; sort = 1; processDefinitionKey = 'clm_contract_approval_v1' }).data
Check ($typeId -gt 0) "type created id=$typeId"
$versions = (Api $admin GET "/clm/contract-type/version/list?typeId=$typeId").data
$draft = $versions | Where-Object { $_.status -eq 0 } | Select-Object -First 1
Check ($draft.versionNo -eq 1) 'draft version 1 exists'
$fields = @(
  '{"type":"input","field":"projectName","title":"项目名称","$required":true,"props":{},"_fc_id":"id_p1","name":"ref_p1","_fc_drag_tag":"input","hidden":false,"display":true}',
  '{"type":"inputNumber","field":"paymentDays","title":"付款周期(天)","props":{},"_fc_id":"id_p2","name":"ref_p2","_fc_drag_tag":"inputNumber","hidden":false,"display":true}'
)
$r = Api $admin PUT '/clm/contract-type/version/update' @{ id = $draft.id; formConf = '{"form":{"labelWidth":"120px"}}'; formFields = $fields; processDefinitionKey = 'clm_contract_approval_v1'; remark = 'v1' }
Check ($r.code -eq 0) 'draft schema saved'
$r = Api $admin POST "/clm/contract-type/version/publish?id=$($draft.id)"
Check ($r.code -eq 0) 'version 1 published'
$r = Api $admin PUT '/clm/contract-type/version/update' @{ id = $draft.id; formConf = '{}'; formFields = @(); processDefinitionKey = 'clm_contract_approval_v1' }
Check ($r.code -ne 0) "published version is immutable (code=$($r.code) msg=$($r.msg))"
$type = (Api $admin GET "/clm/contract-type/get?id=$typeId").data
Check ($type.currentVersionId -eq $draft.id) 'type.currentVersionId points to published v1'
$draft2Id = (Api $admin POST "/clm/contract-type/version/create-draft?typeId=$typeId").data
Check ($draft2Id -gt 0) "draft v2 created id=$draft2Id (v1 unaffected)"

Write-Host "== parties"
$ourId = (Api $admin POST '/clm/party/create' @{ partyType = 1; name = "我方科技有限公司 $stamp"; unifiedCreditCode = '91310000MA1K3W0001'; internalFlag = $true; status = 0 }).data
$cpId  = (Api $admin POST '/clm/party/create' @{ partyType = 1; name = "客户集团股份有限公司 $stamp"; unifiedCreditCode = '91440300MA5D0002'; internalFlag = $false; status = 0 }).data
Check (($ourId -gt 0) -and ($cpId -gt 0)) "parties created our=$ourId cp=$cpId"

Write-Host "== contract draft (created directly, no request object)"
$contractBody = @{ title = "e2e 销售合同 $stamp"; typeId = $typeId; amount = 123456.78; currency = 'CNY'; signDate = '2026-08-24'; effectiveDate = '2026-09-01'; expiryDate = '2027-08-31'; description = 'e2e'; customData = @{ projectName = '一诺对标 POC'; paymentDays = 30 }; parties = @(@{ partyId = $ourId; roleCode = 'OUR_SIDE'; sort = 1 }, @{ partyId = $cpId; roleCode = 'COUNTERPARTY'; sort = 2 }) }
$r = Api $admin POST '/clm/contract/create' $contractBody
Check ($r.code -eq 0) "contract created (msg=$($r.msg))"
$contractId = $r.data
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.contractNo -like 'HT*') "contractNo generated: $($c.contractNo)"
Check ($c.approvalStatus -eq 0 -and $c.lifecycleStatus -eq 1) 'status NOT_SUBMITTED / DRAFT'
Check ($c.typeVersionId -eq $draft.id) 'contract pinned to type version v1'
Check ($c.permissions.canEdit -eq $true -and $c.permissions.canSubmit -eq $false) 'owner can edit; cannot submit without main document'
$bad = Api $admin POST '/clm/contract/create' @{ title = 'bad'; typeId = $typeId; customData = @{ paymentDays = 1 }; parties = $contractBody.parties }
Check ($bad.code -ne 0) "required custom field enforced server-side (msg=$($bad.msg))"
$bad2 = Api $admin POST '/clm/contract/create' @{ title = 'bad2'; typeId = $typeId; customData = @{ projectName = 'x'; hacker = 1 }; parties = $contractBody.parties }
Check ($bad2.code -ne 0) "unknown custom field rejected (msg=$($bad2.msg))"

Write-Host "== documents: upload same file twice -> v1, v2 (immutable)"
$docDir = 'D:\dev\clm\runtime\e2e'; New-Item -ItemType Directory -Force $docDir | Out-Null
$docx = Join-Path $docDir 'contract.docx'
if (-not (Test-Path $docx)) {
  # minimal but valid DOCX (zip with [Content_Types].xml + word/document.xml)
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $tmp = Join-Path $docDir 'docx_src'; New-Item -ItemType Directory -Force "$tmp\word" | Out-Null; New-Item -ItemType Directory -Force "$tmp\_rels" | Out-Null
  [IO.File]::WriteAllText("$tmp\[Content_Types].xml", '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
  [IO.File]::WriteAllText("$tmp\_rels\.rels", '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
  [IO.File]::WriteAllText("$tmp\word\document.xml", '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>销售合同 e2e 正文 v1</w:t></w:r></w:p></w:body></w:document>')
  if (Test-Path $docx) { Remove-Item $docx }
  [IO.Compression.ZipFile]::CreateFromDirectory($tmp, $docx)
}
$v1 = Upload $admin '/clm/contract/document/upload' @{ contractId = $contractId; roleCode = 'MAIN'; remark = 'v1' } $docx
Check ($v1.code -eq 0) "upload v1 ok (msg=$($v1.msg))"
$v2 = Upload $admin '/clm/contract/document/upload' @{ contractId = $contractId; roleCode = 'MAIN'; remark = 'v2 same content' } $docx
Check ($v2.code -eq 0 -and $v2.data -ne $v1.data) 'upload again produced a NEW version id'
$docs = (Api $admin GET "/clm/contract/document/list?contractId=$contractId").data
$main = $docs | Where-Object { $_.roleCode -eq 'MAIN' }
Check ($main.versions.Count -eq 2) "MAIN has 2 versions"
$ver1 = $main.versions | Where-Object { $_.versionNo -eq 1 }; $ver2 = $main.versions | Where-Object { $_.versionNo -eq 2 }
Check ($ver1.checksumSha256.Length -eq 64 -and $ver1.checksumSha256 -eq $ver2.checksumSha256) 'sha256 present and identical for identical content'
Check ($main.currentVersionId -eq $ver2.id) 'document current version = v2'
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.currentDocumentVersionId -eq $ver2.id -and $c.permissions.canSubmit -eq $true) 'contract current main version = v2; canSubmit now true'

Write-Host "== ACL before submit"
$r = Api $test GET "/clm/contract/get?id=$contractId"
Check ($r.code -ne 0) "unauthorized user cannot read contract (code=$($r.code))"
$page = (Api $test GET "/clm/contract/page?pageNo=1&pageSize=50").data
Check (($page.list | Where-Object { $_.id -eq $contractId }).Count -eq 0) 'unauthorized user does not see it in the list'
$d = DownloadStatus $test $ver2.id
Check ($d.code -ne 0) "unauthorized user cannot download (code=$($d.code))"
$d = DownloadStatus $admin $ver2.id
Check ($d.code -eq 0 -and $d.bytes -gt 0) "owner download ok ($($d.bytes) bytes)"
$r = Api $yudao GET "/clm/contract/get?id=$contractId"
Check ($r.code -ne 0) 'future approver (yudao) has no access before the process starts, despite super_admin role'

Write-Host "== submit approval"
$prev = (Api $admin GET "/clm/contract/approval-preview?id=$contractId").data
Check ($prev.processDefinitionId) "active process definition found: $($prev.processDefinitionId)"
$r = Api $admin POST '/clm/contract/submit' @{ id = $contractId; remark = 'e2e submit' }
Check ($r.code -eq 0) "submit ok (msg=$($r.msg))"
$bindingId = $r.data
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.approvalStatus -eq 1 -and $c.currentBindingId -eq $bindingId) 'contract RUNNING with binding'
$bindings = (Api $admin GET "/clm/workflow-binding/list?contractId=$contractId").data
$b = $bindings | Where-Object { $_.id -eq $bindingId }
Check ($b.status -eq 1 -and $b.processInstanceId -and $b.documentVersionId -eq $ver2.id) "binding RUNNING, instance=$($b.processInstanceId), bound to v2"
$ver2b = (Api $admin GET "/clm/document/version/get?id=$($ver2.id)").data
Check ($ver2b.frozen -eq $true) 'bound version frozen'
$up = Upload $admin '/clm/contract/document/upload' @{ contractId = $contractId; roleCode = 'MAIN' } $docx
Check ($up.code -ne 0) "upload blocked while approval RUNNING (msg=$($up.msg))"
$r = Api $admin PUT '/clm/contract/update' (Merge $contractBody @{ id = $contractId; title = 'changed' })
Check ($r.code -ne 0) "edit blocked while approval RUNNING (msg=$($r.msg))"

Write-Host "== approver (yudao) sees the bound snapshot via binding detail and can download the bound version"
$det = Api $yudao GET "/clm/workflow-binding/get?id=$bindingId"
Check ($det.code -eq 0 -and $det.data.documentVersion.id -eq $ver2.id) 'approver can open binding detail (process participant)'
$d = DownloadStatus $yudao $ver2.id
Check ($d.code -eq 0 -and $d.bytes -gt 0) 'approver can download the bound version'

Write-Host "== approve task 1 as yudao"
$todo = (Api $yudao GET '/bpm/task/todo-page?pageNo=1&pageSize=50').data.list | Where-Object { $_.processInstance.id -eq $b.processInstanceId }
Check (@($todo).Count -ge 1) "yudao has a todo task for the instance ($(@($todo).Count))"
$r = Api $yudao PUT '/bpm/task/approve' @{ id = @($todo)[0].id; reason = '法务通过' }
Check ($r.code -eq 0) "task 1 approved (msg=$($r.msg))"
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.approvalStatus -eq 1) 'still RUNNING after first approval'

Write-Host "== approve task 2 as admin"
$todo2 = (Api $admin GET '/bpm/task/todo-page?pageNo=1&pageSize=50').data.list | Where-Object { $_.processInstance.id -eq $b.processInstanceId }
Check (@($todo2).Count -ge 1) "admin has a todo task ($(@($todo2).Count))"
$r = Api $admin PUT '/bpm/task/approve' @{ id = @($todo2)[0].id; reason = '负责人通过' }
Check ($r.code -eq 0) "task 2 approved (msg=$($r.msg))"
Start-Sleep -Seconds 1
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.approvalStatus -eq 2 -and $c.lifecycleStatus -eq 2) "contract APPROVED (approval=$($c.approvalStatus), lifecycle=$($c.lifecycleStatus))"
$b = (Api $admin GET "/clm/workflow-binding/list?contractId=$contractId").data | Where-Object { $_.id -eq $bindingId }
Check ($b.status -eq 2) 'binding APPROVED via listener'
$audit = (Api $admin GET "/clm/contract/audit-event/page?contractId=$contractId&pageNo=1&pageSize=100").data.list
$actions = $audit | ForEach-Object { $_.action }
Check (($actions -contains 'CONTRACT_CREATE') -and ($actions -contains 'DOCUMENT_UPLOAD') -and ($actions -contains 'CONTRACT_SUBMIT') -and ($actions -contains 'APPROVAL_RESULT') -and ($actions -contains 'DOCUMENT_DOWNLOAD')) "audit trail has create/upload/submit/result/download ($($actions.Count) events)"
$c = (Api $admin GET "/clm/contract/get?id=$contractId").data
Check ($c.permissions.canSubmit -eq $false) 'nothing new to approve -> canSubmit false'

Write-Host "== participants: grant test user view+download, then revoke"
$r = Api $admin PUT '/clm/contract/participant/save' @{ contractId = $contractId; participants = @(@{ principalType = 'USER'; principalId = 104; roleCode = 'VIEWER'; canView = $true; canEdit = $false; canDownload = $true; canManage = $false }) }
Check ($r.code -eq 0) 'participant granted'
$r = Api $test GET "/clm/contract/get?id=$contractId"
Check ($r.code -eq 0) 'test user can now read the contract'
$d = DownloadStatus $test $ver2.id
Check ($d.code -eq 0 -and $d.bytes -gt 0) 'test user can now download'
$r = Api $admin PUT '/clm/contract/participant/save' @{ contractId = $contractId; participants = @() }
Check ($r.code -eq 0) 'participant revoked'
$r = Api $test GET "/clm/contract/get?id=$contractId"
Check ($r.code -ne 0) 'test user denied again immediately'

Write-Host "== reject path on a second contract"
$r = Api $admin POST '/clm/contract/create' (Merge $contractBody @{ title = "e2e 驳回合同 $stamp" })
$c2 = $r.data
$v = Upload $admin '/clm/contract/document/upload' @{ contractId = $c2; roleCode = 'MAIN' } $docx
$r = Api $admin POST '/clm/contract/submit' @{ id = $c2 }
Check ($r.code -eq 0) 'second contract submitted'
$b2 = (Api $admin GET "/clm/workflow-binding/list?contractId=$c2").data | Select-Object -First 1
$todo = (Api $yudao GET '/bpm/task/todo-page?pageNo=1&pageSize=50').data.list | Where-Object { $_.processInstance.id -eq $b2.processInstanceId }
$r = Api $yudao PUT '/bpm/task/reject' @{ id = @($todo)[0].id; reason = '条款不符' }
Check ($r.code -eq 0) "task rejected (msg=$($r.msg))"
Start-Sleep -Seconds 1
$c = (Api $admin GET "/clm/contract/get?id=$c2").data
Check ($c.approvalStatus -eq 3 -and $c.lifecycleStatus -eq 1) "contract REJECTED, lifecycle stays DRAFT"
$v2b = Upload $admin '/clm/contract/document/upload' @{ contractId = $c2; roleCode = 'MAIN'; remark = 'fix after reject' } $docx
Check ($v2b.code -eq 0) 'new version allowed after reject'
$r = Api $admin POST '/clm/contract/submit' @{ id = $c2 }
Check ($r.code -eq 0) 'resubmit creates a new binding'
$bl = (Api $admin GET "/clm/workflow-binding/list?contractId=$c2").data
Check (@($bl).Count -eq 2 -and @($bl | Where-Object { $_.status -eq 3 }).Count -eq 1) 'old rejected binding preserved, new binding running'

Write-Host ""
if ($script:Failures -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green } else { Write-Host "$($script:Failures) CHECK(S) FAILED" -ForegroundColor Red; exit 1 }
