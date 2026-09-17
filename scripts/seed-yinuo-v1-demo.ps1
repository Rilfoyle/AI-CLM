param(
  [string]$BaseUrl = 'http://127.0.0.1:48080/admin-api',
  [string]$Username = 'admin',
  [string]$Password = 'admin123',
  [string]$TenantName = 'TuriX',
  [string]$TenantId = '1',
  [switch]$DraftOnly
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot 'lib/YinuoV1.ps1')
Start-YinuoRun -Name 'seed-yinuo-v1-demo' -BaseUrl $BaseUrl

$repoRoot = Split-Path -Parent $PSScriptRoot
$seedPrefix = 'demo-v1 '
$draftName = 'demo-v1 标准采购合同（待提交）'
$approvalName = 'demo-v1 标准采购合同（审批中）'
$ourPartyName = 'demo-v1 我方主体'
$counterpartyName = 'demo-v1 相对方'
$demoPassword = 'admin123'
$demoBusinessUsername = 'business'
$demoContractAdminUsername = 'contractadmin'
$demoSystemAdminUsername = 'systemadmin'

function Find-YinuoExact {
  param(
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Items,
    [Parameter(Mandatory = $true)][string]$ExpectedName
  )
  return @($Items | Where-Object {
    $actual = if ($_.PSObject.Properties['name']) { $_.name } else { Get-YinuoContractName $_ }
    "${actual}" -eq $ExpectedName
  })
}

function Get-YinuoExactUser {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Username
  )
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/system/user/page?username=$([Uri]::EscapeDataString($Username))&pageNo=1&pageSize=100" -Body $null
  if (-not $response.ok) { throw "user query failed for '$Username': $(Get-YinuoErrorText $response)" }
  return @((Get-YinuoItems $response.data) | Where-Object {
    "$(Get-YinuoProperty -Object $_ -Names @('username'))" -eq $Username
  })
}

function Ensure-YinuoDemoUser {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Nickname,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$DeptId,
    [Parameter(Mandatory = $true)][string]$CheckPrefix
  )
  $matches = @(Get-YinuoExactUser -Headers $Headers -Username $Username)
  if ($matches.Count -gt 1) { throw "duplicate system user '$Username' exists" }
  if ($matches.Count -eq 0) {
    $create = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/system/user/create' -Body @{
      username = $Username
      nickname = $Nickname
      password = $Password
      deptId = [int64]$DeptId
      remark = 'demo-v1 deterministic CLM product-role account'
    }
    $userId = Get-YinuoId $create.data
    $null = Add-YinuoCheck -Condition ($create.ok -and [bool]$userId) -Id "${CheckPrefix}_CREATE" -Message "created fixed demo user '$Username'" -Details $(if ($create.ok) { "userId=$userId" } else { Get-YinuoErrorText $create })
    if (-not $create.ok -or -not $userId) { throw "user create failed for '$Username': $(Get-YinuoErrorText $create)" }
  } else {
    $userId = "$(Get-YinuoProperty -Object $matches[0] -Names @('id'))"
    $update = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/system/user/update' -Body @{
      id = [int64]$userId
      username = $Username
      nickname = $Nickname
      deptId = [int64]$DeptId
      remark = 'demo-v1 deterministic CLM product-role account'
    }
    if (-not $update.ok) { throw "user normalization failed for '$Username': $(Get-YinuoErrorText $update)" }
    $null = Add-YinuoCheck -Condition ([bool]$userId) -Id "${CheckPrefix}_REUSE" -Message "reused and normalized fixed demo user '$Username'" -Details "userId=$userId"
  }
  $enable = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/system/user/update-status' -Body @{ id = [int64]$userId; status = 0 }
  if (-not $enable.ok) { throw "user enable failed for '$Username': $(Get-YinuoErrorText $enable)" }
  $passwordReset = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/system/user/update-password' -Body @{ id = [int64]$userId; password = $Password }
  if (-not $passwordReset.ok) { throw "password reset failed for '$Username': $(Get-YinuoErrorText $passwordReset)" }
  return $userId
}

function Ensure-YinuoExistingEnabledUser {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$CheckId
  )
  $matches = @(Get-YinuoExactUser -Headers $Headers -Username $Username)
  if ($matches.Count -ne 1) { throw "expected exactly one existing system user '$Username', found $($matches.Count)" }
  $userId = "$(Get-YinuoProperty -Object $matches[0] -Names @('id'))"
  $enable = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/system/user/update-status' -Body @{ id = [int64]$userId; status = 0 }
  $passwordReset = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/system/user/update-password' -Body @{ id = [int64]$userId; password = $Password }
  $ok = $enable.ok -and $passwordReset.ok -and [bool]$userId
  $null = Add-YinuoCheck -Condition $ok -Id $CheckId -Message "fixed existing approval user '$Username' is enabled with deterministic local password" -Details $(if ($ok) { "userId=$userId" } else { "enable=$(Get-YinuoErrorText $enable); password=$(Get-YinuoErrorText $passwordReset)" })
  if (-not $ok) { throw "existing approval user '$Username' could not be normalized" }
  return $userId
}

