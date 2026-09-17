$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

function Start-YinuoRun {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$BaseUrl
  )
  $script:YinuoRunName = $Name
  $script:YinuoBaseUrl = $BaseUrl.TrimEnd('/')
  $script:YinuoChecks = [Collections.Generic.List[object]]::new()
  $script:YinuoStartedAt = [DateTimeOffset]::Now
}

function Add-YinuoCheck {
  param(
    [Parameter(Mandatory = $true)][bool]$Condition,
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][string]$Message,
    [AllowNull()][object]$Details
  )
  $entry = [ordered]@{
    id = $Id
    passed = $Condition
    message = $Message
  }
  if ($null -ne $Details -and "${Details}" -ne '') { $entry.details = "${Details}" }
  $script:YinuoChecks.Add([pscustomobject]$entry)
  if ($Condition) {
    Write-Host "  PASS  [$Id] $Message" -ForegroundColor Green
  } else {
    $suffix = if ($null -ne $Details -and "${Details}" -ne '') { " :: ${Details}" } else { '' }
    Write-Host "  FAIL  [$Id] $Message$suffix" -ForegroundColor Red
  }
  return $Condition
}

function Add-YinuoSkip {
  param(
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][string]$Message,
    [Parameter(Mandatory = $true)][string]$Details
  )
  $script:YinuoChecks.Add([pscustomobject][ordered]@{
    id = $Id
    passed = $null
    skipped = $true
    message = $Message
    details = $Details
  })
  Write-Host "  SKIP  [$Id] $Message :: $Details" -ForegroundColor Yellow
}

function Write-YinuoSummary {
  $checks = @($script:YinuoChecks)
  $passed = @($checks | Where-Object { $_.passed -eq $true }).Count
  $failed = @($checks | Where-Object { $_.passed -eq $false }).Count
  $skipped = @($checks | Where-Object { $_.PSObject.Properties['skipped'] -and $_.skipped -eq $true }).Count
  $summary = [ordered]@{
    script = $script:YinuoRunName
    status = if ($failed -gt 0) { 'FAILED' } elseif ($skipped -gt 0) { 'PASSED_WITH_SKIPS' } else { 'PASSED' }
    total = $checks.Count
    passed = $passed
    failed = $failed
    skipped = $skipped
    startedAt = $script:YinuoStartedAt.ToString('o')
    finishedAt = [DateTimeOffset]::Now.ToString('o')
    checks = $checks
  }
  $script:YinuoFailedCount = $failed
  Write-Output ($summary | ConvertTo-Json -Compress -Depth 8)
}

function Get-YinuoErrorText {
  param([AllowNull()][object]$Response)
  if ($null -eq $Response) { return 'no response' }
  $parts = [Collections.Generic.List[string]]::new()
  if ($Response.PSObject.Properties['httpStatus']) { $parts.Add("http=$($Response.httpStatus)") }
  if ($Response.PSObject.Properties['code'] -and $null -ne $Response.code) { $parts.Add("code=$($Response.code)") }
  if ($Response.PSObject.Properties['msg'] -and $Response.msg) { $parts.Add("msg=$($Response.msg)") }
  if ($Response.PSObject.Properties['error'] -and $Response.error) { $parts.Add("error=$($Response.error)") }
  if ($parts.Count -eq 0) { return 'unknown response' }
  return ($parts -join ', ')
}

