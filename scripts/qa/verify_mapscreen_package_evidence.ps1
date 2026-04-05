param(
    [ValidateSet("pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1")]
    [string]$PackageId,
    [string]$RunId,
    [string]$ArtifactRoot,
    [switch]$UpdateGateResult,
    [switch]$AllowPending
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-Hashtable {
    param([Parameter(Mandatory = $true)][AllowNull()]$InputObject)

    if ($null -eq $InputObject) { return $null }

    if ($InputObject -is [System.Collections.IDictionary]) {
        $hash = [ordered]@{}
        foreach ($key in $InputObject.Keys) {
            $hash[$key] = ConvertTo-Hashtable -InputObject $InputObject[$key]
        }
        return $hash
    }

    if ($InputObject -is [pscustomobject]) {
        $hash = [ordered]@{}
        foreach ($prop in $InputObject.PSObject.Properties) {
            $hash[$prop.Name] = ConvertTo-Hashtable -InputObject $prop.Value
        }
        return $hash
    }

    if (($InputObject -is [System.Collections.IEnumerable]) -and -not ($InputObject -is [string])) {
        $items = @()
        foreach ($item in $InputObject) {
            $items += @(ConvertTo-Hashtable -InputObject $item)
        }
        return ,$items
    }

    return $InputObject
}

function Resolve-ArtifactRoot {
    param(
        [string]$Package,
        [string]$Run,
        [string]$ExplicitRoot,
        [string]$RepoRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitRoot)) {
        return (Resolve-Path $ExplicitRoot).Path
    }
    if ([string]::IsNullOrWhiteSpace($Package)) {
        throw "PackageId is required when ArtifactRoot is not provided."
    }

    $phaseByPackage = @{
        "pkg-d1" = "phase2"
        "pkg-g1" = "phase2"
        "pkg-w1" = "phase2"
        "pkg-e1" = "phase3"
    }
    $phase = $phaseByPackage[$Package]
    if ([string]::IsNullOrWhiteSpace($phase)) {
        throw "Unsupported package id: $Package"
    }

    $packageRoot = Join-Path $RepoRoot "artifacts/mapscreen/$phase/$Package"
    if (-not (Test-Path $packageRoot)) {
        throw "Package artifact root not found: $packageRoot"
    }

    if (-not [string]::IsNullOrWhiteSpace($Run)) {
        $candidate = Join-Path $packageRoot $Run
        if (-not (Test-Path $candidate)) {
            throw "Run id '$Run' not found under $packageRoot"
        }
        return (Resolve-Path $candidate).Path
    }

    $latestCandidates = @(Get-ChildItem -Path $packageRoot -Directory |
        Where-Object { $_.Name -match "^\d{8}-\d{6}$" } |
        Sort-Object -Property Name -Descending)
    if ($latestCandidates.Count -gt 0) {
        return $latestCandidates[0].FullName
    }

    # Fallback for legacy/manual folder naming when canonical timestamp folders are not present.
    $latest = Get-ChildItem -Path $packageRoot -Directory | Sort-Object -Property Name -Descending | Select-Object -First 1
    if ($null -eq $latest) {
        throw "No artifact runs found under $packageRoot"
    }
    return $latest.FullName
}

function Get-PathValue {
    param(
        [AllowNull()]$Data,
        [Parameter(Mandatory = $true)][string]$Path
    )

    if ($null -eq $Data) { return $null }

    $current = $Data
    foreach ($part in ($Path -split "\.")) {
        if ($null -eq $current) { return $null }
        if ($current -is [System.Collections.IDictionary]) {
            if (-not $current.Contains($part)) { return $null }
            $current = $current[$part]
            continue
        }
        return $null
    }
    return $current
}

function Get-FirstValue {
    param(
        [AllowNull()]$Data,
        [Parameter(Mandatory = $true)][string[]]$Paths
    )
    foreach ($path in $Paths) {
        $value = Get-PathValue -Data $Data -Path $path
        if ($null -ne $value) {
            return [ordered]@{
                found = $true
                value = $value
                path = $path
            }
        }
    }
    return [ordered]@{
        found = $false
        value = $null
        path = $null
    }
}

function Try-ParseDouble {
    param([AllowNull()]$Value)
    if ($null -eq $Value) {
        return [ordered]@{ ok = $false; value = 0.0 }
    }
    try {
        return [ordered]@{ ok = $true; value = [double]$Value }
    } catch {
        return [ordered]@{ ok = $false; value = 0.0 }
    }
}

