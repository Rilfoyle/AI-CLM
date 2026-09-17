param(
  [switch]$ConfirmDestructive,
  [switch]$AllowNativeMySql,
  [string]$NativeMySqlServerUuid = ''
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot 'lib/YinuoV1.ps1')

$repoRoot = Split-Path -Parent $PSScriptRoot
$databaseName = 'ruoyi-vue-pro'
$baseSqlPath = Join-Path $repoRoot 'backend/sql/mysql/ruoyi-vue-pro.sql'
$migrationRoot = Join-Path $repoRoot 'sql'
$bootstrapSqlPath = Join-Path $migrationRoot 'bootstrap/turix_empty_baseline.sql'
$composeFile = Join-Path $repoRoot 'infra/compose.onlyoffice.yaml'
$envFile = Join-Path $repoRoot 'infra/.env'
$expectedMigrationNames = @(
  '01_bpm_tables.sql',
  '02_clm_tables.sql',
  '03_clm_menus_dicts.sql',
  '04_menu_trim.sql',
  '05_menu_redesign.sql',
  '06_demo_upgrade.sql',
  '07_process_menu_optimize.sql',
  '08_yinuo_v1_schema.sql',
  '09_yinuo_v1_roles_menus.sql',
  '10_yinuo_v1_collaboration_support.sql',
  '11_yinuo_v1_approval_edit.sql',
  '12_yinuo_v1_process_designer.sql',
  '13_yinuo_menu_alignment.sql'
)

if (-not $ConfirmDestructive) {
  throw @"
This reset permanently deletes the local '$databaseName' database, all CLM
business data, sample tenants, users, logs and cached sessions. Re-run with
-ConfirmDestructive after verifying that the target is the disposable local
AI-CLM environment.
"@
}

foreach ($requiredPath in @($baseSqlPath, $bootstrapSqlPath, $composeFile, $envFile)) {
  if (-not (Test-Path $requiredPath -PathType Leaf)) {
    throw "required reset input is missing: $requiredPath"
  }
}
$actualMigrationNames = @(Get-ChildItem $migrationRoot -File -Filter '*.sql' | Sort-Object Name | ForEach-Object Name)
if (Compare-Object $expectedMigrationNames $actualMigrationNames) {
  throw "root SQL migration set differs from the locked 01-13 list: actual=$($actualMigrationNames -join ',')"
}
$migrationFiles = @($expectedMigrationNames | ForEach-Object {
  $migrationPath = Join-Path $migrationRoot $_
  if (-not (Test-Path $migrationPath -PathType Leaf)) { throw "required migration is missing: $migrationPath" }
  Get-Item $migrationPath
})

# Refuse to race a running application. The caller must stop the backend first;
# this keeps the reset script usable on both macOS and Windows.
$backendSocket = [Net.Sockets.TcpClient]::new()
$backendListening = $false
try {
  $connectTask = $backendSocket.ConnectAsync('127.0.0.1', 48080)
  try {
    $null = $connectTask.Wait(2000)
    $backendListening = $backendSocket.Connected
  } catch {
    $backendListening = $false
  }
} finally {
  $backendSocket.Dispose()
}
if ($backendListening) {
  throw 'backend is listening on 127.0.0.1:48080; fully stop/unload it before resetting the database'
}