function Invoke-YinuoApi {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][ValidateSet('GET','POST','PUT','DELETE','PATCH')][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [AllowNull()][object]$Body
  )
  $parameters = @{
    Uri = "$script:YinuoBaseUrl$Path"
    Method = $Method
    Headers = $Headers
    UseBasicParsing = $true
  }
  if ($PSVersionTable.PSVersion.Major -ge 7) { $parameters.SkipHttpErrorCheck = $true }
  if ($null -ne $Body) {
    $parameters.ContentType = 'application/json;charset=UTF-8'
    $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress -Depth 20))
  }

  $httpStatus = 0
  $content = ''
  $networkError = $null
  try {
    $raw = Invoke-WebRequest @parameters
    $httpStatus = [int]$raw.StatusCode
    $content = [string]$raw.Content
  } catch {
    $networkError = $_.Exception.Message
    if ($_.Exception.Response) {
      try { $httpStatus = [int]$_.Exception.Response.StatusCode } catch { $httpStatus = 0 }
    }
    if ($_.ErrorDetails.Message) { $content = [string]$_.ErrorDetails.Message }
    if (-not $content -and $_.Exception.Response -and $_.Exception.Response.PSObject.Methods['GetResponseStream']) {
      $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      try { $content = $reader.ReadToEnd() } finally { $reader.Close() }
    }
  }

  $json = $null
  $parseError = $null
  if ($content) {
    try { $json = $content | ConvertFrom-Json } catch { $parseError = $_.Exception.Message }
  }
  $code = $null
  $msg = $null
  $data = $null
  if ($null -ne $json) {
    if ($json.PSObject.Properties['code']) { $code = $json.code }
    if ($json.PSObject.Properties['msg']) { $msg = $json.msg }
    if ($json.PSObject.Properties['data']) { $data = $json.data }
  }
  $httpOk = $httpStatus -ge 200 -and $httpStatus -lt 300
  $apiOk = $httpOk -and $null -ne $json -and $null -ne $code -and [int64]$code -eq 0
  return [pscustomobject]@{
    ok = $apiOk
    httpStatus = $httpStatus
    code = $code
    msg = $msg
    data = $data
    json = $json
    raw = $content
    error = if ($networkError) { $networkError } elseif ($parseError) { "invalid JSON: $parseError" } else { $null }
    missing = ($httpStatus -eq 404 -or $httpStatus -eq 405 -or $code -eq 404 -or $content -match 'No static resource|Not Found|请求地址不存在')
  }
}