function Evaluate-Rule {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Rule,
        [AllowNull()]$TierData
    )

    $lookup = Get-FirstValue -Data $TierData -Paths $Rule.paths
    if (-not $lookup.found) {
        return [ordered]@{
            status = "missing_metric"
            pass = $false
            metric = $Rule.name
            detail = "Missing metric key. Tried: $($Rule.paths -join ', ')"
        }
    }

    $actual = $lookup.value
    $op = [string]$Rule.op
    $expected = $Rule.value
    $pass = $false
    $detail = ""

    if ($op -eq "eq_bool") {
        try {
            $pass = ([bool]$actual -eq [bool]$expected)
        } catch {
            $pass = $false
        }
        $detail = "actual=$actual expected=$expected"
    } else {
        $parsed = Try-ParseDouble -Value $actual
        if (-not $parsed.ok) {
            return [ordered]@{
                status = "invalid_metric"
                pass = $false
                metric = $Rule.name
                detail = "Metric '$($lookup.path)' must be numeric. actual=$actual"
            }
        }
        $actualDouble = [double]$parsed.value
        switch ($op) {
            "le" { $pass = ($actualDouble -le [double]$expected) }
            "lt" { $pass = ($actualDouble -lt [double]$expected) }
            "ge" { $pass = ($actualDouble -ge [double]$expected) }
            "gt" { $pass = ($actualDouble -gt [double]$expected) }
            "eq" { $pass = ($actualDouble -eq [double]$expected) }
            default { throw "Unsupported rule op: $op" }
        }
        $detail = "actual=$actualDouble op=$op expected=$expected"
    }

    return [ordered]@{
        status = if ($pass) { "pass" } else { "fail" }
        pass = $pass
        metric = $Rule.name
        metricPath = $lookup.path
        detail = $detail
    }
}

$sloRules = @{
    "MS-UX-01" = @(
        @{ name = "frame_time_p95_ms"; op = "le"; value = 16.7; paths = @("frame_time_p95_ms", "p95_frame_time_ms", "frame_time_ms_p95") },
        @{ name = "frame_time_p99_ms"; op = "le"; value = 24.0; paths = @("frame_time_p99_ms", "p99_frame_time_ms", "frame_time_ms_p99") },
        @{ name = "jank_percent"; op = "le"; value = 5.0; paths = @("jank_percent", "jank_rate_percent") }
    )
    "MS-UX-02" = @(
        @{ name = "drag_latency_p95_ms"; op = "le"; value = 50.0; paths = @("drag_latency_p95_ms", "latency_p95_ms") },
        @{ name = "drag_latency_p99_ms"; op = "le"; value = 75.0; paths = @("drag_latency_p99_ms", "latency_p99_ms") },
        @{ name = "full_teardown_per_move_count"; op = "eq"; value = 0.0; paths = @("full_teardown_per_move_count", "teardown_per_move_count") }
    )
    "MS-UX-03" = @(
        @{ name = "snap_events_per_target_per_5min"; op = "le"; value = 1.0; paths = @("snap_events_per_target_per_5min", "snap_event_rate") }
    )
    "MS-UX-04" = @(
        @{ name = "redundant_reorders_per_min"; op = "eq"; value = 0.0; paths = @("redundant_reorders_per_min", "steady_state_redundant_reorders_per_min") },
        @{ name = "reorders_per_transition"; op = "le"; value = 1.0; paths = @("reorders_per_transition", "reorder_per_transition") }
    )
    "MS-UX-05" = @(
        @{ name = "scrub_latency_p95_ms"; op = "le"; value = 120.0; paths = @("scrub_latency_p95_ms", "latency_p95_ms") },
        @{ name = "scrub_latency_p99_ms"; op = "le"; value = 180.0; paths = @("scrub_latency_p99_ms", "latency_p99_ms") },
        @{ name = "noop_recompute_count"; op = "eq"; value = 0.0; paths = @("noop_recompute_count", "no_op_recompute_count") }
    )
    "MS-UX-06" = @(
        @{ name = "cold_start_p95_ms"; op = "le"; value = 1800.0; paths = @("cold_start_p95_ms") },
        @{ name = "warm_start_p95_ms"; op = "le"; value = 900.0; paths = @("warm_start_p95_ms") },
        @{ name = "redraw_bursts_first_2s"; op = "le"; value = 2.0; paths = @("redraw_bursts_first_2s") }
    )
    "MS-ENG-01" = @(
        @{ name = "overlay_apply_p95_ms"; op = "le"; value = 30.0; paths = @("overlay_apply_p95_ms", "apply_duration_p95_ms") }
    )
    "MS-ENG-02" = @(
        @{ name = "startup_overlay_init_p95_ms"; op = "le"; value = 400.0; paths = @("startup_overlay_init_p95_ms", "overlay_init_p95_ms") }
    )
    "MS-ENG-03" = @(
        @{ name = "adsb_feature_build_p95_ms"; op = "le"; value = 8.0; paths = @("adsb_feature_build_p95_ms", "feature_build_p95_ms") }
    )
    "MS-ENG-04" = @(
        @{ name = "unconditional_full_sort_on_unchanged_keys"; op = "eq"; value = 0.0; paths = @("unconditional_full_sort_on_unchanged_keys", "full_sort_on_unchanged_keys_count") }
    )
    "MS-ENG-05" = @(
        @{ name = "scia_dense_trail_render_p95_ms"; op = "le"; value = 20.0; paths = @("scia_dense_trail_render_p95_ms", "dense_trail_render_p95_ms") }
    )
    "MS-ENG-06" = @(
        @{ name = "lifecycle_sync_invocations_per_transition"; op = "le"; value = 1.0; paths = @("lifecycle_sync_invocations_per_transition") }
    )
    "MS-ENG-07" = @(
        @{ name = "adsb_retarget_window_p95_ms"; op = "le"; value = 2000.0; paths = @("adsb_retarget_window_p95_ms", "retarget_window_p95_ms") },
        @{ name = "zero_seeded_window_count"; op = "eq"; value = 0.0; paths = @("zero_seeded_window_count", "zero_seeded_sample_window_count") }
    )
    "MS-ENG-08" = @(
        @{ name = "tile_size_mismatch_rebuild_miss_count"; op = "eq"; value = 0.0; paths = @("tile_size_mismatch_rebuild_miss_count", "cache_mismatch_count") }
    )
    "MS-ENG-09" = @(
        @{ name = "mapscreen_root_recomposition_ratio_vs_baseline"; op = "le"; value = 0.60; paths = @("mapscreen_root_recomposition_ratio_vs_baseline", "recomposition_ratio_vs_baseline") }
    )
    "MS-ENG-10" = @(
        @{ name = "duplicate_frame_owner_count_per_5min"; op = "eq"; value = 0.0; paths = @("duplicate_frame_owner_count_per_5min", "duplicate_frame_owner_count") }
    )
    "MS-ENG-11" = @(
        @{ name = "ogn_selection_alloc_bytes_ratio_vs_baseline"; op = "le"; value = 0.50; paths = @("ogn_selection_alloc_bytes_ratio_vs_baseline", "alloc_bytes_ratio_vs_baseline") },
        @{ name = "ogn_selection_alloc_count_ratio_vs_baseline"; op = "le"; value = 0.50; paths = @("ogn_selection_alloc_count_ratio_vs_baseline", "alloc_count_ratio_vs_baseline") }
    )
}

