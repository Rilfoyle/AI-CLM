# create-bpm-model.ps1
# Idempotently create/update + deploy the SIMPLE-designer BPM model defined in
# clm_contract_approval_v1.model.json against a running RuoYi-Vue-Pro backend.
# PowerShell 5.1 compatible.
#
# Usage:
#   pwsh ./scripts/create-bpm-model.ps1
#   powershell -ExecutionPolicy Bypass -File D:\dev\clm\scripts\create-bpm-model.ps1
#   (optional) -BaseUrl http://127.0.0.1:48080 -Username admin -Password admin123 -TenantName TuriX -TenantId 1

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:48080',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [string]$TenantName = 'TuriX',
    [string]$TenantId = '1',
    [ValidateRange(1, [long]::MaxValue)][long]$LegalUserId = 200002,
    [ValidateRange(1, [long]::MaxValue)][long]$ManagerUserId = 200003,
    [string]$ModelFile = ''
)

$ErrorActionPreference = 'Stop'
$ClmRoot = Split-Path -Parent $PSScriptRoot
if (-not $ModelFile) {
    $scriptsDir = Join-Path $ClmRoot 'scripts'
    $ModelFile = Join-Path $scriptsDir 'clm_contract_approval_v1.model.json'
}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Make console output UTF-8 so Chinese names print correctly (best effort).
try { [Console]::OutputEncoding = $Utf8NoBom } catch {}

# ---------- helpers ----------
function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body = $null,          # hashtable/object -> JSON, or raw JSON string
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $h = @{ 'tenant-id' = $TenantId; 'Accept' = 'application/json' }
    foreach ($k in $Headers.Keys) { $h[$k] = $Headers[$k] }
    $args = @{ Method = $Method; Uri = $uri; Headers = $h; UseBasicParsing = $true }
    if ($PSVersionTable.PSVersion.Major -ge 7) { $args['SkipHttpErrorCheck'] = $true }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 50 -Compress }
        $args['Body'] = $Utf8NoBom.GetBytes($json)   # send as UTF-8 bytes (PS 5.1 would otherwise send Latin-1)
        $args['ContentType'] = 'application/json;charset=UTF-8'
    }
    try {
        $resp = Invoke-WebRequest @args
        $raw = $resp.Content
    } catch {
        $raw = $_.ErrorDetails.Message
        $errorResponse = $_.Exception.Response
        if (-not $raw -and $errorResponse) {
            if ($errorResponse.PSObject.Methods['GetResponseStream']) {
                $sr = New-Object IO.StreamReader($errorResponse.GetResponseStream(), $Utf8NoBom)
                $raw = $sr.ReadToEnd(); $sr.Close()
            } else {
                $contentProperty = $errorResponse.PSObject.Properties['Content']
                $content = if ($contentProperty) { $contentProperty.Value } else { $null }
                if ($content -and $content.PSObject.Methods['ReadAsStringAsync']) {
                    $readTask = $content.ReadAsStringAsync()
                    $raw = $readTask.GetAwaiter().GetResult()
                }
            }
        }
        if (-not $raw) { throw "HTTP error calling $Method $Path : $($_.Exception.Message)" }
    }
    # Invoke-WebRequest decodes content as ISO-8859-1 unless charset is declared; re-decode if needed.
    if ($raw -is [string] -and $resp -and $resp.RawContentStream) {
        $bytes = $resp.RawContentStream.ToArray()
        $raw = $Utf8NoBom.GetString($bytes)
    }
    $result = $raw | ConvertFrom-Json
    $codeText = if ($null -ne $result.code) { $result.code } else { '?' }
    Write-Host ("[{0} {1}] code={2} msg={3}" -f $Method, $Path, $codeText, $result.msg)
    return $result
}

function Assert-Ok {
    param([object]$Result, [string]$What)
    if ($null -eq $Result -or $Result.code -ne 0) {
        throw ("{0} failed: code={1} msg={2}" -f $What, $Result.code, $Result.msg)
    }
}

