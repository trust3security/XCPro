param(
    [ValidateSet("pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1")]
    [string[]]$PackageIds = @("pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1"),
    [string]$RunId
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

function Resolve-RunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$PackageId,
        [string]$SpecificRunId
    )

    $phaseByPackage = @{
        "pkg-d1" = "phase2"
        "pkg-g1" = "phase2"
        "pkg-w1" = "phase2"
        "pkg-e1" = "phase3"
    }
    $phase = $phaseByPackage[$PackageId]
    if ([string]::IsNullOrWhiteSpace($phase)) {
        throw "Unsupported package id: $PackageId"
    }

    $packageRoot = Join-Path $RepoRoot "artifacts/mapscreen/$phase/$PackageId"
    if (-not (Test-Path $packageRoot)) {
        throw "Package root not found: $packageRoot"
    }

    if (-not [string]::IsNullOrWhiteSpace($SpecificRunId)) {
        $candidate = Join-Path $packageRoot $SpecificRunId
        if (-not (Test-Path $candidate)) {
            throw "RunId '$SpecificRunId' not found for $PackageId"
        }
        return (Resolve-Path $candidate).Path
    }

    $latest = Get-ChildItem -Path $packageRoot -Directory | Sort-Object -Property Name -Descending | Select-Object -First 1
    if ($null -eq $latest) {
        throw "No run directories found for $PackageId"
    }
    return $latest.FullName
}

function New-TierTemplate {
    param([Parameter(Mandatory = $true)][string[]]$Keys)
    $template = [ordered]@{}
    foreach ($key in $Keys) {
        $template[$key] = $null
    }
    return $template
}

$requiredKeysBySlo = @{
    "MS-UX-01" = @("frame_time_p95_ms", "frame_time_p99_ms", "jank_percent")
    "MS-UX-05" = @("scrub_latency_p95_ms", "scrub_latency_p99_ms", "noop_recompute_count")
    "MS-ENG-03" = @("adsb_feature_build_p95_ms")
    "MS-ENG-07" = @("adsb_retarget_window_p95_ms", "zero_seeded_window_count")
    "MS-ENG-08" = @("tile_size_mismatch_rebuild_miss_count")
    "MS-ENG-04" = @("unconditional_full_sort_on_unchanged_keys")
    "MS-ENG-05" = @("scia_dense_trail_render_p95_ms")
    "MS-ENG-11" = @("ogn_selection_alloc_bytes_ratio_vs_baseline", "ogn_selection_alloc_count_ratio_vs_baseline")
}

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")

foreach ($packageId in $PackageIds) {
    $runDir = Resolve-RunDirectory -RepoRoot $repoRoot -PackageId $packageId -SpecificRunId $RunId
    $metricsPath = Join-Path $runDir "metrics.json"
    if (-not (Test-Path $metricsPath)) {
        throw "metrics.json not found: $metricsPath"
    }

    $metrics = ConvertTo-Hashtable -InputObject (Get-Content -Path $metricsPath -Raw | ConvertFrom-Json)
    if ($null -eq $metrics.slo_targets) {
        throw "slo_targets missing in $metricsPath"
    }

    $changed = $false
    if (-not ($metrics.slo_targets -is [System.Array])) {
        $changed = $true
    }
    $normalizedTargets = @()
    foreach ($target in @($metrics.slo_targets)) {
        $sloId = [string]$target.slo_id
        $requiredKeys = $requiredKeysBySlo[$sloId]
        if ($null -eq $requiredKeys) {
            $normalizedTargets += @($target)
            continue
        }

        if ($null -eq $target.post_change) {
            $target.post_change = [ordered]@{
                tier_a = (New-TierTemplate -Keys $requiredKeys)
                tier_b = (New-TierTemplate -Keys $requiredKeys)
            }
            $changed = $true
            $normalizedTargets += @($target)
            continue
        }

        foreach ($tierName in @("tier_a", "tier_b")) {
            if ($null -eq $target.post_change[$tierName]) {
                $target.post_change[$tierName] = New-TierTemplate -Keys $requiredKeys
                $changed = $true
                continue
            }
            foreach ($key in $requiredKeys) {
                if (-not $target.post_change[$tierName].Contains($key)) {
                    $target.post_change[$tierName][$key] = $null
                    $changed = $true
                }
            }
        }
        $normalizedTargets += @($target)
    }
    $metrics.slo_targets = $normalizedTargets

    if ($changed) {
        $metrics | ConvertTo-Json -Depth 20 | Set-Content -Path $metricsPath -Encoding UTF8
        Write-Host "[mapscreen-template] Seeded metric template keys in: $metricsPath"
    } else {
        Write-Host "[mapscreen-template] No template changes needed: $metricsPath"
    }
}

exit 0