function Evaluate-SloTarget {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$SloTarget
    )

    $sloId = [string]$SloTarget.slo_id
    $status = [string]$SloTarget.status
    $postChange = $SloTarget.post_change
    $rules = $sloRules[$sloId]

    if ($status -like "pending*") {
        return [ordered]@{
            slo_id = $sloId
            status = "pending"
            reason = "status is $status"
            tier_results = @()
        }
    }
    if ($null -eq $postChange) {
        return [ordered]@{
            slo_id = $sloId
            status = "pending"
            reason = "post_change metrics are missing"
            tier_results = @()
        }
    }
    if ($null -eq $rules) {
        return [ordered]@{
            slo_id = $sloId
            status = "pending"
            reason = "no threshold rules configured for $sloId"
            tier_results = @()
        }
    }

    $tierA = Get-PathValue -Data $postChange -Path "tier_a"
    $tierB = Get-PathValue -Data $postChange -Path "tier_b"
    if ($null -eq $tierA -or $null -eq $tierB) {
        return [ordered]@{
            slo_id = $sloId
            status = "pending"
            reason = "post_change must include both tier_a and tier_b metrics"
            tier_results = @()
        }
    }

    $tierResults = @()
    foreach ($tier in @("tier_a", "tier_b")) {
        $tierData = Get-PathValue -Data $postChange -Path $tier
        $checks = @()
        foreach ($rule in $rules) {
            $checks += ,(Evaluate-Rule -Rule $rule -TierData $tierData)
        }
        $tierFailures = @($checks | Where-Object { -not $_.pass })
        $tierPass = $tierFailures.Count -eq 0
        $tierResults += ,([ordered]@{
            tier = $tier
            status = if ($tierPass) { "pass" } else { "fail" }
            checks = $checks
        })
    }

    $tierNonPass = @($tierResults | Where-Object { $_.status -ne "pass" })
    $overallPass = $tierNonPass.Count -eq 0
    return [ordered]@{
        slo_id = $sloId
        status = if ($overallPass) { "pass" } else { "fail" }
        reason = if ($overallPass) { "all threshold checks passed on tier_a and tier_b" } else { "one or more threshold checks failed" }
        tier_results = $tierResults
    }
}

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")
$artifactRootResolved = Resolve-ArtifactRoot -Package $PackageId -Run $RunId -ExplicitRoot $ArtifactRoot -RepoRoot $repoRoot

