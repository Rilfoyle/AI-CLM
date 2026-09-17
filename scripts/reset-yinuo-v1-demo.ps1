param(
  [ValidateSet('E2E', 'Legacy', 'Demo', 'All')][string]$Scope = 'E2E',
  [string]$BaseUrl = 'http://127.0.0.1:48080/admin-api',
  [string]$Username = 'admin',
  [string]$Password = 'admin123',
  [string]$TenantName = 'TuriX',
  [string]$TenantId = '1'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot 'lib/YinuoV1.ps1')
Start-YinuoRun -Name 'reset-yinuo-v1-demo' -BaseUrl $BaseUrl

$repoRoot = Split-Path -Parent $PSScriptRoot
$prefix = switch ($Scope) {
  'E2E'  { 'demo-v1-e2e' }
  'Legacy' { 'e2e ' }
  'Demo' { 'demo-v1 ' }
  'All'  { 'demo-v1' }
}
$sqlLike = switch ($Scope) {
  'E2E'  { 'demo-v1-e2e%' }
  'Legacy' { 'e2e %' }
  'Demo' { 'demo-v1 %' }
  'All'  { 'demo-v1%' }
}
$partySqlWhere = if ($Scope -eq 'Legacy') {
  "(name LIKE '我方科技有限公司 %' OR name LIKE '客户集团股份有限公司 %')"
} else {
  "name LIKE '$sqlLike'"
}

function Test-YinuoRunningBinding {
  param([AllowNull()][object]$Binding)
  $status = Get-YinuoProperty -Object $Binding -Names @('status', 'statusCode')
  return ($status -eq 0 -or $status -eq 1 -or "${status}" -in @('PREPARING', 'RUNNING', 'IN_APPROVAL'))
}

