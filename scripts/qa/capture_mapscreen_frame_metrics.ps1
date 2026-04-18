param(
    [string]$ApplicationId = "com.trust3.xcpro.debug",
    [string]$Activity = "com.trust3.xcpro.MainActivity",
    [int]$DurationSeconds = 60,
    [int]$SwipePauseMs = 350,
    [string]$OutputJsonPath,
    [switch]$NoGestures
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    # adb sometimes emits benign stderr warnings (e.g. activity already top-most).
    $PSNativeCommandUseErrorActionPreference = $false
}

function Require-Adb {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        throw "adb is required but was not found in PATH."
    }
}

function Require-ConnectedDevice {
    $lines = adb devices
    $device = $lines | Select-String -Pattern "^\S+\s+device(\s|$)" | Select-Object -First 1
    if ($null -eq $device) {
        throw "No connected adb device detected."
    }
}

function Parse-GfxMetric {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$Pattern
    )
    $line = $Lines | Where-Object { $_ -match $Pattern } | Select-Object -First 1
    if ($null -eq $line) { return $null }
    return [double]$Matches[1]
}

function Parse-GfxInfoSummary {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string[]]$Lines)

    $p95 = Parse-GfxMetric -Lines $Lines -Pattern "95th percentile:\s*([0-9]+(?:\.[0-9]+)?)ms"
    $p99 = Parse-GfxMetric -Lines $Lines -Pattern "99th percentile:\s*([0-9]+(?:\.[0-9]+)?)ms"
    $jank = Parse-GfxMetric -Lines $Lines -Pattern "Janky frames:\s*[0-9]+\s*\(([0-9]+(?:\.[0-9]+)?)%\)"

    if ($null -eq $p95 -or $null -eq $p99 -or $null -eq $jank) {
        throw "Failed to parse gfxinfo summary (p95/p99/jank). Raw output did not contain expected summary lines."
    }

    return [ordered]@{
        frame_time_p95_ms = $p95
        frame_time_p99_ms = $p99
        jank_percent = $jank
    }
}

function Send-Swipe {
    param(
        [Parameter(Mandatory = $true)][int]$X1,
        [Parameter(Mandatory = $true)][int]$Y1,
        [Parameter(Mandatory = $true)][int]$X2,
        [Parameter(Mandatory = $true)][int]$Y2,
        [int]$DurationMs = 260
    )
    adb shell input swipe $X1 $Y1 $X2 $Y2 $DurationMs | Out-Null
}

Require-Adb
Require-ConnectedDevice

if ($DurationSeconds -lt 15) {
    throw "DurationSeconds must be >= 15 for stable percentile capture."
}

$component = "$ApplicationId/$Activity"
Write-Host "[mapscreen-metrics] Launching: $component"
# am start may emit a benign warning when activity is already top-most.
$launchCmd = "adb shell am start -n `"$component`" >nul 2>nul"
cmd /c $launchCmd | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to launch activity: $component"
}
Start-Sleep -Seconds 2

Write-Host "[mapscreen-metrics] Resetting gfxinfo counters..."
adb shell dumpsys gfxinfo $ApplicationId reset | Out-Null

$stopAt = (Get-Date).AddSeconds($DurationSeconds)
Write-Host "[mapscreen-metrics] Capturing for $DurationSeconds seconds..."

if (-not $NoGestures) {
    while ((Get-Date) -lt $stopAt) {
        # Move map in four directions to exercise render path.
        Send-Swipe -X1 900 -Y1 1200 -X2 180 -Y2 1200
        Start-Sleep -Milliseconds $SwipePauseMs
        Send-Swipe -X1 180 -Y1 1200 -X2 900 -Y2 1200
        Start-Sleep -Milliseconds $SwipePauseMs
        Send-Swipe -X1 540 -Y1 1600 -X2 540 -Y2 700
        Start-Sleep -Milliseconds $SwipePauseMs
        Send-Swipe -X1 540 -Y1 700 -X2 540 -Y2 1600
        Start-Sleep -Milliseconds $SwipePauseMs
    }
} else {
    Start-Sleep -Seconds $DurationSeconds
}

Write-Host "[mapscreen-metrics] Reading gfxinfo summary..."
$gfxLines = @(adb shell dumpsys gfxinfo $ApplicationId | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$gfxLines = @($gfxLines)
$gfxCount = $gfxLines.Count
if ($gfxCount -eq 0) {
    throw "No gfxinfo output captured for package '$ApplicationId'."
}
$summary = Parse-GfxInfoSummary -Lines $gfxLines
$summary["captured_at"] = (Get-Date).ToString("o")
$summary["application_id"] = $ApplicationId
$summary["activity"] = $Activity
$summary["duration_seconds"] = $DurationSeconds

$json = $summary | ConvertTo-Json -Depth 6
if (-not [string]::IsNullOrWhiteSpace($OutputJsonPath)) {
    $dir = Split-Path -Path $OutputJsonPath -Parent
    if (-not [string]::IsNullOrWhiteSpace($dir) -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $json | Set-Content -Path $OutputJsonPath -Encoding UTF8
    Write-Host "[mapscreen-metrics] Wrote: $OutputJsonPath"
}

Write-Host "[mapscreen-metrics] frame_time_p95_ms=$($summary.frame_time_p95_ms) frame_time_p99_ms=$($summary.frame_time_p99_ms) jank_percent=$($summary.jank_percent)"
Write-Output $json
