Set-StrictMode -Version 2.0

$script:TurixDemoPrefix = '演示｜'
$script:TurixDemoPolicyRemark = 'TuriX V1 默认权限政策'
$script:TurixDemoPartyCodes = @(
  'TURIX-DEMO-OUR-001',
  'TURIX-DEMO-CPTY-001',
  'TURIX-DEMO-CPTY-002',
  'TURIX-DEMO-CPTY-003',
  'TURIX-DEMO-CPTY-004',
  'TURIX-DEMO-CPTY-005'
)

function Test-TurixArrayEqual {
  param([AllowNull()][object[]]$Left, [AllowNull()][object[]]$Right)
  $leftText = @($Left | ForEach-Object { "$_" }) -join "`u{0}"
  $rightText = @($Right | ForEach-Object { "$_" }) -join "`u{0}"
  return $leftText -ceq $rightText
}

function Test-TurixSetEqual {
  param([AllowNull()][object[]]$Left, [AllowNull()][object[]]$Right)
  $leftText = @($Left | ForEach-Object { "$_" } | Sort-Object -Unique) -join ','
  $rightText = @($Right | ForEach-Object { "$_" } | Sort-Object -Unique) -join ','
  return $leftText -ceq $rightText
}

function Test-TurixNullableIdEqual {
  param([AllowNull()][object]$Left, [AllowNull()][object]$Right)
  $leftText = if ($null -eq $Left -or "$Left" -eq '') { '' } else { "$Left" }
  $rightText = if ($null -eq $Right -or "$Right" -eq '') { '' } else { "$Right" }
  return $leftText -eq $rightText
}

function ConvertTo-TurixCanonicalValue {
  param([AllowNull()][object]$Value)
  if ($null -eq $Value) { return $null }
  if ($Value -is [string] -or $Value -is [char] -or $Value -is [bool] -or
      $Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or
      $Value -is [int64] -or $Value -is [decimal] -or $Value -is [double] -or
      $Value -is [single] -or $Value -is [datetime]) { return $Value }
  if ($Value -is [System.Collections.IDictionary]) {
    $ordered = [ordered]@{}
    foreach ($key in @($Value.Keys | ForEach-Object { "$_" } | Sort-Object)) {
      $ordered[$key] = ConvertTo-TurixCanonicalValue $Value[$key]
    }
    return [pscustomobject]$ordered
  }
  if ($Value -is [System.Collections.IEnumerable]) {
    $items = @()
    foreach ($item in $Value) { $items += ,(ConvertTo-TurixCanonicalValue $item) }
    return ,$items
  }
  $properties = @($Value.PSObject.Properties | Where-Object {
    $_.MemberType -in @('NoteProperty', 'Property')
  } | Sort-Object Name)
  if ($properties.Count -gt 0) {
    $ordered = [ordered]@{}
    foreach ($property in $properties) {
      $ordered[$property.Name] = ConvertTo-TurixCanonicalValue $property.Value
    }
    return [pscustomobject]$ordered
  }
  return "$Value"
}

function Get-TurixCanonicalJson {
  param([AllowNull()][object]$Value)
  return (ConvertTo-TurixCanonicalValue $Value | ConvertTo-Json -Depth 50 -Compress)
}

function Assert-TurixApi {
  param(
    [Parameter(Mandatory = $true)][object]$Response,
    [Parameter(Mandatory = $true)][string]$What
  )
  if (-not $Response.ok) { throw "$What failed: $(Get-YinuoErrorText $Response)" }
  return $Response
}

function Get-TurixExactUser {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$Username
  )
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/system/user/page?username=$([Uri]::EscapeDataString($Username))&pageNo=1&pageSize=100" -Body $null
  $null = Assert-TurixApi -Response $response -What "query fixed user '$Username'"
  return @((Get-YinuoItems $response.data) | Where-Object {
    "$(Get-YinuoProperty -Object $_ -Names @('username'))" -ceq $Username
  })
}

function Assert-TurixFixedUser {
  param(
    [Parameter(Mandatory = $true)][hashtable]$AdminHeaders,
    [Parameter(Mandatory = $true)][hashtable]$Spec,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$TenantName,
    [Parameter(Mandatory = $true)][string]$TenantId
  )
  $matches = @(Get-TurixExactUser -Headers $AdminHeaders -Username $Spec.username)
  if ($matches.Count -ne 1) { throw "expected exactly one fixed user '$($Spec.username)', found $($matches.Count)" }
  $user = $matches[0]
  $actualId = "$(Get-YinuoProperty -Object $user -Names @('id'))"
  $actualDept = "$(Get-YinuoProperty -Object $user -Names @('deptId'))"
  $actualNickname = "$(Get-YinuoProperty -Object $user -Names @('nickname'))"
  $actualStatus = Get-YinuoProperty -Object $user -Names @('status')
  if ($actualId -ne "$($Spec.id)" -or $actualDept -ne "$($Spec.deptId)" -or
      $actualNickname -cne $Spec.nickname -or $actualStatus -ne 0) {
    throw "fixed user '$($Spec.username)' drifted: expected id=$($Spec.id), dept=$($Spec.deptId), nickname=$($Spec.nickname), status=0; actual id=$actualId, dept=$actualDept, nickname=$actualNickname, status=$actualStatus"
  }
  $userHeaders = Connect-YinuoApi -Username $Spec.username -Password $Password -TenantName $TenantName -TenantId $TenantId
  if ($null -eq $userHeaders) { throw "fixed password verification failed for '$($Spec.username)'" }
  $null = Add-YinuoCheck -Condition $true -Id "FIXED_USER_$($Spec.id)" -Message "fixed TuriX user '$($Spec.username)' was verified without mutation" -Details "id=$actualId, dept=$actualDept, nickname=$actualNickname"
  return [pscustomobject]@{ id = [int64]$actualId; headers = $userHeaders; user = $user }
}