function ConvertTo-CanonicalValue {
    param([AllowNull()][object]$Value, [switch]$OmitNullProperties)
    if ($null -eq $Value) { return $null }
    if ($Value -is [string] -or $Value -is [char] -or $Value -is [bool] -or
        $Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or
        $Value -is [int64] -or $Value -is [decimal] -or $Value -is [double] -or
        $Value -is [single] -or $Value -is [datetime]) { return $Value }
    if ($Value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | ForEach-Object { "$_" } | Sort-Object)) {
            if ($OmitNullProperties -and $null -eq $Value[$key]) { continue }
            $ordered[$key] = ConvertTo-CanonicalValue $Value[$key] -OmitNullProperties:$OmitNullProperties
        }
        return [pscustomobject]$ordered
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $items = @()
        foreach ($item in $Value) { $items += ,(ConvertTo-CanonicalValue $item -OmitNullProperties:$OmitNullProperties) }
        return ,$items
    }
    $properties = @($Value.PSObject.Properties | Where-Object {
        $_.MemberType -in @('NoteProperty', 'Property')
    } | Sort-Object Name)
    if ($properties.Count -gt 0) {
        $ordered = [ordered]@{}
        foreach ($property in $properties) {
            if ($OmitNullProperties -and $null -eq $property.Value) { continue }
            $ordered[$property.Name] = ConvertTo-CanonicalValue $property.Value -OmitNullProperties:$OmitNullProperties
        }
        return [pscustomobject]$ordered
    }
    return "$Value"
}

function Get-CanonicalJson {
    param([AllowNull()][object]$Value, [switch]$OmitNullProperties)
    return (ConvertTo-CanonicalValue $Value -OmitNullProperties:$OmitNullProperties | ConvertTo-Json -Depth 50 -Compress)
}

function Get-ModelMetadataFingerprint {
    param([Parameter(Mandatory)][object]$Model)
    $modelType = $Model.type
    if ($null -eq $modelType -and $Model.PSObject.Properties['modelType']) {
        $modelType = $Model.modelType
    }
    return [pscustomobject][ordered]@{
        key = $Model.key
        name = $Model.name
        category = $Model.category
        icon = "$($Model.icon)"
        description = "$($Model.description)"
        type = $modelType
        formType = $Model.formType
        formId = $Model.formId
        formCustomCreatePath = "$($Model.formCustomCreatePath)"
        formCustomViewPath = "$($Model.formCustomViewPath)"
        visible = $Model.visible
        startUserIds = @($Model.startUserIds)
        startDeptIds = @($Model.startDeptIds)
        managerUserIds = @($Model.managerUserIds)
        allowCancelRunningProcess = $Model.allowCancelRunningProcess
        allowWithdrawTask = $Model.allowWithdrawTask
    }
}

# ---------- 0. load model payload ----------
if (-not (Test-Path $ModelFile)) { throw "Model file not found: $ModelFile" }
$payloadText = [IO.File]::ReadAllText($ModelFile, $Utf8NoBom)
$payload = $payloadText | ConvertFrom-Json
# Model administration and approval assignment are different concerns. Keep the
# bootstrap administrator able to update/deploy this model while also allowing
# the contract administrator to manage it from the product UI.
$payload.managerUserIds = @(1, $ManagerUserId | Sort-Object -Unique)
$legalNode = $payload.simpleModel.childNode
$managerNode = $legalNode.childNode
$legalNode.candidateParam = "$LegalUserId"
$legalNode.showText = "指定成员：TuriX 法务（$LegalUserId）"
$managerNode.candidateParam = "$ManagerUserId"
$managerNode.showText = "指定成员：TuriX 合同管理员（$ManagerUserId）"
$payloadText = $payload | ConvertTo-Json -Depth 50 -Compress
$modelKey = $payload.key
Write-Host "Model key: $modelKey  (payload: $ModelFile)"