function Ensure-YinuoPublishedPolicy {
  param([Parameter(Mandatory = $true)][hashtable]$Headers)
  $capabilitiesJson = ([ordered]@{
    clm_business = @('CREATE_DRAFT','EDIT_OWN','START_COLLABORATION','SUBMIT_APPROVAL')
    clm_legal = @('HANDLE_COLLABORATION','REQUEST_CHANGE','COMPLETE_COLLABORATION','VIEW_AUTHORIZED')
    clm_contract_admin = @('PUBLISH_GOVERNANCE','RESOLVE_GOVERNANCE','CONFIRM_RECONCILIATION','VIEW_AUTHORIZED')
    clm_system_admin = @('ASSIGN_USER_SCOPE','RUN_INTEGRATION','REPLAY_RECONCILIATION')
  } | ConvertTo-Json -Compress -Depth 10)
  $scopeLimitsJson = ([ordered]@{
    clm_business = [ordered]@{ orgScope = 'ASSIGNED'; typeScope = 'ASSIGNED' }
    clm_legal = [ordered]@{ orgScope = 'ASSIGNED'; typeScope = 'ASSIGNED' }
    clm_contract_admin = [ordered]@{ orgScope = 'ASSIGNED'; typeScope = 'ASSIGNED' }
    clm_system_admin = [ordered]@{ orgScope = 'NONE'; typeScope = 'NONE' }
  } | ConvertTo-Json -Compress -Depth 10)
  $nodePolicyJson = ([ordered]@{
    default = [ordered]@{
      enabled = $true
      editableFields = @('name','amount','customData','parties','document')
      majorFields = @('amount','customData','parties','document')
    }
    nodes = [ordered]@{}
  } | ConvertTo-Json -Compress -Depth 10)
  $remark = 'demo-v1 G05 deterministic policy'

  $list = Invoke-YinuoApi -Headers $Headers -Method GET -Path '/clm/permission/policy/list' -Body $null
  if (-not $list.ok) { throw "permission policy list failed: $(Get-YinuoErrorText $list)" }
  $policies = @(Get-YinuoItems $list.data)
  $published = @($policies | Where-Object {
    $_.status -eq 'PUBLISHED' -and $_.remark -eq $remark -and
    $_.roleCapabilitiesJson -eq $capabilitiesJson -and $_.scopeLimitsJson -eq $scopeLimitsJson -and
    $_.nodeEditPolicyJson -eq $nodePolicyJson
  } | Select-Object -First 1)
  if ($published.Count -eq 1) {
    $policyId = "$(Get-YinuoProperty -Object $published[0] -Names @('id'))"
    $null = Add-YinuoCheck -Condition ([bool]$policyId) -Id 'SEED_POLICY_REUSE' -Message 'reused the published deterministic G05 policy' -Details "policyVersionId=$policyId"
    return $policyId
  }

  $demoDrafts = @($policies | Where-Object { $_.status -eq 'DRAFT' -and $_.remark -eq $remark })
  if ($demoDrafts.Count -gt 1) { throw "multiple demo-v1 permission-policy drafts exist; resolve them before seeding" }
  $foreignDrafts = @($policies | Where-Object { $_.status -eq 'DRAFT' -and $_.remark -ne $remark })
  if ($demoDrafts.Count -eq 0 -and $foreignDrafts.Count -gt 0) {
    throw 'a non-demo G05 policy draft already exists; refusing to overwrite administrator work'
  }
  $foreignPublished = @($policies | Where-Object { $_.status -eq 'PUBLISHED' -and $_.remark -ne $remark })
  if ($foreignPublished.Count -gt 0) {
    throw 'a non-demo G05 policy is published; refusing to deactivate it from a demo seed'
  }
  $body = @{
    roleCapabilitiesJson = $capabilitiesJson
    scopeLimitsJson = $scopeLimitsJson
    nodeEditPolicyJson = $nodePolicyJson
    remark = $remark
  }
  if ($demoDrafts.Count -eq 1) { $body.id = [int64](Get-YinuoProperty -Object $demoDrafts[0] -Names @('id')) }
  $save = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/permission/policy/save' -Body $body
  $policyId = Get-YinuoId $save.data
  $null = Add-YinuoCheck -Condition ($save.ok -and [bool]$policyId) -Id 'SEED_POLICY_SAVE' -Message 'saved a complete four-role G05 policy draft' -Details $(if ($save.ok) { "policyVersionId=$policyId" } else { Get-YinuoErrorText $save })
  if (-not $save.ok -or -not $policyId) { throw "permission policy save failed: $(Get-YinuoErrorText $save)" }
  $publish = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/permission/policy/publish?id=$([Uri]::EscapeDataString($policyId))" -Body $null
  $null = Add-YinuoCheck -Condition $publish.ok -Id 'SEED_POLICY_PUBLISH' -Message 'published the deterministic G05 policy' -Details $(if ($publish.ok) { "policyVersionId=$policyId" } else { Get-YinuoErrorText $publish })
  if (-not $publish.ok) { throw "permission policy publish failed: $(Get-YinuoErrorText $publish)" }
  return $policyId
}