# Resolve and validate every external dependency before the first destructive
# database statement. A failed preflight must leave the current database intact.
$runner = Get-YinuoMysqlRunner -RepoRoot $repoRoot
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) { throw 'the full local reset requires Docker for Redis and ONLYOFFICE cleanup' }
$expectedComposeWorkingDirectory = (Resolve-Path (Join-Path $repoRoot 'infra')).Path
if ($env:CLM_DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost):3306/ruoyi-vue-pro\?') {
  throw 'CLM_DB_URL is not the locked local ruoyi-vue-pro endpoint; refusing destructive reset'
}
if ($env:CLM_REDIS_HOST -notin @('127.0.0.1', 'localhost') -or "$($env:CLM_REDIS_PORT)" -ne '6379') {
  throw 'CLM_REDIS_HOST/PORT is not the locked local clm-redis endpoint; refusing destructive reset'
}
if ($env:CLM_ONLYOFFICE_URL -notin @('http://127.0.0.1:8090', 'http://localhost:8090')) {
  throw 'CLM_ONLYOFFICE_URL is not the locked local clm-onlyoffice endpoint; refusing destructive reset'
}
if ($runner.kind -eq 'docker') {
  $mysqlLabelsJson = & $dockerCommand.Source inspect clm-mysql --format '{{json .Config.Labels}}' 2>$null
  if ($LASTEXITCODE -ne 0) { throw 'the project clm-mysql container is not available; no database changes were made' }
  $mysqlLabels = $mysqlLabelsJson | ConvertFrom-Json
  if ($mysqlLabels.'com.docker.compose.service' -ne 'mysql' -or
      $mysqlLabels.'com.docker.compose.project.working_dir' -ne $expectedComposeWorkingDirectory -or
      $mysqlLabels.'com.docker.compose.project.environment_file' -ne $envFile) {
    throw 'clm-mysql is not owned by this AI-CLM checkout; refusing to target it'
  }
} else {
  if (-not $AllowNativeMySql -or -not $NativeMySqlServerUuid) {
    throw 'native MySQL reset is locked: pass both -AllowNativeMySql and the exact -NativeMySqlServerUuid after independently verifying the local server'
  }
  if ($NativeMySqlServerUuid -notmatch '^[0-9a-fA-F-]{16,64}$') {
    throw 'NativeMySqlServerUuid has an invalid format; no database changes were made'
  }
}
$redisLabelsJson = & $dockerCommand.Source inspect clm-redis --format '{{json .Config.Labels}}' 2>$null
if ($LASTEXITCODE -ne 0) { throw 'the project clm-redis container is not available; no database changes were made' }
$redisLabels = $redisLabelsJson | ConvertFrom-Json
if ($redisLabels.'com.docker.compose.service' -ne 'redis' -or
    $redisLabels.'com.docker.compose.project.working_dir' -ne $expectedComposeWorkingDirectory -or
    $redisLabels.'com.docker.compose.project.environment_file' -ne $envFile) {
  throw 'clm-redis is not owned by this AI-CLM checkout; refusing to flush it'
}
$redisRunning = & $dockerCommand.Source inspect --format '{{.State.Running}}' clm-redis 2>$null
if ($LASTEXITCODE -ne 0 -or ([string]$redisRunning).Trim() -ne 'true') {
  throw 'clm-redis must be running so the reset can invalidate every old token and permission cache'
}
$null = & $dockerCommand.Source compose --env-file $envFile -f $composeFile config --quiet 2>&1
if ($LASTEXITCODE -ne 0) { throw 'ONLYOFFICE compose/env preflight failed; no database changes were made' }

$namedCacheVolumes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$onlyOfficeExists = & $dockerCommand.Source inspect --format '{{.Name}}' clm-onlyoffice 2>$null
$onlyOfficePresent = $LASTEXITCODE -eq 0 -and ([string]$onlyOfficeExists).Trim() -eq '/clm-onlyoffice'
if (-not $onlyOfficePresent) {
  throw 'clm-onlyoffice must exist before reset so checkout ownership and exact cache volumes can be verified safely'
}
$onlyOfficeLabelsJson = & $dockerCommand.Source inspect clm-onlyoffice --format '{{json .Config.Labels}}'
if ($LASTEXITCODE -ne 0) { throw 'failed to inspect clm-onlyoffice labels; no database changes were made' }
$onlyOfficeLabels = $onlyOfficeLabelsJson | ConvertFrom-Json
if ($onlyOfficeLabels.'com.docker.compose.service' -ne 'onlyoffice' -or
    $onlyOfficeLabels.'com.docker.compose.project.working_dir' -ne $expectedComposeWorkingDirectory -or
    $onlyOfficeLabels.'com.docker.compose.project.environment_file' -ne $envFile) {
  throw 'clm-onlyoffice is not owned by this AI-CLM checkout; refusing to remove it'
}
$mountJson = & $dockerCommand.Source inspect clm-onlyoffice --format '{{json .Mounts}}'
if ($LASTEXITCODE -ne 0) { throw 'failed to inspect clm-onlyoffice mounts; no database changes were made' }
foreach ($mount in @($mountJson | ConvertFrom-Json)) {
  if ($mount.Type -eq 'volume' -and $mount.Name -and
      $mount.Destination -in @('/var/www/onlyoffice/Data', '/var/log/onlyoffice')) {
    $null = $namedCacheVolumes.Add([string]$mount.Name)
  }
}
if ($namedCacheVolumes.Count -ne 2) {
  throw "expected exactly two project ONLYOFFICE cache volumes, found $($namedCacheVolumes.Count)"
}
foreach ($volumeName in $namedCacheVolumes) {
  if ($volumeName -notmatch '^[A-Za-z0-9_.-]*clm-onlyoffice-(data|logs)$') {
    throw "refusing to remove unexpected ONLYOFFICE volume '$volumeName'; no database changes were made"
  }
  $volumeLabelsJson = & $dockerCommand.Source volume inspect $volumeName --format '{{json .Labels}}' 2>$null
  if ($LASTEXITCODE -ne 0) { throw "failed to inspect ONLYOFFICE volume '$volumeName'" }
  $volumeLabels = $volumeLabelsJson | ConvertFrom-Json
  if ($volumeLabels.'com.docker.compose.project' -ne $onlyOfficeLabels.'com.docker.compose.project' -or
      $volumeLabels.'com.docker.compose.volume' -notin @('clm-onlyoffice-data', 'clm-onlyoffice-logs')) {
    throw "ONLYOFFICE volume '$volumeName' is not owned by the verified compose project"
  }
}