# ---------- 1. login ----------
$login = Invoke-Api -Method POST -Path '/admin-api/system/auth/login' -Body @{
    username = $Username; password = $Password; tenantName = $TenantName
}
Assert-Ok $login 'login'
$token = $login.data.accessToken
if (-not $token) { throw 'login returned no accessToken' }
$auth = @{ 'Authorization' = "Bearer $token" }

# ---------- 2. find existing model by key ----------
$list = Invoke-Api -Method GET -Path '/admin-api/bpm/model/list' -Headers $auth
Assert-Ok $list 'model/list'
$existing = @($list.data | Where-Object { $_.key -eq $modelKey }) | Select-Object -First 1

$defsBefore = Invoke-Api -Method GET -Path '/admin-api/bpm/process-definition/list?suspensionState=1' -Headers $auth
Assert-Ok $defsBefore 'process-definition/list preflight'
$activeDefBefore = @($defsBefore.data | Where-Object { $_.key -eq $modelKey }) | Sort-Object version -Descending | Select-Object -First 1
$activeDefinitionExact = $false
if ($activeDefBefore) {
    $deployedSimple = if ($activeDefBefore.simpleModel -is [string]) {
        $activeDefBefore.simpleModel | ConvertFrom-Json
    } else {
        $activeDefBefore.simpleModel
    }
    $activeDefinitionExact =
        (Get-CanonicalJson (Get-ModelMetadataFingerprint $activeDefBefore)) -eq
            (Get-CanonicalJson (Get-ModelMetadataFingerprint $payload)) -and
        (Get-CanonicalJson $deployedSimple -OmitNullProperties) -eq
            (Get-CanonicalJson $payload.simpleModel -OmitNullProperties)
}

# ---------- 3. create or update ----------
$modelId = $null
$modelChanged = $false
if ($existing) {
    $modelId = $existing.id
    $currentMeta = Invoke-Api -Method GET -Path "/admin-api/bpm/model/get?id=$modelId" -Headers $auth
    Assert-Ok $currentMeta 'model/get before update'
    $currentSimple = Invoke-Api -Method GET -Path "/admin-api/bpm/model/simple/get?id=$modelId" -Headers $auth
    Assert-Ok $currentSimple 'model/simple/get before update'
    $sameMetadata = (Get-CanonicalJson (Get-ModelMetadataFingerprint $currentMeta.data)) -eq
        (Get-CanonicalJson (Get-ModelMetadataFingerprint $payload))
    $sameSimpleModel = (Get-CanonicalJson $currentSimple.data -OmitNullProperties) -eq
        (Get-CanonicalJson $payload.simpleModel -OmitNullProperties)
    $modelChanged = -not ($sameMetadata -and $sameSimpleModel)
    if ($modelChanged) {
        Write-Host "Model '$modelKey' exists (id=$modelId) -> configuration changed, update"
        # re-serialize payload with id; ConvertTo-Json keeps nested objects (depth 50)
        $payload | Add-Member -NotePropertyName id -NotePropertyValue $modelId -Force
        $update = Invoke-Api -Method PUT -Path '/admin-api/bpm/model/update' -Headers $auth -Body $payload
        Assert-Ok $update 'model/update'
    } else {
        Write-Host "Model '$modelKey' exists (id=$modelId) and is identical -> reuse"
    }
} else {
    Write-Host "Model '$modelKey' not found -> create"
    $create = Invoke-Api -Method POST -Path '/admin-api/bpm/model/create' -Headers $auth -Body $payloadText
    Assert-Ok $create 'model/create'
    $modelId = $create.data
    $modelChanged = $true
    Write-Host "Created model id=$modelId"
}
if (-not $modelId) { throw 'no model id' }

# ---------- 4. deploy ----------
$deployed = $false
$needsDeploy = $modelChanged -or -not $activeDefinitionExact
if ($needsDeploy) {
    $deploy = Invoke-Api -Method POST -Path "/admin-api/bpm/model/deploy?id=$modelId" -Headers $auth
    Assert-Ok $deploy 'model/deploy'
    $deployed = $true
} else {
    Write-Host ("Deployment is current -> reuse definition id={0} version={1}" -f $activeDefBefore.id, $activeDefBefore.version)
}

