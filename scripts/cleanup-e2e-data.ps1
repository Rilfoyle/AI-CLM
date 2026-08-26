# Remove old e2e test data before seeding demo data (logical delete + cancel running instances).
# Only touches rows whose title/name starts with 'e2e ' (contracts) or code starts with sales_/oo_ (types).
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Base = 'http://127.0.0.1:48080/admin-api'

function Login($username, $password) {
  $body = [Text.Encoding]::UTF8.GetBytes((@{ username = $username; password = $password; tenantName = '芋道源码' } | ConvertTo-Json -Compress))
  $r = Invoke-WebRequest -Uri "$Base/system/auth/login" -Method POST -ContentType 'application/json;charset=UTF-8' -Headers @{ 'tenant-id' = '1' } -Body $body -UseBasicParsing
  $j = $r.Content | ConvertFrom-Json
  if ($j.code -ne 0) { throw "login failed: $($j.msg)" }
  return @{ Authorization = "Bearer $($j.data.accessToken)"; 'tenant-id' = '1' }
}
function Api($headers, $method, $path, $data) {
  $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
  if ($null -ne $data) { $params.ContentType = 'application/json;charset=UTF-8'; $params.Body = [Text.Encoding]::UTF8.GetBytes(($data | ConvertTo-Json -Compress -Depth 10)) }
  try { $r = Invoke-WebRequest @params } catch { $r = $_.Exception.Response; if ($null -eq $r) { throw }; $sr = New-Object IO.StreamReader($r.GetResponseStream()); return ($sr.ReadToEnd() | ConvertFrom-Json) }
  return ($r.Content | ConvertFrom-Json)
}

$admin = Login 'admin' 'admin123'

# 1) cancel running approval instances of e2e contracts (started by admin)
$page = (Api $admin GET '/clm/contract/page?pageNo=1&pageSize=100').data
$e2e = @($page.list | Where-Object { $_.title -like 'e2e *' })
Write-Host "e2e contracts visible: $($e2e.Count)"
foreach ($c in $e2e) {
  if ($c.approvalStatus -eq 1 -and $c.currentBindingId) {
    $bindings = (Api $admin GET "/clm/workflow-binding/list?contractId=$($c.id)").data
    $b = $bindings | Where-Object { $_.id -eq $c.currentBindingId }
    if ($b -and $b.processInstanceId) {
      $r = Api $admin DELETE "/bpm/process-instance/cancel-by-start-user" @{ id = $b.processInstanceId; reason = '演示数据清场' }
      Write-Host "  cancel instance $($b.processInstanceId): code=$($r.code) $($r.msg)"
    }
  }
}

# 2) logical delete via SQL (contracts + related rows + e2e types/parties)
. "$PSScriptRoot\..\infra\env.ps1"
$sql = @"
UPDATE clm_contract SET deleted=1 WHERE title LIKE 'e2e %';
UPDATE clm_contract_type SET deleted=1 WHERE code LIKE 'sales\_%' OR code LIKE 'oo\_%';
UPDATE clm_party SET deleted=1 WHERE name LIKE '%科技有限公司 20%' OR name LIKE '%集团股份有限公司 20%';
SELECT COUNT(*) AS remaining_contracts FROM clm_contract WHERE deleted=0;
"@
$tmp = Join-Path $env:TEMP 'cleanup_e2e.sql'
[IO.File]::WriteAllText($tmp, $sql, [Text.UTF8Encoding]::new($false))
cmd /c "`"$env:MYSQL_HOME\bin\mysql.exe`" --host=127.0.0.1 --user=root --password=$env:CLM_DB_PASSWORD --default-character-set=utf8mb4 ruoyi-vue-pro < `"$tmp`"" 2>&1 | Where-Object { $_ -notmatch 'Warning' }
Write-Host 'cleanup done'