$mysqlProbe = @(Invoke-YinuoMysql -Runner $runner -Sql "SELECT CONCAT(@@server_uuid, '|', @@version, '|', @@port, '|', USER());" -SkipHeaders -Database '')
if ($mysqlProbe.Count -ne 1 -or $mysqlProbe[0] -notmatch '^([^|]+)\|[^|]+\|[0-9]+\|[^|]+$') {
  throw 'local MySQL identity preflight failed; no database changes were made'
}
if ($runner.kind -eq 'native' -and $Matches[1] -ne $NativeMySqlServerUuid) {
  throw "native MySQL UUID mismatch (actual=$($Matches[1])); no database changes were made"
}
Write-Host "== Preflight passed (mysql=$($runner.kind), server=$($mysqlProbe[0]))"

$destructivePhaseStarted = $true
try {
Write-Host "== Recreate local database '$databaseName'"
$createDatabaseSql = @"
DROP DATABASE IF EXISTS ``$databaseName``;
CREATE DATABASE ``$databaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"@
$null = Invoke-YinuoMysql -Runner $runner -Sql $createDatabaseSql -Database 'mysql'

Write-Host '== Import upstream schema dump'
$null = Invoke-YinuoMysql -Runner $runner -Sql (Get-Content $baseSqlPath -Raw) -Database $databaseName

Write-Host '== Apply product schema migrations'
foreach ($migrationFile in $migrationFiles) {
  Write-Host "   $($migrationFile.Name)"
  $null = Invoke-YinuoMysql -Runner $runner -Sql (Get-Content $migrationFile.FullName -Raw) -Database $databaseName
}

Write-Host '== Apply destructive TuriX empty-baseline bootstrap'
$null = Invoke-YinuoMysql -Runner $runner -Sql (Get-Content $bootstrapSqlPath -Raw) -Database $databaseName

Write-Host '== Flush local authentication and permission cache'
$redisResult = & $dockerCommand.Source exec clm-redis redis-cli FLUSHDB
if ($LASTEXITCODE -ne 0 -or ([string]$redisResult).Trim() -ne 'OK') {
  throw "failed to flush clm-redis: $redisResult"
}

Write-Host '== Recreate project-scoped ONLYOFFICE cache volumes'
if ($onlyOfficePresent) {
  $null = & $dockerCommand.Source rm -f -v clm-onlyoffice
  if ($LASTEXITCODE -ne 0) { throw 'failed to remove clm-onlyoffice container' }
}
foreach ($volumeName in $namedCacheVolumes) {
  $null = & $dockerCommand.Source volume rm $volumeName
  if ($LASTEXITCODE -ne 0) { throw "failed to remove ONLYOFFICE volume '$volumeName'" }
}
$composeOutput = & $dockerCommand.Source compose --env-file $envFile -f $composeFile up -d
if ($LASTEXITCODE -ne 0) { throw "failed to start clean ONLYOFFICE: $composeOutput" }
$onlyOfficeDeadline = [DateTime]::UtcNow.AddMinutes(3)
do {
  $onlyOfficeHealth = & $dockerCommand.Source inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' clm-onlyoffice 2>$null
  if ($LASTEXITCODE -eq 0 -and ([string]$onlyOfficeHealth).Trim() -eq 'healthy') { break }
  Start-Sleep -Seconds 2
} while ([DateTime]::UtcNow -lt $onlyOfficeDeadline)
if (([string]$onlyOfficeHealth).Trim() -ne 'healthy') {
  throw "clean clm-onlyoffice did not become healthy (status=$onlyOfficeHealth)"
}

Write-Host '== Verify the clean TuriX identity and configuration baseline'
$verificationSql = @"
SELECT 'tenant_total', COUNT(*) FROM system_tenant;
SELECT 'tenant_turix', COUNT(*) FROM system_tenant WHERE id=1 AND name='TuriX' AND package_id=0 AND deleted=b'0';
SELECT 'user_total', COUNT(*) FROM system_users WHERE deleted=b'0';
SELECT 'fixed_users', COUNT(*) FROM system_users WHERE tenant_id=1 AND deleted=b'0' AND username IN ('admin','business','legal','contractadmin','systemadmin');
SELECT 'role_total', COUNT(*) FROM system_role WHERE deleted=b'0';
SELECT 'product_roles', COUNT(*) FROM system_role WHERE tenant_id=1 AND deleted=b'0' AND code IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin');
SELECT 'product_role_unique', COUNT(*) FROM (
  SELECT code FROM system_role
  WHERE tenant_id=1 AND deleted=b'0' AND code IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin')
  GROUP BY code HAVING COUNT(*)=1
) role_cardinality;
SELECT 'department_total', COUNT(*) FROM system_dept WHERE tenant_id=1 AND deleted=b'0';
SELECT 'user_role_total', COUNT(*) FROM system_user_role WHERE tenant_id=1 AND deleted=b'0';
SELECT 'fixed_user_role_pairs', COUNT(DISTINCT CONCAT(u.username, '|', r.code))
FROM system_user_role ur
JOIN system_users u ON u.id=ur.user_id AND u.tenant_id=1 AND u.deleted=b'0'
JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=1 AND r.deleted=b'0'
WHERE ur.tenant_id=1 AND ur.deleted=b'0'
  AND ((u.username='admin' AND r.code='super_admin')
    OR (u.username='business' AND r.code='clm_business')
    OR (u.username='legal' AND r.code='clm_legal')
    OR (u.username='contractadmin' AND r.code='clm_contract_admin')
    OR (u.username='systemadmin' AND r.code='clm_system_admin'));
SELECT 'unexpected_user_role_pairs', COUNT(*)
FROM system_user_role ur
JOIN system_users u ON u.id=ur.user_id AND u.tenant_id=1 AND u.deleted=b'0'
JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=1 AND r.deleted=b'0'
WHERE ur.tenant_id=1 AND ur.deleted=b'0'
  AND NOT ((u.username='admin' AND r.code='super_admin')
    OR (u.username='business' AND r.code='clm_business')
    OR (u.username='legal' AND r.code='clm_legal')
    OR (u.username='contractadmin' AND r.code='clm_contract_admin')
    OR (u.username='systemadmin' AND r.code='clm_system_admin'));
SELECT 'invalid_menu_total', COUNT(*) FROM system_menu WHERE id NOT BETWEEN 7000 AND 7999 AND id NOT IN (1,100,101,103,1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1017,1018,1019,1020,1063,1064,1065);
SELECT 'oauth_client_total', COUNT(*) FROM system_oauth2_client WHERE deleted=b'0';
SELECT 'oauth_client_turix', COUNT(*) FROM system_oauth2_client WHERE client_id='default' AND name='TuriX 管理端' AND deleted=b'0';
SELECT 'oauth_client_minimal', COUNT(*) FROM system_oauth2_client
WHERE client_id='default' AND name='TuriX 管理端' AND deleted=b'0'
  AND redirect_uris='[]' AND authorized_grant_types='[]' AND scopes='[]'
  AND auto_approve_scopes='[]' AND authorities='[]' AND resource_ids='[]'
  AND additional_information='{}' AND CHAR_LENGTH(secret)=64;
SELECT 'tenant_package_total', COUNT(*) FROM system_tenant_package;
SELECT 'bpm_category_total', COUNT(*) FROM bpm_category WHERE deleted=b'0';
SELECT 'bpm_category_contract', COUNT(*) FROM bpm_category WHERE tenant_id=1 AND code='contract' AND deleted=b'0';
SELECT 'file_config_total', COUNT(*) FROM infra_file_config WHERE deleted=b'0';
SELECT 'communication_payload_total',
  (SELECT COUNT(*) FROM system_mail_account) +
  (SELECT COUNT(*) FROM system_mail_template) +
  (SELECT COUNT(*) FROM system_mail_log) +
  (SELECT COUNT(*) FROM system_sms_channel) +
  (SELECT COUNT(*) FROM system_sms_template) +
  (SELECT COUNT(*) FROM system_sms_log) +
  (SELECT COUNT(*) FROM system_notify_template) +
  (SELECT COUNT(*) FROM infra_file) +
  (SELECT COUNT(*) FROM infra_file_content);
SELECT 'global_sample_total', (SELECT COUNT(*) FROM infra_job) + (SELECT COUNT(*) FROM infra_job_log) + (SELECT COUNT(*) FROM infra_codegen_table) + (SELECT COUNT(*) FROM infra_codegen_column);
SELECT 'demo_table_total', COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'yudao_demo%';
SELECT 'dict_type_total', COUNT(*) FROM system_dict_type WHERE deleted=b'0';
SELECT 'dict_type_disallowed', COUNT(*) FROM system_dict_type
WHERE deleted=b'0'
  AND type NOT IN ('common_status','system_user_sex','system_role_type','system_data_scope',
    'bpm_model_type','bpm_model_form_type','bpm_task_candidate_strategy',
    'bpm_process_instance_status','bpm_task_status','bpm_process_listener_type',
    'bpm_process_listener_value_type')
  AND type NOT LIKE 'clm\_%';
SELECT 'dict_data_orphan', COUNT(*)
FROM system_dict_data d
LEFT JOIN system_dict_type t ON t.type=d.dict_type AND t.deleted=b'0'
WHERE t.id IS NULL;
SELECT 'clm_business_menus', COUNT(*) FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id WHERE r.tenant_id=1 AND r.code='clm_business' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'clm_legal_menus', COUNT(*) FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id WHERE r.tenant_id=1 AND r.code='clm_legal' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'clm_contract_admin_menus', COUNT(*) FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id WHERE r.tenant_id=1 AND r.code='clm_contract_admin' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'clm_system_admin_menus', COUNT(*) FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id WHERE r.tenant_id=1 AND r.code='clm_system_admin' AND r.deleted=b'0' AND rm.deleted=b'0';
SET SESSION group_concat_max_len=100000;
SELECT 'role_menu_exact_mismatch', COUNT(*)
FROM (
  SELECT r.code, GROUP_CONCAT(rm.menu_id ORDER BY rm.menu_id SEPARATOR ',') AS menu_ids
  FROM system_role r
  JOIN system_role_menu rm ON rm.role_id=r.id AND rm.deleted=b'0'
  WHERE r.tenant_id=1 AND r.deleted=b'0'
    AND r.code IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin')
  GROUP BY r.code
) actual_role_menu
WHERE menu_ids <> CASE code
  WHEN 'clm_business' THEN '7000,7030,7031,7032,7033,7034,7035,7036,7037,7040,7060,7061,7062,7063,7066,7070,7071,7072,7073,7074,7075,7077,7078,7080,7081,7082,7083,7084,7085,7086,7087,7088,7089,7090,7091,7100,7502'
  WHEN 'clm_legal' THEN '7000,7030,7031,7033,7036,7060,7061,7063,7064,7065,7070,7071,7072,7073,7074,7075,7076,7077,7078,7080,7083,7084,7085,7086,7087,7088,7089,7090,7091,7100,7502'
  WHEN 'clm_contract_admin' THEN '7000,7010,7011,7012,7013,7014,7015,7020,7021,7022,7023,7024,7030,7031,7070,7071,7072,7073,7074,7075,7077,7078,7080,7081,7083,7100,7101,7102,7103,7111,7112,7113,7121,7122,7123,7131,7132,7133,7141,7142,7143,7151,7152,7161,7162,7163,7171,7172,7181,7182,7191,7192,7193,7302,7303,7304,7305,7306,7308,7309,7310,7311,7500,7501,7502,7504'
  WHEN 'clm_system_admin' THEN '1,100,101,103,1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1017,1018,1019,1020,1063,1064,1065,7000,7070,7071,7080,7100,7103,7171,7173,7200,7211,7212,7221,7222,7223,7231,7232,7233,7309,7401,7402,7403'
END;
SELECT 'contract_admin_body_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
JOIN system_menu m ON m.id=rm.menu_id
WHERE r.tenant_id=1 AND r.code='clm_contract_admin'
  AND (m.permission REGEXP '^clm:(document|commitment|ai-review):'
       OR m.permission REGEXP '^clm:contract:(create|update|delete|submit|download|manage-member|restore)$'
       OR m.permission='clm:revision:update'
       OR m.permission='clm:approval:edit');
SELECT 'system_admin_body_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
JOIN system_menu m ON m.id=rm.menu_id
WHERE r.tenant_id=1 AND r.code='clm_system_admin'
  AND (m.permission REGEXP '^clm:(contract|revision|document|commitment|template|ai-review):'
       OR m.permission REGEXP '^clm:approval:(approve|reject|return|history|edit|collaborate|withdraw-case)$');
"@
$verificationOutput = @(Invoke-YinuoMysql -Runner $runner -Sql $verificationSql -SkipHeaders -Database $databaseName)
$actual = @{}
foreach ($line in $verificationOutput) {
  if ($line -match '^([^\s]+)\s+([0-9]+)$') { $actual[$Matches[1]] = [int64]$Matches[2] }
}
$expected = [ordered]@{
  tenant_total = 1
  tenant_turix = 1
  user_total = 5
  fixed_users = 5
  role_total = 5
  product_roles = 4
  product_role_unique = 4
  department_total = 5
  user_role_total = 5
  fixed_user_role_pairs = 5
  unexpected_user_role_pairs = 0
  invalid_menu_total = 0
  oauth_client_total = 1
  oauth_client_turix = 1
  oauth_client_minimal = 1
  tenant_package_total = 0
  bpm_category_total = 1
  bpm_category_contract = 1
  file_config_total = 1
  communication_payload_total = 0
  global_sample_total = 0
  demo_table_total = 0
  dict_type_total = 22
  dict_type_disallowed = 0
  dict_data_orphan = 0
  clm_business_menus = 37
  clm_legal_menus = 31
  clm_contract_admin_menus = 66
  clm_system_admin_menus = 44
  role_menu_exact_mismatch = 0
  contract_admin_body_grants = 0
  system_admin_body_grants = 0
}
$verificationFailures = @($expected.GetEnumerator() | Where-Object {
  -not $actual.ContainsKey($_.Key) -or $actual[$_.Key] -ne $_.Value
} | ForEach-Object {
  "$($_.Key): expected=$($_.Value), actual=$($actual[$_.Key])"
})
if ($verificationFailures.Count -gt 0) {
  throw "TuriX baseline verification failed: $($verificationFailures -join '; ')"
}

$clmTables = @(Invoke-YinuoMysql -Runner $runner -Sql "SHOW TABLES LIKE 'clm\_%';" -SkipHeaders -Database $databaseName)
$nonEmptyClmTables = [Collections.Generic.List[string]]::new()
foreach ($clmTable in $clmTables) {
  if ($clmTable -notmatch '^clm_[A-Za-z0-9_]+$') { throw "unexpected CLM table name '$clmTable'" }
  $rowCount = @(Invoke-YinuoMysql -Runner $runner -Sql "SELECT COUNT(*) FROM ``$clmTable``;" -SkipHeaders -Database $databaseName)
  if ([int64]$rowCount[0] -ne 0) { $nonEmptyClmTables.Add("$clmTable=$($rowCount[0])") }
}
if ($nonEmptyClmTables.Count -gt 0) {
  throw "CLM baseline is not empty: $($nonEmptyClmTables -join ', ')"
}
if ($clmTables.Count -ne 36) {
  throw "expected the locked 36 CLM tables after SQL01-13, found $($clmTables.Count)"
}

$migrationPreflight = Get-YinuoMigrationPreflight -RepoRoot $repoRoot -TenantId '1'
if (-not $migrationPreflight.ready) {
  throw "SQL01-13 permission/schema preflight failed after reset: $($migrationPreflight.problems -join '; ')"
}

Write-Host "PASS: TuriX local baseline is clean ($($clmTables.Count) CLM tables checked)." -ForegroundColor Green
} catch {
  if ($destructivePhaseStarted) {
    Write-Warning "The destructive phase did not finish. Keep the backend stopped, correct the reported error, then recover with: pwsh -NoProfile -File '$PSCommandPath' -ConfirmDestructive"
  }
  throw
}