function Invoke-YinuoMultipartApi {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][ValidateSet('POST','PUT')][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][hashtable]$Form
  )
  if ($PSVersionTable.PSVersion.Major -lt 7) {
    return [pscustomobject]@{
      ok = $false; httpStatus = 0; code = $null; msg = $null; data = $null; json = $null; raw = ''
      error = 'multipart acceptance requires PowerShell 7 or newer'; missing = $false
    }
  }

  $httpStatus = 0
  $content = ''
  $networkError = $null
  try {
    $raw = Invoke-WebRequest -Uri "$script:YinuoBaseUrl$Path" -Method $Method -Headers $Headers `
      -Form $Form -SkipHttpErrorCheck -UseBasicParsing
    $httpStatus = [int]$raw.StatusCode
    $content = [string]$raw.Content
  } catch {
    $networkError = $_.Exception.Message
    if ($_.Exception.Response) {
      try { $httpStatus = [int]$_.Exception.Response.StatusCode } catch { $httpStatus = 0 }
    }
    if ($_.ErrorDetails.Message) { $content = [string]$_.ErrorDetails.Message }
  }

  $json = $null
  $parseError = $null
  if ($content) {
    try { $json = $content | ConvertFrom-Json } catch { $parseError = $_.Exception.Message }
  }
  $code = if ($null -ne $json -and $json.PSObject.Properties['code']) { $json.code } else { $null }
  $msg = if ($null -ne $json -and $json.PSObject.Properties['msg']) { $json.msg } else { $null }
  $data = if ($null -ne $json -and $json.PSObject.Properties['data']) { $json.data } else { $null }
  $httpOk = $httpStatus -ge 200 -and $httpStatus -lt 300
  return [pscustomobject]@{
    ok = ($httpOk -and $null -ne $code -and [int64]$code -eq 0)
    httpStatus = $httpStatus
    code = $code
    msg = $msg
    data = $data
    json = $json
    raw = $content
    error = if ($networkError) { $networkError } elseif ($parseError) { "invalid JSON: $parseError" } else { $null }
    missing = ($httpStatus -eq 404 -or $httpStatus -eq 405 -or $code -eq 404 -or $content -match 'No static resource|Not Found|请求地址不存在')
  }
}

function Connect-YinuoApi {
  param(
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$TenantName = 'TuriX',
    [string]$TenantId = '1'
  )
  $headers = @{ 'tenant-id' = $TenantId }
  $result = Invoke-YinuoApi -Headers $headers -Method POST -Path '/system/auth/login' -Body @{
    username = $Username
    password = $Password
    tenantName = $TenantName
  }
  if (-not $result.ok -or -not $result.data.accessToken) {
    throw "login failed for '$Username': $(Get-YinuoErrorText $result)"
  }
  return @{
    Authorization = "Bearer $($result.data.accessToken)"
    'tenant-id' = $TenantId
  }
}

function Assert-YinuoApi {
  param(
    [Parameter(Mandatory = $true)][object]$Response,
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][string]$Message,
    [switch]$ThrowOnFailure
  )
  $ok = [bool]$Response.ok
  $null = Add-YinuoCheck -Condition $ok -Id $Id -Message $Message -Details $(if ($ok) { $null } else { Get-YinuoErrorText $Response })
  if (-not $ok -and $ThrowOnFailure) {
    $kind = if ($Response.missing) { 'required endpoint is not implemented' } else { 'API preflight failed' }
    throw "${kind}: $Message ($(Get-YinuoErrorText $Response))"
  }
  return $ok
}

function Get-YinuoItems {
  param([AllowNull()][object]$Data)
  if ($null -eq $Data) { return @() }
  if ($Data.PSObject.Properties['list']) { return @($Data.list) }
  if ($Data -is [Array]) { return @($Data) }
  if ($Data -is [Collections.IEnumerable] -and -not ($Data -is [string])) { return @($Data) }
  return @($Data)
}

function Get-YinuoProperty {
  param(
    [AllowNull()][object]$Object,
    [Parameter(Mandatory = $true)][string[]]$Names
  )
  if ($null -eq $Object) { return $null }
  foreach ($name in $Names) {
    $property = $Object.PSObject.Properties[$name]
    if ($property -and $null -ne $property.Value) { return $property.Value }
  }
  return $null
}

function Get-YinuoContractName {
  param([AllowNull()][object]$Contract)
  return Get-YinuoProperty -Object $Contract -Names @('name', 'title', 'contractName')
}

function Get-YinuoId {
  param([AllowNull()][object]$Data)
  if ($null -eq $Data) { return $null }
  if ($Data -is [string] -or $Data -is [ValueType]) { return "$Data" }
  return "$(Get-YinuoProperty -Object $Data -Names @('id','contractId','revisionId','templateVersionId'))"
}

function Get-YinuoAllPages {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Path,
    [int]$PageSize = 100,
    [int]$MaxPages = 20
  )
  $all = [Collections.Generic.List[object]]::new()
  $completed = $false
  for ($pageNo = 1; $pageNo -le $MaxPages; $pageNo++) {
    $separator = if ($Path.Contains('?')) { '&' } else { '?' }
    $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "$Path${separator}pageNo=$pageNo&pageSize=$PageSize" -Body $null
    if (-not $response.ok) { throw "page query failed for '$Path': $(Get-YinuoErrorText $response)" }
    $items = @(Get-YinuoItems $response.data)
    foreach ($item in $items) { $all.Add($item) }
    $total = Get-YinuoProperty -Object $response.data -Names @('total')
    if ($items.Count -lt $PageSize -or ($null -ne $total -and $all.Count -ge [int64]$total)) {
      $completed = $true
      break
    }
  }
  if (-not $completed) {
    throw "page query for '$Path' reached MaxPages=$MaxPages with PageSize=$PageSize before exhaustion; refusing a truncated result"
  }
  return @($all)
}

function Get-YinuoCurrentRevision {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$ContractId
  )
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/contract/revision/list?contractId=$([Uri]::EscapeDataString($ContractId))" -Body $null
  if (-not $response.ok) { throw "revision list failed for contract ${ContractId}: $(Get-YinuoErrorText $response)" }
  $items = @(Get-YinuoItems $response.data)
  if ($items.Count -eq 0) { throw "contract $ContractId has no revision" }
  $current = @($items | Where-Object {
    (Get-YinuoProperty -Object $_ -Names @('current','isCurrent')) -eq $true
  } | Select-Object -First 1)
  if ($current.Count -gt 0) { return $current[0] }
  return @($items | Sort-Object { [int64](Get-YinuoProperty -Object $_ -Names @('revisionNo','versionNo','id')) } -Descending)[0]
}

function Wait-YinuoCondition {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Probe,
    [int]$TimeoutSeconds = 20,
    [int]$IntervalMilliseconds = 500
  )
  $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
  do {
    $value = & $Probe
    if ($null -ne $value) { return $value }
    Start-Sleep -Milliseconds $IntervalMilliseconds
  } while ([DateTime]::UtcNow -lt $deadline)
  return $null
}

function Import-YinuoEnvironment {
  param([Parameter(Mandatory = $true)][string]$RepoRoot)
  $envFile = Join-Path $RepoRoot 'infra/.env'
  if (-not (Test-Path $envFile)) { return }
  foreach ($line in Get-Content $envFile) {
    if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') { continue }
    $name = $Matches[1]
    if ([Environment]::GetEnvironmentVariable($name, 'Process')) { continue }
    $value = $Matches[2].Trim()
    if (($value.StartsWith("'") -and $value.EndsWith("'")) -or
        ($value.StartsWith('"') -and $value.EndsWith('"'))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    Set-Item -Path "Env:$name" -Value $value
  }
}

function Get-YinuoMysqlRunner {
  param([Parameter(Mandatory = $true)][string]$RepoRoot)
  Import-YinuoEnvironment -RepoRoot $RepoRoot
  $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
  if ($dockerCommand) {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
      $state = & $dockerCommand.Source inspect --format '{{.State.Running}}' clm-mysql 2>$null
      if ($LASTEXITCODE -eq 0 -and ([string]$state).Trim() -eq 'true') {
        return [pscustomobject]@{ kind = 'docker'; executable = $dockerCommand.Source }
      }
    } finally {
      $ErrorActionPreference = $oldPreference
    }
  }

  $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
  if ($mysqlCommand) { return [pscustomobject]@{ kind = 'native'; executable = $mysqlCommand.Source } }
  if ($env:OS -eq 'Windows_NT') {
    $mysqlHome = $env:MYSQL_HOME
    if (-not $mysqlHome) { $mysqlHome = Join-Path $RepoRoot 'runtime/tools/mysql-8.0.33-winx64' }
    $candidate = Join-Path $mysqlHome 'bin/mysql.exe'
    if (Test-Path $candidate) { return [pscustomobject]@{ kind = 'native'; executable = $candidate } }
  }
  throw 'no usable MySQL client (expected running clm-mysql container, mysql on PATH, or MYSQL_HOME)'
}

function Invoke-YinuoMysql {
  param(
    [Parameter(Mandatory = $true)][object]$Runner,
    [Parameter(Mandatory = $true)][string]$Sql,
    [switch]$SkipHeaders,
    [AllowEmptyString()][string]$Database = 'ruoyi-vue-pro'
  )
  $dbUser = if ($env:CLM_DB_USERNAME) { $env:CLM_DB_USERNAME } else { 'root' }
  $dbPassword = $env:CLM_DB_PASSWORD
  if (-not $dbPassword) { throw 'CLM_DB_PASSWORD is not configured in the process environment or infra/.env' }
  $arguments = @("--user=$dbUser", '--default-character-set=utf8mb4', '--batch', '--raw')
  if ($SkipHeaders) { $arguments += '--skip-column-names' }
  if ($Database) { $arguments += $Database }

  $previousPassword = $env:MYSQL_PWD
  $previousPreference = $ErrorActionPreference
  $env:MYSQL_PWD = $dbPassword
  $ErrorActionPreference = 'Continue'
  try {
    if ($Runner.kind -eq 'docker') {
      $output = $Sql | & $Runner.executable exec -i -e MYSQL_PWD clm-mysql mysql @arguments 2>&1
    } else {
      $output = $Sql | & $Runner.executable '--host=127.0.0.1' @arguments 2>&1
    }
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
    $env:MYSQL_PWD = $previousPassword
  }
  if ($exitCode -ne 0) { throw "MySQL command failed: $($output -join [Environment]::NewLine)" }
  return @($output | ForEach-Object { "$_" } | Where-Object { $_ -notmatch '^mysql: \[Warning\]' })
}

function Get-YinuoMigrationPreflight {
  param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][ValidatePattern('^[1-9][0-9]*$')][string]$TenantId
  )
  $tenantIdSql = [int64]$TenantId
  $runner = Get-YinuoMysqlRunner -RepoRoot $RepoRoot
  $metadataSql = @"
SELECT CONCAT(TABLE_NAME, '|', COLUMN_NAME)
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'ruoyi-vue-pro'
  AND (TABLE_NAME LIKE 'clm\_%' OR TABLE_NAME LIKE 'ACT\_%');
"@
  $metadata = @(Invoke-YinuoMysql -Runner $runner -Sql $metadataSql -SkipHeaders)
  $columns = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
  foreach ($line in $metadata) {
    if ($line -match '^[^|]+\|[^|]+$') { $null = $columns.Add($line) }
  }
  $tables = @($columns | ForEach-Object { ($_ -split '\|', 2)[0] } | Sort-Object -Unique)
  $requiredTables = @(
    'clm_template', 'clm_template_version', 'clm_contract_revision', 'clm_commitment',
    'clm_numbering_rule_version', 'clm_number_sequence', 'clm_routing_rule_version',
    'clm_approval_task_revision_binding',
    'clm_collaboration_case', 'clm_collaboration_event', 'clm_governance_issue',
    'clm_reconciliation_run', 'clm_permission_policy_version', 'clm_user_scope',
    'clm_user_scope_item',
    'clm_saved_filter', 'clm_ai_review_run', 'clm_ai_review_finding',
    'clm_party_import_job', 'clm_party_import_item', 'clm_integration_run',
    'clm_integration_delivery', 'clm_handover_case', 'clm_handover_item',
    'clm_approval_edit_request'
  )
  $missingTables = @($requiredTables | Where-Object { $_ -notin $tables })
  $requiredColumns = @(
    'clm_contract|id', 'clm_contract|title', 'clm_contract|current_revision_id',
    'clm_contract|source_mode', 'clm_contract|stage_code',
    'clm_contract|no_commitment_confirmed', 'clm_contract|numbering_rule_version_id',
    'clm_workflow_binding|submitted_revision_id', 'clm_workflow_binding|current_revision_id',
    'clm_workflow_binding|approved_revision_id', 'clm_workflow_binding|route_version_id',
    'clm_workflow_binding|submit_request_id', 'clm_workflow_binding|supersedes_case_id',
    'clm_workflow_binding|cancel_reason', 'clm_party|short_name',
    'clm_numbering_rule_version|include_party_short_name',
    'clm_permission_policy_version|role_capabilities_json',
    'clm_permission_policy_version|scope_limits_json',
    'clm_permission_policy_version|node_edit_policy_json',
    'clm_user_scope_item|user_scope_id', 'clm_user_scope_item|scope_type',
    'clm_user_scope_item|scope_id',
    'clm_approval_edit_request|contract_id', 'clm_approval_edit_request|request_id',
    'clm_approval_edit_request|result_revision_id'
  )
  $missingColumns = @($requiredColumns | Where-Object { -not $columns.Contains($_) })
  $roleMenuStateSql = @"
SELECT 'product_roles', COUNT(*) FROM system_role
WHERE tenant_id=$tenantIdSql AND deleted=b'0'
  AND code IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin');
SELECT 'w05_w06_visible', COUNT(*) FROM system_menu
WHERE ((id=7060 AND name='合同协同' AND parent_id=7502)
    OR (id=7070 AND name='审批中心' AND parent_id=7100))
  AND status=0 AND visible=b'1' AND deleted=b'0';
SELECT 'canonical_gs_pages', COUNT(*) FROM system_menu
WHERE id IN (7302,7303,7304,7305,7306,7308,7309,7310,7311,7401,7402,7403,7504)
  AND status=0 AND visible=b'1' AND deleted=b'0' AND component<>'';
SELECT 'canonical_menu_alignment', COUNT(*) FROM system_menu
WHERE status=0 AND visible=b'1' AND deleted=b'0' AND (
     (id=7502 AND name='合同拟制' AND type=1 AND parent_id=7000 AND path='drafting')
  OR (id=7040 AND name='合同起草' AND type=2 AND parent_id=7502 AND path='draft-center')
  OR (id=7030 AND name='合同查询' AND type=2 AND parent_id=7502 AND path='contract')
  OR (id=7060 AND name='合同协同' AND type=2 AND parent_id=7502 AND path='collaboration')
  OR (id=7100 AND name='审批管理' AND type=1 AND parent_id=7000 AND path='approval-management')
  OR (id=7070 AND name='审批中心' AND type=2 AND parent_id=7100 AND path='approval')
  OR (id=7101 AND name='工作流设置' AND type=1 AND parent_id=7100 AND path='workflow-settings')
  OR (id=7311 AND name='流程定义' AND type=2 AND parent_id=7101 AND path='process')
  OR (id=7304 AND name='业务单据流程配置' AND type=2 AND parent_id=7101 AND path='routing')
  OR (id=7302 AND name='模板管理' AND type=2 AND parent_id=7000 AND path='template')
  OR (id=7500 AND name='基础设置' AND type=1 AND parent_id=7000 AND path='base-settings')
  OR (id=7010 AND name='合同分类' AND type=2 AND parent_id=7500 AND path='contract-type')
  OR (id=7504 AND name='页面布局配置' AND type=2 AND parent_id=7500 AND path='page-layout'
      AND component='clm/contractType/layout/index' AND component_name='ClmContractTypeLayout')
  OR (id=7303 AND name='编码规则设置' AND type=2 AND parent_id=7500 AND path='numbering')
  OR (id=7305 AND name='数据权限规则' AND type=2 AND parent_id=7500 AND path='permission')
  OR (id=7102 AND name='基础数据' AND type=1 AND parent_id=7000 AND path='basic-data')
  OR (id=7020 AND name='相对方信息' AND type=2 AND parent_id=7102 AND path='directory')
  OR (id=7308 AND name='相对方导入' AND type=2 AND parent_id=7102 AND path='import')
  OR (id=7501 AND name='工作交接事项' AND type=1 AND parent_id=7000 AND path='handover')
  OR (id=7310 AND name='经办人变更' AND type=2 AND parent_id=7501 AND path='owner-change')
  OR (id=7103 AND name='异常处理' AND type=1 AND parent_id=7000 AND path='exceptions')
  OR (id=7306 AND name='配置异常' AND type=2 AND parent_id=7103 AND path='issues')
  OR (id=7309 AND name='审批异常' AND type=2 AND parent_id=7103 AND path='reconciliation')
  OR (id=7200 AND name='系统管理' AND type=1 AND parent_id=7000 AND path='system')
);
SELECT 'canonical_process_designer', COUNT(*) FROM system_menu
WHERE (id=7311 AND name='流程定义' AND parent_id=7101 AND path='process'
       AND component='clm/governance/process/index'
       AND component_name='ClmGovernanceProcess'
       AND status=0 AND visible=b'1' AND deleted=b'0')
   OR (id IN (7191,7192,7193) AND parent_id=7311
       AND name IN ('流程定义查询','流程定义维护','流程定义发布')
       AND permission IN ('clm:governance:process:query','clm:governance:process:update','clm:governance:process:publish')
       AND status=0 AND deleted=b'0');
SELECT 'process_designer_contract_admin_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0' AND r.code='clm_contract_admin'
  AND rm.deleted=b'0' AND rm.menu_id IN (7311,7191,7192,7193);
SELECT 'canonical_g08_g10', COUNT(*) FROM system_menu
WHERE status=0 AND visible=b'1' AND deleted=b'0'
  AND ((id=7308 AND parent_id=7102 AND path='import'
        AND component='clm/governance/partyImport/index'
        AND component_name='ClmGovernancePartyImport')
    OR (id=7310 AND parent_id=7501 AND path='owner-change'
        AND component='clm/governance/handover/index'
        AND component_name='ClmGovernanceHandover'));
SELECT 'canonical_g01_g07', COUNT(*) FROM system_menu
WHERE status=0 AND visible=b'1' AND deleted=b'0'
  AND ((id=7010 AND name='合同分类' AND parent_id=7500 AND path='contract-type')
    OR (id=7020 AND name='相对方信息' AND parent_id=7102 AND path='directory'));
SELECT 'clm_menu_orphans', COUNT(*)
FROM system_menu m
LEFT JOIN system_menu p ON p.id=m.parent_id AND p.deleted=b'0'
WHERE m.id BETWEEN 7000 AND 7999 AND m.deleted=b'0'
  AND m.status=0 AND m.visible=b'1' AND m.parent_id<>0 AND p.id IS NULL;
SELECT 'product_role_menu_duplicates', COUNT(*) FROM (
  SELECT rm.role_id, rm.menu_id
  FROM system_role_menu rm
  JOIN system_role r ON r.id=rm.role_id
  WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0'
    AND r.code IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin')
    AND rm.deleted=b'0'
  GROUP BY rm.role_id, rm.menu_id
  HAVING COUNT(*)<>1
) duplicate_grants;
SELECT 'business_menu_count', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.code='clm_business' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'legal_menu_count', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.code='clm_legal' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'contract_admin_menu_count', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.code='clm_contract_admin' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'system_admin_menu_count', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.code='clm_system_admin' AND r.deleted=b'0' AND rm.deleted=b'0';
SELECT 'alignment_parent_grants', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0' AND rm.deleted=b'0' AND (
     (r.code='clm_business' AND rm.menu_id IN (7502,7100))
  OR (r.code='clm_legal' AND rm.menu_id IN (7502,7100))
  OR (r.code='clm_contract_admin' AND rm.menu_id IN (7502,7500,7501,7504))
  OR (r.code='clm_system_admin' AND rm.menu_id=7100)
);
SELECT 'alignment_forbidden_grants', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0' AND rm.deleted=b'0'
  AND r.code IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin')
  AND ((rm.menu_id IN (7500,7501,7504,7101,7311,7304) AND r.code<>'clm_contract_admin')
    OR (rm.menu_id=7502 AND r.code NOT IN ('clm_business','clm_legal','clm_contract_admin')));
SELECT 'common_clm_grants', COUNT(*)
FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.code='common' AND rm.menu_id BETWEEN 7000 AND 7999;
SELECT 'system_admin_body_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
JOIN system_menu m ON m.id=rm.menu_id
WHERE r.tenant_id=$tenantIdSql AND r.code='clm_system_admin'
  AND (m.permission REGEXP '^clm:(contract|revision|document|commitment|template|ai-review):'
       OR m.permission REGEXP '^clm:approval:(approve|reject|return|history|edit|collaborate|withdraw-case)$');
SELECT 'approval_collaboration_permissions', COUNT(*) FROM system_menu
WHERE id IN (7077,7078)
  AND permission IN ('clm:approval:collaborate','clm:approval:withdraw-case')
  AND status=0 AND deleted=b'0';
SELECT 'approval_collaboration_role_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0'
  AND r.code IN ('clm_business','clm_legal','clm_contract_admin')
  AND rm.menu_id IN (7077,7078);
SELECT 'party_import_confirm_permission', COUNT(*) FROM system_menu
WHERE id=7163 AND parent_id=7308 AND permission='clm:party-import:confirm' AND status=0 AND deleted=b'0';
SELECT 'handover_permissions', COUNT(*) FROM system_menu
WHERE id IN (7181,7182)
  AND parent_id=7310
  AND permission IN ('clm:handover:query','clm:handover:reassign-task')
  AND status=0 AND deleted=b'0';
SELECT 'handover_contract_admin_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0' AND r.code='clm_contract_admin'
  AND rm.menu_id IN (7181,7182);
SELECT 'handover_system_admin_grants', COUNT(*)
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id
WHERE r.tenant_id=$tenantIdSql AND r.deleted=b'0' AND r.code='clm_system_admin'
  AND rm.menu_id IN (7181,7182);
"@
  $roleMenuOutput = @(Invoke-YinuoMysql -Runner $runner -Sql $roleMenuStateSql -SkipHeaders)
  $roleMenuState = @{}
  foreach ($line in $roleMenuOutput) {
    if ($line -match '^([a-z0-9_]+)\s+([0-9]+)$') { $roleMenuState[$Matches[1]] = [int]$Matches[2] }
  }
  $roleMenuReady = $roleMenuState['product_roles'] -eq 4 -and
      $roleMenuState['w05_w06_visible'] -eq 2 -and
      $roleMenuState['canonical_gs_pages'] -eq 13 -and
      $roleMenuState['canonical_menu_alignment'] -eq 24 -and
      $roleMenuState['canonical_process_designer'] -eq 4 -and
      $roleMenuState['process_designer_contract_admin_grants'] -eq 4 -and
      $roleMenuState['canonical_g08_g10'] -eq 2 -and
      $roleMenuState['canonical_g01_g07'] -eq 2 -and
      $roleMenuState['clm_menu_orphans'] -eq 0 -and
      $roleMenuState['product_role_menu_duplicates'] -eq 0 -and
      $roleMenuState['business_menu_count'] -eq 37 -and
      $roleMenuState['legal_menu_count'] -eq 31 -and
      $roleMenuState['contract_admin_menu_count'] -eq 66 -and
      $roleMenuState['system_admin_menu_count'] -eq 44 -and
      $roleMenuState['alignment_parent_grants'] -eq 9 -and
      $roleMenuState['alignment_forbidden_grants'] -eq 0 -and
      $roleMenuState['common_clm_grants'] -eq 0 -and
      $roleMenuState['system_admin_body_grants'] -eq 0 -and
      $roleMenuState['approval_collaboration_permissions'] -eq 2 -and
      $roleMenuState['approval_collaboration_role_grants'] -eq 6 -and
      $roleMenuState['party_import_confirm_permission'] -eq 1 -and
      $roleMenuState['handover_permissions'] -eq 2 -and
      $roleMenuState['handover_contract_admin_grants'] -eq 2 -and
      $roleMenuState['handover_system_admin_grants'] -eq 0
  $problems = [Collections.Generic.List[string]]::new()
  if ($missingTables.Count -gt 0) { $problems.Add("tables=$($missingTables -join ',')") }
  if ($missingColumns.Count -gt 0) { $problems.Add("columns=$($missingColumns -join ',')") }
  if (-not $roleMenuReady) {
    $roleDetails = ($roleMenuState.GetEnumerator() | Sort-Object Name | ForEach-Object {
      '{0}:{1}' -f $_.Name, $_.Value
    }) -join ','
    $problems.Add("roles/menus=$roleDetails")
  }
  return [pscustomobject]@{
    ready = $missingTables.Count -eq 0 -and $missingColumns.Count -eq 0 -and $roleMenuReady
    runner = $runner
    columns = $columns
    tables = $tables
    missingTables = $missingTables
    missingColumns = $missingColumns
    roleMenuState = $roleMenuState
    problems = @($problems)
  }
}