$manifestPath = Join-Path $artifactRootResolved "manifest.json"
$metricsPath = Join-Path $artifactRootResolved "metrics.json"
$gateResultPath = Join-Path $artifactRootResolved "gate_result.json"

foreach ($requiredPath in @($manifestPath, $metricsPath, $gateResultPath)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required artifact file missing: $requiredPath"
    }
}

$manifest = ConvertTo-Hashtable -InputObject (Get-Content -Path $manifestPath -Raw | ConvertFrom-Json)
$metrics = ConvertTo-Hashtable -InputObject (Get-Content -Path $metricsPath -Raw | ConvertFrom-Json)
$gateResult = ConvertTo-Hashtable -InputObject (Get-Content -Path $gateResultPath -Raw | ConvertFrom-Json)

$sloTargets = @($metrics.slo_targets)
$sloEvaluations = @()
foreach ($target in $sloTargets) {
    $sloEvaluations += @(Evaluate-SloTarget -SloTarget $target)
}

$commandGateFailures = @()
foreach ($gate in @($metrics.command_gates)) {
    $status = [string]$gate.status
    if ($status -ne "pass" -and $status -ne "pass_after_rerun") {
        $commandGateFailures += @("$($gate.label):$status")
    }
}

$determinismFailures = @()
if ($metrics.Contains("determinism_targets")) {
    foreach ($det in @($metrics.determinism_targets)) {
        $detStatus = [string]$det.status
        if ($detStatus -ne "unit_validated" -and $detStatus -ne "pass") {
            $determinismFailures += @("$($det.det_id):$detStatus")
        }
    }
}

$failedSlo = @($sloEvaluations | Where-Object { $_.status -eq "fail" })
$pendingSlo = @($sloEvaluations | Where-Object { $_.status -eq "pending" })

$promotionDecision = ""
if ($commandGateFailures.Count -gt 0 -or $determinismFailures.Count -gt 0 -or $failedSlo.Count -gt 0) {
    $promotionDecision = "blocked_failed_thresholds"
} elseif ($pendingSlo.Count -gt 0) {
    $promotionDecision = "blocked_pending_perf_evidence"
} else {
    $promotionDecision = "ready_for_promotion"
}

$summary = [ordered]@{
    checked_at = (Get-Date).ToString("o")
    artifact_root = $artifactRootResolved
    package_id = [string]$metrics.package_id
    phase = [string]$manifest.phase
    command_gates_pass = ($commandGateFailures.Count -eq 0)
    determinism_gates_pass = ($determinismFailures.Count -eq 0)
    failed_slo_count = $failedSlo.Count
    pending_slo_count = $pendingSlo.Count
    promotion_decision = $promotionDecision
    command_gate_failures = $commandGateFailures
    determinism_failures = $determinismFailures
    slo_evaluations = $sloEvaluations
}

$summaryPath = Join-Path $artifactRootResolved "threshold_check.json"
$summary | ConvertTo-Json -Depth 20 | Set-Content -Path $summaryPath -Encoding UTF8

if ($UpdateGateResult) {
    $gateResult.generated_at = (Get-Date).ToString("o")
    $gateResult.slo_gates = @($sloEvaluations | ForEach-Object {
        [ordered]@{
            slo_id = $_.slo_id
            status = if ($_.status -eq "pending") { "not_evaluated" } else { $_.status }
            reason = $_.reason
        }
    })
    $gateResult.promotion_decision = $promotionDecision

    if ($promotionDecision -eq "ready_for_promotion") {
        $gateResult.rollback_recommendation = "No rollback action needed; package is promotion-ready."
    } elseif ($promotionDecision -eq "blocked_pending_perf_evidence") {
        $gateResult.rollback_recommendation = "Complete tier_a/tier_b perf evidence and rerun threshold verification."
    } else {
        $gateResult.rollback_recommendation = "Do not promote; fix failing gates/SLOs and rerun verification."
    }

    $gateResult | ConvertTo-Json -Depth 20 | Set-Content -Path $gateResultPath -Encoding UTF8
}

Write-Host "[mapscreen-thresholds] Artifact: $artifactRootResolved"
Write-Host "[mapscreen-thresholds] Promotion decision: $promotionDecision"
Write-Host "[mapscreen-thresholds] Failed SLOs: $($failedSlo.Count)  Pending SLOs: $($pendingSlo.Count)"
Write-Host "[mapscreen-thresholds] Summary: $summaryPath"

if ($promotionDecision -eq "blocked_failed_thresholds") {
    exit 1
}
if ($promotionDecision -eq "blocked_pending_perf_evidence" -and -not $AllowPending) {
    exit 1
}
exit 0