function Ensure-TurixContractType {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][hashtable]$Spec,
    [Parameter(Mandatory = $true)][string]$ProcessDefinitionKey
  )
  $types = @(Get-YinuoAllPages -Headers $Headers -Path '/clm/contract-type/page')
  $matches = @($types | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('code'))" -ceq $Spec.code })
  if ($matches.Count -gt 1) { throw "duplicate contract type code '$($Spec.code)'" }
  if ($matches.Count -eq 0) {
    $create = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/contract-type/create' -Body @{
      code = $Spec.code; name = $Spec.name; description = $Spec.description
      status = 0; sort = $Spec.sort; processDefinitionKey = $ProcessDefinitionKey
    }
    $null = Assert-TurixApi -Response $create -What "create contract type '$($Spec.code)'"
    $typeId = "$(Get-YinuoId $create.data)"
    if (-not $typeId) { throw "contract type create returned no id for '$($Spec.code)'" }
  } else {
    $type = $matches[0]
    $typeId = "$(Get-YinuoProperty -Object $type -Names @('id'))"
    if ("$(Get-YinuoProperty -Object $type -Names @('name'))" -cne $Spec.name -or
        "$(Get-YinuoProperty -Object $type -Names @('description'))" -cne $Spec.description -or
        (Get-YinuoProperty -Object $type -Names @('status')) -ne 0 -or
        (Get-YinuoProperty -Object $type -Names @('sort')) -ne $Spec.sort) {
      throw "contract type '$($Spec.code)' exists with non-seed metadata; refusing to overwrite it"
    }
  }

  $versionsResponse = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/contract-type/version/list?typeId=$([Uri]::EscapeDataString($typeId))" -Body $null
  $null = Assert-TurixApi -Response $versionsResponse -What "list versions for contract type '$($Spec.code)'"
  $versions = @(Get-YinuoItems $versionsResponse.data)
  $current = @($versions | Where-Object { $_.status -eq 1 } | Sort-Object versionNo -Descending | Select-Object -First 1)
  $currentExact = $current.Count -eq 1 -and
    "$(Get-YinuoProperty -Object $current[0] -Names @('formConf'))" -ceq $Spec.formConf -and
    (Test-TurixArrayEqual -Left @($current[0].formFields) -Right @($Spec.formFields)) -and
    "$(Get-YinuoProperty -Object $current[0] -Names @('processDefinitionKey'))" -ceq $ProcessDefinitionKey -and
    "$(Get-YinuoProperty -Object $current[0] -Names @('remark'))" -ceq $Spec.remark
  if ($currentExact) {
    $versionId = "$(Get-YinuoProperty -Object $current[0] -Names @('id'))"
    $null = Add-YinuoCheck -Condition $true -Id "TYPE_$($Spec.key)_REUSE" -Message "reused published contract type '$($Spec.name)'" -Details "typeId=$typeId, versionId=$versionId"
    return [pscustomobject]@{ id = [int64]$typeId; versionId = [int64]$versionId }
  }

  $drafts = @($versions | Where-Object { $_.status -eq 0 })
  if ($drafts.Count -gt 1) { throw "multiple drafts exist for contract type '$($Spec.code)'" }
  if ($drafts.Count -eq 1) {
    $draftId = "$(Get-YinuoProperty -Object $drafts[0] -Names @('id'))"
  } else {
    $draftResponse = Invoke-YinuoApi -Headers $Headers -Method POST -Path "/clm/contract-type/version/create-draft?typeId=$([Uri]::EscapeDataString($typeId))" -Body $null
    $null = Assert-TurixApi -Response $draftResponse -What "create draft for contract type '$($Spec.code)'"
    $draftId = "$(Get-YinuoId $draftResponse.data)"
  }
  $update = Invoke-YinuoApi -Headers $Headers -Method PUT -Path '/clm/contract-type/version/update' -Body @{
    id = [int64]$draftId; formConf = $Spec.formConf; formFields = @($Spec.formFields)
    processDefinitionKey = $ProcessDefinitionKey; remark = $Spec.remark
  }
  $null = Assert-TurixApi -Response $update -What "update draft for contract type '$($Spec.code)'"
  $publish = Invoke-YinuoApi -Headers $Headers -Method POST -Path "/clm/contract-type/version/publish?id=$([Uri]::EscapeDataString($draftId))" -Body $null
  $null = Assert-TurixApi -Response $publish -What "publish contract type '$($Spec.code)'"
  $null = Add-YinuoCheck -Condition $true -Id "TYPE_$($Spec.key)_PUBLISH" -Message "published contract type '$($Spec.name)'" -Details "typeId=$typeId, versionId=$draftId"
  return [pscustomobject]@{ id = [int64]$typeId; versionId = [int64]$draftId }
}

function Ensure-TurixTemplate {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][hashtable]$Spec,
    [Parameter(Mandatory = $true)][long]$ContractTypeId,
    [Parameter(Mandatory = $true)][string]$FilePath
  )
  $localChecksum = (Get-FileHash -Algorithm SHA256 -Path $FilePath).Hash.ToLowerInvariant()
  $templates = @(Get-YinuoAllPages -Headers $Headers -Path '/clm/governance/template/page')
  $matches = @($templates | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('code'))" -ceq $Spec.code })
  if ($matches.Count -gt 1) { throw "duplicate template code '$($Spec.code)'" }
  $templateId = ''
  $draftVersionId = ''
  if ($matches.Count -eq 1) {
    $templateId = "$(Get-YinuoProperty -Object $matches[0] -Names @('id'))"
    $detailResponse = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/governance/template/get?id=$([Uri]::EscapeDataString($templateId))" -Body $null
    $null = Assert-TurixApi -Response $detailResponse -What "get template '$($Spec.code)'"
    $detail = $detailResponse.data
    if (-not (Test-TurixNullableIdEqual $detail.contractTypeId $ContractTypeId)) {
      throw "template '$($Spec.code)' belongs to contract type $($detail.contractTypeId), expected $ContractTypeId"
    }
    $currentId = "$(Get-YinuoProperty -Object $detail -Names @('currentVersionId'))"
    $current = @($detail.versions | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('id'))" -eq $currentId })
    $metadataExact = "$(Get-YinuoProperty -Object $detail -Names @('name'))" -ceq $Spec.name -and
      "$(Get-YinuoProperty -Object $detail -Names @('description'))" -ceq $Spec.description
    $fileExact = $current.Count -eq 1 -and
      "$(Get-YinuoProperty -Object $current[0] -Names @('status'))" -ceq 'PUBLISHED' -and
      "$(Get-YinuoProperty -Object $current[0] -Names @('checksumSha256'))".ToLowerInvariant() -ceq $localChecksum -and
      "$(Get-YinuoProperty -Object $current[0] -Names @('remark'))" -ceq $Spec.remark
    if ($metadataExact -and $fileExact) {
      $null = Add-YinuoCheck -Condition $true -Id "TEMPLATE_$($Spec.key)_REUSE" -Message "reused published independent template '$($Spec.name)'" -Details "templateId=$templateId, versionId=$currentId, sha256=$localChecksum"
      return [pscustomobject]@{ id = [int64]$templateId; versionId = [int64]$currentId; checksum = $localChecksum }
    }
    $drafts = @($detail.versions | Where-Object { "$(Get-YinuoProperty -Object $_ -Names @('status'))" -ceq 'DRAFT' })
    if ($drafts.Count -gt 1) { throw "multiple drafts exist for template '$($Spec.code)'" }
    if ($drafts.Count -eq 1) { $draftVersionId = "$(Get-YinuoProperty -Object $drafts[0] -Names @('id'))" }
  }

  $form = @{
    code = $Spec.code; name = $Spec.name; contractTypeId = "$ContractTypeId"
    description = $Spec.description; remark = $Spec.remark; file = Get-Item $FilePath
  }
  if ($templateId) { $form.templateId = $templateId }
  if ($draftVersionId) { $form.versionId = $draftVersionId }
  $save = Invoke-YinuoMultipartApi -Headers $Headers -Method POST -Path '/clm/governance/template/save-draft' -Form $form
  $null = Assert-TurixApi -Response $save -What "save template '$($Spec.code)'"
  $versionId = "$(Get-YinuoId $save.data)"
  if (-not $versionId) { throw "template save returned no version id for '$($Spec.code)'" }
  $publish = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/governance/template/publish?id=$([Uri]::EscapeDataString($versionId))" -Body $null
  $null = Assert-TurixApi -Response $publish -What "publish template '$($Spec.code)'"
  $null = Add-YinuoCheck -Condition $true -Id "TEMPLATE_$($Spec.key)_PUBLISH" -Message "published independent template '$($Spec.name)'" -Details "versionId=$versionId, sha256=$localChecksum"
  $templateId = if ($templateId) { $templateId } else {
    $refresh = @(Get-YinuoAllPages -Headers $Headers -Path '/clm/governance/template/page') | Where-Object { $_.code -ceq $Spec.code }
    "$(Get-YinuoProperty -Object @($refresh)[0] -Names @('id'))"
  }
  return [pscustomobject]@{ id = [int64]$templateId; versionId = [int64]$versionId; checksum = $localChecksum }
}