function Ensure-YinuoUserScope {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$UserId,
    [Parameter(Mandatory = $true)][string]$RoleCode,
    [Parameter(Mandatory = $true)][string]$PolicyVersionId,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$OrgIds,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$ContractTypeIds,
    [Parameter(Mandatory = $true)][string]$CheckId
  )
  $expectedOrg = @($OrgIds | ForEach-Object { "$_" } | Sort-Object)
  $expectedType = @($ContractTypeIds | ForEach-Object { "$_" } | Sort-Object)
  $existingList = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/permission/user-scope/list?userId=$([Uri]::EscapeDataString($UserId))" -Body $null
  if (-not $existingList.ok) { throw "user scope query failed for '$RoleCode'/${UserId}: $(Get-YinuoErrorText $existingList)" }
  $existing = @((Get-YinuoItems $existingList.data) | Where-Object { $_.roleCode -eq $RoleCode -and $_.status -eq 'ACTIVE' })
  if ($existing.Count -eq 1) {
    $existingOrg = @($existing[0].orgScopeJson | ConvertFrom-Json)
    $existingType = @($existing[0].typeScopeJson | ConvertFrom-Json)
    $sameScope = (@($existingOrg | ForEach-Object { "$_" } | Sort-Object) -join ',') -eq ($expectedOrg -join ',') -and
      (@($existingType | ForEach-Object { "$_" } | Sort-Object) -join ',') -eq ($expectedType -join ',') -and
      "$(Get-YinuoProperty -Object $existing[0] -Names @('policyVersionId'))" -eq $PolicyVersionId
    if ($sameScope) {
      $scopeId = "$(Get-YinuoProperty -Object $existing[0] -Names @('id'))"
      $null = Add-YinuoCheck -Condition $true -Id $CheckId -Message "reused ACTIVE $RoleCode contract-domain scope" -Details "scopeId=$scopeId, userId=$UserId, org=[$($expectedOrg -join ',')], type=[$($expectedType -join ',')]"
      return $scopeId
    }
  }
  $save = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/permission/user-scope/save' -Body @{
    userId = [int64]$UserId
    roleCode = $RoleCode
    policyVersionId = [int64]$PolicyVersionId
    orgIds = @($OrgIds | ForEach-Object { [int64]$_ })
    contractTypeIds = @($ContractTypeIds | ForEach-Object { [int64]$_ })
    status = 'ACTIVE'
  }
  if (-not $save.ok) { throw "user scope save failed for '$RoleCode'/${UserId}: $(Get-YinuoErrorText $save)" }
  $list = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/permission/user-scope/list?userId=$([Uri]::EscapeDataString($UserId))" -Body $null
  $match = @((Get-YinuoItems $list.data) | Where-Object { $_.roleCode -eq $RoleCode -and $_.status -eq 'ACTIVE' })
  $actualOrg = if ($match.Count -eq 1) { @($match[0].orgScopeJson | ConvertFrom-Json) } else { @() }
  $actualType = if ($match.Count -eq 1) { @($match[0].typeScopeJson | ConvertFrom-Json) } else { @() }
  $scopeOk = $list.ok -and $match.Count -eq 1 -and
    (@($actualOrg | ForEach-Object { "$_" } | Sort-Object) -join ',') -eq ($expectedOrg -join ',') -and
    (@($actualType | ForEach-Object { "$_" } | Sort-Object) -join ',') -eq ($expectedType -join ',') -and
    "$(Get-YinuoProperty -Object $match[0] -Names @('policyVersionId'))" -eq $PolicyVersionId
  $null = Add-YinuoCheck -Condition $scopeOk -Id $CheckId -Message "persisted ACTIVE $RoleCode contract-domain scope" -Details "userId=$UserId, org=[$($expectedOrg -join ',')], type=[$($expectedType -join ',')]"
  if (-not $scopeOk) { throw "user scope verification failed for '$RoleCode'/$UserId" }
  return "$(Get-YinuoId $save.data)"
}

function Ensure-YinuoParty {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][bool]$Internal,
    [AllowEmptyString()][string]$ShortName = '',
    [Parameter(Mandatory = $true)][string]$CheckId
  )
  $items = @(Get-YinuoAllPages -Headers $Headers -Path '/clm/party/page')
  $matches = @(Find-YinuoExact -Items $items -ExpectedName $Name)
  if ($matches.Count -gt 1) { throw "duplicate seed party '$Name' exists ($($matches.Count) active rows)" }
  if ($matches.Count -eq 1) {
    $id = "$(Get-YinuoProperty -Object $matches[0] -Names @('id'))"
    $currentShortName = "$(Get-YinuoProperty -Object $matches[0] -Names @('shortName'))"
    if ($Internal -and $currentShortName -ne $ShortName) {
      $update = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/clm/party/update' -Body @{
        id = $id
        partyType = 1
        name = $Name
        shortName = $ShortName
        internalFlag = $true
        status = 0
        contactName = '合同运营'
        contactPhone = '0571-88001001'
        remark = 'Yinuo V1 deterministic API seed'
      }
      $null = Add-YinuoCheck -Condition $update.ok -Id $CheckId -Message "reused fixed party '$Name' and normalized its numbering short name" -Details $(if ($update.ok) { "id=$id, shortName=$ShortName" } else { Get-YinuoErrorText $update })
      if (-not $update.ok) { throw "party short-name update failed for '$Name': $(Get-YinuoErrorText $update)" }
    } else {
      $null = Add-YinuoCheck -Condition ([bool]$id) -Id $CheckId -Message "reused fixed party '$Name'" -Details "id=$id, shortName=$currentShortName"
    }
    return $id
  }
  $create = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/party/create' -Body @{
    partyType = 1
    name = $Name
    shortName = if ($Internal) { $ShortName } else { $null }
    internalFlag = $Internal
    status = 0
    contactName = if ($Internal) { '合同运营' } else { '客户代表' }
    contactPhone = if ($Internal) { '0571-88001001' } else { '021-66001002' }
    remark = 'Yinuo V1 deterministic API seed'
  }
  $id = Get-YinuoId $create.data
  $null = Add-YinuoCheck -Condition ($create.ok -and [bool]$id) -Id $CheckId -Message "created fixed party '$Name' through API" -Details $(if ($create.ok) { "id=$id" } else { Get-YinuoErrorText $create })
  if (-not $create.ok -or -not $id) { throw "party create failed for '$Name': $(Get-YinuoErrorText $create)" }
  return $id
}

