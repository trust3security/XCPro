param(
    [Parameter(Mandatory = $true)][string]$RunId,
    [ValidateSet("tier_a", "tier_b")][string]$Tier = "tier_a",
    [Parameter(Mandatory = $true)][double]$FrameTimeP95Ms,
    [Parameter(Mandatory = $true)][double]$FrameTimeP99Ms,
    [Parameter(Mandatory = $true)][double]$JankPercent
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-HasFrameMetrics {
    param([AllowNull()]$TierMetrics)
    if ($null -eq $TierMetrics) { return $false }
    $props = $TierMetrics.PSObject.Properties
    if ($null -eq $props["frame_time_p95_ms"] -or $null -eq $props["frame_time_p99_ms"] -or $null -eq $props["jank_percent"]) {
        return $false
    }
    return ($null -ne $props["frame_time_p95_ms"].Value -and $null -ne $props["frame_time_p99_ms"].Value -and $null -ne $props["jank_percent"].Value)
}

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")
$metricsPath = Join-Path $repoRoot "artifacts/mapscreen/phase3/pkg-e1/$RunId/metrics.json"

if (-not (Test-Path $metricsPath)) {
    throw "metrics.json not found: $metricsPath"
}

$metrics = Get-Content -Path $metricsPath -Raw | ConvertFrom-Json
if ($null -eq $metrics.slo_targets) {
    throw "slo_targets missing in $metricsPath"
}

foreach ($target in $metrics.slo_targets) {
    if ($target.slo_id -ne "MS-UX-01") { continue }
    if ($null -eq $target.post_change) {
        $target | Add-Member -NotePropertyName post_change -NotePropertyValue ([pscustomobject]@{}) -Force
    }
    $tierProperty = $target.post_change.PSObject.Properties[$Tier]
    if ($null -eq $tierProperty -or $null -eq $tierProperty.Value) {
        $target.post_change | Add-Member -NotePropertyName $Tier -NotePropertyValue ([pscustomobject]@{}) -Force
    }
    $tierMetrics = $target.post_change.PSObject.Properties[$Tier].Value
    $tierMetrics | Add-Member -NotePropertyName frame_time_p95_ms -NotePropertyValue $FrameTimeP95Ms -Force
    $tierMetrics | Add-Member -NotePropertyName frame_time_p99_ms -NotePropertyValue $FrameTimeP99Ms -Force
    $tierMetrics | Add-Member -NotePropertyName jank_percent -NotePropertyValue $JankPercent -Force

    $tierA = if ($null -ne $target.post_change.PSObject.Properties["tier_a"]) { $target.post_change.PSObject.Properties["tier_a"].Value } else { $null }
    $tierB = if ($null -ne $target.post_change.PSObject.Properties["tier_b"]) { $target.post_change.PSObject.Properties["tier_b"].Value } else { $null }
    $hasTierA = Test-HasFrameMetrics -TierMetrics $tierA
    $hasTierB = Test-HasFrameMetrics -TierMetrics $tierB
    $target.status = if ($hasTierA -and $hasTierB) {
        "captured"
    } elseif ($hasTierA -or $hasTierB) {
        "captured_partial"
    } else {
        "pending_perf_capture"
    }
}

$metrics | ConvertTo-Json -Depth 20 | Set-Content -Path $metricsPath -Encoding UTF8
Write-Host "[mapscreen-metrics] Updated MS-UX-01 $Tier metrics in: $metricsPath"
