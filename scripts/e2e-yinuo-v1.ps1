param(
  [string]$BaseUrl = 'http://127.0.0.1:48080/admin-api',
  [string]$BusinessUsername = 'admin',
  [string]$BusinessPassword = 'admin123',
  [string]$LegalUsername = 'legal',
  [string]$LegalPassword = 'admin123',
  [string]$DemoBusinessUsername = 'business',
  [string]$ContractAdminUsername = 'contractadmin',
  [string]$SystemAdminUsername = 'systemadmin',
  [string]$DemoPassword = 'admin123',
  [string]$TenantName = 'TuriX',
  [string]$TenantId = '1'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot 'lib/YinuoV1.ps1')
Start-YinuoRun -Name 'e2e-yinuo-v1' -BaseUrl $BaseUrl

$repoRoot = Split-Path -Parent $PSScriptRoot
$runId = Get-Date -Format 'yyyyMMddHHmmssfff'
$contractName = "demo-v1-e2e $runId 标准采购合同"
$ourPartyName = "demo-v1-e2e $runId 我方主体"
$counterpartyName = "demo-v1-e2e $runId 相对方"
$nonstandardName = "demo-v1-e2e $runId 非标上传合同"
$editContractName = "demo-v1-e2e $runId 审批编辑合同"
$handoverContractName = "demo-v1-e2e $runId 经办人变更草稿"
$savedFilterName = "demo-v1-e2e $runId saved filter"
$integrationRunKey = "demo-v1-e2e-$runId-integration"
$partyImportJobKey = "demo-v1-e2e-$runId-party-import"
$partyImportName = "demo-v1-e2e $runId 导入相对方"
$resetScript = Join-Path $PSScriptRoot 'reset-yinuo-v1-demo.ps1'
$demoDocument = Join-Path $repoRoot 'runtime/demo-docs/物资采购合同范本.docx'
$engine = (Get-Process -Id $PID).Path

function Invoke-YinuoE2EReset {
  $output = & $engine -NoProfile -File $resetScript -Scope E2E -BaseUrl $BaseUrl -Username $BusinessUsername -Password $BusinessPassword -TenantName $TenantName -TenantId $TenantId 2>&1
  $exitCode = $LASTEXITCODE
  foreach ($line in @($output)) { Write-Host "  reset> $line" }
  return $exitCode
}

function Get-YinuoContractDetail {
  param([hashtable]$Headers, [string]$ContractId)
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/contract/get?id=$([Uri]::EscapeDataString($ContractId))" -Body $null
  if (-not $response.ok) { throw "contract detail failed for ${ContractId}: $(Get-YinuoErrorText $response)" }
  return $response.data
}

function New-YinuoParty {
  param(
    [hashtable]$Headers,
    [string]$Name,
    [bool]$Internal,
    [AllowEmptyString()][string]$ShortName = ''
  )
  $response = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/party/create' -Body @{
    partyType = 1
    name = $Name
    shortName = if ($Internal) { $ShortName } else { $null }
    internalFlag = $Internal
    status = 0
    remark = 'Yinuo V1 E2E; reset by exact demo-v1-e2e prefix'
  }
  if (-not $response.ok -or -not (Get-YinuoId $response.data)) {
    throw "party create failed for '$Name': $(Get-YinuoErrorText $response)"
  }
  return Get-YinuoId $response.data
}

function Find-YinuoApprovalTask {
  param(
    [hashtable]$Headers,
    [string]$ContractId,
    [string]$ExcludedTaskId
  )
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path '/clm/workbench/items?type=APPROVAL&pageNo=1&pageSize=100' -Body $null
  if (-not $response.ok) { throw "approval work-item query failed: $(Get-YinuoErrorText $response)" }
  $matches = @(Get-YinuoItems $response.data) | Where-Object {
    "$(Get-YinuoProperty -Object $_ -Names @('contractId'))" -eq $ContractId -and
    [bool](Get-YinuoProperty -Object $_ -Names @('taskId')) -and
    (-not $ExcludedTaskId -or "$(Get-YinuoProperty -Object $_ -Names @('taskId'))" -ne $ExcludedTaskId)
  }
  if ($matches.Count -eq 0) { return $null }
  return $matches[0]
}

function Test-YinuoHistoryBinding {
  param(
    [AllowNull()][object]$Node,
    [Parameter(Mandatory = $true)][string]$TaskId,
    [Parameter(Mandatory = $true)][string]$RevisionId
  )
  if ($null -eq $Node -or $Node -is [string] -or $Node -is [ValueType]) { return $false }
  $nodeTaskId = Get-YinuoProperty -Object $Node -Names @('taskId')
  $nodeRevisionId = Get-YinuoProperty -Object $Node -Names @('decisionRevisionId', 'revisionId')
  if ("${nodeTaskId}" -eq $TaskId -and "${nodeRevisionId}" -eq $RevisionId) { return $true }
  if ($Node -is [Collections.IDictionary]) {
    foreach ($value in $Node.Values) {
      if (Test-YinuoHistoryBinding -Node $value -TaskId $TaskId -RevisionId $RevisionId) { return $true }
    }
    return $false
  }
  if ($Node -is [Collections.IEnumerable]) {
    foreach ($value in $Node) {
      if (Test-YinuoHistoryBinding -Node $value -TaskId $TaskId -RevisionId $RevisionId) { return $true }
    }
    return $false
  }
  foreach ($property in $Node.PSObject.Properties) {
    if ($property.Name -in @('taskId','decisionRevisionId','revisionId')) { continue }
    if (Test-YinuoHistoryBinding -Node $property.Value -TaskId $TaskId -RevisionId $RevisionId) { return $true }
  }
  return $false
}

function Wait-YinuoContractState {
  param(
    [hashtable]$Headers,
    [string]$ContractId,
    [scriptblock]$Predicate
  )
  return Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe {
    $detail = Get-YinuoContractDetail -Headers $Headers -ContractId $ContractId
    if (& $Predicate $detail) { return $detail }
    return $null
  }
}

function Get-YinuoExactUser {
  param([hashtable]$Headers, [string]$Username)
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/system/user/page?username=$([Uri]::EscapeDataString($Username))&pageNo=1&pageSize=100" -Body $null
  if (-not $response.ok) { throw "system user query failed for '$Username': $(Get-YinuoErrorText $response)" }
  $matches = @((Get-YinuoItems $response.data) | Where-Object { $_.username -eq $Username })
  if ($matches.Count -ne 1) { throw "expected one system user '$Username', found $($matches.Count); run seed-demo-data.ps1" }
  return $matches[0]
}

function Connect-YinuoOptionalAccount {
  param([string]$Username, [string]$Password)
  try {
    return [pscustomobject]@{ headers = (Connect-YinuoApi -Username $Username -Password $Password -TenantName $TenantName -TenantId $TenantId); error = $null }
  } catch {
    return [pscustomobject]@{ headers = $null; error = $_.Exception.Message }
  }
}

function New-YinuoReadyStandardContract {
  param(
    [hashtable]$Headers,
    [string]$Name,
    [string]$TemplateVersionId,
    [string]$OurPartyId,
    [string]$CounterpartyId,
    [decimal]$Amount
  )
  $create = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/contract/create-from-template' -Body @{
    templateVersionId = [int64]$TemplateVersionId; name = $Name
  }
  $id = Get-YinuoId $create.data
  if (-not $create.ok -or -not $id) { throw "ready-contract create failed: $(Get-YinuoErrorText $create)" }
  $initial = Get-YinuoCurrentRevision -Headers $Headers -ContractId $id
  $baseId = "$(Get-YinuoProperty -Object $initial -Names @('id','revisionId'))"
  $save = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/clm/contract/revision/save' -Body @{
    contractId = [int64]$id; baseRevisionId = [int64]$baseId; name = $Name; amount = $Amount
    currency = 'CNY'; startDate = '2026-09-01'; endDate = '2027-08-31'
    ourPartyId = [int64]$OurPartyId; counterpartyIds = @([int64]$CounterpartyId)
    customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = 'E2E 到货验收'; warrantyMonths = 24 }
    changeReason = 'demo-v1-e2e acceptance setup'
  }
  if (-not $save.ok) { throw "ready-contract revision failed: $(Get-YinuoErrorText $save)" }
  $confirm = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/contract/commitment/confirm-none?contractId=$([Uri]::EscapeDataString($id))" -Body $null
  if (-not $confirm.ok) { throw "ready-contract commitment confirmation failed: $(Get-YinuoErrorText $confirm)" }
  $revision = Get-YinuoCurrentRevision -Headers $Headers -ContractId $id
  return [pscustomobject]@{ contractId = $id; revisionId = "$(Get-YinuoProperty -Object $revision -Names @('id','revisionId'))" }
}