# ---------- 5. verify model meta ----------
$get = Invoke-Api -Method GET -Path "/admin-api/bpm/model/get?id=$modelId" -Headers $auth
Assert-Ok $get 'model/get'
$m = $get.data
Write-Host ("model/get: id={0} key={1} name={2} type={3} formType={4} createPath={5} viewPath={6} category={7} deploymentId={8}" -f `
    $m.id, $m.key, $m.name, $m.type, $m.formType, $m.formCustomCreatePath, $m.formCustomViewPath, $m.category, $m.deploymentId)
if ($m.formType -ne 20) { throw "formType expected 20, got $($m.formType)" }
if ($m.formCustomCreatePath -ne $payload.formCustomCreatePath) { throw 'formCustomCreatePath mismatch' }
if ($m.formCustomViewPath -ne $payload.formCustomViewPath) { throw 'formCustomViewPath mismatch' }

# ---------- 6. verify simple model round-trip ----------
$simple = Invoke-Api -Method GET -Path "/admin-api/bpm/model/simple/get?id=$modelId" -Headers $auth
Assert-Ok $simple 'model/simple/get'
$chain = @()
$n = $simple.data
while ($null -ne $n) {
    $chain += ("{0}[type={1},id={2},strategy={3},param={4},method={5},reject={6}]" -f $n.name, $n.type, $n.id, $n.candidateStrategy, $n.candidateParam, $n.approveMethod, $n.rejectHandler.type)
    $n = $n.childNode
}
Write-Host ("simple/get chain: " + ($chain -join ' -> '))
if ($chain.Count -ne 4) { throw "expected 4 nodes in simple model chain, got $($chain.Count)" }
$actualLegalNode = $simple.data.childNode
$actualContractAdminNode = $actualLegalNode.childNode
if ($actualLegalNode.candidateParam -ne "$LegalUserId") {
    throw "legal approval node expected candidate $LegalUserId, got $($actualLegalNode.candidateParam)"
}
if ($actualContractAdminNode.candidateParam -ne "$ManagerUserId") {
    throw "contract-admin approval node expected candidate $ManagerUserId, got $($actualContractAdminNode.candidateParam)"
}
if (@($m.managerUserIds | ForEach-Object { [long]$_ }) -notcontains 1) {
    throw 'model managerUserIds must retain bootstrap administrator id=1 for idempotent deployment'
}

# ---------- 7. verify process definition ----------
$defs = Invoke-Api -Method GET -Path '/admin-api/bpm/process-definition/list?suspensionState=1' -Headers $auth
Assert-Ok $defs 'process-definition/list'
$def = @($defs.data | Where-Object { $_.key -eq $modelKey }) | Sort-Object version -Descending | Select-Object -First 1
if (-not $def) { throw "process definition with key $modelKey not found in active list" }
$deployedSimple = if ($def.simpleModel -is [string]) { $def.simpleModel | ConvertFrom-Json } else { $def.simpleModel }
$definitionExact =
    (Get-CanonicalJson (Get-ModelMetadataFingerprint $def)) -eq
        (Get-CanonicalJson (Get-ModelMetadataFingerprint $payload)) -and
    (Get-CanonicalJson $deployedSimple -OmitNullProperties) -eq
        (Get-CanonicalJson $payload.simpleModel -OmitNullProperties)
if (-not $definitionExact) {
    throw "active process definition '$($def.id)' does not match the current TuriX model payload"
}
Write-Host ("DEPLOYED: definitionId={0} key={1} version={2} name={3} deploymentId={4} formType={5}" -f `
    $def.id, $def.key, $def.version, $def.name, $def.deploymentId, $def.formType)

# machine-readable summary (last line)
Write-Output (@{ modelId = $modelId; definitionId = $def.id; key = $def.key; version = $def.version; name = $def.name; changed = $modelChanged; deployed = $deployed } | ConvertTo-Json -Compress)