function Ensure-TurixNumberingRule {
  param([Parameter(Mandatory = $true)][hashtable]$Headers)
  $desired = [ordered]@{
    ruleCode = 'TURIX-DEMO-DEFAULT'; contractTypeId = $null; prefix = 'HT'
    datePattern = 'yyyy'; separator = '-'; sequenceLength = 4
    resetPeriod = 'YEAR'; includePartyShortName = $true
  }
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path '/clm/governance/numbering/page?pageNo=1&pageSize=100' -Body $null
  $null = Assert-TurixApi -Response $response -What 'list numbering rules'
  $items = @(Get-YinuoItems $response.data)
  $same = @($items | Where-Object {
    $_.ruleCode -ceq $desired.ruleCode -and $_.status -ceq 'PUBLISHED' -and
    (Test-TurixNullableIdEqual $_.contractTypeId $null) -and $_.prefix -ceq $desired.prefix -and
    $_.datePattern -ceq $desired.datePattern -and $_.separator -ceq $desired.separator -and
    $_.sequenceLength -eq $desired.sequenceLength -and $_.resetPeriod -ceq $desired.resetPeriod -and
    $_.includePartyShortName -eq $true
  })
  if ($same.Count -eq 1) {
    $id = "$(Get-YinuoProperty -Object $same[0] -Names @('id'))"
    $null = Add-YinuoCheck -Condition $true -Id 'NUMBERING_REUSE' -Message 'reused the single published TuriX numbering rule' -Details "id=$id"
    return [int64]$id
  }
  if ($same.Count -gt 1) { throw 'duplicate exact TuriX numbering rules are published' }
  $foreignRows = @($items | Where-Object { $_.ruleCode -cne $desired.ruleCode })
  if ($foreignRows.Count -gt 0) { throw 'non-demo numbering configuration already exists; refusing to replace it because the TuriX baseline requires exactly one rule family' }
  $drafts = @($items | Where-Object { $_.ruleCode -ceq $desired.ruleCode -and $_.status -ceq 'DRAFT' })
  if ($drafts.Count -gt 1) { throw 'multiple TuriX numbering drafts exist' }
  $body = @{} + $desired
  if ($drafts.Count -eq 1) { $body.id = [int64](Get-YinuoProperty -Object $drafts[0] -Names @('id')) }
  $save = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/governance/numbering/save-draft' -Body $body
  $null = Assert-TurixApi -Response $save -What 'save TuriX numbering rule'
  $id = "$(Get-YinuoId $save.data)"
  $publish = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/governance/numbering/publish?id=$([Uri]::EscapeDataString($id))" -Body $null
  $null = Assert-TurixApi -Response $publish -What 'publish TuriX numbering rule'
  $null = Add-YinuoCheck -Condition $true -Id 'NUMBERING_PUBLISH' -Message 'published the single TuriX numbering rule' -Details "id=$id"
  return [int64]$id
}

function Ensure-TurixRoutingRule {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$ProcessDefinitionKey
  )
  $desired = [ordered]@{
    ruleCode = 'TURIX-DEMO-DEFAULT'; name = 'TuriX 默认业务单据流程配置'
    contractTypeId = $null; priority = 100; condition = @{}
    processDefinitionKey = $ProcessDefinitionKey
  }
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path '/clm/governance/routing/page?pageNo=1&pageSize=100' -Body $null
  $null = Assert-TurixApi -Response $response -What 'list routing rules'
  $items = @(Get-YinuoItems $response.data)
  $same = @($items | Where-Object {
    $_.ruleCode -ceq $desired.ruleCode -and $_.status -ceq 'PUBLISHED' -and
    $_.name -ceq $desired.name -and (Test-TurixNullableIdEqual $_.contractTypeId $null) -and
    $_.priority -eq $desired.priority -and $_.processDefinitionKey -ceq $ProcessDefinitionKey -and
    @($_.condition.PSObject.Properties).Count -eq 0
  })
  if ($same.Count -eq 1) {
    $id = "$(Get-YinuoProperty -Object $same[0] -Names @('id'))"
    $null = Add-YinuoCheck -Condition $true -Id 'ROUTING_REUSE' -Message 'reused the single published TuriX document-workflow configuration' -Details "id=$id"
    return [int64]$id
  }
  if ($same.Count -gt 1) { throw 'duplicate exact TuriX routing rules are published' }
  $foreignRows = @($items | Where-Object { $_.ruleCode -cne $desired.ruleCode })
  if ($foreignRows.Count -gt 0) { throw 'non-demo routing configuration already exists; refusing to replace it because the TuriX baseline requires exactly one route family' }
  $drafts = @($items | Where-Object { $_.ruleCode -ceq $desired.ruleCode -and $_.status -ceq 'DRAFT' })
  if ($drafts.Count -gt 1) { throw 'multiple TuriX routing drafts exist' }
  $body = @{} + $desired
  if ($drafts.Count -eq 1) { $body.id = [int64](Get-YinuoProperty -Object $drafts[0] -Names @('id')) }
  $save = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/governance/routing/save-draft' -Body $body
  $null = Assert-TurixApi -Response $save -What 'save TuriX routing rule'
  $id = "$(Get-YinuoId $save.data)"
  $publish = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/governance/routing/publish?id=$([Uri]::EscapeDataString($id))" -Body $null
  $null = Assert-TurixApi -Response $publish -What 'publish TuriX routing rule'
  $null = Add-YinuoCheck -Condition $true -Id 'ROUTING_PUBLISH' -Message 'published the single TuriX document-workflow configuration' -Details "id=$id"
  return [int64]$id
}

function Normalize-TurixDemoRoutingDisplayName {
  param(
    [Parameter(Mandatory = $true)][object]$Migration,
    [Parameter(Mandatory = $true)][ValidatePattern('^[1-9][0-9]*$')][string]$TenantId,
    [Parameter(Mandatory = $true)][string]$TenantName
  )
  if ($TenantId -ne '1' -or $TenantName -cne 'TuriX') {
    throw "demo routing display-name normalization is hard-bounded to tenant id=1 named TuriX"
  }
  $sql = @"
UPDATE clm_routing_rule_version r
JOIN system_tenant t ON t.id=r.tenant_id AND t.deleted=b'0'
SET r.name='TuriX 默认业务单据流程配置', r.update_time=r.update_time
WHERE r.tenant_id=1 AND t.name='TuriX'
  AND r.rule_code='TURIX-DEMO-DEFAULT' AND r.deleted=b'0'
  AND r.name IN ('TuriX 默认审批路由', 'TuriX 默认合同审批路由');
SELECT CONCAT(ROW_COUNT(), '|', (
  SELECT COUNT(*) FROM clm_routing_rule_version r
  JOIN system_tenant t ON t.id=r.tenant_id AND t.deleted=b'0'
  WHERE r.tenant_id=1 AND t.name='TuriX'
    AND r.rule_code='TURIX-DEMO-DEFAULT' AND r.deleted=b'0'
    AND r.name IN ('TuriX 默认审批路由', 'TuriX 默认合同审批路由')
));
"@
  $result = @(Invoke-YinuoMysql -Runner $Migration.runner -Sql $sql -SkipHeaders)
  if ($result.Count -ne 1 -or "$($result[0])" -notmatch '^(\d+)\|(\d+)$') {
    throw "unexpected demo routing display-name normalization result: $($result -join ',')"
  }
  $normalized = [int]$Matches[1]
  $legacyRemaining = [int]$Matches[2]
  if ($legacyRemaining -ne 0) {
    throw "legacy TuriX demo routing display names remain after normalization: $legacyRemaining"
  }
  return $normalized
}