function Get-YinuoContractDetail {
  param([hashtable]$Headers, [string]$ContractId)
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/contract/get?id=$([Uri]::EscapeDataString($ContractId))" -Body $null
  if (-not $response.ok) { throw "contract detail failed for ${ContractId}: $(Get-YinuoErrorText $response)" }
  return $response.data
}

function Ensure-YinuoSeedContract {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$TemplateVersionId,
    [Parameter(Mandatory = $true)][string]$OurPartyId,
    [Parameter(Mandatory = $true)][string]$CounterpartyId,
    [Parameter(Mandatory = $true)][bool]$Submit,
    [Parameter(Mandatory = $true)][decimal]$Amount,
    [Parameter(Mandatory = $true)][string]$CheckPrefix
  )
  $active = @(Get-YinuoAllPages -Headers $Headers -Path "/clm/contract/page?title=$([Uri]::EscapeDataString($Name))")
  $matches = @(Find-YinuoExact -Items $active -ExpectedName $Name)
  if ($matches.Count -gt 1) { throw "duplicate seed contract '$Name' exists ($($matches.Count) active rows)" }

  $created = $false
  if ($matches.Count -eq 0) {
    $deletedResponse = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/contract/page?deletedOnly=true&title=$([Uri]::EscapeDataString($Name))&pageNo=1&pageSize=100" -Body $null
    if ($deletedResponse.ok) {
      $deletedMatches = @(Find-YinuoExact -Items @(Get-YinuoItems $deletedResponse.data) -ExpectedName $Name)
      if ($deletedMatches.Count -gt 1) { throw "duplicate deleted seed contract '$Name' exists" }
      if ($deletedMatches.Count -eq 1) {
        $deletedId = "$(Get-YinuoProperty -Object $deletedMatches[0] -Names @('id'))"
        $restore = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/contract/restore-draft?id=$([Uri]::EscapeDataString($deletedId))" -Body $null
        if (-not $restore.ok) { throw "restore failed for seed contract '$Name': $(Get-YinuoErrorText $restore)" }
        $matches = @((Get-YinuoContractDetail -Headers $Headers -ContractId $deletedId))
      }
    }
  }

  if ($matches.Count -eq 0) {
    $create = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/contract/create-from-template' -Body @{
      templateVersionId = $TemplateVersionId
      name = $Name
    }
    $contractId = Get-YinuoId $create.data
    $null = Add-YinuoCheck -Condition ($create.ok -and [bool]$contractId) -Id "${CheckPrefix}_CREATE" -Message "created '$Name' from a published template" -Details $(if ($create.ok) { "contractId=$contractId" } else { Get-YinuoErrorText $create })
    if (-not $create.ok -or -not $contractId) { throw "create-from-template failed for '$Name': $(Get-YinuoErrorText $create)" }
    $created = $true
  } else {
    $contractId = "$(Get-YinuoProperty -Object $matches[0] -Names @('id','contractId'))"
    $null = Add-YinuoCheck -Condition ([bool]$contractId) -Id "${CheckPrefix}_REUSE" -Message "reused fixed contract '$Name'" -Details "contractId=$contractId"
  }

  $detail = Get-YinuoContractDetail -Headers $Headers -ContractId $contractId
  $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))"
  $approvalStatus = Get-YinuoProperty -Object $detail -Names @('approvalStatus')
  if ($created) {
    $null = Add-YinuoCheck -Condition (-not (Get-YinuoProperty -Object $detail -Names @('contractNo'))) -Id "${CheckPrefix}_DRAFT_NO_NUMBER" -Message 'new draft has no contract number' -Details $null
  }

  $isDraft = ($stage -eq 'DRAFT' -or $approvalStatus -eq 0 -or -not $stage)
  if ($isDraft) {
    $currentRevision = Get-YinuoCurrentRevision -Headers $Headers -ContractId $contractId
    $baseRevisionId = "$(Get-YinuoProperty -Object $currentRevision -Names @('id','revisionId'))"
    $parties = @(Get-YinuoProperty -Object $detail -Names @('parties'))
    $currentAmount = Get-YinuoProperty -Object $detail -Names @('amount')
    $currentCustomData = Get-YinuoProperty -Object $detail -Names @('customData')
    $deliveryDate = if ($null -ne $currentCustomData) { Get-YinuoProperty -Object $currentCustomData -Names @('deliveryDate') } else { $null }
    # A rejected approval must be resubmitted from a new immutable revision, even when the
    # deterministic demo fields themselves did not change.
    $needsDetails = $created -or $approvalStatus -eq 3 -or $parties.Count -lt 2 -or $null -eq $currentAmount -or
      [decimal]$currentAmount -ne $Amount -or -not $deliveryDate
    if ($needsDetails) {
      $save = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/clm/contract/revision/save' -Body @{
        contractId = $contractId
        baseRevisionId = $baseRevisionId
        name = $Name
        amount = $Amount
        currency = 'CNY'
        startDate = '2026-09-01'
        endDate = '2027-08-31'
        ourPartyId = $OurPartyId
        counterpartyIds = @($CounterpartyId)
        customData = @{
          deliveryDate = '2026-09-30'
          acceptCriteria = '到货后完成开箱验收并签署验收单。'
          warrantyMonths = 24
        }
        changeReason = 'Yinuo V1 deterministic demo seed'
      }
      $null = Add-YinuoCheck -Condition $save.ok -Id "${CheckPrefix}_REVISION" -Message 'saved structured fields and party snapshot as a new revision' -Details $(if ($save.ok) { $null } else { Get-YinuoErrorText $save })
      if (-not $save.ok) { throw "revision save failed for '$Name': $(Get-YinuoErrorText $save)" }
      $detail = Get-YinuoContractDetail -Headers $Headers -ContractId $contractId
    }
    if ((Get-YinuoProperty -Object $detail -Names @('noCommitmentConfirmed')) -ne $true) {
      $confirm = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/contract/commitment/confirm-none?contractId=$([Uri]::EscapeDataString($contractId))" -Body $null
      $null = Add-YinuoCheck -Condition $confirm.ok -Id "${CheckPrefix}_NO_COMMITMENT" -Message 'explicitly confirmed no major commitment' -Details $(if ($confirm.ok) { $null } else { Get-YinuoErrorText $confirm })
      if (-not $confirm.ok) { throw "major commitment confirmation failed for '$Name': $(Get-YinuoErrorText $confirm)" }
    }
  }

  if ($Submit) {
    $detail = Get-YinuoContractDetail -Headers $Headers -ContractId $contractId
    $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))"
    $approvalStatus = Get-YinuoProperty -Object $detail -Names @('approvalStatus')
    if ($stage -eq 'APPROVED' -or $approvalStatus -eq 2) {
      throw "seed contract '$Name' was already approved; reset Demo scope before rebuilding an in-approval sample"
    }
    if ($stage -eq 'DRAFT' -or $approvalStatus -eq 0 -or -not $stage) {
      $currentRevision = Get-YinuoCurrentRevision -Headers $Headers -ContractId $contractId
      $revisionId = "$(Get-YinuoProperty -Object $currentRevision -Names @('id','revisionId'))"
      $submitResponse = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/contract/submit' -Body @{
        id = $contractId
        contractId = $contractId
        baseRevisionId = $revisionId
        submitRequestId = "demo-v1-seed-approval-$revisionId"
        remark = 'Yinuo V1 standard-template direct submit demo'
      }
      $assignedNumber = Get-YinuoProperty -Object $submitResponse.data -Names @('contractNo')
      $null = Add-YinuoCheck -Condition ($submitResponse.ok -and [bool]$assignedNumber) -Id "${CheckPrefix}_SUBMIT" -Message 'submitted standard template directly and assigned a permanent number' -Details $(if ($submitResponse.ok) { "contractNo=$assignedNumber" } else { Get-YinuoErrorText $submitResponse })
      if (-not $submitResponse.ok -or -not $assignedNumber) { throw "submit failed for '$Name': $(Get-YinuoErrorText $submitResponse)" }
    } else {
      $null = Add-YinuoCheck -Condition ($stage -in @('APPROVING','IN_APPROVAL') -or $approvalStatus -eq 1) -Id "${CheckPrefix}_IN_APPROVAL" -Message 'reused the existing in-approval sample' -Details "stage=$stage, approvalStatus=$approvalStatus"
    }
  } else {
    $detail = Get-YinuoContractDetail -Headers $Headers -ContractId $contractId
    $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))"
    $approvalStatus = Get-YinuoProperty -Object $detail -Names @('approvalStatus')
    $null = Add-YinuoCheck -Condition ($stage -eq 'DRAFT' -or $approvalStatus -eq 0) -Id "${CheckPrefix}_REMAINS_DRAFT" -Message 'draft sample remains unsubmitted' -Details "stage=$stage, approvalStatus=$approvalStatus"
  }
  return $contractId
}

