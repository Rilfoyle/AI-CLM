[CmdletBinding()]
param(
  [string]$BaseUrl = 'http://127.0.0.1:48080/admin-api',
  [string]$Username = 'admin',
  [string]$Password = 'admin123',
  [string]$TenantName = 'TuriX',
  [ValidatePattern('^[1-9][0-9]*$')][string]$TenantId = '1',
  [string]$DemoPassword = 'admin123',
  [switch]$ResetData
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot 'lib/YinuoV1.ps1')
. (Join-Path $PSScriptRoot 'lib/TurixDemoSeed.ps1')
Start-YinuoRun -Name 'seed-demo-data' -BaseUrl $BaseUrl

$repoRoot = Split-Path -Parent $PSScriptRoot
$documentDir = Join-Path $repoRoot 'runtime/demo-docs'
$processDefinitionKey = 'clm_contract_approval_v1'
$modelScript = Join-Path $PSScriptRoot 'create-bpm-model.ps1'
$formConf = '{"form":{"labelWidth":"120px"}}'

$typeSpecs = @(
  @{
    key = 'purchase'; code = 'turix-purchase'; name = '采购合同'; sort = 10
    description = '货物、设备与物料采购类合同'; document = '物资采购合同范本.docx'
    templateCode = 'TURIX-PURCHASE-STANDARD'; templateName = '采购合同标准模板'
    remark = 'TuriX V1 采购合同类型'; templateRemark = 'TuriX V1 独立发布模板'
    formConf = $formConf
    formFields = @(
      '{"type":"datePicker","field":"deliveryDate","title":"交付日期","$required":true,"props":{"type":"date","valueFormat":"yyyy-MM-dd"},"_fc_id":"turix_purchase_delivery","name":"turix_purchase_delivery","_fc_drag_tag":"datePicker","hidden":false,"display":true}',
      '{"type":"input","field":"acceptCriteria","title":"验收标准","props":{"type":"textarea"},"_fc_id":"turix_purchase_accept","name":"turix_purchase_accept","_fc_drag_tag":"input","hidden":false,"display":true}',
      '{"type":"inputNumber","field":"warrantyMonths","title":"质保期（月）","props":{},"_fc_id":"turix_purchase_warranty","name":"turix_purchase_warranty","_fc_drag_tag":"inputNumber","hidden":false,"display":true}'
    )
  },
  @{
    key = 'sales'; code = 'turix-sales'; name = '销售合同'; sort = 20
    description = '产品与服务对外销售类合同'; document = '产品销售合同范本.docx'
    templateCode = 'TURIX-SALES-STANDARD'; templateName = '销售合同标准模板'
    remark = 'TuriX V1 销售合同类型'; templateRemark = 'TuriX V1 独立发布模板'
    formConf = $formConf
    formFields = @(
      '{"type":"inputNumber","field":"paymentDays","title":"回款账期（天）","props":{},"_fc_id":"turix_sales_payment","name":"turix_sales_payment","_fc_drag_tag":"inputNumber","hidden":false,"display":true}',
      '{"type":"switch","field":"freightIncluded","title":"是否含运费","props":{},"_fc_id":"turix_sales_freight","name":"turix_sales_freight","_fc_drag_tag":"switch","hidden":false,"display":true}'
    )
  },
  @{
    key = 'tech-service'; code = 'turix-tech-service'; name = '技术服务合同'; sort = 30
    description = '技术开发、实施、运维与咨询服务合同'; document = '技术服务合同范本.docx'
    templateCode = 'TURIX-TECH-SERVICE-STANDARD'; templateName = '技术服务合同标准模板'
    remark = 'TuriX V1 技术服务合同类型'; templateRemark = 'TuriX V1 独立发布模板'
    formConf = $formConf
    formFields = @(
      '{"type":"input","field":"servicePeriod","title":"服务周期","props":{},"_fc_id":"turix_service_period","name":"turix_service_period","_fc_drag_tag":"input","hidden":false,"display":true}',
      '{"type":"input","field":"milestones","title":"验收里程碑","props":{"type":"textarea"},"_fc_id":"turix_service_milestones","name":"turix_service_milestones","_fc_drag_tag":"input","hidden":false,"display":true}'
    )
  },
  @{
    key = 'nda'; code = 'turix-nda'; name = '保密协议'; sort = 40
    description = '商务合作与联合研发保密协议'; document = '标准保密协议范本.docx'
    templateCode = 'TURIX-NDA-STANDARD'; templateName = '保密协议标准模板'
    remark = 'TuriX V1 保密协议类型'; templateRemark = 'TuriX V1 独立发布模板'
    formConf = $formConf
    formFields = @(
      '{"type":"inputNumber","field":"confidentialYears","title":"保密期限（年）","props":{},"_fc_id":"turix_nda_years","name":"turix_nda_years","_fc_drag_tag":"inputNumber","hidden":false,"display":true}'
    )
  }
)

