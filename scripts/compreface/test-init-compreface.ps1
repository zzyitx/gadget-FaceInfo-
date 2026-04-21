$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "init-compreface.ps1"
if (-not (Test-Path $scriptPath)) {
    throw "missing script: $scriptPath"
}

$scriptContent = Get-Content $scriptPath -Raw

function Assert-Contains {
    param(
        [string]$Pattern,
        [string]$Message
    )

    if ($scriptContent -notmatch $Pattern) {
        throw $Message
    }
}

Assert-Contains -Pattern 'Invoke-CompreFaceJson\s+-Method Get\s+-Uri "\$BaseUrl/admin/app"' -Message "script should query existing applications before creating or reusing app"
Assert-Contains -Pattern 'Invoke-CompreFaceJson\s+-Method Post\s+-Uri "\$BaseUrl/admin/app"' -Message "script should create gadget application when it is missing"
Assert-Contains -Pattern 'Invoke-CompreFaceJson\s+-Method Get\s+-Uri "\$BaseUrl/admin/app/\$appGuid/model"' -Message "script should query existing models under gadget app"
Assert-Contains -Pattern 'type\s*=\s*"DETECTION"' -Message "script should create detection model when it is missing"
Assert-Contains -Pattern 'type\s*=\s*"VERIFY"' -Message "script should create verification model when it is missing"
Assert-Contains -Pattern 'COMPREFACE_DETECTION_API_KEY=' -Message "script should print detection api key"
Assert-Contains -Pattern 'COMPREFACE_VERIFICATION_API_KEY=' -Message "script should print verification api key"
Write-Host "PASS: init-compreface script contains first-time initialization logic."