function Ensure-TurixPublishedPolicy {
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
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path '/clm/permission/policy/list' -Body $null
  $null = Assert-TurixApi -Response $response -What 'list permission policies'
  $policies = @(Get-YinuoItems $response.data)
  $exact = @($policies | Where-Object {
    $_.status -ceq 'PUBLISHED' -and $_.remark -ceq $script:TurixDemoPolicyRemark -and
    $_.roleCapabilitiesJson -ceq $capabilitiesJson -and $_.scopeLimitsJson -ceq $scopeLimitsJson -and
    $_.nodeEditPolicyJson -ceq $nodePolicyJson
  })
  if ($exact.Count -eq 1) {
    $id = "$(Get-YinuoProperty -Object $exact[0] -Names @('id'))"
    $null = Add-YinuoCheck -Condition $true -Id 'POLICY_REUSE' -Message 'reused the published TuriX four-role permission policy' -Details "id=$id"
    return [int64]$id
  }
  if ($exact.Count -gt 1) { throw 'duplicate published exact TuriX permission policies exist' }
  $foreignPublished = @($policies | Where-Object { $_.status -ceq 'PUBLISHED' -and $_.remark -cne $script:TurixDemoPolicyRemark })
  if ($foreignPublished.Count -gt 0) { throw 'a non-demo permission policy is published; refusing to deactivate administrator work' }
  $foreignDrafts = @($policies | Where-Object { $_.status -ceq 'DRAFT' -and $_.remark -cne $script:TurixDemoPolicyRemark })
  $demoDrafts = @($policies | Where-Object { $_.status -ceq 'DRAFT' -and $_.remark -ceq $script:TurixDemoPolicyRemark })
  if ($demoDrafts.Count -gt 1) { throw 'multiple TuriX permission-policy drafts exist' }
  if ($demoDrafts.Count -eq 0 -and $foreignDrafts.Count -gt 0) { throw 'a non-demo permission-policy draft exists; refusing to overwrite it' }
  $body = @{
    roleCapabilitiesJson = $capabilitiesJson; scopeLimitsJson = $scopeLimitsJson
    nodeEditPolicyJson = $nodePolicyJson; remark = $script:TurixDemoPolicyRemark
  }
  if ($demoDrafts.Count -eq 1) { $body.id = [int64](Get-YinuoProperty -Object $demoDrafts[0] -Names @('id')) }
  $save = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/permission/policy/save' -Body $body
  $null = Assert-TurixApi -Response $save -What 'save TuriX permission policy'
  $id = "$(Get-YinuoId $save.data)"
  $publish = Invoke-YinuoApi -Headers $Headers -Method PUT -Path "/clm/permission/policy/publish?id=$([Uri]::EscapeDataString($id))" -Body $null
  $null = Assert-TurixApi -Response $publish -What 'publish TuriX permission policy'
  $null = Add-YinuoCheck -Condition $true -Id 'POLICY_PUBLISH' -Message 'published the TuriX four-role permission policy' -Details "id=$id"
  return [int64]$id
}

function Ensure-TurixUserScope {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][long]$UserId,
    [Parameter(Mandatory = $true)][string]$RoleCode,
    [Parameter(Mandatory = $true)][long]$PolicyVersionId,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$OrgIds,
    [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$ContractTypeIds
  )
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/permission/user-scope/list?userId=$UserId" -Body $null
  $null = Assert-TurixApi -Response $response -What "list $RoleCode scope for user $UserId"
  $otherActive = @((Get-YinuoItems $response.data) | Where-Object { $_.status -ceq 'ACTIVE' -and $_.roleCode -cne $RoleCode })
  if ($otherActive.Count -gt 0) {
    throw "user $UserId already has another ACTIVE CLM role scope; refusing to broaden or silently replace fixed role identity"
  }
  $matches = @((Get-YinuoItems $response.data) | Where-Object { $_.roleCode -ceq $RoleCode })
  if ($matches.Count -gt 1) { throw "duplicate $RoleCode scopes exist for user $UserId" }
  $same = $false
  if ($matches.Count -eq 1) {
    $actualOrg = @($matches[0].orgScopeJson | ConvertFrom-Json)
    $actualType = @($matches[0].typeScopeJson | ConvertFrom-Json)
    $same = $matches[0].status -ceq 'ACTIVE' -and
      "$(Get-YinuoProperty -Object $matches[0] -Names @('policyVersionId'))" -eq "$PolicyVersionId" -and
      $null -eq (Get-YinuoProperty -Object $matches[0] -Names @('effectiveFrom')) -and
      $null -eq (Get-YinuoProperty -Object $matches[0] -Names @('effectiveTo')) -and
      (Test-TurixSetEqual $actualOrg $OrgIds) -and (Test-TurixSetEqual $actualType $ContractTypeIds)
  }
  if (-not $same) {
    $save = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/permission/user-scope/save' -Body @{
      userId = $UserId; roleCode = $RoleCode; policyVersionId = $PolicyVersionId
      orgIds = @($OrgIds | ForEach-Object { [int64]$_ })
      contractTypeIds = @($ContractTypeIds | ForEach-Object { [int64]$_ })
      effectiveFrom = $null; effectiveTo = $null; status = 'ACTIVE'
    }
    $null = Assert-TurixApi -Response $save -What "save $RoleCode scope for user $UserId"
  }
  $verify = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/permission/user-scope/list?userId=$UserId" -Body $null
  $null = Assert-TurixApi -Response $verify -What "verify $RoleCode scope for user $UserId"
  $allActive = @((Get-YinuoItems $verify.data) | Where-Object { $_.status -ceq 'ACTIVE' })
  $verified = @($allActive | Where-Object { $_.roleCode -ceq $RoleCode })
  if ($allActive.Count -ne 1 -or $verified.Count -ne 1 -or
      "$(Get-YinuoProperty -Object $verified[0] -Names @('policyVersionId'))" -ne "$PolicyVersionId" -or
      $null -ne (Get-YinuoProperty -Object $verified[0] -Names @('effectiveFrom')) -or
      $null -ne (Get-YinuoProperty -Object $verified[0] -Names @('effectiveTo')) -or
      -not (Test-TurixSetEqual @($verified[0].orgScopeJson | ConvertFrom-Json) $OrgIds) -or
      -not (Test-TurixSetEqual @($verified[0].typeScopeJson | ConvertFrom-Json) $ContractTypeIds)) {
    throw "exact single-scope verification failed for $RoleCode user $UserId (activeScopes=$($allActive.Count))"
  }
  $id = "$(Get-YinuoProperty -Object $verified[0] -Names @('id'))"
  $null = Add-YinuoCheck -Condition $true -Id "SCOPE_$UserId" -Message "verified exact $RoleCode scope" -Details "scopeId=$id, org=[$($OrgIds -join ',')], types=[$($ContractTypeIds -join ',')]"
  return [int64]$id
}