$userSpecs = @(
  @{ id = 200001; username = 'business'; nickname = '业务经办人'; deptId = 101; roleCode = 'clm_business' },
  @{ id = 200002; username = 'legal'; nickname = '法务'; deptId = 102; roleCode = 'clm_legal' },
  @{ id = 200003; username = 'contractadmin'; nickname = '合同管理员'; deptId = 103; roleCode = 'clm_contract_admin' },
  @{ id = 200004; username = 'systemadmin'; nickname = '系统管理员'; deptId = 104; roleCode = 'clm_system_admin' }
)

$partySpecs = @(
  @{
    key = 'our'; code = 'TURIX-DEMO-OUR-001'; name = 'TuriX 演示科技有限公司'; shortName = 'TuriX'
    internal = $true; contactName = '演示联系人（我方）'; contactPhone = '000-0000-0001'; address = '演示地址（非真实）'
  },
  @{
    key = 'east-manufacturing'; code = 'TURIX-DEMO-CPTY-001'; name = '演示华东智造有限公司'; shortName = ''
    internal = $false; contactName = '演示联系人甲'; contactPhone = '000-0000-0101'; address = '演示地址甲（非真实）'
  },
  @{
    key = 'data-service'; code = 'TURIX-DEMO-CPTY-002'; name = '演示数云技术服务有限公司'; shortName = ''
    internal = $false; contactName = '演示联系人乙'; contactPhone = '000-0000-0102'; address = '演示地址乙（非真实）'
  },
  @{
    key = 'sensor'; code = 'TURIX-DEMO-CPTY-003'; name = '演示精工传感器有限公司'; shortName = ''
    internal = $false; contactName = '演示联系人丙'; contactPhone = '000-0000-0103'; address = '演示地址丙（非真实）'
  },
  @{
    key = 'customer-platform'; code = 'TURIX-DEMO-CPTY-004'; name = '演示客户运营科技有限公司'; shortName = ''
    internal = $false; contactName = '演示联系人丁'; contactPhone = '000-0000-0104'; address = '演示地址丁（非真实）'
  },
  @{
    key = 'joint-rd'; code = 'TURIX-DEMO-CPTY-005'; name = '演示联合研发中心有限公司'; shortName = ''
    internal = $false; contactName = '演示联系人戊'; contactPhone = '000-0000-0105'; address = '演示地址戊（非真实）'
  }
)

