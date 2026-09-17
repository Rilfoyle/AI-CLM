# Remove old e2e test data before seeding demo data (physical cleanup + cancel running instances).
# Only touches rows whose title/name starts with 'e2e ' (contracts) or code starts with sales_/oo_ (types).
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Base = 'http://127.0.0.1:48080/admin-api'
$ClmRoot = Split-Path -Parent $PSScriptRoot

function Login($username, $password) {
  $body = [Text.Encoding]::UTF8.GetBytes((@{ username = $username; password = $password; tenantName = 'TuriX' } | ConvertTo-Json -Compress))
  $r = Invoke-WebRequest -Uri "$Base/system/auth/login" -Method POST -ContentType 'application/json;charset=UTF-8' -Headers @{ 'tenant-id' = '1' } -Body $body -UseBasicParsing
  $j = $r.Content | ConvertFrom-Json
  if ($j.code -ne 0) { throw "login failed: $($j.msg)" }
  return @{ Authorization = "Bearer $($j.data.accessToken)"; 'tenant-id' = '1' }
}
function Api($headers, $method, $path, $data) {
  $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
  if ($PSVersionTable.PSVersion.Major -ge 7) { $params['SkipHttpErrorCheck'] = $true }
  if ($null -ne $data) { $params.ContentType = 'application/json;charset=UTF-8'; $params.Body = [Text.Encoding]::UTF8.GetBytes(($data | ConvertTo-Json -Compress -Depth 10)) }
  try { $r = Invoke-WebRequest @params } catch {
    $txt = $_.ErrorDetails.Message
    $response = $_.Exception.Response
    if (-not $txt -and $response -and $response.PSObject.Methods['GetResponseStream']) {
      $sr = New-Object IO.StreamReader($response.GetResponseStream()); $txt = $sr.ReadToEnd(); $sr.Close()
    }
    if (-not $txt) { throw }
    return ($txt | ConvertFrom-Json)
  }
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

# 2) physically delete only the script-owned e2e rows, child-first. This keeps repeated local runs from
#    accumulating active document/version/blob rows behind logically deleted contracts.
$onWindows = $env:OS -eq 'Windows_NT'
$infraDir = Join-Path $ClmRoot 'infra'
$envFile = Join-Path $infraDir '.env'
if (Test-Path $envFile) {
  Get-Content $envFile | Where-Object { $_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$' } | ForEach-Object {
    $key = $Matches[1]
    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
      Set-Item -Path "Env:$key" -Value $Matches[2].Trim()
    }
  }
}
$sql = @"
CREATE TEMPORARY TABLE tmp_clm_e2e_contract_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_clm_e2e_contract_ids SELECT id FROM clm_contract WHERE title LIKE 'e2e %';
CREATE TEMPORARY TABLE tmp_clm_e2e_type_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_clm_e2e_type_ids SELECT id FROM clm_contract_type WHERE code LIKE 'sales\_%' OR code LIKE 'oo\_%';
CREATE TEMPORARY TABLE tmp_clm_e2e_blob_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO tmp_clm_e2e_blob_ids
  SELECT CAST(file_key AS UNSIGNED) FROM clm_document_version
  WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids) AND file_key REGEXP '^[0-9]+$';
INSERT IGNORE INTO tmp_clm_e2e_blob_ids
  SELECT CAST(template_file_key AS UNSIGNED) FROM clm_contract_type
  WHERE id IN (SELECT id FROM tmp_clm_e2e_type_ids) AND template_file_key REGEXP '^[0-9]+$';
DELETE FROM clm_audit_event WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_workflow_binding WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_contract_participant WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_contract_party WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_document_version WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_document WHERE contract_id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_contract WHERE id IN (SELECT id FROM tmp_clm_e2e_contract_ids);
DELETE FROM clm_contract_type_version WHERE type_id IN (SELECT id FROM tmp_clm_e2e_type_ids);
DELETE FROM clm_contract_type WHERE id IN (SELECT id FROM tmp_clm_e2e_type_ids);
DELETE FROM clm_document_blob WHERE id IN (SELECT id FROM tmp_clm_e2e_blob_ids);
DELETE FROM clm_party WHERE name LIKE '%科技有限公司 20%' OR name LIKE '%集团股份有限公司 20%';
DROP TEMPORARY TABLE tmp_clm_e2e_blob_ids;
DROP TEMPORARY TABLE tmp_clm_e2e_type_ids;
DROP TEMPORARY TABLE tmp_clm_e2e_contract_ids;
SELECT COUNT(*) AS remaining_contracts FROM clm_contract WHERE deleted=0;
"@
$tmp = Join-Path ([IO.Path]::GetTempPath()) 'cleanup_e2e.sql'
[IO.File]::WriteAllText($tmp, $sql, [Text.UTF8Encoding]::new($false))
$dbUser = $env:CLM_DB_USERNAME
if (-not $dbUser) { $dbUser = 'root' }
$dbPass = $env:CLM_DB_PASSWORD

$dockerExe = $null
$useDockerMysql = $false
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
  $dockerExe = $dockerCommand.Source
  $inspectEap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $dockerState = & $dockerExe inspect --format '{{.State.Running}}' clm-mysql 2>$null
  $useDockerMysql = ($LASTEXITCODE -eq 0 -and ([string]$dockerState).Trim() -eq 'true')
  $ErrorActionPreference = $inspectEap
}

$mysqlExe = $null
if (-not $useDockerMysql) {
  if ($onWindows) {
    $mysqlHome = $env:MYSQL_HOME
    if (-not $mysqlHome) {
      $runtimeDir = Join-Path $ClmRoot 'runtime'
      $toolsDir = Join-Path $runtimeDir 'tools'
      $mysqlHome = Join-Path $toolsDir 'mysql-8.0.33-winx64'
    }
    $mysqlExe = Join-Path (Join-Path $mysqlHome 'bin') 'mysql.exe'
    if (-not (Test-Path $mysqlExe)) { $mysqlExe = $null }
  }
  if (-not $mysqlExe) {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($mysqlCommand) { $mysqlExe = $mysqlCommand.Source }
  }
  if (-not $mysqlExe) { throw 'no usable MySQL client (clm-mysql container or mysql executable)' }
}

$previousMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $dbPass
$nativeEap = $ErrorActionPreference
try {
  $ErrorActionPreference = 'Continue'
  $sqlInput = [IO.File]::ReadAllText($tmp)
  if ($useDockerMysql) {
    $out = $sqlInput | & $dockerExe exec -i -e MYSQL_PWD clm-mysql mysql "-u$dbUser" '--default-character-set=utf8mb4' 'ruoyi-vue-pro' 2>&1
  } else {
    $out = $sqlInput | & $mysqlExe '--host=127.0.0.1' "--user=$dbUser" '--default-character-set=utf8mb4' 'ruoyi-vue-pro' 2>&1
  }
  $mysqlOk = $LASTEXITCODE -eq 0
} finally {
  $ErrorActionPreference = $nativeEap
  $env:MYSQL_PWD = $previousMysqlPwd
}
$out | Where-Object { $_ -notmatch 'Warning' }
if (-not $mysqlOk) { throw 'cleanup SQL failed' }
Write-Host 'cleanup done'