try {
  $tenantIdSafe = $TenantId -match '^[1-9][0-9]*$'
  $null = Add-YinuoCheck -Condition $tenantIdSafe -Id 'RESET_TENANT_ID' -Message 'cleanup tenant id is a positive integer safe for exact SQL predicates' -Details "tenantId=$TenantId"
  if (-not $tenantIdSafe) { throw "invalid TenantId '$TenantId'; no API cancellation or SQL cleanup was attempted" }
  $tenantIdSql = [int64]$TenantId

  Write-Host '== SQL 01-13 migration preflight'
  $migration = Get-YinuoMigrationPreflight -RepoRoot $repoRoot -TenantId $TenantId
  $runner = $migration.runner
  $columns = $migration.columns
  $tables = $migration.tables
  $null = Add-YinuoCheck -Condition $migration.ready -Id 'RESET_DB_PREFLIGHT_01_13' -Message 'SQL 01-13 schema, columns, product roles, and aligned menus are initialized before cleanup' -Details $(if ($migration.ready) { "runner=$($runner.kind)" } else { $migration.problems -join '; ' })
  if (-not $migration.ready) {
    throw "required SQL 01-13 schema/roles/menus are incomplete; no API cancellation or rows deletion was attempted ($($migration.problems -join '; '))"
  }

  Write-Host "== API cancellation preflight ($Scope / prefix '$prefix')"
  $headers = Connect-YinuoApi -Username $Username -Password $Password -TenantName $TenantName -TenantId $TenantId
  $null = Add-YinuoCheck -Condition ($null -ne $headers) -Id 'RESET_LOGIN' -Message 'cleanup account logged in' -Details $null

  $preflight = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/contract/page?pageNo=1&pageSize=100' -Body $null
  $null = Assert-YinuoApi -Response $preflight -Id 'RESET_PREFLIGHT_CONTRACT_PAGE' -Message 'contract page endpoint is available' -ThrowOnFailure
  $contracts = @(Get-YinuoAllPages -Headers $headers -Path '/clm/contract/page') | Where-Object {
    "$(Get-YinuoContractName $_)".StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
  }

  $cancelFailures = 0
  foreach ($contract in $contracts) {
    $contractId = "$(Get-YinuoProperty -Object $contract -Names @('id','contractId'))"
    $bindingsResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path "/clm/workflow-binding/list?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
    if (-not $bindingsResponse.ok) {
      $cancelFailures++
      $null = Add-YinuoCheck -Condition $false -Id "RESET_BINDINGS_$contractId" -Message "load workflow bindings for contract $contractId" -Details (Get-YinuoErrorText $bindingsResponse)
      continue
    }
    foreach ($binding in @(Get-YinuoItems $bindingsResponse.data)) {
      $processInstanceId = "$(Get-YinuoProperty -Object $binding -Names @('processInstanceId'))"
      if (-not $processInstanceId -or -not (Test-YinuoRunningBinding $binding)) { continue }
      $cancel = Invoke-YinuoApi -Headers $headers -Method DELETE -Path '/bpm/process-instance/cancel-by-start-user' -Body @{
        id = $processInstanceId
        reason = "Yinuo V1 $Scope deterministic cleanup"
      }
      if (-not $cancel.ok) {
        $cancelFailures++
        $null = Add-YinuoCheck -Condition $false -Id "RESET_CANCEL_$processInstanceId" -Message "cancel running process $processInstanceId" -Details (Get-YinuoErrorText $cancel)
      }
    }
  }
  $null = Add-YinuoCheck -Condition ($cancelFailures -eq 0) -Id 'RESET_CANCEL_RUNNING' -Message 'all matching running approval instances were canceled through API' -Details "contracts=$($contracts.Count), failures=$cancelFailures"
  if ($cancelFailures -gt 0) { throw 'refusing physical cleanup because one or more running process instances could not be canceled safely' }

  if ($Scope -in @('Demo','All')) {
    # Delete demo policy scopes through the service after process cancellation and before
    # physical cleanup. This keeps system_user_role and USER_ROLE_ID_LIST cache consistent.
    $policyResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/permission/policy/list' -Body $null
    if (-not $policyResponse.ok) { throw "demo policy query failed before scope cleanup: $(Get-YinuoErrorText $policyResponse)" }
    $demoPolicyIds = @((Get-YinuoItems $policyResponse.data) | Where-Object { $_.remark -eq 'demo-v1 G05 deterministic policy' } | ForEach-Object {
      "$(Get-YinuoProperty -Object $_ -Names @('id'))"
    } | Where-Object { [bool]$_ })
    $scopeDeleteFailures = 0
    $scopeDeleteCount = 0
    foreach ($demoUsername in @('business','contractadmin','systemadmin','legal')) {
      $userResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path "/system/user/page?username=$([Uri]::EscapeDataString($demoUsername))&pageNo=1&pageSize=100" -Body $null
      if (-not $userResponse.ok) { $scopeDeleteFailures++; continue }
      $users = @((Get-YinuoItems $userResponse.data) | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('username'))" -eq $demoUsername })
      foreach ($user in $users) {
        $userId = "$(Get-YinuoProperty -Object $user -Names @('id'))"
        $scopesResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path "/clm/permission/user-scope/list?userId=$([Uri]::EscapeDataString($userId))" -Body $null
        if (-not $scopesResponse.ok) { $scopeDeleteFailures++; continue }
        foreach ($scopeRow in @(Get-YinuoItems $scopesResponse.data)) {
          $policyVersionId = "$(Get-YinuoProperty -Object $scopeRow -Names @('policyVersionId'))"
          if ($policyVersionId -notin $demoPolicyIds) { continue }
          $scopeId = "$(Get-YinuoProperty -Object $scopeRow -Names @('id'))"
          $deleteScope = Invoke-YinuoApi -Headers $headers -Method DELETE -Path "/clm/permission/user-scope/delete?id=$([Uri]::EscapeDataString($scopeId))" -Body $null
          if ($deleteScope.ok) { $scopeDeleteCount++ } else { $scopeDeleteFailures++ }
        }
      }
    }
    $null = Add-YinuoCheck -Condition ($scopeDeleteFailures -eq 0) -Id 'RESET_USER_SCOPE_API' -Message 'demo user scopes and cached CLM role assignments were removed through the permission service' -Details "policies=$($demoPolicyIds.Count), deletedScopes=$scopeDeleteCount, failures=$scopeDeleteFailures"
    if ($scopeDeleteFailures -gt 0) { throw 'refusing physical cleanup because user-scope service cleanup failed' }
  }

  Write-Host '== exact-prefix physical cleanup'
  $sql = [Collections.Generic.List[string]]::new()
  $sql.Add('SET FOREIGN_KEY_CHECKS = 0;')
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_contract_ids (id BIGINT PRIMARY KEY);')
  $sql.Add("INSERT IGNORE INTO tmp_yinuo_contract_ids SELECT id FROM clm_contract WHERE tenant_id=$tenantIdSql AND title LIKE '$sqlLike';")
  if ($Scope -eq 'Legacy') {
    $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_contract_type_ids (id BIGINT PRIMARY KEY);')
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_contract_type_ids SELECT id FROM clm_contract_type WHERE tenant_id=$tenantIdSql AND (code LIKE 'sales_%' OR code LIKE 'oo_%');")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_binding_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_workflow_binding|contract_id')) {
    $sql.Add('INSERT IGNORE INTO tmp_yinuo_binding_ids SELECT id FROM clm_workflow_binding WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);')
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_process_ids (id VARCHAR(128) PRIMARY KEY);')
  if ($columns.Contains('clm_workflow_binding|process_instance_id')) {
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_process_ids SELECT process_instance_id FROM clm_workflow_binding WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids) AND process_instance_id IS NOT NULL AND process_instance_id <> '';")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_blob_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_document_version|contract_id') -and $columns.Contains('clm_document_version|file_key')) {
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_blob_ids SELECT CAST(file_key AS UNSIGNED) FROM clm_document_version WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids) AND file_key REGEXP '^[0-9]+$';")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_bytearray_ids (id VARCHAR(128) PRIMARY KEY);')
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_party_import_job_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_party_import_job|job_key')) {
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_party_import_job_ids SELECT id FROM clm_party_import_job WHERE tenant_id=$tenantIdSql AND job_key LIKE '$sqlLike';")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_integration_run_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_integration_run|run_key')) {
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_integration_run_ids SELECT id FROM clm_integration_run WHERE tenant_id=$tenantIdSql AND run_key LIKE '$sqlLike';")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_handover_case_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_handover_item|contract_id')) {
    $sql.Add('INSERT IGNORE INTO tmp_yinuo_handover_case_ids SELECT DISTINCT case_id FROM clm_handover_item WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);')
  }
  if ($Scope -in @('Demo','All')) {
    $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_demo_policy_ids (id BIGINT PRIMARY KEY);')
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_demo_policy_ids SELECT id FROM clm_permission_policy_version WHERE tenant_id=$tenantIdSql AND remark='demo-v1 G05 deterministic policy';")
    $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_demo_user_ids (id BIGINT PRIMARY KEY);')
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_demo_user_ids SELECT id FROM system_users WHERE tenant_id=$tenantIdSql AND username IN ('business','contractadmin','systemadmin','legal');")
    $sql.Add('CREATE TEMPORARY TABLE tmp_yinuo_user_scope_ids (id BIGINT PRIMARY KEY);')
    $sql.Add("INSERT IGNORE INTO tmp_yinuo_user_scope_ids SELECT id FROM clm_user_scope WHERE tenant_id=$tenantIdSql AND policy_version_id IN (SELECT id FROM tmp_yinuo_demo_policy_ids) AND user_id IN (SELECT id FROM tmp_yinuo_demo_user_ids);")
  }

  foreach ($table in @('ACT_RU_VARIABLE', 'ACT_HI_VARINST', 'ACT_HI_DETAIL')) {
    $processColumn = if ($columns.Contains("$table|PROC_INST_ID_")) { 'PROC_INST_ID_' } elseif ($columns.Contains("$table|PROCESS_INSTANCE_ID_")) { 'PROCESS_INSTANCE_ID_' } else { $null }
    if ($processColumn -and $columns.Contains("$table|BYTEARRAY_ID_")) {
      $sql.Add("INSERT IGNORE INTO tmp_yinuo_bytearray_ids SELECT BYTEARRAY_ID_ FROM $table WHERE $processColumn IN (SELECT id FROM tmp_yinuo_process_ids) AND BYTEARRAY_ID_ IS NOT NULL;")
    }
  }

  # Delete Flowable runtime/history rows for only the captured process instances. The process was canceled above.
  $flowableTables = @($tables | Where-Object { $_ -like 'ACT_*' -and $_ -ne 'ACT_GE_BYTEARRAY' }) | Sort-Object {
    if ($_ -eq 'ACT_HI_PROCINST') { 90 } elseif ($_ -eq 'ACT_RU_EXECUTION') { 80 } else { 10 }
  }
  foreach ($table in $flowableTables) {
    # MySQL cannot reference the same temporary table twice in one statement.
    # PROC_INST_ID_ is the authoritative process column when present; job tables use PROCESS_INSTANCE_ID_.
    $processColumn = if ($columns.Contains("$table|PROC_INST_ID_")) { 'PROC_INST_ID_' } elseif ($columns.Contains("$table|PROCESS_INSTANCE_ID_")) { 'PROCESS_INSTANCE_ID_' } else { $null }
    if ($processColumn) { $sql.Add("DELETE FROM $table WHERE $processColumn IN (SELECT id FROM tmp_yinuo_process_ids);") }
  }
  if ($columns.Contains('ACT_GE_BYTEARRAY|ID_')) {
    $sql.Add('DELETE FROM ACT_GE_BYTEARRAY WHERE ID_ IN (SELECT id FROM tmp_yinuo_bytearray_ids);')
  }

  # SQL 08-11 child-first cleanup. These tables are ordered by conceptual ownership even though
  # the local schema intentionally has no physical foreign keys.
  if ($columns.Contains('clm_approval_task_revision_binding|approval_case_id')) {
    $sql.Add('DELETE FROM clm_approval_task_revision_binding WHERE approval_case_id IN (SELECT id FROM tmp_yinuo_binding_ids);')
  }
  if ($columns.Contains('clm_approval_task_revision_binding|workflow_binding_id')) {
    $sql.Add('DELETE FROM clm_approval_task_revision_binding WHERE workflow_binding_id IN (SELECT id FROM tmp_yinuo_binding_ids);')
  }
  $orderedContractChildren = @(
    'clm_approval_edit_request',
    'clm_approval_task_revision_binding',
    'clm_ai_review_finding', 'clm_ai_review_run',
    'clm_collaboration_event', 'clm_collaboration_case',
    'clm_integration_delivery', 'clm_governance_issue', 'clm_handover_item',
    'clm_commitment', 'clm_contract_revision',
    'clm_audit_event', 'clm_workflow_binding', 'clm_contract_participant',
    'clm_contract_party', 'clm_document_version', 'clm_document'
  )
  foreach ($table in $orderedContractChildren) {
    if ($columns.Contains("$table|contract_id")) {
      $sql.Add("DELETE FROM $table WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);")
    }
  }
  $remainingContractOwnedTables = @($tables | Where-Object {
    $_ -like 'clm_*' -and $_ -notin (@('clm_contract', 'clm_document_blob') + $orderedContractChildren) -and $columns.Contains("$_|contract_id")
  })
  foreach ($table in $remainingContractOwnedTables) {
    $sql.Add("DELETE FROM $table WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);")
  }
  # These aggregates deliberately have no contract_id. Remove their exact-prefix audit events
  # before deleting the owning job/run/case, otherwise cleanup can falsely report no residue.
  if ($columns.Contains('clm_audit_event|aggregate_type') -and $columns.Contains('clm_audit_event|aggregate_id')) {
    $sql.Add("DELETE FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND aggregate_type='PARTY_IMPORT' AND aggregate_id IN (SELECT id FROM tmp_yinuo_party_import_job_ids);")
    $sql.Add("DELETE FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND aggregate_type='INTEGRATION' AND aggregate_id IN (SELECT id FROM tmp_yinuo_integration_run_ids);")
    $sql.Add("DELETE FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND aggregate_type='HANDOVER' AND aggregate_id IN (SELECT id FROM tmp_yinuo_handover_case_ids) AND NOT EXISTS (SELECT 1 FROM clm_handover_item i WHERE i.case_id=clm_audit_event.aggregate_id);")
  }
  if ($columns.Contains('clm_handover_case|id')) {
    $sql.Add('DELETE FROM clm_handover_case WHERE id IN (SELECT id FROM tmp_yinuo_handover_case_ids) AND NOT EXISTS (SELECT 1 FROM clm_handover_item i WHERE i.case_id=clm_handover_case.id);')
  }
  if ($columns.Contains('clm_party_import_item|job_id')) {
    $sql.Add('DELETE FROM clm_party_import_item WHERE job_id IN (SELECT id FROM tmp_yinuo_party_import_job_ids);')
  }
  if ($columns.Contains('clm_party_import_job|id')) {
    $sql.Add('DELETE FROM clm_party_import_job WHERE id IN (SELECT id FROM tmp_yinuo_party_import_job_ids);')
  }
  if ($columns.Contains('clm_reconciliation_run|run_key')) {
    $sql.Add("DELETE FROM clm_reconciliation_run WHERE tenant_id=$tenantIdSql AND run_key LIKE '$sqlLike';")
  }
  if ($columns.Contains('clm_integration_delivery|delivery_key')) {
    $sql.Add("DELETE FROM clm_integration_delivery WHERE tenant_id=$tenantIdSql AND delivery_key LIKE '$sqlLike';")
  }
  if ($columns.Contains('clm_integration_run|run_key')) {
    $sql.Add("DELETE FROM clm_integration_run WHERE tenant_id=$tenantIdSql AND run_key LIKE '$sqlLike';")
  }
  if ($columns.Contains('clm_saved_filter|name')) {
    $sql.Add("DELETE FROM clm_saved_filter WHERE tenant_id=$tenantIdSql AND name LIKE '$sqlLike';")
  }
  if ($Scope -in @('Demo','All')) {
    if ($columns.Contains('clm_audit_event|aggregate_type') -and $columns.Contains('clm_audit_event|aggregate_id')) {
      $sql.Add("DELETE FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND aggregate_type='USER_SCOPE' AND aggregate_id IN (SELECT id FROM tmp_yinuo_user_scope_ids);")
      $sql.Add("DELETE FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND aggregate_type='PERMISSION_POLICY' AND aggregate_id IN (SELECT id FROM tmp_yinuo_demo_policy_ids);")
    }
    $sql.Add('DELETE FROM clm_user_scope_item WHERE user_scope_id IN (SELECT id FROM tmp_yinuo_user_scope_ids);')
    $sql.Add('DELETE FROM clm_user_scope WHERE id IN (SELECT id FROM tmp_yinuo_user_scope_ids);')
    $sql.Add('DELETE FROM clm_permission_policy_version WHERE id IN (SELECT id FROM tmp_yinuo_demo_policy_ids) AND NOT EXISTS (SELECT 1 FROM clm_user_scope s WHERE s.policy_version_id=clm_permission_policy_version.id);')
  }
  $sql.Add('DELETE FROM clm_contract WHERE id IN (SELECT id FROM tmp_yinuo_contract_ids);')
  if ($Scope -eq 'Legacy') {
    if ($columns.Contains('clm_routing_rule_version|contract_type_id')) {
      $sql.Add("DELETE FROM clm_routing_rule_version WHERE tenant_id=$tenantIdSql AND (contract_type_id IN (SELECT id FROM tmp_yinuo_contract_type_ids) OR rule_code LIKE 'legacy-e2e-route-%' OR rule_code LIKE 'onlyoffice-e2e-route-%');")
    }
    $sql.Add('DELETE FROM clm_contract_type_version WHERE type_id IN (SELECT id FROM tmp_yinuo_contract_type_ids);')
    $sql.Add('DELETE FROM clm_contract_type WHERE id IN (SELECT id FROM tmp_yinuo_contract_type_ids);')
  }
  if ($columns.Contains('clm_document_blob|id')) {
    $sql.Add('DELETE FROM clm_document_blob WHERE id IN (SELECT id FROM tmp_yinuo_blob_ids);')
  }
  if ($columns.Contains('clm_party|name')) {
    $sql.Add("DELETE FROM clm_party WHERE tenant_id=$tenantIdSql AND $partySqlWhere;")
  }

  $expectedMetricNames = [Collections.Generic.List[string]]::new()
  $sql.Add("SELECT 'remaining_contracts' AS metric, COUNT(*) AS value FROM clm_contract WHERE tenant_id=$tenantIdSql AND title LIKE '$sqlLike';")
  $expectedMetricNames.Add('remaining_contracts')
  if ($columns.Contains('clm_party|name')) {
    $sql.Add("SELECT 'remaining_parties' AS metric, COUNT(*) AS value FROM clm_party WHERE tenant_id=$tenantIdSql AND $partySqlWhere;")
    $expectedMetricNames.Add('remaining_parties')
  }
  if ($Scope -eq 'Legacy') {
    $sql.Add("SELECT 'remaining_contract_types' AS metric, COUNT(*) AS value FROM clm_contract_type WHERE tenant_id=$tenantIdSql AND (code LIKE 'sales_%' OR code LIKE 'oo_%');")
    $expectedMetricNames.Add('remaining_contract_types')
    if ($columns.Contains('clm_routing_rule_version|rule_code')) {
      $sql.Add("SELECT 'remaining_routes' AS metric, COUNT(*) AS value FROM clm_routing_rule_version WHERE tenant_id=$tenantIdSql AND (rule_code LIKE 'legacy-e2e-route-%' OR rule_code LIKE 'onlyoffice-e2e-route-%');")
      $expectedMetricNames.Add('remaining_routes')
    }
  }
  if ($columns.Contains('ACT_HI_PROCINST|PROC_INST_ID_')) {
    $sql.Add("SELECT 'remaining_processes' AS metric, COUNT(*) AS value FROM ACT_HI_PROCINST WHERE PROC_INST_ID_ IN (SELECT id FROM tmp_yinuo_process_ids);")
    $expectedMetricNames.Add('remaining_processes')
  }
  if ($columns.Contains('clm_saved_filter|name')) {
    $sql.Add("SELECT 'remaining_saved_filters' AS metric, COUNT(*) AS value FROM clm_saved_filter WHERE tenant_id=$tenantIdSql AND name LIKE '$sqlLike';")
    $expectedMetricNames.Add('remaining_saved_filters')
  }
  if ($columns.Contains('clm_party_import_job|job_key')) {
    $sql.Add("SELECT 'remaining_party_import_jobs' AS metric, COUNT(*) AS value FROM clm_party_import_job WHERE tenant_id=$tenantIdSql AND job_key LIKE '$sqlLike';")
    $expectedMetricNames.Add('remaining_party_import_jobs')
  }
  if ($columns.Contains('clm_party_import_item|job_id')) {
    $sql.Add("SELECT 'remaining_party_import_items' AS metric, COUNT(*) AS value FROM clm_party_import_item WHERE job_id IN (SELECT id FROM tmp_yinuo_party_import_job_ids);")
    $expectedMetricNames.Add('remaining_party_import_items')
  }
  if ($columns.Contains('clm_integration_run|run_key')) {
    $sql.Add("SELECT 'remaining_integration_runs' AS metric, COUNT(*) AS value FROM clm_integration_run WHERE tenant_id=$tenantIdSql AND run_key LIKE '$sqlLike';")
    $expectedMetricNames.Add('remaining_integration_runs')
  }
  if ($columns.Contains('clm_integration_delivery|delivery_key')) {
    $sql.Add("SELECT 'remaining_integration_deliveries' AS metric, COUNT(*) AS value FROM clm_integration_delivery WHERE tenant_id=$tenantIdSql AND delivery_key LIKE '$sqlLike';")
    $expectedMetricNames.Add('remaining_integration_deliveries')
  }
  if ($columns.Contains('clm_reconciliation_run|run_key')) {
    $sql.Add("SELECT 'remaining_reconciliation_runs' AS metric, COUNT(*) AS value FROM clm_reconciliation_run WHERE tenant_id=$tenantIdSql AND run_key LIKE '$sqlLike';")
    $expectedMetricNames.Add('remaining_reconciliation_runs')
  }
  if ($columns.Contains('clm_handover_item|contract_id')) {
    $sql.Add("SELECT 'remaining_handover_items' AS metric, COUNT(*) AS value FROM clm_handover_item WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);")
    $expectedMetricNames.Add('remaining_handover_items')
  }
  if ($columns.Contains('clm_handover_case|id')) {
    $sql.Add("SELECT 'remaining_removable_handover_cases' AS metric, COUNT(*) AS value FROM clm_handover_case WHERE id IN (SELECT id FROM tmp_yinuo_handover_case_ids) AND NOT EXISTS (SELECT 1 FROM clm_handover_item i WHERE i.case_id=clm_handover_case.id);")
    $expectedMetricNames.Add('remaining_removable_handover_cases')
  }
  if ($columns.Contains('clm_approval_edit_request|contract_id')) {
    $sql.Add("SELECT 'remaining_approval_edits' AS metric, COUNT(*) AS value FROM clm_approval_edit_request WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);")
    $expectedMetricNames.Add('remaining_approval_edits')
  }
  if ($columns.Contains('clm_ai_review_run|contract_id')) {
    $sql.Add("SELECT 'remaining_ai_runs' AS metric, COUNT(*) AS value FROM clm_ai_review_run WHERE contract_id IN (SELECT id FROM tmp_yinuo_contract_ids);")
    $expectedMetricNames.Add('remaining_ai_runs')
  }
  if ($columns.Contains('clm_audit_event|aggregate_type') -and $columns.Contains('clm_audit_event|aggregate_id')) {
    $sql.Add("SELECT 'remaining_detached_audit_events' AS metric, COUNT(*) AS value FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND ((aggregate_type='PARTY_IMPORT' AND aggregate_id IN (SELECT id FROM tmp_yinuo_party_import_job_ids)) OR (aggregate_type='INTEGRATION' AND aggregate_id IN (SELECT id FROM tmp_yinuo_integration_run_ids)) OR (aggregate_type='HANDOVER' AND aggregate_id IN (SELECT id FROM tmp_yinuo_handover_case_ids) AND NOT EXISTS (SELECT 1 FROM clm_handover_item i WHERE i.case_id=clm_audit_event.aggregate_id)));")
    $expectedMetricNames.Add('remaining_detached_audit_events')
  }
  $sql.Add("SELECT 'remaining_postapproval' AS metric, COUNT(*) AS value FROM clm_contract WHERE tenant_id=$tenantIdSql AND title LIKE '$sqlLike' AND (UPPER(COALESCE(stage_code,'')) REGEXP 'SIGN|ARCHIV|PERFORM|EFFECTIVE|EXPIRED|TERMINAT|CLOSED' OR COALESCE(lifecycle_status,0)>=3);")
  $expectedMetricNames.Add('remaining_postapproval')
  if ($Scope -in @('Demo','All')) {
    $sql.Add("SELECT 'remaining_user_scopes' AS metric, COUNT(*) AS value FROM clm_user_scope WHERE id IN (SELECT id FROM tmp_yinuo_user_scope_ids);")
    $sql.Add("SELECT 'remaining_user_scope_items' AS metric, COUNT(*) AS value FROM clm_user_scope_item WHERE user_scope_id IN (SELECT id FROM tmp_yinuo_user_scope_ids);")
    $sql.Add("SELECT 'remaining_demo_policies' AS metric, COUNT(*) AS value FROM clm_permission_policy_version WHERE id IN (SELECT id FROM tmp_yinuo_demo_policy_ids);")
    $expectedMetricNames.Add('remaining_user_scopes')
    $expectedMetricNames.Add('remaining_user_scope_items')
    $expectedMetricNames.Add('remaining_demo_policies')
    if ($columns.Contains('clm_audit_event|aggregate_type') -and $columns.Contains('clm_audit_event|aggregate_id')) {
      $sql.Add("SELECT 'remaining_demo_audit_events' AS metric, COUNT(*) AS value FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND ((aggregate_type='USER_SCOPE' AND aggregate_id IN (SELECT id FROM tmp_yinuo_user_scope_ids)) OR (aggregate_type='PERMISSION_POLICY' AND aggregate_id IN (SELECT id FROM tmp_yinuo_demo_policy_ids)));")
      $expectedMetricNames.Add('remaining_demo_audit_events')
    }
  }
  $sql.Add('SET FOREIGN_KEY_CHECKS = 1;')

  $cleanupOutput = @(Invoke-YinuoMysql -Runner $runner -Sql ($sql -join [Environment]::NewLine))
  $metrics = @{}
  foreach ($line in $cleanupOutput) {
    if ($line -match '^(remaining_[a-z_]+)\s+([0-9]+)$') { $metrics[$Matches[1]] = [int]$Matches[2] }
  }
  $remaining = @($metrics.Values | Where-Object { $_ -ne 0 }).Count
  $missingMetricNames = @($expectedMetricNames | Where-Object { -not $metrics.ContainsKey($_) })
  $requiredMetricsPresent = $missingMetricNames.Count -eq 0
  $metricDetails = (($metrics.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', ')
  if ($missingMetricNames.Count -gt 0) { $metricDetails = "$metricDetails; missingMetrics=$($missingMetricNames -join ',')" }
  $null = Add-YinuoCheck -Condition ($remaining -eq 0 -and $requiredMetricsPresent) -Id 'RESET_NO_RESIDUE' -Message 'no matching CLM, workflow, import, handover, approval-edit, AI, detached-audit, or post-approval residue remains' -Details $metricDetails
} catch {
  $null = Add-YinuoCheck -Condition $false -Id 'RESET_EXCEPTION' -Message 'cleanup completed without an unhandled blocker' -Details $_.Exception.Message
}

Write-YinuoSummary
exit $script:YinuoFailedCount