$contractSpecs = @(
  @{
    key = 'purchase-draft'; state = 'DRAFT'; type = 'purchase'; counterparty = 'east-manufacturing'
    name = '演示｜2026 年度办公设备采购合同'; amount = 480000
    startDate = '2026-09-01'; endDate = '2027-08-31'; description = '办公设备采购演示草稿，不包含真实主体或真实交易。'
    customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = '开箱验机并完成资产登记后验收。'; warrantyMonths = 24 }
  },
  @{
    key = 'service-collaboration'; state = 'COLLABORATING'; type = 'tech-service'; counterparty = 'data-service'
    name = '演示｜数据中台建设技术服务合同'; amount = 1680000
    startDate = '2026-09-15'; endDate = '2027-06-30'; description = '技术服务范围与验收边界法务协同演示。'
    customData = @{ servicePeriod = '2026 年 9 月至 2027 年 6 月'; milestones = '需求确认、数据接入、指标服务上线、项目验收。' }
  },
  @{
    key = 'purchase-pending-legal'; state = 'PENDING_LEGAL'; type = 'purchase'; counterparty = 'sensor'
    name = '演示｜工业传感器年度采购框架合同'; amount = 2860000
    startDate = '2026-10-01'; endDate = '2027-09-30'; description = '等待法务审批的一期演示合同。'
    customData = @{ deliveryDate = '2026-11-15'; acceptCriteria = '分批抽检，双方签署到货验收单。'; warrantyMonths = 36 }
  },
  @{
    key = 'service-pending-admin'; state = 'PENDING_CONTRACT_ADMIN'; type = 'tech-service'; counterparty = 'customer-platform'
    name = '演示｜客户服务平台技术服务合同'; amount = 1260000
    startDate = '2026-09-20'; endDate = '2027-05-31'; description = '法务已通过、等待合同管理员复核的一期演示合同。'
    customData = @{ servicePeriod = '2026 年 9 月至 2027 年 5 月'; milestones = '蓝图确认、试运行、正式上线、终验。' }
  },
  @{
    key = 'nda-rejected'; state = 'REJECTED'; type = 'nda'; counterparty = 'joint-rd'
    name = '演示｜联合研发保密协议'; amount = 50000
    startDate = '2026-09-01'; endDate = '2031-08-31'; description = '法务退回状态的一期演示协议。'
    customData = @{ confidentialYears = 5 }
  },
  @{
    key = 'sales-approved'; state = 'APPROVED'; type = 'sales'; counterparty = 'east-manufacturing'
    name = '演示｜华东区产品销售框架合同'; amount = 4120000
    startDate = '2026-10-01'; endDate = '2027-09-30'; description = '审批完成且明确停在签署前的一期演示合同。'
    customData = @{ paymentDays = 60; freightIncluded = $true }
  }
)

