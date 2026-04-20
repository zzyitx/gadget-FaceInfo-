param(
    [string]$BaseUrl = "http://127.0.0.1:8000",
    [Parameter(Mandatory = $true)]
    [string]$AdminEmail,
    [Parameter(Mandatory = $true)]
    [string]$AdminPassword,
    [string]$ApplicationName = "gadget",
    [string]$DetectionModelName = "gadget-detection",
    [string]$VerificationModelName = "gadget-verification",
    [int]$TimeoutSec = 15
)

$ErrorActionPreference = "Stop"

function New-AuthHeaders {
    param([string]$AccessToken)
    return @{
        Authorization = "Bearer $AccessToken"
    }
}

function Get-CollectionItems {
    param([object]$Response)

    if ($null -eq $Response) {
        return @()
    }

    if ($Response -is [System.Array] -or $Response -is [System.Collections.IEnumerable] -and -not ($Response -is [string])) {
        return @($Response)
    }

    foreach ($propertyName in @("items", "content", "data")) {
        if ($null -ne $Response.PSObject.Properties[$propertyName]) {
            return @(Get-CollectionItems -Response $Response.$propertyName)
        }
    }

    return @($Response)
}

function Find-ModelByTypeOrName {
    param(
        [object[]]$Models,
        [string]$ExpectedType,
        [string]$PreferredName
    )

    $matchedByType = @($Models | Where-Object { $_.type -eq $ExpectedType })
    if ($matchedByType.Count -eq 1) {
        return $matchedByType[0]
    }

    if ($matchedByType.Count -gt 1) {
        $matchedByName = $matchedByType | Where-Object { $_.name -eq $PreferredName }
        if ($matchedByName) {
            return $matchedByName[0]
        }
        throw "Found multiple $ExpectedType models under application '$ApplicationName'. Please keep only one or set a clearer script convention."
    }

    $matchedByName = @($Models | Where-Object { $_.name -eq $PreferredName })
    if ($matchedByName.Count -gt 0) {
        return $matchedByName[0]
    }

    return $null
}

function Invoke-CompreFaceJson {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers -TimeoutSec $TimeoutSec
    }

    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json) -TimeoutSec $TimeoutSec
}

$basicToken = "Basic Q29tbW9uQ2xpZW50SWQ6cGFzc3dvcmQ="
$tokenUri = "$BaseUrl/admin/oauth/token"
$tokenForm = @{
    username   = $AdminEmail
    password   = $AdminPassword
    grant_type = "password"
}

Write-Host "Logging in to CompreFace admin: $tokenUri"
$tokenBody = "username=$([uri]::EscapeDataString($AdminEmail))&password=$([uri]::EscapeDataString($AdminPassword))&grant_type=password"
$tokenResponse = Invoke-RestMethod -Method Post -Uri $tokenUri -Headers @{ Authorization = $basicToken } -ContentType "application/x-www-form-urlencoded" -Body $tokenBody -TimeoutSec $TimeoutSec
if (-not $tokenResponse.access_token) {
    throw "access_token was not returned. Check admin credentials and make sure CompreFace is running."
}

$headers = New-AuthHeaders -AccessToken $tokenResponse.access_token

Write-Host "Querying application list from: $BaseUrl/admin/app"
$applicationsResponse = Invoke-CompreFaceJson -Method Get -Uri "$BaseUrl/admin/app" -Headers $headers -Body $null
$applications = Get-CollectionItems -Response $applicationsResponse
$app = @($applications | Where-Object { $_.name -eq $ApplicationName } | Select-Object -First 1)

if ($app.Count -eq 0 -or -not $app[0].id) {
    throw "Application '$ApplicationName' was not found. Create it once in CompreFace UI, then rerun this script."
}

$appGuid = $app[0].id
Write-Host "Using existing application: $ApplicationName, appGuid=$appGuid"

Write-Host "Querying existing models under application: $ApplicationName"
$modelsResponse = Invoke-CompreFaceJson -Method Get -Uri "$BaseUrl/admin/app/$appGuid/model" -Headers $headers -Body $null
$models = Get-CollectionItems -Response $modelsResponse

# 多次执行脚本时必须复用现有 detection，避免把手工创建好的模型和 key 覆盖掉。
$detectionModel = Find-ModelByTypeOrName -Models $models -ExpectedType "DETECTION" -PreferredName $DetectionModelName
if ($null -eq $detectionModel) {
    throw "Detection model was not found under application '$ApplicationName'. Please create it in CompreFace UI first, then rerun this script."
}

if (-not $detectionModel.apiKey) {
    throw "Detection model exists, but no apiKey was returned from CompreFace."
}

# verification 缺失时才补建，这样脚本既能兼容已有 detection，也能安全重复执行。
$verificationModel = Find-ModelByTypeOrName -Models $models -ExpectedType "VERIFY" -PreferredName $VerificationModelName
if ($null -eq $verificationModel) {
    Write-Host "Verification model not found, creating: $VerificationModelName"
    $verificationModel = Invoke-CompreFaceJson -Method Post -Uri "$BaseUrl/admin/app/$appGuid/model" -Headers $headers -Body @{
        name = $VerificationModelName
        type = "VERIFY"
    }
}
else {
    Write-Host "Using existing verification model: $($verificationModel.name)"
}

if (-not $verificationModel.apiKey) {
    throw "Verification model is missing apiKey. Check CompreFace model state and rerun the script."
}

Write-Host ""
Write-Host "Write the following values into local application.yml or environment variables:"
Write-Host "COMPREFACE_DETECTION_API_KEY=$($detectionModel.apiKey)"
Write-Host "COMPREFACE_VERIFICATION_API_KEY=$($verificationModel.apiKey)"
Write-Host "COMPREFACE_ADMIN_EMAIL=$AdminEmail"
Write-Host "COMPREFACE_ADMIN_PASSWORD=<keep the local password you used>"