function Ensure-TurixParty {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][hashtable]$Spec
  )
  $items = @(Get-YinuoAllPages -Headers $Headers -Path '/clm/party/page')
  $matches = @($items | Where-Object {
    $_.name -ceq $Spec.name -or $_.unifiedCreditCode -ceq $Spec.code
  })
  if ($matches.Count -gt 1) { throw "party identity collision for '$($Spec.name)' / '$($Spec.code)'" }
  if ($matches.Count -eq 1) {
    $party = $matches[0]
    $same = $party.name -ceq $Spec.name -and $party.unifiedCreditCode -ceq $Spec.code -and
      $party.internalFlag -eq $Spec.internal -and $party.partyType -eq 1 -and
      "$(Get-YinuoProperty -Object $party -Names @('shortName'))" -ceq $Spec.shortName -and
      "$(Get-YinuoProperty -Object $party -Names @('contactName'))" -ceq $Spec.contactName -and
      "$(Get-YinuoProperty -Object $party -Names @('contactPhone'))" -ceq $Spec.contactPhone -and
      "$(Get-YinuoProperty -Object $party -Names @('address'))" -ceq $Spec.address -and
      $party.status -eq 0
    if (-not $same) { throw "party '$($Spec.code)' exists with non-seed metadata; use a different fixed demo identity or clean it explicitly" }
    $id = "$(Get-YinuoProperty -Object $party -Names @('id'))"
    $null = Add-YinuoCheck -Condition $true -Id "PARTY_$($Spec.key)_REUSE" -Message "reused fictional party '$($Spec.name)'" -Details "id=$id"
    return [int64]$id
  }
  $create = Invoke-YinuoApi -Headers $Headers -Method POST -Path '/clm/party/create' -Body @{
    partyType = 1; name = $Spec.name; shortName = $Spec.shortName
    unifiedCreditCode = $Spec.code; internalFlag = $Spec.internal
    contactName = $Spec.contactName; contactPhone = $Spec.contactPhone
    address = $Spec.address; status = 0; remark = 'TuriX 固定虚构演示主体'
  }
  $null = Assert-TurixApi -Response $create -What "create party '$($Spec.name)'"
  $id = "$(Get-YinuoId $create.data)"
  if (-not $id) { throw "party create returned no id for '$($Spec.name)'" }
  $null = Add-YinuoCheck -Condition $true -Id "PARTY_$($Spec.key)_CREATE" -Message "created fictional party '$($Spec.name)'" -Details "id=$id"
  return [int64]$id
}

function Get-TurixContractDetail {
  param([Parameter(Mandatory = $true)][hashtable]$Headers, [Parameter(Mandatory = $true)][long]$ContractId)
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path "/clm/contract/get?id=$ContractId" -Body $null
  $null = Assert-TurixApi -Response $response -What "get contract $ContractId"
  return $response.data
}

function Get-TurixApprovalWorkItem {
  param([Parameter(Mandatory = $true)][hashtable]$Headers, [Parameter(Mandatory = $true)][long]$ContractId)
  $response = Invoke-YinuoApi -Headers $Headers -Method GET -Path '/clm/workbench/items?type=APPROVAL&pageNo=1&pageSize=100' -Body $null
  if (-not $response.ok) { return $null }
  return @((Get-YinuoItems $response.data) | Where-Object {
    "$(Get-YinuoProperty -Object $_ -Names @('contractId'))" -eq "$ContractId" -and
    [bool](Get-YinuoProperty -Object $_ -Names @('taskId'))
  } | Select-Object -First 1)
}

function Wait-TurixApprovalWorkItem {
  param([Parameter(Mandatory = $true)][hashtable]$Headers, [Parameter(Mandatory = $true)][long]$ContractId)
  return Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe {
    Get-TurixApprovalWorkItem -Headers $Headers -ContractId $ContractId
  }
}

function Test-TurixContractState {
  param(
    [Parameter(Mandatory = $true)][hashtable]$BusinessHeaders,
    [Parameter(Mandatory = $true)][hashtable]$LegalHeaders,
    [Parameter(Mandatory = $true)][hashtable]$ContractAdminHeaders,
    [Parameter(Mandatory = $true)][long]$ContractId,
    [Parameter(Mandatory = $true)][string]$TargetState
  )
  $detail = Get-TurixContractDetail -Headers $BusinessHeaders -ContractId $ContractId
  $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))".ToUpperInvariant()
  $approval = Get-YinuoProperty -Object $detail -Names @('approvalStatus')
  switch ($TargetState) {
    'DRAFT' { return $stage -eq 'DRAFT' -and $approval -eq 0 }
    'COLLABORATING' { return $stage -eq 'COLLABORATING' -and $approval -eq 0 }
    'PENDING_LEGAL' {
      if ($approval -ne 1 -or $stage -notin @('APPROVING','IN_APPROVAL')) { return $false }
      return $null -ne (Get-TurixApprovalWorkItem -Headers $LegalHeaders -ContractId $ContractId)
    }
    'PENDING_CONTRACT_ADMIN' {
      if ($approval -ne 1 -or $stage -notin @('APPROVING','IN_APPROVAL')) { return $false }
      return $null -ne (Get-TurixApprovalWorkItem -Headers $ContractAdminHeaders -ContractId $ContractId)
    }
    'REJECTED' { return $approval -eq 3 -and $stage -notmatch 'SIGN|ARCHIV|PERFORM|EFFECTIVE|CLOSED' }
    'APPROVED' { return $approval -eq 2 -and $stage -eq 'APPROVED' }
    default { throw "unknown target state '$TargetState'" }
  }
}

function Get-TurixApprovalRequestId {
  param(
    [Parameter(Mandatory = $true)][string]$SpecKey,
    [Parameter(Mandatory = $true)][ValidateSet('legal','reject','admin')][string]$Action,
    [Parameter(Mandatory = $true)][long]$ContractId
  )
  $requestId = "turix-demo-$SpecKey-$Action-$ContractId"
  if ($requestId.Length -gt 64) { throw "approval request id exceeds the schema limit: $requestId" }
  return $requestId
}

function Move-TurixApprovalToTarget {
  param(
    [Parameter(Mandatory = $true)][hashtable]$BusinessHeaders,
    [Parameter(Mandatory = $true)][hashtable]$LegalHeaders,
    [Parameter(Mandatory = $true)][hashtable]$ContractAdminHeaders,
    [Parameter(Mandatory = $true)][hashtable]$Spec,
    [Parameter(Mandatory = $true)][long]$ContractId
  )
  if ($Spec.state -notin @('PENDING_CONTRACT_ADMIN','REJECTED','APPROVED')) { return }

  $pendingLegal = Test-TurixContractState -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -ContractId $ContractId -TargetState 'PENDING_LEGAL'
  if ($pendingLegal) {
    $legalTask = Wait-TurixApprovalWorkItem -Headers $LegalHeaders -ContractId $ContractId
    $legalTaskId = "$(Get-YinuoProperty -Object $legalTask -Names @('taskId'))"
    $legalRevisionId = "$(Get-YinuoProperty -Object $legalTask -Names @('revisionId'))"
    if (-not $legalTaskId -or -not $legalRevisionId) { throw "legal approval work item was not projected for '$($Spec.name)'" }
    if ($Spec.state -eq 'REJECTED') {
      $decision = Invoke-YinuoApi -Headers $LegalHeaders -Method PUT -Path '/clm/approval/task/reject' -Body @{
        taskId = $legalTaskId; revisionId = [int64]$legalRevisionId
        reason = '演示退回：付款节点与验收责任需进一步明确。'
        requestId = Get-TurixApprovalRequestId -SpecKey $Spec.key -Action 'reject' -ContractId $ContractId
      }
      $null = Assert-TurixApi -Response $decision -What "reject '$($Spec.name)'"
      return
    }
    $decision = Invoke-YinuoApi -Headers $LegalHeaders -Method PUT -Path '/clm/approval/task/approve' -Body @{
      taskId = $legalTaskId; revisionId = [int64]$legalRevisionId
      reason = '演示法务审批通过'
      requestId = Get-TurixApprovalRequestId -SpecKey $Spec.key -Action 'legal' -ContractId $ContractId
    }
    $null = Assert-TurixApi -Response $decision -What "legal approve '$($Spec.name)'"
  }

  if ($Spec.state -eq 'APPROVED') {
    $adminTask = Wait-TurixApprovalWorkItem -Headers $ContractAdminHeaders -ContractId $ContractId
    $adminTaskId = "$(Get-YinuoProperty -Object $adminTask -Names @('taskId'))"
    $adminRevisionId = "$(Get-YinuoProperty -Object $adminTask -Names @('revisionId'))"
    if (-not $adminTaskId -or -not $adminRevisionId) { throw "contract-admin approval work item was not projected for '$($Spec.name)'" }
    $decision = Invoke-YinuoApi -Headers $ContractAdminHeaders -Method PUT -Path '/clm/approval/task/approve' -Body @{
      taskId = $adminTaskId; revisionId = [int64]$adminRevisionId
      reason = '演示合同管理员复核通过'
      requestId = Get-TurixApprovalRequestId -SpecKey $Spec.key -Action 'admin' -ContractId $ContractId
    }
    $null = Assert-TurixApi -Response $decision -What "contract-admin approve '$($Spec.name)'"
  }
}