try {
  Write-Host '== preflight'
  $headers = Connect-YinuoApi -Username $Username -Password $Password -TenantName $TenantName -TenantId $TenantId
  $null = Add-YinuoCheck -Condition ($null -ne $headers) -Id 'SEED_LOGIN' -Message 'seed account logged in' -Details $null
  $migration = Get-YinuoMigrationPreflight -RepoRoot $repoRoot -TenantId $TenantId
  $null = Add-YinuoCheck -Condition $migration.ready -Id 'SEED_PREFLIGHT_SQL_01_13' -Message 'SQL 01-13 tables, required columns, roles, and OneContract-aligned menus are initialized' -Details $(if ($migration.ready) { "runner=$($migration.runner.kind)" } else { $migration.problems -join '; ' })
  if (-not $migration.ready) { throw "SQL 01-13 migration preflight failed; seed made no writes ($($migration.problems -join '; '))" }

  $workbench = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/workbench/summary' -Body $null
  $null = Assert-YinuoApi -Response $workbench -Id 'SEED_PREFLIGHT_WORKBENCH' -Message 'W01 workbench endpoint is available' -ThrowOnFailure
  $templatesResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/template/published-page?pageNo=1&pageSize=100' -Body $null
  $null = Assert-YinuoApi -Response $templatesResponse -Id 'SEED_PREFLIGHT_TEMPLATE' -Message 'W02 published-template endpoint is available' -ThrowOnFailure
  $contractsResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/contract/page?pageNo=1&pageSize=100' -Body $null
  $null = Assert-YinuoApi -Response $contractsResponse -Id 'SEED_PREFLIGHT_CONTRACT' -Message 'W03 contract page endpoint is available' -ThrowOnFailure
  $partiesResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/party/page?pageNo=1&pageSize=100' -Body $null
  $null = Assert-YinuoApi -Response $partiesResponse -Id 'SEED_PREFLIGHT_PARTY' -Message 'party directory endpoint is available' -ThrowOnFailure
  $collaborationResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/collaboration/page?view=TODO&pageNo=1&pageSize=20' -Body $null
  $null = Assert-YinuoApi -Response $collaborationResponse -Id 'SEED_PREFLIGHT_COLLABORATION' -Message 'collaboration support endpoint is initialized' -ThrowOnFailure
  $rolesResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/system/role/list-all-simple' -Body $null
  $null = Assert-YinuoApi -Response $rolesResponse -Id 'SEED_PREFLIGHT_ROLE_API' -Message 'product-role query endpoint is available' -ThrowOnFailure
  $menusResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path '/system/menu/list-all-simple' -Body $null
  $null = Assert-YinuoApi -Response $menusResponse -Id 'SEED_PREFLIGHT_MENU_API' -Message 'menu query endpoint is available' -ThrowOnFailure
  $roleCodes = @((Get-YinuoItems $rolesResponse.data) | ForEach-Object { "$(Get-YinuoProperty -Object $_ -Names @('code'))" })
  $menuIds = @((Get-YinuoItems $menusResponse.data) | ForEach-Object { "$(Get-YinuoProperty -Object $_ -Names @('id'))" })
  $requiredRoleCodes = @('clm_business','clm_legal','clm_contract_admin','clm_system_admin')
  $missingRoleCodes = @($requiredRoleCodes | Where-Object { $_ -notin $roleCodes })
  $requiredMenuIds = @(
    '7060','7070','7077','7078','7080','7082','7163','7181','7182',
    '7302','7306','7308','7309','7310','7401','7402','7403'
  )
  $missingMenuIds = $requiredMenuIds | Where-Object { $_ -notin $menuIds }
  $roleMenuReady = $missingRoleCodes.Count -eq 0 -and @($missingMenuIds).Count -eq 0
  $null = Add-YinuoCheck -Condition $roleMenuReady -Id 'SEED_PREFLIGHT_SQL_09' -Message 'SQL 09 product roles, W06 collaboration actions, G08/G10, and canonical W/G/S menus are initialized' -Details $(if ($roleMenuReady) { 'roles=4, requiredMenus=17' } else { "missingRoles=$($missingRoleCodes -join ','), missingMenus=$(@($missingMenuIds) -join ',')" })
  if (-not $roleMenuReady) { throw 'SQL 09 product-role/menu migration is incomplete; seed made no writes' }

  $templates = @(Get-YinuoItems $templatesResponse.data)
  $template = @($templates | Where-Object { $_.name -match '采购|Purchase' } | Select-Object -First 1)
  if ($template.Count -eq 0) { $template = @($templates | Select-Object -First 1) }
  $templateVersionId = if ($template.Count -gt 0) { "$(Get-YinuoProperty -Object $template[0] -Names @('currentVersionId'))" } else { '' }
  $contractTypeId = if ($template.Count -gt 0) { "$(Get-YinuoProperty -Object $template[0] -Names @('contractTypeId'))" } else { '' }
  $null = Add-YinuoCheck -Condition ([bool]$templateVersionId -and [bool]$contractTypeId) -Id 'SEED_PUBLISHED_TEMPLATE' -Message 'a published template version and contract type are available for API seeding' -Details $(if ($templateVersionId -and $contractTypeId) { "templateVersionId=$templateVersionId, contractTypeId=$contractTypeId" } else { 'publish a template before running this seed' })
  if (-not $templateVersionId -or -not $contractTypeId) { throw 'no complete published TemplateVersion is available; the seed will not fabricate template data' }

  Write-Host '== G05 policy and fixed product-role users'
  $adminUsers = @(Get-YinuoExactUser -Headers $headers -Username $Username)
  if ($adminUsers.Count -ne 1) { throw "seed administrator '$Username' was not uniquely queryable" }
  $deptId = "$(Get-YinuoProperty -Object $adminUsers[0] -Names @('deptId'))"
  if (-not $deptId) {
    $depts = Invoke-YinuoApi -Headers $headers -Method GET -Path '/system/dept/simple-list' -Body $null
    if (-not $depts.ok) { throw "department preflight failed: $(Get-YinuoErrorText $depts)" }
    $deptId = "$(Get-YinuoProperty -Object @((Get-YinuoItems $depts.data) | Select-Object -First 1)[0] -Names @('id'))"
  }
  $null = Add-YinuoCheck -Condition ([bool]$deptId) -Id 'SEED_DEMO_DEPT' -Message 'selected an enabled organization for deterministic role scopes' -Details "deptId=$deptId"
  if (-not $deptId) { throw 'no enabled department is available for the demo users' }

  $businessUserId = Ensure-YinuoDemoUser -Headers $headers -Username $demoBusinessUsername -Nickname '演示业务经办人' -Password $demoPassword -DeptId $deptId -CheckPrefix 'SEED_USER_BUSINESS'
  $legalUserId = Ensure-YinuoExistingEnabledUser -Headers $headers -Username 'legal' -Password $demoPassword -CheckId 'SEED_USER_LEGAL'
  $contractAdminUserId = Ensure-YinuoDemoUser -Headers $headers -Username $demoContractAdminUsername -Nickname '演示合同管理员' -Password $demoPassword -DeptId $deptId -CheckPrefix 'SEED_USER_CONTRACT_ADMIN'
  $systemAdminUserId = Ensure-YinuoDemoUser -Headers $headers -Username $demoSystemAdminUsername -Nickname '演示系统管理员' -Password $demoPassword -DeptId $deptId -CheckPrefix 'SEED_USER_SYSTEM_ADMIN'
  $policyVersionId = Ensure-YinuoPublishedPolicy -Headers $headers
  $businessScopeId = Ensure-YinuoUserScope -Headers $headers -UserId $businessUserId -RoleCode 'clm_business' -PolicyVersionId $policyVersionId -OrgIds @($deptId) -ContractTypeIds @($contractTypeId) -CheckId 'SEED_SCOPE_BUSINESS'
  $legalScopeId = Ensure-YinuoUserScope -Headers $headers -UserId $legalUserId -RoleCode 'clm_legal' -PolicyVersionId $policyVersionId -OrgIds @($deptId) -ContractTypeIds @($contractTypeId) -CheckId 'SEED_SCOPE_LEGAL'
  $contractAdminScopeId = Ensure-YinuoUserScope -Headers $headers -UserId $contractAdminUserId -RoleCode 'clm_contract_admin' -PolicyVersionId $policyVersionId -OrgIds @($deptId) -ContractTypeIds @($contractTypeId) -CheckId 'SEED_SCOPE_CONTRACT_ADMIN'
  $systemAdminScopeId = Ensure-YinuoUserScope -Headers $headers -UserId $systemAdminUserId -RoleCode 'clm_system_admin' -PolicyVersionId $policyVersionId -OrgIds @() -ContractTypeIds @() -CheckId 'SEED_SCOPE_SYSTEM_ADMIN_NONE'
  $verifiedScopeIds = @(@($businessScopeId,$legalScopeId,$contractAdminScopeId,$systemAdminScopeId) | Where-Object { [bool]$_ })
  $null = Add-YinuoCheck -Condition ($verifiedScopeIds.Count -eq 4) -Id 'SEED_SCOPE_BASELINE' -Message 'all four fixed product roles have one verified ACTIVE scope' -Details 'business/legal/contract-admin=explicit org+type; system-admin=NONE/NONE'

  Write-Host '== fixed parties'
  $ourPartyId = Ensure-YinuoParty -Headers $headers -Name $ourPartyName -Internal $true -ShortName '演示方' -CheckId 'SEED_OUR_PARTY'
  $counterpartyId = Ensure-YinuoParty -Headers $headers -Name $counterpartyName -Internal $false -CheckId 'SEED_COUNTERPARTY'
  $ourPartyResponse = Invoke-YinuoApi -Headers $headers -Method GET -Path "/clm/party/get?id=$([Uri]::EscapeDataString($ourPartyId))" -Body $null
  $actualShortName = "$(Get-YinuoProperty -Object $ourPartyResponse.data -Names @('shortName'))"
  $null = Add-YinuoCheck -Condition ($ourPartyResponse.ok -and $actualShortName -eq '演示方') -Id 'SEED_OUR_PARTY_SHORT_NAME' -Message 'internal demo party exposes the fixed numbering short name through API' -Details "shortName=$actualShortName"
  if (-not $ourPartyResponse.ok -or $actualShortName -ne '演示方') { throw 'internal demo party short_name was not persisted' }

  Write-Host '== fixed demo contracts'
  $draftId = Ensure-YinuoSeedContract -Headers $headers -Name $draftName -TemplateVersionId $templateVersionId -OurPartyId $ourPartyId -CounterpartyId $counterpartyId -Submit $false -Amount 480000 -CheckPrefix 'SEED_DRAFT'
  if (-not $DraftOnly) {
    $approvalId = Ensure-YinuoSeedContract -Headers $headers -Name $approvalName -TemplateVersionId $templateVersionId -OurPartyId $ourPartyId -CounterpartyId $counterpartyId -Submit $true -Amount 1280000 -CheckPrefix 'SEED_APPROVAL'
    $approvalDetail = Get-YinuoContractDetail -Headers $headers -ContractId $approvalId
    $approvalContractNo = "$(Get-YinuoProperty -Object $approvalDetail -Names @('contractNo'))"
    $numberUsesPartyShortName = [bool]$approvalContractNo -and $approvalContractNo.IndexOf('演示方', [StringComparison]::Ordinal) -ge 0
    $null = Add-YinuoCheck -Condition $numberUsesPartyShortName -Id 'SEED_NUMBER_USES_PARTY_SHORT_NAME' -Message 'submitted demo number contains the persisted internal-party short name' -Details "contractNo=$approvalContractNo"
    if (-not $numberUsesPartyShortName) { throw 'published numbering rule did not include the demo internal-party short name' }
  }

  Write-Host '== scope guard'
  $seedContracts = @(Get-YinuoAllPages -Headers $headers -Path "/clm/contract/page?title=$([Uri]::EscapeDataString('demo-v1'))") | Where-Object {
    "$(Get-YinuoContractName $_)".StartsWith($seedPrefix, [StringComparison]::OrdinalIgnoreCase)
  }
  $forbidden = @($seedContracts | Where-Object {
    $stage = "$(Get-YinuoProperty -Object $_ -Names @('stageCode'))".ToUpperInvariant()
    $lifecycle = Get-YinuoProperty -Object $_ -Names @('lifecycleStatus')
    $stage -match 'SIGN|ARCHIV|PERFORM|EFFECTIVE|EXPIRED|TERMINAT|CLOSED' -or ($null -ne $lifecycle -and [int]$lifecycle -ge 3)
  })
  $null = Add-YinuoCheck -Condition ($forbidden.Count -eq 0) -Id 'SEED_NO_POST_APPROVAL_DATA' -Message 'seed contains no signed, archived, performance, effective, or closed contract data' -Details "seedContracts=$($seedContracts.Count), forbidden=$($forbidden.Count)"

  Write-Host '== deterministic demo baseline'
  $draftSamples = @($seedContracts | Where-Object {
    "$(Get-YinuoContractName $_)" -eq $draftName -and
    ("$(Get-YinuoProperty -Object $_ -Names @('stageCode'))" -eq 'DRAFT' -or
      (Get-YinuoProperty -Object $_ -Names @('approvalStatus')) -eq 0)
  })
  $approvalSamples = @($seedContracts | Where-Object {
    "$(Get-YinuoContractName $_)" -eq $approvalName -and
    ("$(Get-YinuoProperty -Object $_ -Names @('stageCode'))" -in @('APPROVING','IN_APPROVAL') -or
      (Get-YinuoProperty -Object $_ -Names @('approvalStatus')) -eq 1)
  })
  $expectedApprovalCount = if ($DraftOnly) { 0 } else { 1 }
  $demoParties = @(Get-YinuoAllPages -Headers $headers -Path '/clm/party/page') | Where-Object {
    "$(Get-YinuoProperty -Object $_ -Names @('name'))" -in @($ourPartyName,$counterpartyName)
  }
  $policyList = Invoke-YinuoApi -Headers $headers -Method GET -Path '/clm/permission/policy/list' -Body $null
  $publishedDemoPolicies = @((Get-YinuoItems $policyList.data) | Where-Object { $_.status -eq 'PUBLISHED' -and $_.remark -eq 'demo-v1 G05 deterministic policy' })
  $publishedNodePolicy = if ($publishedDemoPolicies.Count -eq 1) { $publishedDemoPolicies[0].nodeEditPolicyJson | ConvertFrom-Json } else { $null }
  $editable = if ($null -ne $publishedNodePolicy) { @($publishedNodePolicy.default.editableFields) } else { @() }
  $major = if ($null -ne $publishedNodePolicy) { @($publishedNodePolicy.default.majorFields) } else { @() }
  $missingEditableFields = @(@('name','amount','customData','parties','document') | Where-Object { $_ -notin $editable })
  $missingMajorFields = @(@('amount','customData','parties','document') | Where-Object { $_ -notin $major })
  $nodePolicyOk = $null -ne $publishedNodePolicy -and $publishedNodePolicy.default.enabled -eq $true -and
    $missingEditableFields.Count -eq 0 -and $missingMajorFields.Count -eq 0
  $null = Add-YinuoCheck -Condition $nodePolicyOk -Id 'SEED_POLICY_NODE_EDIT_BASELINE' -Message 'published G05 policy enables controlled name/amount/customData/parties/document edits and classifies major fields' -Details "editable=$($editable -join ','), major=$($major -join ',')"
  $baselineOk = $draftSamples.Count -eq 1 -and $approvalSamples.Count -eq $expectedApprovalCount -and
    $seedContracts.Count -eq (1 + $expectedApprovalCount) -and $demoParties.Count -eq 2 -and
    $publishedDemoPolicies.Count -eq 1 -and $forbidden.Count -eq 0
  $null = Add-YinuoCheck -Condition $baselineOk -Id 'SEED_BASELINE_COUNTS' -Message 'demo baseline counts are deterministic and stop at approval' -Details "parties=$($demoParties.Count), policies=$($publishedDemoPolicies.Count), drafts=$($draftSamples.Count), inApproval=$($approvalSamples.Count), totalContracts=$($seedContracts.Count), postApproval=$($forbidden.Count)"
} catch {
  $null = Add-YinuoCheck -Condition $false -Id 'SEED_EXCEPTION' -Message 'seed completed without an unhandled blocker' -Details $_.Exception.Message
}

Write-YinuoSummary
exit $script:YinuoFailedCount
