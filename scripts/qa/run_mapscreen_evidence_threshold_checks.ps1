param(
    [ValidateSet("pkg-f1", "pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1")]
    [string[]]$PackageIds = @("pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1"),
    [switch]$UpdateGateResults,
    [switch]$AllowPending
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "verify_mapscreen_package_evidence.ps1"
if (-not (Test-Path $scriptPath)) {
    throw "Missing verifier script: $scriptPath"
}

$results = @()
foreach ($packageId in $PackageIds) {
    Write-Host "[mapscreen-thresholds] Checking package: $packageId"
    $invokeArgs = @{
        PackageId = $packageId
    }
    if ($UpdateGateResults) { $invokeArgs["UpdateGateResult"] = $true }
    if ($AllowPending) { $invokeArgs["AllowPending"] = $true }

    & $scriptPath @invokeArgs
    $exitCode = $LASTEXITCODE
    $results += @([ordered]@{
        package_id = $packageId
        exit_code = $exitCode
        status = if ($exitCode -eq 0) { "pass" } else { "fail" }
    })
}

Write-Host ""
Write-Host "[mapscreen-thresholds] Summary"
foreach ($result in $results) {
    Write-Host ("- {0}: {1} (exit {2})" -f $result.package_id, $result.status, $result.exit_code)
}

$failed = @($results | Where-Object { $_.exit_code -ne 0 })
if ($failed.Count -gt 0) {
    exit 1
}
exit 0