function Ensure-TurixDemoContract {
  param(
    [Parameter(Mandatory = $true)][hashtable]$BusinessHeaders,
    [Parameter(Mandatory = $true)][hashtable]$LegalHeaders,
    [Parameter(Mandatory = $true)][hashtable]$ContractAdminHeaders,
    [Parameter(Mandatory = $true)][hashtable]$Spec,
    [Parameter(Mandatory = $true)][long]$ContractTypeId,
    [Parameter(Mandatory = $true)][long]$ContractTypeVersionId,
    [Parameter(Mandatory = $true)][long]$TemplateVersionId,
    [Parameter(Mandatory = $true)][string]$TemplateChecksum,
    [Parameter(Mandatory = $true)][long]$OurPartyId,
    [Parameter(Mandatory = $true)][long]$CounterpartyId
  )
  if (-not $Spec.name.StartsWith($script:TurixDemoPrefix, [StringComparison]::Ordinal)) {
    throw "demo contract name must start with '$script:TurixDemoPrefix': $($Spec.name)"
  }
  $items = @(Get-YinuoAllPages -Headers $BusinessHeaders -Path "/clm/contract/page?title=$([Uri]::EscapeDataString($Spec.name))")
  $matches = @($items | Where-Object { "$(Get-YinuoContractName $_)" -ceq $Spec.name })
  if ($matches.Count -gt 1) { throw "duplicate demo contract '$($Spec.name)'" }
  if ($matches.Count -eq 1) {
    $contractId = [int64](Get-YinuoProperty -Object $matches[0] -Names @('id','contractId'))
    $detail = Get-TurixContractDetail -Headers $BusinessHeaders -ContractId $contractId
    $revision = Get-YinuoCurrentRevision -Headers $BusinessHeaders -ContractId $contractId
    $ourParties = @($detail.parties | Where-Object {
      $_.roleCode -ceq 'OUR_SIDE' -and "$(Get-YinuoProperty -Object $_ -Names @('partyId'))" -eq "$OurPartyId"
    })
    $counterparties = @($detail.parties | Where-Object {
      $_.roleCode -ceq 'COUNTERPARTY' -and "$(Get-YinuoProperty -Object $_ -Names @('partyId'))" -eq "$CounterpartyId"
    })
    $amountExact = $false
    try { $amountExact = [decimal]$detail.amount -eq [decimal]$Spec.amount } catch { $amountExact = $false }
    $contentExact =
      "$(Get-YinuoProperty -Object $detail -Names @('typeId'))" -eq "$ContractTypeId" -and
      "$(Get-YinuoProperty -Object $detail -Names @('typeVersionId'))" -eq "$ContractTypeVersionId" -and
      "$(Get-YinuoProperty -Object $detail -Names @('ownerUserId'))" -eq '200001' -and
      "$(Get-YinuoProperty -Object $detail -Names @('ownerDeptId'))" -eq '101' -and
      $amountExact -and "$(Get-YinuoProperty -Object $detail -Names @('currency'))" -ceq 'CNY' -and
      "$(Get-YinuoProperty -Object $detail -Names @('effectiveDate'))" -ceq $Spec.startDate -and
      "$(Get-YinuoProperty -Object $detail -Names @('expiryDate'))" -ceq $Spec.endDate -and
      "$(Get-YinuoProperty -Object $detail -Names @('description'))" -ceq $Spec.description -and
      (Get-TurixCanonicalJson $detail.customData) -ceq (Get-TurixCanonicalJson $Spec.customData) -and
      "$(Get-YinuoProperty -Object $detail -Names @('sourceMode'))" -ceq 'TEMPLATE' -and
      $detail.noCommitmentConfirmed -eq $true -and
      @($detail.parties).Count -eq 2 -and $ourParties.Count -eq 1 -and $counterparties.Count -eq 1 -and
      "$(Get-YinuoProperty -Object $detail.currentDocumentVersion -Names @('checksumSha256'))".ToLowerInvariant() -ceq $TemplateChecksum.ToLowerInvariant() -and
      "$(Get-YinuoProperty -Object $revision -Names @('contractTypeVersionId'))" -eq "$ContractTypeVersionId" -and
      "$(Get-YinuoProperty -Object $revision -Names @('templateVersionId'))" -eq "$TemplateVersionId" -and
      $revision.noCommitment -eq $true
    if (-not $contentExact) {
      throw "existing demo contract '$($Spec.name)' has deterministic field, party, owner, type, or document drift; rerun with -ResetData instead of silently reusing it"
    }
    $stateOk = Test-TurixContractState -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -ContractId $contractId -TargetState $Spec.state
    if (-not $stateOk) {
      $pendingLegal = Test-TurixContractState -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -ContractId $contractId -TargetState 'PENDING_LEGAL'
      $pendingAdmin = Test-TurixContractState -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -ContractId $contractId -TargetState 'PENDING_CONTRACT_ADMIN'
      $canResume = ($Spec.state -in @('PENDING_CONTRACT_ADMIN','REJECTED','APPROVED') -and $pendingLegal) -or
        ($Spec.state -eq 'APPROVED' -and $pendingAdmin)
      if (-not $canResume) {
        throw "existing demo contract '$($Spec.name)' is not in expected state '$($Spec.state)' or a deterministic predecessor; rerun with -ResetData instead of silently changing it"
      }
      Move-TurixApprovalToTarget -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -Spec $Spec -ContractId $contractId
      $stateOk = $null -ne (Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe {
        if (Test-TurixContractState -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -ContractId $contractId -TargetState $Spec.state) { return $true }
        return $null
      })
      if (-not $stateOk) { throw "resumed demo contract '$($Spec.name)' did not reach '$($Spec.state)'" }
      $null = Add-YinuoCheck -Condition $true -Id "CONTRACT_$($Spec.key)_RESUME" -Message "resumed deterministic sample to $($Spec.state) '$($Spec.name)'" -Details "id=$contractId"
      return $contractId
    }
    $null = Add-YinuoCheck -Condition $true -Id "CONTRACT_$($Spec.key)_REUSE" -Message "reused deterministic $($Spec.state) sample '$($Spec.name)'" -Details "id=$contractId"
    return $contractId
  }
  $deletedResponse = Invoke-YinuoApi -Headers $BusinessHeaders -Method GET -Path "/clm/contract/page?deletedOnly=true&title=$([Uri]::EscapeDataString($Spec.name))&pageNo=1&pageSize=100" -Body $null
  if ($deletedResponse.ok) {
    $deleted = @((Get-YinuoItems $deletedResponse.data) | Where-Object { "$(Get-YinuoContractName $_)" -ceq $Spec.name })
    if ($deleted.Count -gt 0) { throw "a deleted copy of '$($Spec.name)' exists; use -ResetData for a deterministic rebuild" }
  }

  $create = Invoke-YinuoApi -Headers $BusinessHeaders -Method POST -Path '/clm/contract/create-from-template' -Body @{
    templateVersionId = $TemplateVersionId; name = $Spec.name; ownerUserId = 200001
  }
  $null = Assert-TurixApi -Response $create -What "create demo contract '$($Spec.name)'"
  $contractId = "$(Get-YinuoId $create.data)"
  if (-not $contractId) { throw "create-from-template returned no contract id for '$($Spec.name)'" }
  $initialRevision = Get-YinuoCurrentRevision -Headers $BusinessHeaders -ContractId $contractId
  $initialRevisionId = "$(Get-YinuoProperty -Object $initialRevision -Names @('id','revisionId'))"
  $save = Invoke-YinuoApi -Headers $BusinessHeaders -Method PUT -Path '/clm/contract/revision/save' -Body @{
    contractId = [int64]$contractId; baseRevisionId = [int64]$initialRevisionId
    name = $Spec.name; amount = $Spec.amount; currency = 'CNY'
    startDate = $Spec.startDate; endDate = $Spec.endDate
    ourPartyId = $OurPartyId; counterpartyIds = @($CounterpartyId)
    customData = $Spec.customData; description = $Spec.description
    changeReason = 'TuriX 固定一期演示数据'
  }
  $null = Assert-TurixApi -Response $save -What "save revision for '$($Spec.name)'"
  $confirm = Invoke-YinuoApi -Headers $BusinessHeaders -Method PUT -Path "/clm/contract/commitment/confirm-none?contractId=$contractId" -Body $null
  $null = Assert-TurixApi -Response $confirm -What "confirm no major commitment for '$($Spec.name)'"
  $revision = Get-YinuoCurrentRevision -Headers $BusinessHeaders -ContractId $contractId
  $revisionId = "$(Get-YinuoProperty -Object $revision -Names @('id','revisionId'))"

  if ($Spec.state -eq 'COLLABORATING') {
    $start = Invoke-YinuoApi -Headers $BusinessHeaders -Method POST -Path '/clm/collaboration/start' -Body @{
      contractId = [int64]$contractId; revisionId = [int64]$revisionId; legalUserId = 200002
      reason = '演示法务协同：请核对服务范围、验收标准与责任边界。'
    }
    $null = Assert-TurixApi -Response $start -What "start collaboration for '$($Spec.name)'"
  } elseif ($Spec.state -ne 'DRAFT') {
    $submit = Invoke-YinuoApi -Headers $BusinessHeaders -Method POST -Path '/clm/contract/submit' -Body @{
      id = [int64]$contractId; baseRevisionId = [int64]$revisionId
      submitRequestId = "turix-demo-$($Spec.key)-submit-$revisionId"
      remark = 'TuriX 一期演示审批'
    }
    $null = Assert-TurixApi -Response $submit -What "submit '$($Spec.name)'"
    Move-TurixApprovalToTarget -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -Spec $Spec -ContractId $contractId
  }

  $state = Wait-YinuoCondition -TimeoutSeconds 30 -IntervalMilliseconds 500 -Probe {
    if (Test-TurixContractState -BusinessHeaders $BusinessHeaders -LegalHeaders $LegalHeaders -ContractAdminHeaders $ContractAdminHeaders -ContractId $contractId -TargetState $Spec.state) { return $true }
    return $null
  }
  if ($null -eq $state) { throw "demo contract '$($Spec.name)' did not reach expected state '$($Spec.state)'" }
  $detail = Get-TurixContractDetail -Headers $BusinessHeaders -ContractId $contractId
  $stage = "$(Get-YinuoProperty -Object $detail -Names @('stageCode'))".ToUpperInvariant()
  $lifecycle = Get-YinuoProperty -Object $detail -Names @('lifecycleStatus')
  if ($stage -match 'SIGN|ARCHIV|PERFORM|EFFECTIVE|EXPIRED|TERMINAT|CLOSED' -or ($null -ne $lifecycle -and [int]$lifecycle -ge 3)) {
    throw "phase-one seed crossed the post-approval boundary for '$($Spec.name)'"
  }
  $null = Add-YinuoCheck -Condition $true -Id "CONTRACT_$($Spec.key)_CREATE" -Message "created deterministic $($Spec.state) sample '$($Spec.name)'" -Details "id=$contractId, stage=$stage"
  return [int64]$contractId
}