function Add-YinuoZipTextEntry {
  param([IO.Compression.ZipArchive]$Archive, [string]$Path, [string]$Content)
  $entry = $Archive.CreateEntry($Path)
  $stream = $entry.Open()
  $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
  try { $writer.Write($Content) } finally { $writer.Dispose(); $stream.Dispose() }
}

function New-YinuoPartyImportWorkbook {
  param([string]$Path, [string]$PartyName, [string]$CreditCode)
  Add-Type -AssemblyName System.IO.Compression
  $escape = { param($value) [Security.SecurityElement]::Escape([string]$value) }
  $values = @(
    @('相对方名称*','统一社会信用代码*','联系人','联系电话'),
    @($PartyName,$CreditCode,'演示联系人','13800000000')
  )
  $rows = [Collections.Generic.List[string]]::new()
  for ($rowIndex = 0; $rowIndex -lt $values.Count; $rowIndex++) {
    $cells = [Collections.Generic.List[string]]::new()
    for ($columnIndex = 0; $columnIndex -lt $values[$rowIndex].Count; $columnIndex++) {
      $column = [char]([int][char]'A' + $columnIndex)
      $cellValue = & $escape $values[$rowIndex][$columnIndex]
      $cells.Add("<c r=`"$column$($rowIndex + 1)`" t=`"inlineStr`"><is><t>$cellValue</t></is></c>")
    }
    $rows.Add("<row r=`"$($rowIndex + 1)`">$($cells -join '')</row>")
  }
  $file = [IO.File]::Open($Path, [IO.FileMode]::Create, [IO.FileAccess]::ReadWrite)
  $archive = [IO.Compression.ZipArchive]::new($file, [IO.Compression.ZipArchiveMode]::Create, $false)
  try {
    Add-YinuoZipTextEntry $archive '[Content_Types].xml' '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>'
    Add-YinuoZipTextEntry $archive '_rels/.rels' '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'
    Add-YinuoZipTextEntry $archive 'xl/workbook.xml' '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="相对方" sheetId="1" r:id="rId1"/></sheets></workbook>'
    Add-YinuoZipTextEntry $archive 'xl/_rels/workbook.xml.rels' '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>'
    Add-YinuoZipTextEntry $archive 'xl/worksheets/sheet1.xml' "<?xml version=`"1.0`" encoding=`"UTF-8`" standalone=`"yes`"?><worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData>$($rows -join '')</sheetData></worksheet>"
  } finally { $archive.Dispose(); $file.Dispose() }
  return Get-Item $Path
}

$business = $null
$legal = $null
$demoBusiness = $null
$contractAdmin = $null
$systemAdmin = $null
$contractId = $null
$firstTaskId = $null
$secondTaskId = $null
$finalTaskId = $null
$revisionOneId = $null
$revisionTwoId = $null
$firstContractNo = $null
$partyWorkbookPath = Join-Path ([IO.Path]::GetTempPath()) "demo-v1-e2e-$runId-party-import.xlsx"

try {
  Write-Host '== SQL 01-13 migration preflight'
  $migration = Get-YinuoMigrationPreflight -RepoRoot $repoRoot -TenantId $TenantId
  $null = Add-YinuoCheck -Condition $migration.ready -Id 'E2E_PREFLIGHT_SQL_01_13' -Message 'SQL 01-13 tables, required columns, roles, and OneContract-aligned menus are initialized' -Details $(if ($migration.ready) { "runner=$($migration.runner.kind)" } else { $migration.problems -join '; ' })
  if (-not $migration.ready) { throw "SQL 01-13 migration preflight failed; E2E made no writes ($($migration.problems -join '; '))" }

  Write-Host '== deterministic pre-clean'
  $preCleanExit = Invoke-YinuoE2EReset
  $null = Add-YinuoCheck -Condition ($preCleanExit -eq 0) -Id 'E2E_PRE_CLEAN' -Message 'stale demo-v1-e2e rows were removed before the run' -Details "exit=$preCleanExit"
  if ($preCleanExit -ne 0) { throw 'pre-clean failed; refusing to create more E2E data' }

  Write-Host '== API preflight'
  $business = Connect-YinuoApi -Username $BusinessUsername -Password $BusinessPassword -TenantName $TenantName -TenantId $TenantId
  $legal = Connect-YinuoApi -Username $LegalUsername -Password $LegalPassword -TenantName $TenantName -TenantId $TenantId
  $null = Add-YinuoCheck -Condition ($null -ne $business -and $null -ne $legal) -Id 'E2E_LOGIN' -Message 'business and legal users logged in' -Details $null
  $demoBusinessAuth = Connect-YinuoOptionalAccount -Username $DemoBusinessUsername -Password $DemoPassword
  $contractAdminAuth = Connect-YinuoOptionalAccount -Username $ContractAdminUsername -Password $DemoPassword
  $systemAdminAuth = Connect-YinuoOptionalAccount -Username $SystemAdminUsername -Password $DemoPassword
  $demoBusiness = $demoBusinessAuth.headers
  $contractAdmin = $contractAdminAuth.headers
  $systemAdmin = $systemAdminAuth.headers

  $requiredQueries = @(
    @{ id = 'E2E_PREFLIGHT_WORKBENCH'; path = '/clm/workbench/summary'; message = 'W01 workbench summary is available'; headers = $business },
    @{ id = 'E2E_PREFLIGHT_ITEMS'; path = '/clm/workbench/items?type=APPROVAL&pageNo=1&pageSize=20'; message = 'W01 approval work items are available'; headers = $legal },
    @{ id = 'E2E_PREFLIGHT_TEMPLATE'; path = '/clm/template/published-page?pageNo=1&pageSize=100'; message = 'W02 published templates are available'; headers = $business },
    @{ id = 'E2E_PREFLIGHT_CONTRACT'; path = '/clm/contract/page?pageNo=1&pageSize=20'; message = 'W03 contract page is available'; headers = $business },
    @{ id = 'E2E_PREFLIGHT_PARTY'; path = '/clm/party/page?pageNo=1&pageSize=20'; message = 'party directory is available'; headers = $business },
    @{ id = 'E2E_PREFLIGHT_COLLABORATION'; path = '/clm/collaboration/page?view=TODO&pageNo=1&pageSize=20'; message = 'collaboration support endpoint is initialized'; headers = $legal },
    @{ id = 'E2E_PREFLIGHT_POLICY'; path = '/clm/permission/policy/list'; message = 'published G05 policy endpoint is available'; headers = $business }
  )
  $preflightResponses = @{}
  foreach ($query in $requiredQueries) {
    $response = Invoke-YinuoApi -Headers $query.headers -Method GET -Path $query.path -Body $null
    $preflightResponses[$query.id] = $response
    $null = Assert-YinuoApi -Response $response -Id $query.id -Message $query.message -ThrowOnFailure
  }

  if ($null -eq $contractAdmin) {
    Add-YinuoSkip -Id 'E2E_GOVERNANCE_PROCESS_DESIGN' -Message 'contract administrator can list and open the visual process definition' -Details "account '$ContractAdminUsername' unavailable: $($contractAdminAuth.error); run seed-demo-data.ps1"
  } else {
    $processList = Invoke-YinuoApi -Headers $contractAdmin -Method GET -Path '/clm/governance/process/list' -Body $null
    $processModels = @(Get-YinuoItems $processList.data)
    $defaultProcess = @($processModels | Where-Object { $_.key -eq 'clm_contract_approval_v1' } | Select-Object -First 1)
    $processId = if ($defaultProcess.Count -eq 1) { "$(Get-YinuoProperty -Object $defaultProcess[0] -Names @('id'))" } else { '' }
    $processDetail = if ($processId) {
      Invoke-YinuoApi -Headers $contractAdmin -Method GET -Path "/clm/governance/process/get?id=$([Uri]::EscapeDataString($processId))" -Body $null
    } else { $null }
    $processReady = $processList.ok -and $defaultProcess.Count -eq 1 -and $processDetail.ok -and
      $processDetail.data.type -eq 20 -and $processDetail.data.formType -eq 20 -and
      $processDetail.data.formCustomCreatePath -eq '/clm/contract/create' -and
      $processDetail.data.formCustomViewPath -eq '/clm/contract/bpm/index.vue' -and
      $null -ne $processDetail.data.simpleModel
    $null = Add-YinuoCheck -Condition $processReady -Id 'E2E_GOVERNANCE_PROCESS_DESIGN' -Message 'contract administrator can list and open the canonical visual process definition' -Details "models=$($processModels.Count), id=$processId"

    if ($null -ne $demoBusiness) {
      $businessProcessList = Invoke-YinuoApi -Headers $demoBusiness -Method GET -Path '/clm/governance/process/list' -Body $null
      $null = Add-YinuoCheck -Condition (-not $businessProcessList.ok) -Id 'E2E_GOVERNANCE_PROCESS_BUSINESS_DENIED' -Message 'business role cannot manage process definitions' -Details "http=$($businessProcessList.httpStatus), code=$($businessProcessList.code)"
    }
    if ($null -ne $systemAdmin) {
      $systemProcessList = Invoke-YinuoApi -Headers $systemAdmin -Method GET -Path '/clm/governance/process/list' -Body $null
      $null = Add-YinuoCheck -Condition (-not $systemProcessList.ok) -Id 'E2E_GOVERNANCE_PROCESS_SYSTEM_DENIED' -Message 'system role cannot cross into contract process-definition governance' -Details "http=$($systemProcessList.httpStatus), code=$($systemProcessList.code)"
    }
  }

  $templates = @(Get-YinuoItems $preflightResponses['E2E_PREFLIGHT_TEMPLATE'].data)
  $template = @($templates | Where-Object { $_.name -match '采购|Purchase' } | Select-Object -First 1)
  if ($template.Count -eq 0) { $template = @($templates | Select-Object -First 1) }
  $templateVersionId = if ($template.Count -gt 0) { "$(Get-YinuoProperty -Object $template[0] -Names @('currentVersionId'))" } else { '' }
  $contractTypeId = if ($template.Count -gt 0) { "$(Get-YinuoProperty -Object $template[0] -Names @('contractTypeId'))" } else { '' }
  $null = Add-YinuoCheck -Condition ([bool]$templateVersionId -and [bool]$contractTypeId) -Id 'E2E_TEMPLATE_VERSION' -Message 'a published standard-template version and contract type were selected' -Details "templateVersionId=$templateVersionId, contractTypeId=$contractTypeId"
  if (-not $templateVersionId -or -not $contractTypeId) { throw 'no complete published TemplateVersion; E2E will not fabricate a success' }

  $publishedPolicies = @((Get-YinuoItems $preflightResponses['E2E_PREFLIGHT_POLICY'].data) | Where-Object { $_.status -eq 'PUBLISHED' })
  $policyNode = if ($publishedPolicies.Count -eq 1) { $publishedPolicies[0].nodeEditPolicyJson | ConvertFrom-Json } else { $null }
  $policyEditable = if ($null -ne $policyNode) { @($policyNode.default.editableFields) } else { @() }
  $policyMajor = if ($null -ne $policyNode) { @($policyNode.default.majorFields) } else { @() }
  $policyReady = $publishedPolicies.Count -eq 1 -and $policyNode.default.enabled -eq $true -and
    @(@('name','amount','customData','parties','document') | Where-Object { $_ -notin $policyEditable }).Count -eq 0 -and
    @(@('amount','customData','parties','document') | Where-Object { $_ -notin $policyMajor }).Count -eq 0
  $null = Add-YinuoCheck -Condition $policyReady -Id 'E2E_G05_POLICY' -Message 'published G05 policy exposes controlled approval editing including custom form data' -Details "editable=$($policyEditable -join ','), major=$($policyMajor -join ',')"
  if (-not $policyReady) { throw 'published G05 policy is missing or incomplete; run seed-demo-data.ps1' }

  Write-Host '== saved filter'
  $savedFilter = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/saved-filter/save' -Body @{
    sceneCode = 'CONTRACT_LEDGER'; name = $savedFilterName
    filterJson = '{"stageCode":"DRAFT","scope":"MY"}'; defaultFlag = $false
  }
  $savedFilterId = Get-YinuoId $savedFilter.data
  $savedFilterList = Invoke-YinuoApi -Headers $business -Method GET -Path '/clm/saved-filter/list?sceneCode=CONTRACT_LEDGER' -Body $null
  $savedFilterMatch = @((Get-YinuoItems $savedFilterList.data) | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('id'))" -eq $savedFilterId -and $_.name -eq $savedFilterName })
  $null = Add-YinuoCheck -Condition ($savedFilter.ok -and $savedFilterList.ok -and $savedFilterMatch.Count -eq 1) -Id 'E2E_SAVED_FILTER' -Message 'contract-ledger saved filter round-tripped for the current user' -Details "id=$savedFilterId"

  Write-Host '== create draft and exact initial revision'
  $ourPartyId = New-YinuoParty -Headers $business -Name $ourPartyName -Internal $true -ShortName '演示方'
  $counterpartyId = New-YinuoParty -Headers $business -Name $counterpartyName -Internal $false
  $null = Add-YinuoCheck -Condition ([bool]$ourPartyId -and [bool]$counterpartyId) -Id 'E2E_PARTIES' -Message 'isolated E2E parties were created through API' -Details "our=$ourPartyId, counterparty=$counterpartyId"
  $ourPartyResponse = Invoke-YinuoApi -Headers $business -Method GET -Path "/clm/party/get?id=$([Uri]::EscapeDataString($ourPartyId))" -Body $null
  $actualShortName = "$(Get-YinuoProperty -Object $ourPartyResponse.data -Names @('shortName'))"
  $null = Add-YinuoCheck -Condition ($ourPartyResponse.ok -and $actualShortName -eq '演示方') -Id 'E2E_OUR_PARTY_SHORT_NAME' -Message 'internal E2E party persisted the numbering short name' -Details "shortName=$actualShortName"
  if (-not $ourPartyResponse.ok -or $actualShortName -ne '演示方') { throw 'internal E2E party short_name was not persisted' }

  $create = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/contract/create-from-template' -Body @{
    templateVersionId = $templateVersionId
    name = $contractName
  }
  $contractId = Get-YinuoId $create.data
  $null = Add-YinuoCheck -Condition ($create.ok -and [bool]$contractId) -Id 'E2E_CREATE_FROM_TEMPLATE' -Message 'standard-template draft was created without legal collaboration' -Details $(if ($create.ok) { "contractId=$contractId" } else { Get-YinuoErrorText $create })
  if (-not $create.ok -or -not $contractId) { throw "create-from-template failed: $(Get-YinuoErrorText $create)" }

  $draft = Get-YinuoContractDetail -Headers $business -ContractId $contractId
  $null = Add-YinuoCheck -Condition (-not (Get-YinuoProperty -Object $draft -Names @('contractNo'))) -Id 'E2E_DRAFT_NUMBER_NULL' -Message 'draft contractNo is null before first successful submit' -Details $null
  $initialRevision = Get-YinuoCurrentRevision -Headers $business -ContractId $contractId
  $initialRevisionId = "$(Get-YinuoProperty -Object $initialRevision -Names @('id','revisionId'))"
  $saveOne = Invoke-YinuoApi -Headers $business -Method PUT -Path '/clm/contract/revision/save' -Body @{
    contractId = $contractId
    baseRevisionId = $initialRevisionId
    name = $contractName
    amount = 888000
    currency = 'CNY'
    startDate = '2026-09-01'
    endDate = '2027-08-31'
    ourPartyId = $ourPartyId
    counterpartyIds = @($counterpartyId)
    customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = 'E2E 到货验收'; warrantyMonths = 24 }
    changeReason = 'E2E ready for first direct submit'
  }
  $null = Add-YinuoCheck -Condition $saveOne.ok -Id 'E2E_REVISION_ONE_SAVE' -Message 'structured fields and party snapshot produced a new revision' -Details $(if ($saveOne.ok) { $null } else { Get-YinuoErrorText $saveOne })
  if (-not $saveOne.ok) { throw "first revision save failed: $(Get-YinuoErrorText $saveOne)" }
  $confirmOne = Invoke-YinuoApi -Headers $business -Method PUT -Path "/clm/contract/commitment/confirm-none?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
  $null = Add-YinuoCheck -Condition $confirmOne.ok -Id 'E2E_CONFIRM_NONE_ONE' -Message 'no-major-commitment confirmation produced the submit-ready revision' -Details $(if ($confirmOne.ok) { $null } else { Get-YinuoErrorText $confirmOne })
  if (-not $confirmOne.ok) { throw "commitment confirmation failed: $(Get-YinuoErrorText $confirmOne)" }
  $revisionOne = Get-YinuoCurrentRevision -Headers $business -ContractId $contractId
  $revisionOneId = "$(Get-YinuoProperty -Object $revisionOne -Names @('id','revisionId'))"

  Write-Host '== local AI review on exact revision'
  $aiRun = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/ai-review/run' -Body @{
    contractId = [int64]$contractId; revisionId = [int64]$revisionOneId; runType = 'RISK_REVIEW'
  }
  $aiRunId = Get-YinuoId $aiRun.data
  $aiListOne = Invoke-YinuoApi -Headers $business -Method GET -Path "/clm/ai-review/list?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
  $aiOne = @((Get-YinuoItems $aiListOne.data) | Where-Object { "$(Get-YinuoProperty -Object $_.run -Names @('id'))" -eq $aiRunId })
  $aiFresh = $aiRun.ok -and $aiListOne.ok -and $aiOne.Count -eq 1 -and
    $aiOne[0].run.providerCode -eq 'LOCAL_DETERMINISTIC' -and $aiOne[0].run.status -eq 'SUCCEEDED' -and
    $aiOne[0].stale -eq $false
  $null = Add-YinuoCheck -Condition $aiFresh -Id 'E2E_AI_LOCAL_FRESH' -Message 'local deterministic AI review completed without external transmission and is fresh' -Details "runId=$aiRunId, provider=$($aiOne[0].run.providerCode), status=$($aiOne[0].run.status), stale=$($aiOne[0].stale)"
  if (-not $aiFresh) { throw 'local AI review did not produce the expected fresh deterministic result' }

  Write-Host '== first direct submit and exact-revision rejection'
  $submitOne = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/contract/submit' -Body @{
    id = $contractId
    contractId = $contractId
    baseRevisionId = $revisionOneId
    submitRequestId = "demo-v1-e2e-$runId-submit-1"
    remark = 'standard-template direct submit, first instance'
  }
  $firstContractNo = "$(Get-YinuoProperty -Object $submitOne.data -Names @('contractNo'))"
  $submittedRevisionOne = "$(Get-YinuoProperty -Object $submitOne.data -Names @('submittedRevisionId'))"
  $approvalCaseOne = "$(Get-YinuoProperty -Object $submitOne.data -Names @('approvalCaseId'))"
  $processOne = "$(Get-YinuoProperty -Object $submitOne.data -Names @('processInstanceId'))"
  $directSubmitOk = $submitOne.ok -and [bool]$firstContractNo -and $submittedRevisionOne -eq $revisionOneId -and [bool]$approvalCaseOne -and [bool]$processOne
  $null = Add-YinuoCheck -Condition $directSubmitOk -Id 'E2E_DIRECT_SUBMIT_ONE' -Message 'standard template submitted directly with number, approval case, process, and exact submitted revision' -Details $(if ($submitOne.ok) { "contractNo=$firstContractNo, revision=$submittedRevisionOne" } else { Get-YinuoErrorText $submitOne })
  if (-not $directSubmitOk) { throw "first direct submit contract was incomplete: $(Get-YinuoErrorText $submitOne)" }
  $numberUsesPartyShortName = $firstContractNo.IndexOf('演示方', [StringComparison]::Ordinal) -ge 0
  $null = Add-YinuoCheck -Condition $numberUsesPartyShortName -Id 'E2E_NUMBER_USES_PARTY_SHORT_NAME' -Message 'first permanent number includes the persisted internal-party short name' -Details "contractNo=$firstContractNo"
  if (-not $numberUsesPartyShortName) { throw 'published numbering rule did not include the E2E internal-party short name' }

  $taskOne = Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe { Find-YinuoApprovalTask -Headers $legal -ContractId $contractId -ExcludedTaskId '' }
  $firstTaskId = "$(Get-YinuoProperty -Object $taskOne -Names @('taskId'))"
  $null = Add-YinuoCheck -Condition ([bool]$firstTaskId) -Id 'E2E_TASK_ONE' -Message 'legal user received the first approval task' -Details "taskId=$firstTaskId"
  if (-not $firstTaskId) { throw 'first approval task was not projected to the legal workbench' }
  $taskOneDetail = Invoke-YinuoApi -Headers $legal -Method GET -Path "/clm/approval/task/get?taskId=$([Uri]::EscapeDataString($firstTaskId))" -Body $null
  $detailSubmittedOne = "$(Get-YinuoProperty -Object $taskOneDetail.data -Names @('submittedRevisionId'))"
  $detailCurrentOne = "$(Get-YinuoProperty -Object $taskOneDetail.data -Names @('currentRevisionId'))"
  $null = Add-YinuoCheck -Condition ($taskOneDetail.ok -and $detailSubmittedOne -eq $revisionOneId -and $detailCurrentOne -eq $revisionOneId) -Id 'E2E_TASK_ONE_REVISION' -Message 'first approval task exposes exactly the submitted revision' -Details "submitted=$detailSubmittedOne, current=$detailCurrentOne"
  $rejectOne = Invoke-YinuoApi -Headers $legal -Method PUT -Path '/clm/approval/task/reject' -Body @{
    taskId = $firstTaskId
    revisionId = $revisionOneId
    reason = 'E2E reject revision one before resubmit'
    requestId = "demo-v1-e2e-$runId-reject-1"
  }
  $null = Add-YinuoCheck -Condition $rejectOne.ok -Id 'E2E_REJECT_ONE' -Message 'legal rejected the first exact revision through the CLM approval facade' -Details $(if ($rejectOne.ok) { $null } else { Get-YinuoErrorText $rejectOne })
  if (-not $rejectOne.ok) { throw "first rejection failed: $(Get-YinuoErrorText $rejectOne)" }

  $rejected = Wait-YinuoContractState -Headers $business -ContractId $contractId -Predicate {
    param($detail)
    $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))"
    $approvalStatus = Get-YinuoProperty -Object $detail -Names @('approvalStatus')
    return ($stage -eq 'DRAFT' -or $stage -eq 'READY_TO_SUBMIT' -or $approvalStatus -eq 3)
  }
  $numberAfterReject = "$(Get-YinuoProperty -Object $rejected -Names @('contractNo'))"
  $null = Add-YinuoCheck -Condition ($null -ne $rejected -and $numberAfterReject -eq $firstContractNo) -Id 'E2E_NUMBER_SURVIVES_REJECT' -Message 'rejection returned the same contract to revision while retaining its permanent number' -Details "contractNo=$numberAfterReject"

  Write-Host '== revise, resubmit, and approve exact revision two'
  $baseAfterReject = Get-YinuoCurrentRevision -Headers $business -ContractId $contractId
  $baseAfterRejectId = "$(Get-YinuoProperty -Object $baseAfterReject -Names @('id','revisionId'))"
  $saveTwo = Invoke-YinuoApi -Headers $business -Method PUT -Path '/clm/contract/revision/save' -Body @{
    contractId = $contractId
    baseRevisionId = $baseAfterRejectId
    name = $contractName
    amount = 886000
    currency = 'CNY'
    startDate = '2026-09-01'
    endDate = '2027-08-31'
    ourPartyId = $ourPartyId
    counterpartyIds = @($counterpartyId)
    customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = 'E2E 修订后二次验收'; warrantyMonths = 24 }
    changeReason = 'E2E revision two after rejection'
  }
  $null = Add-YinuoCheck -Condition $saveTwo.ok -Id 'E2E_REVISION_TWO_SAVE' -Message 'post-rejection edit created revision two' -Details $(if ($saveTwo.ok) { $null } else { Get-YinuoErrorText $saveTwo })
  if (-not $saveTwo.ok) { throw "second revision save failed: $(Get-YinuoErrorText $saveTwo)" }
  $confirmTwo = Invoke-YinuoApi -Headers $business -Method PUT -Path "/clm/contract/commitment/confirm-none?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
  if (-not $confirmTwo.ok) { throw "second commitment confirmation failed: $(Get-YinuoErrorText $confirmTwo)" }
  $revisionTwo = Get-YinuoCurrentRevision -Headers $business -ContractId $contractId
  $revisionTwoId = "$(Get-YinuoProperty -Object $revisionTwo -Names @('id','revisionId'))"
  $null = Add-YinuoCheck -Condition ([bool]$revisionTwoId -and $revisionTwoId -ne $revisionOneId) -Id 'E2E_REVISION_TWO_DISTINCT' -Message 'resubmission uses a distinct immutable revision' -Details "revisionOne=$revisionOneId, revisionTwo=$revisionTwoId"
  $aiListTwo = Invoke-YinuoApi -Headers $business -Method GET -Path "/clm/ai-review/list?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
  $aiAfterRevision = @((Get-YinuoItems $aiListTwo.data) | Where-Object { "$(Get-YinuoProperty -Object $_.run -Names @('id'))" -eq $aiRunId })
  $aiStale = $aiListTwo.ok -and $aiAfterRevision.Count -eq 1 -and $aiAfterRevision[0].stale -eq $true
  $null = Add-YinuoCheck -Condition $aiStale -Id 'E2E_AI_STALE_AFTER_REVISION' -Message 'AI result is visibly stale after the contract moves to a new revision' -Details "runId=$aiRunId, stale=$($aiAfterRevision[0].stale)"

  $submitTwo = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/contract/submit' -Body @{
    id = $contractId
    contractId = $contractId
    baseRevisionId = $revisionTwoId
    submitRequestId = "demo-v1-e2e-$runId-submit-2"
    remark = 'resubmit exact revision two'
  }
  $secondContractNo = "$(Get-YinuoProperty -Object $submitTwo.data -Names @('contractNo'))"
  $submittedRevisionTwo = "$(Get-YinuoProperty -Object $submitTwo.data -Names @('submittedRevisionId'))"
  $numberReused = $submitTwo.ok -and $secondContractNo -eq $firstContractNo -and $submittedRevisionTwo -eq $revisionTwoId
  $null = Add-YinuoCheck -Condition $numberReused -Id 'E2E_RESUBMIT_NUMBER_REUSED' -Message 'resubmit reused the permanent number and froze revision two' -Details "first=$firstContractNo, second=$secondContractNo, submittedRevision=$submittedRevisionTwo"
  if (-not $numberReused) { throw "resubmit did not preserve number/revision: $(Get-YinuoErrorText $submitTwo)" }

  $taskTwo = Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe { Find-YinuoApprovalTask -Headers $legal -ContractId $contractId -ExcludedTaskId $firstTaskId }
  $secondTaskId = "$(Get-YinuoProperty -Object $taskTwo -Names @('taskId'))"
  $null = Add-YinuoCheck -Condition ([bool]$secondTaskId) -Id 'E2E_TASK_TWO' -Message 'legal received a new task for the resubmitted revision' -Details "taskId=$secondTaskId"
  if (-not $secondTaskId) { throw 'second approval task was not projected to legal workbench' }
  $approveTwo = Invoke-YinuoApi -Headers $legal -Method PUT -Path '/clm/approval/task/approve' -Body @{
    taskId = $secondTaskId
    revisionId = $revisionTwoId
    reason = 'E2E legal approval of exact revision two'
    requestId = "demo-v1-e2e-$runId-approve-legal"
  }
  $null = Add-YinuoCheck -Condition $approveTwo.ok -Id 'E2E_APPROVE_REVISION_TWO' -Message 'legal approved revision two through the CLM approval facade' -Details $(if ($approveTwo.ok) { $null } else { Get-YinuoErrorText $approveTwo })
  if (-not $approveTwo.ok) { throw "legal approval failed: $(Get-YinuoErrorText $approveTwo)" }

  if ($null -eq $contractAdmin) {
    throw "account '$ContractAdminUsername' is required for the final contract-administrator approval"
  }
  $finalTask = Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe { Find-YinuoApprovalTask -Headers $contractAdmin -ContractId $contractId -ExcludedTaskId $secondTaskId }
  $finalTaskId = "$(Get-YinuoProperty -Object $finalTask -Names @('taskId'))"
  $null = Add-YinuoCheck -Condition ([bool]$finalTaskId) -Id 'E2E_FINAL_TASK' -Message 'contract administrator received the final approval task' -Details "taskId=$finalTaskId"
  if (-not $finalTaskId) { throw 'final approval task was not projected to the contract-administrator workbench' }
  $approveFinal = Invoke-YinuoApi -Headers $contractAdmin -Method PUT -Path '/clm/approval/task/approve' -Body @{
    taskId = $finalTaskId
    revisionId = $revisionTwoId
    reason = 'E2E final approval of exact revision two'
    requestId = "demo-v1-e2e-$runId-approve-final"
  }
  $null = Add-YinuoCheck -Condition $approveFinal.ok -Id 'E2E_FINAL_APPROVE' -Message 'contract administrator approved the same exact revision two' -Details $(if ($approveFinal.ok) { $null } else { Get-YinuoErrorText $approveFinal })
  if (-not $approveFinal.ok) { throw "final approval failed: $(Get-YinuoErrorText $approveFinal)" }

  $approved = Wait-YinuoContractState -Headers $business -ContractId $contractId -Predicate {
    param($detail)
    $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))"
    $approvalStatus = Get-YinuoProperty -Object $detail -Names @('approvalStatus')
    return ($stage -eq 'APPROVED' -or $approvalStatus -eq 2)
  }
  $approvedRevisionId = "$(Get-YinuoProperty -Object $approved -Names @('currentRevisionId','approvedRevisionId'))"
  $null = Add-YinuoCheck -Condition ($null -ne $approved -and $approvedRevisionId -eq $revisionTwoId) -Id 'E2E_DIRECT_MAINLINE_COMPLETE' -Message 'standard-template mainline completed at APPROVED on revision two' -Details "approvedRevision=$approvedRevisionId"

  $history = Invoke-YinuoApi -Headers $business -Method GET -Path "/clm/approval/history?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
  $null = Add-YinuoCheck -Condition $history.ok -Id 'E2E_HISTORY' -Message 'cross-instance approval history is available' -Details $(if ($history.ok) { $null } else { Get-YinuoErrorText $history })
  if (-not $history.ok) { throw "approval history failed: $(Get-YinuoErrorText $history)" }
  $null = Add-YinuoCheck -Condition (Test-YinuoHistoryBinding -Node $history.data -TaskId $firstTaskId -RevisionId $revisionOneId) -Id 'E2E_HISTORY_REJECT_BINDING' -Message 'rejection history binds task one to revision one' -Details "task=$firstTaskId, revision=$revisionOneId"
  $null = Add-YinuoCheck -Condition (Test-YinuoHistoryBinding -Node $history.data -TaskId $secondTaskId -RevisionId $revisionTwoId) -Id 'E2E_HISTORY_LEGAL_BINDING' -Message 'legal approval history binds task two to revision two' -Details "task=$secondTaskId, revision=$revisionTwoId"
  $null = Add-YinuoCheck -Condition (Test-YinuoHistoryBinding -Node $history.data -TaskId $finalTaskId -RevisionId $revisionTwoId) -Id 'E2E_HISTORY_FINAL_BINDING' -Message 'final approval history binds the final task to revision two' -Details "task=$finalTaskId, revision=$revisionTwoId"

  $finalStage = "$(Get-YinuoProperty -Object $approved -Names @('stageCode'))".ToUpperInvariant()
  $finalLifecycle = Get-YinuoProperty -Object $approved -Names @('lifecycleStatus')
  $noPostApproval = $finalStage -notmatch 'SIGN|ARCHIV|PERFORM|EFFECTIVE|EXPIRED|TERMINAT|CLOSED' -and ($null -eq $finalLifecycle -or [int]$finalLifecycle -le 2)
  $null = Add-YinuoCheck -Condition $noPostApproval -Id 'E2E_NO_POST_APPROVAL_STAGE' -Message 'mainline stopped at approval with no signing, archive, or performance stage' -Details "stage=$finalStage, lifecycle=$finalLifecycle"

  Write-Host '== nonstandard legal gate'
  try {
    if (-not (Test-Path $demoDocument)) { throw "demo upload document is missing: $demoDocument" }
    $uploadDraft = Invoke-YinuoMultipartApi -Headers $business -Method POST -Path '/clm/contract/create-from-upload' -Form @{
      file = Get-Item $demoDocument; name = $nonstandardName; contractTypeId = $contractTypeId
    }
    $nonstandardId = Get-YinuoId $uploadDraft.data
    if (-not $uploadDraft.ok -or -not $nonstandardId) { throw "nonstandard upload failed: $(Get-YinuoErrorText $uploadDraft)" }
    $nonstandardBase = Get-YinuoCurrentRevision -Headers $business -ContractId $nonstandardId
    $nonstandardBaseId = "$(Get-YinuoProperty -Object $nonstandardBase -Names @('id','revisionId'))"
    $nonstandardSave = Invoke-YinuoApi -Headers $business -Method PUT -Path '/clm/contract/revision/save' -Body @{
      contractId = [int64]$nonstandardId; baseRevisionId = [int64]$nonstandardBaseId; name = $nonstandardName
      amount = 320000; currency = 'CNY'; startDate = '2026-09-01'; endDate = '2027-08-31'
      ourPartyId = [int64]$ourPartyId; counterpartyIds = @([int64]$counterpartyId)
      customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = '非标合同法律审查后验收'; warrantyMonths = 12 }
      changeReason = 'uploaded nonstandard contract must pass legal collaboration'
    }
    if (-not $nonstandardSave.ok) { throw "nonstandard revision failed: $(Get-YinuoErrorText $nonstandardSave)" }
    $nonstandardConfirm = Invoke-YinuoApi -Headers $business -Method PUT -Path "/clm/contract/commitment/confirm-none?contractId=$nonstandardId" -Body $null
    if (-not $nonstandardConfirm.ok) { throw "nonstandard commitment confirmation failed: $(Get-YinuoErrorText $nonstandardConfirm)" }
    $nonstandardRevision = Get-YinuoCurrentRevision -Headers $business -ContractId $nonstandardId
    $nonstandardRevisionId = "$(Get-YinuoProperty -Object $nonstandardRevision -Names @('id','revisionId'))"
    $blockedSubmit = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/contract/submit' -Body @{
      id = [int64]$nonstandardId; baseRevisionId = [int64]$nonstandardRevisionId
      submitRequestId = "demo-v1-e2e-$runId-nonstandard-submit"; remark = 'must be blocked without legal conclusion'
    }
    $blockedDetail = Get-YinuoContractDetail -Headers $business -ContractId $nonstandardId
    $gateOk = -not $blockedSubmit.ok -and [int64]$blockedSubmit.code -eq 1070003014 -and
      -not (Get-YinuoProperty -Object $blockedDetail -Names @('contractNo'))
    $null = Add-YinuoCheck -Condition $gateOk -Id 'E2E_NONSTANDARD_LEGAL_GATE' -Message 'nonstandard uploaded contract is blocked before numbering until legal collaboration concludes' -Details (Get-YinuoErrorText $blockedSubmit)
  } catch {
    $null = Add-YinuoCheck -Condition $false -Id 'E2E_NONSTANDARD_LEGAL_GATE' -Message 'nonstandard uploaded contract is blocked before numbering until legal collaboration concludes' -Details $_.Exception.Message
  }

  Write-Host '== approval minor/major edit and starter withdrawal'
  try {
    $editReady = New-YinuoReadyStandardContract -Headers $business -Name $editContractName -TemplateVersionId $templateVersionId -OurPartyId $ourPartyId -CounterpartyId $counterpartyId -Amount 360000
    $editSubmit = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/contract/submit' -Body @{
      id = [int64]$editReady.contractId; baseRevisionId = [int64]$editReady.revisionId
      submitRequestId = "demo-v1-e2e-$runId-edit-submit"; remark = 'approval editing acceptance'
    }
    if (-not $editSubmit.ok) { throw "approval-edit submit failed: $(Get-YinuoErrorText $editSubmit)" }
    $editOriginalCaseId = "$(Get-YinuoProperty -Object $editSubmit.data -Names @('approvalCaseId'))"
    $editOriginalNo = "$(Get-YinuoProperty -Object $editSubmit.data -Names @('contractNo'))"
    $editTask = Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe { Find-YinuoApprovalTask -Headers $legal -ContractId $editReady.contractId -ExcludedTaskId '' }
    $editTaskId = "$(Get-YinuoProperty -Object $editTask -Names @('taskId'))"
    if (-not $editTaskId) { throw 'legal approval-edit task was not projected' }
    $editTaskDetail = Invoke-YinuoApi -Headers $legal -Method GET -Path "/clm/approval/task/get?taskId=$([Uri]::EscapeDataString($editTaskId))" -Body $null
    $editPolicyFields = @(Get-YinuoProperty -Object $editTaskDetail.data.nodeEditPolicy -Names @('editableFields'))
    $taskPolicyOk = $editTaskDetail.ok -and (Get-YinuoProperty -Object $editTaskDetail.data.nodeEditPolicy -Names @('canEdit')) -eq $true -and 'name' -in $editPolicyFields -and 'amount' -in $editPolicyFields
    $null = Add-YinuoCheck -Condition $taskPolicyOk -Id 'E2E_APPROVAL_EDIT_POLICY' -Message 'approval task exposes the controlled node-edit policy' -Details "fields=$($editPolicyFields -join ',')"

    $minorName = "$editContractName 微调"
    $minorEdit = Invoke-YinuoApi -Headers $legal -Method PUT -Path '/clm/approval/task/edit' -Body @{
      taskId = $editTaskId; requestId = "demo-v1-e2e-$runId-edit-minor"
      contractId = [int64]$editReady.contractId; baseRevisionId = [int64]$editReady.revisionId
      name = $minorName; amount = 360000; currency = 'CNY'; startDate = '2026-09-01'; endDate = '2027-08-31'
      ourPartyId = [int64]$ourPartyId; counterpartyIds = @([int64]$counterpartyId)
      customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = 'E2E 到货验收'; warrantyMonths = 24 }
      changeReason = '审批中修正合同名称'
    }
    $minorRevisionId = "$(Get-YinuoProperty -Object $minorEdit.data -Names @('revisionId'))"
    $minorOk = $minorEdit.ok -and $minorEdit.data.majorChange -eq $false -and
      "$(Get-YinuoProperty -Object $minorEdit.data -Names @('approvalCaseId'))" -eq $editOriginalCaseId -and [bool]$minorRevisionId
    $null = Add-YinuoCheck -Condition $minorOk -Id 'E2E_APPROVAL_EDIT_MINOR' -Message 'minor approval edit created a revision without restarting the approval case' -Details "revisionId=$minorRevisionId, caseId=$($minorEdit.data.approvalCaseId)"
    if (-not $minorOk) { throw "minor approval edit failed: $(Get-YinuoErrorText $minorEdit)" }

    $majorEdit = Invoke-YinuoApi -Headers $legal -Method PUT -Path '/clm/approval/task/edit' -Body @{
      taskId = $editTaskId; requestId = "demo-v1-e2e-$runId-edit-major"
      contractId = [int64]$editReady.contractId; baseRevisionId = [int64]$minorRevisionId
      name = $minorName; amount = 1360000; currency = 'CNY'; startDate = '2026-09-01'; endDate = '2027-08-31'
      ourPartyId = [int64]$ourPartyId; counterpartyIds = @([int64]$counterpartyId)
      customData = @{ deliveryDate = '2026-09-30'; acceptCriteria = 'E2E 到货验收'; warrantyMonths = 24 }
      changeReason = '审批中重大金额修改'
    }
    $majorRevisionId = "$(Get-YinuoProperty -Object $majorEdit.data -Names @('revisionId'))"
    $majorCaseId = "$(Get-YinuoProperty -Object $majorEdit.data -Names @('approvalCaseId'))"
    $majorOk = $majorEdit.ok -and $majorEdit.data.majorChange -eq $true -and [bool]$majorRevisionId -and
      [bool]$majorCaseId -and $majorCaseId -ne $editOriginalCaseId -and [bool]$majorEdit.data.processInstanceId
    $null = Add-YinuoCheck -Condition $majorOk -Id 'E2E_APPROVAL_EDIT_MAJOR' -Message 'major approval edit superseded the old case and restarted the full route' -Details "revisionId=$majorRevisionId, oldCase=$editOriginalCaseId, newCase=$majorCaseId"
    if (-not $majorOk) { throw "major approval edit failed: $(Get-YinuoErrorText $majorEdit)" }

    $withdraw = Invoke-YinuoApi -Headers $business -Method PUT -Path '/clm/approval/case/withdraw' -Body @{
      approvalCaseId = [int64]$majorCaseId; reason = '演示发起人撤回后重提'
    }
    if (-not $withdraw.ok) { throw "starter withdrawal failed: $(Get-YinuoErrorText $withdraw)" }
    $withdrawnDetail = Wait-YinuoContractState -Headers $business -ContractId $editReady.contractId -Predicate {
      param($detail) "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))" -eq 'DRAFT'
    }
    $numberAfterWithdraw = "$(Get-YinuoProperty -Object $withdrawnDetail -Names @('contractNo'))"
    $withdrawOk = $null -ne $withdrawnDetail -and $numberAfterWithdraw -eq $editOriginalNo
    $null = Add-YinuoCheck -Condition $withdrawOk -Id 'E2E_STARTER_WITHDRAW_NUMBER' -Message 'starter withdrawal returned to draft without releasing the permanent number' -Details "contractNo=$numberAfterWithdraw"
    $resubmitAfterWithdraw = Invoke-YinuoApi -Headers $business -Method POST -Path '/clm/contract/submit' -Body @{
      id = [int64]$editReady.contractId; baseRevisionId = [int64]$majorRevisionId
      submitRequestId = "demo-v1-e2e-$runId-after-withdraw"; remark = '撤回后沿用编号重提'
    }
    $withdrawResubmitOk = $resubmitAfterWithdraw.ok -and "$(Get-YinuoProperty -Object $resubmitAfterWithdraw.data -Names @('contractNo'))" -eq $editOriginalNo
    $null = Add-YinuoCheck -Condition $withdrawResubmitOk -Id 'E2E_WITHDRAW_RESUBMIT_NUMBER_REUSED' -Message 'resubmission after starter withdrawal reused the same permanent number' -Details "contractNo=$($resubmitAfterWithdraw.data.contractNo)"
  } catch {
    $null = Add-YinuoCheck -Condition $false -Id 'E2E_APPROVAL_EDIT_FLOW' -Message 'minor/major approval edit and starter-withdraw flow completed' -Details $_.Exception.Message
  }

  Write-Host '== DingTalk NOT_CONFIGURED sandbox'
  if ($null -eq $systemAdmin) {
    Add-YinuoSkip -Id 'E2E_INTEGRATION_NOT_CONFIGURED' -Message 'DingTalk sandbox records NOT_CONFIGURED without external calls' -Details "account '$SystemAdminUsername' unavailable: $($systemAdminAuth.error); run seed-demo-data.ps1"
  } else {
    try {
      $integrationStatus = Invoke-YinuoApi -Headers $systemAdmin -Method GET -Path '/clm/integration/dingtalk/status' -Body $null
      $integrationRun = Invoke-YinuoApi -Headers $systemAdmin -Method POST -Path '/clm/integration/dingtalk/run-sandbox' -Body @{
        integrationType = 'DINGTALK_ORG_SYNC'; runKey = $integrationRunKey
      }
      $integrationRunId = Get-YinuoId $integrationRun.data
      $integrationPage = Invoke-YinuoApi -Headers $systemAdmin -Method GET -Path '/clm/integration/run/page?type=DINGTALK_ORG_SYNC&pageNo=1&pageSize=100' -Body $null
      $integrationMatch = @((Get-YinuoItems $integrationPage.data) | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('id'))" -eq $integrationRunId })
      $integrationSummary = if ($integrationMatch.Count -eq 1) { $integrationMatch[0].summaryJson | ConvertFrom-Json } else { $null }
      $integrationOk = $integrationStatus.ok -and $integrationStatus.data.configured -eq $false -and $integrationStatus.data.mode -eq 'SANDBOX' -and
        $integrationRun.ok -and $integrationPage.ok -and $integrationMatch.Count -eq 1 -and $integrationMatch[0].status -eq 'NOT_CONFIGURED' -and
        $integrationSummary.configured -eq $false -and $integrationSummary.externalRequestSent -eq $false
      $null = Add-YinuoCheck -Condition $integrationOk -Id 'E2E_INTEGRATION_NOT_CONFIGURED' -Message 'DingTalk sandbox records NOT_CONFIGURED without external calls' -Details "runId=$integrationRunId, status=$($integrationMatch[0].status), externalRequestSent=$($integrationSummary.externalRequestSent)"
    } catch {
      $null = Add-YinuoCheck -Condition $false -Id 'E2E_INTEGRATION_NOT_CONFIGURED' -Message 'DingTalk sandbox records NOT_CONFIGURED without external calls' -Details $_.Exception.Message
    }
  }

  Write-Host '== G08 party import'
  if ($null -eq $contractAdmin -or $PSVersionTable.PSVersion.Major -lt 7) {
    $reason = if ($PSVersionTable.PSVersion.Major -lt 7) { 'PowerShell 7+ multipart support is required' } else { "account '$ContractAdminUsername' unavailable: $($contractAdminAuth.error); run seed-demo-data.ps1" }
    Add-YinuoSkip -Id 'E2E_PARTY_IMPORT' -Message 'G08 import previews and confirms one valid counterparty row' -Details $reason
  } else {
    try {
      $creditSuffix = $runId.Substring([Math]::Max(0, $runId.Length - 5)).PadLeft(5, '0')
      $creditCode = "91330100MA2X${creditSuffix}A"
      $workbook = New-YinuoPartyImportWorkbook -Path $partyWorkbookPath -PartyName $partyImportName -CreditCode $creditCode
      $partyUpload = Invoke-YinuoMultipartApi -Headers $contractAdmin -Method POST -Path '/clm/party-import/upload' -Form @{ file = $workbook; jobKey = $partyImportJobKey }
      $partyJobId = Get-YinuoId $partyUpload.data
      if (-not $partyUpload.ok -or -not $partyJobId) { throw "party import upload failed: $(Get-YinuoErrorText $partyUpload)" }
      $partyPreview = Invoke-YinuoApi -Headers $contractAdmin -Method GET -Path "/clm/party-import/get?id=$partyJobId" -Body $null
      $previewOk = $partyPreview.ok -and $partyPreview.data.status -eq 'PREVIEW_READY' -and $partyPreview.data.validCount -eq 1 -and $partyPreview.data.invalidCount -eq 0
      if (-not $previewOk) { throw "party import preview was not one valid row: $(Get-YinuoErrorText $partyPreview)" }
      $partyConfirm = Invoke-YinuoApi -Headers $contractAdmin -Method PUT -Path "/clm/party-import/confirm?id=$partyJobId&requestId=$([Uri]::EscapeDataString("demo-v1-e2e-$runId-party-confirm"))" -Body $null
      $partyImportOk = $partyConfirm.ok -and $partyConfirm.data.status -eq 'SUCCEEDED' -and $partyConfirm.data.successCount -eq 1 -and $partyConfirm.data.failedCount -eq 0
      $null = Add-YinuoCheck -Condition $partyImportOk -Id 'E2E_PARTY_IMPORT' -Message 'G08 import previews and confirms one valid counterparty row' -Details "jobId=$partyJobId, status=$($partyConfirm.data.status), success=$($partyConfirm.data.successCount)"
    } catch {
      $null = Add-YinuoCheck -Condition $false -Id 'E2E_PARTY_IMPORT' -Message 'G08 import previews and confirms one valid counterparty row' -Details $_.Exception.Message
    }
  }

  Write-Host '== G10 handover refresh and reassign'
  if ($null -eq $demoBusiness -or $null -eq $contractAdmin -or $null -eq $systemAdmin) {
    Add-YinuoSkip -Id 'E2E_HANDOVER_REASSIGN' -Message 'G10 refreshes and reassigns an owned draft with original assignee retained' -Details "specialized demo accounts unavailable; business=$($demoBusinessAuth.error), contractAdmin=$($contractAdminAuth.error), systemAdmin=$($systemAdminAuth.error); run seed-demo-data.ps1"
  } else {
    try {
      $sourceUser = Get-YinuoExactUser -Headers $business -Username $DemoBusinessUsername
      $targetUser = Get-YinuoExactUser -Headers $business -Username $LegalUsername
      $sourceUserId = "$(Get-YinuoProperty -Object $sourceUser -Names @('id'))"
      $targetUserId = "$(Get-YinuoProperty -Object $targetUser -Names @('id'))"
      $sourceScopes = Invoke-YinuoApi -Headers $business -Method GET -Path "/clm/permission/user-scope/list?userId=$sourceUserId" -Body $null
      $targetScopes = Invoke-YinuoApi -Headers $business -Method GET -Path "/clm/permission/user-scope/list?userId=$targetUserId" -Body $null
      $sourceScope = @((Get-YinuoItems $sourceScopes.data) | Where-Object { $_.status -eq 'ACTIVE' -and $_.roleCode -eq 'clm_business' })
      $targetScope = @((Get-YinuoItems $targetScopes.data) | Where-Object { $_.status -eq 'ACTIVE' -and $_.roleCode -in @('clm_business','clm_legal','clm_contract_admin') })
      $scopePrecheck = $sourceScopes.ok -and $targetScopes.ok -and $sourceScope.Count -ge 1 -and $targetScope.Count -ge 1 -and
        @($targetScope | Where-Object { @($_.orgScopeJson | ConvertFrom-Json).Count -gt 0 -and @($_.typeScopeJson | ConvertFrom-Json).Count -gt 0 }).Count -ge 1
      if (-not $scopePrecheck) {
        Add-YinuoSkip -Id 'E2E_HANDOVER_REASSIGN' -Message 'G10 refreshes and reassigns an owned draft with original assignee retained' -Details 'source/target ACTIVE org+type scopes are missing; run seed-demo-data.ps1'
      } else {
        $handoverCreate = Invoke-YinuoApi -Headers $demoBusiness -Method POST -Path '/clm/contract/create-from-template' -Body @{
          templateVersionId = [int64]$templateVersionId; name = $handoverContractName
        }
        $handoverContractId = Get-YinuoId $handoverCreate.data
        if (-not $handoverCreate.ok -or -not $handoverContractId) { throw "handover draft create failed: $(Get-YinuoErrorText $handoverCreate)" }
        $handoverRefresh = Invoke-YinuoApi -Headers $systemAdmin -Method POST -Path '/clm/handover/open/refresh' -Body @{ sourceUserId = [int64]$sourceUserId }
        $handoverCaseId = "$(Get-YinuoProperty -Object $handoverRefresh.data -Names @('caseId'))"
        $refreshSafe = $handoverRefresh.ok -and [bool]$handoverCaseId -and $handoverRefresh.data.PSObject.Properties['items'] -eq $null
        if (-not $refreshSafe) { throw "handover sandbox refresh failed or leaked item detail: $(Get-YinuoErrorText $handoverRefresh)" }
        $handoverDetail = Invoke-YinuoApi -Headers $contractAdmin -Method GET -Path "/clm/handover/get?id=$handoverCaseId" -Body $null
        $handoverItem = @((Get-YinuoItems $handoverDetail.data.items) | Where-Object { $_.itemType -eq 'CONTRACT' -and "$(Get-YinuoProperty -Object $_ -Names @('contractId'))" -eq $handoverContractId })
        if (-not $handoverDetail.ok -or $handoverItem.Count -ne 1) { throw 'contract-admin handover detail did not contain the owned draft' }
        $handoverItemId = "$(Get-YinuoProperty -Object $handoverItem[0] -Names @('id'))"
        $handoverReassign = Invoke-YinuoApi -Headers $contractAdmin -Method PUT -Path '/clm/handover/reassign' -Body @{
          caseId = [int64]$handoverCaseId; targetUserId = [int64]$targetUserId
          reason = '演示离职合同责任人交接'; itemIds = @([int64]$handoverItemId)
        }
        $resultItem = @((Get-YinuoItems $handoverReassign.data.items) | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('itemId'))" -eq $handoverItemId })
        $targetDetail = Get-YinuoContractDetail -Headers $legal -ContractId $handoverContractId
        $handoverOk = $handoverReassign.ok -and $resultItem.Count -eq 1 -and $resultItem[0].status -eq 'TRANSFERRED' -and
          "$(Get-YinuoProperty -Object $handoverItem[0] -Names @('originalAssignee'))" -eq $sourceUserId -and
          "$(Get-YinuoProperty -Object $targetDetail -Names @('ownerUserId'))" -eq $targetUserId
        $null = Add-YinuoCheck -Condition $handoverOk -Id 'E2E_HANDOVER_REASSIGN' -Message 'G10 refreshes and reassigns an owned draft with original assignee retained' -Details "caseId=$handoverCaseId, itemId=$handoverItemId, original=$sourceUserId, target=$targetUserId, result=$($resultItem[0].status)"
      }
    } catch {
      $null = Add-YinuoCheck -Condition $false -Id 'E2E_HANDOVER_REASSIGN' -Message 'G10 refreshes and reassigns an owned draft with original assignee retained' -Details $_.Exception.Message
    }
  }
} catch {
  $null = Add-YinuoCheck -Condition $false -Id 'E2E_EXCEPTION' -Message 'E2E completed without an unhandled blocker' -Details $_.Exception.Message
} finally {
  Write-Host '== deterministic final cleanup'
  try {
    $cleanupExit = Invoke-YinuoE2EReset
    $null = Add-YinuoCheck -Condition ($cleanupExit -eq 0) -Id 'E2E_FINAL_CLEAN' -Message 'all demo-v1-e2e CLM and captured workflow rows were removed' -Details "exit=$cleanupExit"
  } catch {
    $null = Add-YinuoCheck -Condition $false -Id 'E2E_FINAL_CLEAN' -Message 'all demo-v1-e2e CLM and captured workflow rows were removed' -Details $_.Exception.Message
  }
  try {
    if (Test-Path $partyWorkbookPath) { Remove-Item -LiteralPath $partyWorkbookPath -Force }
    $null = Add-YinuoCheck -Condition (-not (Test-Path $partyWorkbookPath)) -Id 'E2E_TEMP_FILE_CLEAN' -Message 'temporary party-import workbook was removed' -Details $partyWorkbookPath
  } catch {
    $null = Add-YinuoCheck -Condition $false -Id 'E2E_TEMP_FILE_CLEAN' -Message 'temporary party-import workbook was removed' -Details $_.Exception.Message
  }
}

Write-YinuoSummary
exit $script:YinuoFailedCount