try {
  Write-Host '== preflight: PowerShell, documents, schema, endpoints'
  if ($TenantName -cne 'TuriX' -or $TenantId -ne '1') {
    throw "this seed is hard-bounded to the local TuriX tenant (name=TuriX, id=1); requested name=$TenantName, id=$TenantId"
  }
  if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'seed-demo-data.ps1 requires PowerShell 7+ for deterministic multipart template upload; use pwsh'
  }
  foreach ($type in $typeSpecs) {
    $filePath = Join-Path $documentDir $type.document
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) { throw "required demo template is missing: $filePath" }
    if ((Get-Item -LiteralPath $filePath).Length -le 0) { throw "required demo template is empty: $filePath" }
  }
  if (-not (Test-Path -LiteralPath $modelScript -PathType Leaf)) { throw "BPM model script is missing: $modelScript" }

  $migration = Get-YinuoMigrationPreflight -RepoRoot $repoRoot -TenantId $TenantId
  $null = Add-YinuoCheck -Condition $migration.ready -Id 'PREFLIGHT_SCHEMA' -Message 'SQL 01-13 schema, product roles, and OneContract-aligned menus are initialized' -Details $(if ($migration.ready) { "runner=$($migration.runner.kind)" } else { $migration.problems -join '; ' })
  if (-not $migration.ready) { throw "schema/role/menu preflight failed; no seed write was attempted ($($migration.problems -join '; '))" }
  $tenantRows = @(Invoke-YinuoMysql -Runner $migration.runner -Sql "SELECT name FROM system_tenant WHERE id=1 AND deleted=b'0';" -SkipHeaders)
  if ($tenantRows.Count -ne 1 -or "$($tenantRows[0])" -cne 'TuriX') {
    throw "database tenant id=1 is not the unique TuriX tenant; no seed write was attempted (rows=$($tenantRows -join ','))"
  }
  $normalizedRoutingNames = Normalize-TurixDemoRoutingDisplayName -Migration $migration -TenantId $TenantId -TenantName $TenantName
  $null = Add-YinuoCheck -Condition $true -Id 'PREFLIGHT_TENANT_BOUNDARY' -Message 'database writes are bounded to tenant id=1 named TuriX' -Details "normalizedLegacyDemoRoutingNames=$normalizedRoutingNames"

  $adminHeaders = Connect-YinuoApi -Username $Username -Password $Password -TenantName $TenantName -TenantId $TenantId
  $null = Add-YinuoCheck -Condition ($null -ne $adminHeaders) -Id 'PREFLIGHT_LOGIN' -Message 'TuriX administrator logged in' -Details $null
  foreach ($probe in @(
    @{ path = '/clm/contract-type/page?pageNo=1&pageSize=1'; name = 'contract type' },
    @{ path = '/clm/governance/template/page?pageNo=1&pageSize=1'; name = 'template governance' },
    @{ path = '/clm/governance/numbering/page?pageNo=1&pageSize=1'; name = 'numbering governance' },
    @{ path = '/clm/governance/routing/page?pageNo=1&pageSize=1'; name = 'routing governance' },
    @{ path = '/clm/permission/policy/list'; name = 'permission policy' },
    @{ path = '/clm/contract/page?pageNo=1&pageSize=1'; name = 'contract ledger' }
  )) {
    $response = Invoke-YinuoApi -Headers $adminHeaders -Method GET -Path $probe.path -Body $null
    $null = Assert-TurixApi -Response $response -What "$($probe.name) preflight"
  }

  Write-Host '== fixed accounts: verify only, never mutate'
  $fixedUsers = @{}
  foreach ($spec in $userSpecs) {
    $fixedUsers[$spec.username] = Assert-TurixFixedUser -AdminHeaders $adminHeaders -Spec $spec -Password $DemoPassword -TenantName $TenantName -TenantId $TenantId
  }

  Write-Host '== BPM: legal(200002) -> contractadmin(200003), deploy only when changed'
  $pwshExecutable = Join-Path $PSHOME 'pwsh'
  if (-not (Test-Path -LiteralPath $pwshExecutable)) { $pwshExecutable = (Get-Command pwsh -ErrorAction Stop).Source }
  $apiHost = $BaseUrl -replace '/admin-api/?$', ''
  $modelOutput = @(& $pwshExecutable -NoProfile -File $modelScript -BaseUrl $apiHost -Username $Username -Password $Password -TenantName $TenantName -TenantId $TenantId -LegalUserId 200002 -ManagerUserId 200003 2>&1)
  $modelExit = $LASTEXITCODE
  $modelOutput | ForEach-Object { Write-Host "  $_" }
  if ($modelExit -ne 0) { throw "BPM model deployment failed with exit code $modelExit" }
  $null = Add-YinuoCheck -Condition $true -Id 'BPM_MODEL' -Message 'verified the two-node TuriX approval model without duplicate deployment' -Details 'legal=200002 -> contractadmin=200003'
  $processList = Invoke-YinuoApi -Headers $fixedUsers['contractadmin'].headers -Method GET -Path '/clm/governance/process/list' -Body $null
  $processModels = @(Get-YinuoItems $processList.data)
  $defaultProcess = @($processModels | Where-Object { $_.key -eq $processDefinitionKey } | Select-Object -First 1)
  $processId = if ($defaultProcess.Count -eq 1) { "$(Get-YinuoProperty -Object $defaultProcess[0] -Names @('id'))" } else { '' }
  $processDetail = if ($processId) {
    Invoke-YinuoApi -Headers $fixedUsers['contractadmin'].headers -Method GET -Path "/clm/governance/process/get?id=$([Uri]::EscapeDataString($processId))" -Body $null
  } else { $null }
  $processReady = $processList.ok -and $defaultProcess.Count -eq 1 -and $processDetail.ok -and
    $processDetail.data.type -eq 20 -and $processDetail.data.formType -eq 20 -and
    $null -ne $processDetail.data.simpleModel
  $null = Add-YinuoCheck -Condition $processReady -Id 'BPM_MODEL_GOVERNANCE' -Message 'contract administrator can open the default model in the visual process-definition editor' -Details "models=$($processModels.Count), id=$processId"
  if (-not $processReady) { throw 'default BPM model is not available through the CLM process-definition governance API' }

  if ($ResetData) {
    Write-Host '== explicit data reset: business demo only, catalog retained'
    Remove-TurixDemoBusinessData -Headers $adminHeaders -Migration $migration -TenantId $TenantId
  }

  Write-Host '== catalog: 4 contract types and 4 independent published templates'
  $typeResults = @{}
  $templateResults = @{}
  foreach ($spec in $typeSpecs) {
    $typeResults[$spec.key] = Ensure-TurixContractType -Headers $adminHeaders -Spec $spec -ProcessDefinitionKey $processDefinitionKey
    $templateSpec = @{
      key = $spec.key; code = $spec.templateCode; name = $spec.templateName
      description = "$($spec.description)标准独立模板"; remark = $spec.templateRemark
    }
    $templateResults[$spec.key] = Ensure-TurixTemplate -Headers $adminHeaders -Spec $templateSpec -ContractTypeId $typeResults[$spec.key].id -FilePath (Join-Path $documentDir $spec.document)
  }
  $typeIds = @($typeSpecs | ForEach-Object { [int64]$typeResults[$_.key].id })

  Write-Host '== catalog: one numbering rule, one approval route, exact policy scopes'
  $numberingRuleId = Ensure-TurixNumberingRule -Headers $adminHeaders
  $routingRuleId = Ensure-TurixRoutingRule -Headers $adminHeaders -ProcessDefinitionKey $processDefinitionKey
  $policyVersionId = Ensure-TurixPublishedPolicy -Headers $adminHeaders
  foreach ($spec in $userSpecs) {
    if ($spec.roleCode -eq 'clm_system_admin') {
      $null = Ensure-TurixUserScope -Headers $adminHeaders -UserId $spec.id -RoleCode $spec.roleCode -PolicyVersionId $policyVersionId -OrgIds @() -ContractTypeIds @()
    } else {
      $null = Ensure-TurixUserScope -Headers $adminHeaders -UserId $spec.id -RoleCode $spec.roleCode -PolicyVersionId $policyVersionId -OrgIds @(101) -ContractTypeIds $typeIds
    }
  }

  $reservedNames = @($contractSpecs | ForEach-Object { $_.name })
  $prefixedBeforeSeed = @(Get-YinuoAllPages -Headers $fixedUsers['business'].headers -Path "/clm/contract/page?title=$([Uri]::EscapeDataString($script:TurixDemoPrefix))") | Where-Object {
    "$(Get-YinuoContractName $_)".StartsWith($script:TurixDemoPrefix, [StringComparison]::Ordinal)
  }
  $unexpectedPrefixed = @($prefixedBeforeSeed | Where-Object { "$(Get-YinuoContractName $_)" -notin $reservedNames })
  $duplicatePrefixedNames = @($prefixedBeforeSeed | Group-Object { "$(Get-YinuoContractName $_)" } | Where-Object { $_.Count -gt 1 })
  if ($unexpectedPrefixed.Count -gt 0 -or $duplicatePrefixedNames.Count -gt 0) {
    throw "the reserved '$script:TurixDemoPrefix' namespace contains unexpected or duplicate business data; use -ResetData for an explicit clean rebuild"
  }

  Write-Host '== fictional parties'
  $partyIds = @{}
  foreach ($spec in $partySpecs) {
    # The business role intentionally has no party-directory governance writes.
    $partyIds[$spec.key] = Ensure-TurixParty -Headers $adminHeaders -Spec $spec
  }

  Write-Host '== six deterministic phase-one contract states'
  $contractIds = @{}
  foreach ($spec in $contractSpecs) {
    $contractIds[$spec.key] = Ensure-TurixDemoContract `
      -BusinessHeaders $fixedUsers['business'].headers `
      -LegalHeaders $fixedUsers['legal'].headers `
      -ContractAdminHeaders $fixedUsers['contractadmin'].headers `
      -Spec $spec `
      -ContractTypeId $typeResults[$spec.type].id `
      -ContractTypeVersionId $typeResults[$spec.type].versionId `
      -TemplateVersionId $templateResults[$spec.type].versionId `
      -TemplateChecksum $templateResults[$spec.type].checksum `
      -OurPartyId $partyIds['our'] `
      -CounterpartyId $partyIds[$spec.counterparty]
  }

  Write-Host '== final deterministic and scope guards'
  $allDemoContracts = @(Get-YinuoAllPages -Headers $fixedUsers['business'].headers -Path "/clm/contract/page?title=$([Uri]::EscapeDataString($script:TurixDemoPrefix))") | Where-Object {
    "$(Get-YinuoContractName $_)".StartsWith($script:TurixDemoPrefix, [StringComparison]::Ordinal)
  }
  $expectedNames = @($contractSpecs | ForEach-Object { $_.name } | Sort-Object)
  $actualNames = @($allDemoContracts | ForEach-Object { "$(Get-YinuoContractName $_)" } | Sort-Object)
  $countOk = $allDemoContracts.Count -eq 6 -and (Test-TurixArrayEqual $actualNames $expectedNames)
  $null = Add-YinuoCheck -Condition $countOk -Id 'FINAL_CONTRACT_SET' -Message 'the exact six prefixed demo contracts exist with no duplicate title' -Details "count=$($allDemoContracts.Count)"
  if (-not $countOk) { throw "deterministic contract set mismatch: expected=[$($expectedNames -join '; ')], actual=[$($actualNames -join '; ')]" }

  $forbidden = @($allDemoContracts | Where-Object {
    $stage = "$(Get-YinuoProperty -Object $_ -Names @('stageCode'))".ToUpperInvariant()
    $lifecycle = Get-YinuoProperty -Object $_ -Names @('lifecycleStatus')
    $stage -match 'SIGN|ARCHIV|PERFORM|EFFECTIVE|EXPIRED|TERMINAT|CLOSED' -or ($null -ne $lifecycle -and [int]$lifecycle -ge 3)
  })
  $null = Add-YinuoCheck -Condition ($forbidden.Count -eq 0) -Id 'FINAL_PHASE_ONE_BOUNDARY' -Message 'no demo contract entered signing, archive, performance, or later lifecycle stages' -Details "forbidden=$($forbidden.Count)"

  $systemAdminProbe = Invoke-YinuoApi -Headers $fixedUsers['systemadmin'].headers -Method GET -Path "/clm/contract/get?id=$($contractIds['purchase-draft'])" -Body $null
  $null = Add-YinuoCheck -Condition (-not $systemAdminProbe.ok) -Id 'FINAL_SYSTEM_ADMIN_NONE_NONE' -Message 'system administrator cannot read contract body under NONE/NONE scope' -Details $(if ($systemAdminProbe.ok) { 'unexpectedly allowed' } else { Get-YinuoErrorText $systemAdminProbe })

  $summary = [ordered]@{
    tenant = $TenantName; tenantId = $TenantId; resetData = [bool]$ResetData
    users = @($userSpecs | ForEach-Object { @{ id = $_.id; username = $_.username; deptId = $_.deptId } })
    contractTypeIds = $typeIds
    templateVersionIds = @($typeSpecs | ForEach-Object { [int64]$templateResults[$_.key].versionId })
    numberingRuleId = $numberingRuleId; routingRuleId = $routingRuleId; policyVersionId = $policyVersionId
    contractIds = @($contractSpecs | ForEach-Object { [int64]$contractIds[$_.key] })
    boundary = 'approval only; no signing/archive/performance data'
  }
  Write-Output ($summary | ConvertTo-Json -Depth 10 -Compress)
} catch {
  $null = Add-YinuoCheck -Condition $false -Id 'SEED_EXCEPTION' -Message 'TuriX deterministic seed completed without an unhandled blocker' -Details $_.Exception.Message
}

Write-YinuoSummary
exit $script:YinuoFailedCount