function Remove-TurixDemoBusinessData {
  param(
    [Parameter(Mandatory = $true)][hashtable]$Headers,
    [Parameter(Mandatory = $true)][object]$Migration,
    [Parameter(Mandatory = $true)][ValidatePattern('^[1-9][0-9]*$')][string]$TenantId
  )
  $tenantIdSql = [int64]$TenantId
  $partyCodesSql = ($script:TurixDemoPartyCodes | ForEach-Object { "'$_'" }) -join ','
  $safetySql = @"
SELECT 'matching_contracts', COUNT(*) FROM clm_contract
WHERE tenant_id=$tenantIdSql AND title LIKE '演示｜%';
SELECT 'party_external_refs', COUNT(*)
FROM clm_contract_party cp
JOIN clm_party p ON p.id=cp.party_id
WHERE p.tenant_id=$tenantIdSql AND p.unified_credit_code IN ($partyCodesSql)
  AND cp.contract_id NOT IN (
    SELECT id FROM clm_contract WHERE tenant_id=$tenantIdSql AND title LIKE '演示｜%'
  );
"@
  $safetyOutput = @(Invoke-YinuoMysql -Runner $Migration.runner -Sql $safetySql -SkipHeaders)
  $safety = @{}
  foreach ($line in $safetyOutput) {
    if ($line -match '^([a-z_]+)\s+([0-9]+)$') { $safety[$Matches[1]] = [int]$Matches[2] }
  }
  if (-not $safety.ContainsKey('party_external_refs') -or $safety['party_external_refs'] -ne 0) {
    throw "demo party cleanup refused because one or more fixed fictional parties are referenced by non-demo contracts (count=$($safety['party_external_refs']))"
  }

  $runningProcessSql = @"
SELECT DISTINCT b.process_instance_id
FROM clm_workflow_binding b
JOIN clm_contract c ON c.id=b.contract_id
JOIN ACT_RU_EXECUTION e ON e.PROC_INST_ID_=b.process_instance_id
WHERE c.tenant_id=$tenantIdSql AND c.title LIKE '演示｜%'
  AND b.process_instance_id IS NOT NULL AND b.process_instance_id<>'';
"@
  $runningProcessIds = @(Invoke-YinuoMysql -Runner $Migration.runner -Sql $runningProcessSql -SkipHeaders)
  foreach ($processId in $runningProcessIds) {
    if (-not "$processId") { continue }
    $cancel = Invoke-YinuoApi -Headers $Headers -Method DELETE -Path '/bpm/process-instance/cancel-by-admin' -Body @{
      id = "$processId"; reason = 'TuriX -ResetData deterministic demo cleanup'
    }
    $null = Assert-TurixApi -Response $cancel -What "cancel demo process $processId"
  }

  $columns = $Migration.columns
  $tables = $Migration.tables
  $sql = [Collections.Generic.List[string]]::new()
  $sql.Add('SET FOREIGN_KEY_CHECKS = 0;')
  $sql.Add('CREATE TEMPORARY TABLE tmp_turix_demo_contract_ids (id BIGINT PRIMARY KEY);')
  $sql.Add("INSERT IGNORE INTO tmp_turix_demo_contract_ids SELECT id FROM clm_contract WHERE tenant_id=$tenantIdSql AND title LIKE '演示｜%';")
  $sql.Add('CREATE TEMPORARY TABLE tmp_turix_demo_binding_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_workflow_binding|contract_id')) {
    $sql.Add('INSERT IGNORE INTO tmp_turix_demo_binding_ids SELECT id FROM clm_workflow_binding WHERE contract_id IN (SELECT id FROM tmp_turix_demo_contract_ids);')
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_turix_demo_process_ids (id VARCHAR(128) PRIMARY KEY);')
  if ($columns.Contains('clm_workflow_binding|process_instance_id')) {
    $sql.Add("INSERT IGNORE INTO tmp_turix_demo_process_ids SELECT process_instance_id FROM clm_workflow_binding WHERE contract_id IN (SELECT id FROM tmp_turix_demo_contract_ids) AND process_instance_id IS NOT NULL AND process_instance_id<>'';")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_turix_demo_blob_ids (id BIGINT PRIMARY KEY);')
  if ($columns.Contains('clm_document_version|file_key')) {
    $sql.Add("INSERT IGNORE INTO tmp_turix_demo_blob_ids SELECT CAST(file_key AS UNSIGNED) FROM clm_document_version WHERE contract_id IN (SELECT id FROM tmp_turix_demo_contract_ids) AND file_key REGEXP '^[0-9]+$';")
  }
  $sql.Add('CREATE TEMPORARY TABLE tmp_turix_demo_party_ids (id BIGINT PRIMARY KEY);')
  $sql.Add("INSERT IGNORE INTO tmp_turix_demo_party_ids SELECT id FROM clm_party WHERE tenant_id=$tenantIdSql AND unified_credit_code IN ($partyCodesSql);")
  $sql.Add('CREATE TEMPORARY TABLE tmp_turix_demo_bytearray_ids (id VARCHAR(128) PRIMARY KEY);')
  foreach ($table in @('ACT_RU_VARIABLE','ACT_HI_VARINST','ACT_HI_DETAIL')) {
    $processColumn = if ($columns.Contains("$table|PROC_INST_ID_")) { 'PROC_INST_ID_' } elseif ($columns.Contains("$table|PROCESS_INSTANCE_ID_")) { 'PROCESS_INSTANCE_ID_' } else { $null }
    if ($processColumn -and $columns.Contains("$table|BYTEARRAY_ID_")) {
      $sql.Add("INSERT IGNORE INTO tmp_turix_demo_bytearray_ids SELECT BYTEARRAY_ID_ FROM $table WHERE $processColumn IN (SELECT id FROM tmp_turix_demo_process_ids) AND BYTEARRAY_ID_ IS NOT NULL;")
    }
  }
  $flowableTables = @($tables | Where-Object { $_ -like 'ACT_*' -and $_ -ne 'ACT_GE_BYTEARRAY' }) | Sort-Object {
    if ($_ -eq 'ACT_HI_PROCINST') { 90 } elseif ($_ -eq 'ACT_RU_EXECUTION') { 80 } else { 10 }
  }
  foreach ($table in $flowableTables) {
    $processColumn = if ($columns.Contains("$table|PROC_INST_ID_")) { 'PROC_INST_ID_' } elseif ($columns.Contains("$table|PROCESS_INSTANCE_ID_")) { 'PROCESS_INSTANCE_ID_' } else { $null }
    if ($processColumn) { $sql.Add("DELETE FROM $table WHERE $processColumn IN (SELECT id FROM tmp_turix_demo_process_ids);") }
  }
  if ($columns.Contains('ACT_GE_BYTEARRAY|ID_')) { $sql.Add('DELETE FROM ACT_GE_BYTEARRAY WHERE ID_ IN (SELECT id FROM tmp_turix_demo_bytearray_ids);') }
  if ($columns.Contains('clm_approval_task_revision_binding|approval_case_id')) {
    $sql.Add('DELETE FROM clm_approval_task_revision_binding WHERE approval_case_id IN (SELECT id FROM tmp_turix_demo_binding_ids);')
  }
  if ($columns.Contains('clm_approval_task_revision_binding|workflow_binding_id')) {
    $sql.Add('DELETE FROM clm_approval_task_revision_binding WHERE workflow_binding_id IN (SELECT id FROM tmp_turix_demo_binding_ids);')
  }
  $orderedChildren = @(
    'clm_approval_edit_request','clm_approval_task_revision_binding',
    'clm_ai_review_finding','clm_ai_review_run','clm_collaboration_event','clm_collaboration_case',
    'clm_integration_delivery','clm_governance_issue','clm_handover_item','clm_commitment',
    'clm_contract_revision','clm_audit_event','clm_workflow_binding','clm_contract_participant',
    'clm_contract_party','clm_document_version','clm_document'
  )
  foreach ($table in $orderedChildren) {
    if ($columns.Contains("$table|contract_id")) { $sql.Add("DELETE FROM $table WHERE contract_id IN (SELECT id FROM tmp_turix_demo_contract_ids);") }
  }
  $remainingOwned = @($tables | Where-Object {
    $_ -like 'clm_*' -and $_ -notin (@('clm_contract','clm_document_blob') + $orderedChildren) -and $columns.Contains("$_|contract_id")
  })
  foreach ($table in $remainingOwned) { $sql.Add("DELETE FROM $table WHERE contract_id IN (SELECT id FROM tmp_turix_demo_contract_ids);") }
  $sql.Add('DELETE FROM clm_contract WHERE id IN (SELECT id FROM tmp_turix_demo_contract_ids);')
  if ($columns.Contains('clm_document_blob|id')) { $sql.Add('DELETE FROM clm_document_blob WHERE id IN (SELECT id FROM tmp_turix_demo_blob_ids);') }
  if ($columns.Contains('clm_audit_event|aggregate_type') -and $columns.Contains('clm_audit_event|aggregate_id')) {
    $sql.Add("DELETE FROM clm_audit_event WHERE tenant_id=$tenantIdSql AND aggregate_type='PARTY' AND aggregate_id IN (SELECT id FROM tmp_turix_demo_party_ids);")
  }
  $sql.Add('DELETE FROM clm_party WHERE id IN (SELECT id FROM tmp_turix_demo_party_ids) AND NOT EXISTS (SELECT 1 FROM clm_contract_party cp WHERE cp.party_id=clm_party.id);')
  $sql.Add("SELECT 'remaining_contracts', COUNT(*) FROM clm_contract WHERE tenant_id=$tenantIdSql AND title LIKE '演示｜%';")
  $sql.Add("SELECT 'remaining_parties', COUNT(*) FROM clm_party WHERE tenant_id=$tenantIdSql AND unified_credit_code IN ($partyCodesSql);")
  $sql.Add("SELECT 'remaining_processes', COUNT(*) FROM ACT_HI_PROCINST WHERE PROC_INST_ID_ IN (SELECT id FROM tmp_turix_demo_process_ids);")
  $sql.Add('SET FOREIGN_KEY_CHECKS = 1;')
  $cleanupOutput = @(Invoke-YinuoMysql -Runner $Migration.runner -Sql ($sql -join [Environment]::NewLine) -SkipHeaders)
  $metrics = @{}
  foreach ($line in $cleanupOutput) {
    if ($line -match '^(remaining_[a-z_]+)\s+([0-9]+)$') { $metrics[$Matches[1]] = [int]$Matches[2] }
  }
  foreach ($required in @('remaining_contracts','remaining_parties','remaining_processes')) {
    if (-not $metrics.ContainsKey($required) -or $metrics[$required] -ne 0) {
      throw "-ResetData cleanup residue: $required=$($metrics[$required])"
    }
  }
  $null = Add-YinuoCheck -Condition $true -Id 'RESET_DATA_ONLY' -Message 'removed only prefixed demo contracts, their workflows/documents, and fixed fictional parties' -Details 'catalog types/templates/numbering/routing/policy/scopes were retained'
}
