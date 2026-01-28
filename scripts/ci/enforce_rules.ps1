param(
    [switch]$Verbose
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-Rg {
    $rg = Get-Command rg -ErrorAction SilentlyContinue
    if (-not $rg) {
        throw "ripgrep (rg) is required for this script."
    }
}

function Invoke-Rg {
    param(
        [string[]]$RgArgs
    )
    $output = & rg @RgArgs 2>$null
    $code = $LASTEXITCODE
    return [pscustomobject]@{
        Output = $output
        Code   = $code
    }
}

function Assert-NoMatches {
    param(
        [string]$Name,
        [string[]]$RgArgs,
        [scriptblock]$Filter = $null
    )
    $result = Invoke-Rg -RgArgs $RgArgs
    if ($result.Code -gt 1) {
        throw "rg failed for '$Name' (exit $($result.Code))."
    }
    if ($result.Code -eq 0) {
        $lines = $result.Output
        if ($Filter -ne $null) {
            $lines = $lines | Where-Object $Filter
        }
        if ($lines) {
            Write-Host ""
            Write-Host "FAIL: $Name"
            $lines | ForEach-Object { Write-Host $_ }
            $script:HadFailures = $true
        }
    }
}

Require-Rg

$script:HadFailures = $false
$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

# 1) Timebase: no direct system time in domain/fusion logic.
$timebaseArgs = @(
    "-n",
    "-P",
    "(System\.currentTimeMillis|SystemClock|Date\(|Instant\.now)",
    "--glob", "feature/**/src/main/java/**/sensors/**/*.kt",
    "--glob", "feature/**/src/main/java/**/domain/**/*.kt",
    "--glob", "core/**/src/main/java/**/domain/**/*.kt",
    "--glob", "dfcards-library/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Timebase usage in domain/fusion" -RgArgs $timebaseArgs

# 2) DI: core pipeline constructed inside managers (heuristic).
$diArgs = @(
    "-n",
    "FlightDataCalculator\(",
    "--glob", "**/src/main/java/**/*Manager.kt",
    "--glob", "**/src/main/java/**/*Service*.kt"
)
Assert-NoMatches -Name "DI: pipeline constructed inside managers" -RgArgs $diArgs

# 3) ViewModel purity: no SharedPreferences or UI types in ViewModels.
$vmArgs = @(
    "-n",
    "(SharedPreferences|getSharedPreferences|androidx\\.compose\\.ui)",
    "--glob", "**/src/main/java/**/*ViewModel.kt"
)
Assert-NoMatches -Name "ViewModel purity violations" -RgArgs $vmArgs

# 4) Compose lifecycle: ban collectAsState without lifecycle awareness.
$collectArgs = @(
    "-n",
    "collectAsState\(",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "collectAsState without lifecycle" -RgArgs $collectArgs -Filter { $_ -notmatch "collectAsStateWithLifecycle" }

# 5) Vendor strings: no xcsoar in production Kotlin source.
$vendorArgs = @(
    "-n",
    "-i",
    "xcsoar",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Vendor strings in production Kotlin source" -RgArgs $vendorArgs

# 6) ASCII hygiene: no non-ASCII characters in production Kotlin source.
$asciiArgs = @(
    "-n",
    "-P",
    "[^\x00-\x7F]",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Non-ASCII characters in production Kotlin source" -RgArgs $asciiArgs

if ($script:HadFailures) {
    Write-Host ""
    Write-Host "Rule enforcement failed."
    exit 1
}

Write-Host "Rule enforcement passed."
exit 0
