param(
    [string]$RunId,
    [switch]$SkipThresholdCheck,
    [switch]$AllowNonGreenPackages
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-Hashtable {
    param([AllowNull()]$InputObject)

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
        return $items
    }

    return $InputObject
}

function Resolve-LatestRunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$PackageId,
        [Parameter(Mandatory = $true)][string]$Phase
    )

    $packageRoot = Join-Path $RepoRoot "artifacts/mapscreen/$Phase/$PackageId"
    if (-not (Test-Path $packageRoot)) {
        throw "Package root not found: $packageRoot"
    }

    $latest = @(Get-ChildItem -Path $packageRoot -Directory |
        Where-Object { $_.Name -match "^\d{8}-\d{6}$" } |
        Sort-Object -Property Name -Descending |
        Select-Object -First 1)
    if ($latest.Count -eq 0) {
        throw "No canonical run directories found under $packageRoot"
    }
    return $latest[0].FullName
}

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = Get-Date -Format "yyyyMMdd-HHmmss"
}

if (-not $SkipThresholdCheck) {
    $thresholdScript = Join-Path $PSScriptRoot "run_mapscreen_evidence_threshold_checks.ps1"
    & $thresholdScript -UpdateGateResults
    if ($LASTEXITCODE -ne 0 -and -not $AllowNonGreenPackages) {
        throw "Threshold checks failed; release package cannot be finalized."
    }
}

$packagePhaseMap = [ordered]@{
    "pkg-d1" = "phase2"
    "pkg-g1" = "phase2"
    "pkg-w1" = "phase2"
    "pkg-e1" = "phase3"
}

$packageSummaries = @()
foreach ($packageId in $packagePhaseMap.Keys) {
    $phase = [string]$packagePhaseMap[$packageId]
    $runDir = Resolve-LatestRunDirectory -RepoRoot $repoRoot -PackageId $packageId -Phase $phase
    $thresholdPath = Join-Path $runDir "threshold_check.json"
    if (-not (Test-Path $thresholdPath)) {
        throw "Missing threshold_check.json for ${packageId}: $thresholdPath"
    }
    $threshold = ConvertTo-Hashtable -InputObject (Get-Content -Raw $thresholdPath | ConvertFrom-Json)
    $packageSummaries += @([ordered]@{
        package_id = $packageId
        phase = $phase
        run_id = Split-Path -Leaf $runDir
        artifact_root = $runDir
        promotion_decision = [string]$threshold.promotion_decision
        failed_slo_count = [int]$threshold.failed_slo_count
        pending_slo_count = [int]$threshold.pending_slo_count
    })
}

$nonGreen = @($packageSummaries | Where-Object { $_.promotion_decision -ne "ready_for_promotion" })
if ($nonGreen.Count -gt 0 -and -not $AllowNonGreenPackages) {
    $details = ($nonGreen | ForEach-Object { "$($_.package_id):$($_.promotion_decision)" }) -join ", "
    throw "Release package blocked; non-green packages detected: $details"
}

$releaseArtifactRoot = Join-Path $repoRoot "artifacts/mapscreen/phase4/pkg-r1/$RunId"
New-Item -ItemType Directory -Path $releaseArtifactRoot -Force | Out-Null

$sha = (git rev-parse HEAD).Trim()
$branch = (git branch --show-current).Trim()
$dirty = -not [string]::IsNullOrWhiteSpace((git status --porcelain))
$generatedAt = (Get-Date).ToString("o")

$manifest = [ordered]@{
    package_id = "pkg-r1"
    phase = "phase4"
    generated_at = $generatedAt
    commit_sha = $sha
    branch = $branch
    dirty_worktree = $dirty
    depends_on_packages = @("pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1")
    package_runs = $packageSummaries
}

$metrics = [ordered]@{
    package_id = "pkg-r1"
    generated_at = $generatedAt
    quality_summary = [ordered]@{
        total_packages = $packageSummaries.Count
        ready_for_promotion_count = @($packageSummaries | Where-Object { $_.promotion_decision -eq "ready_for_promotion" }).Count
        blocked_count = $nonGreen.Count
    }
    package_decisions = $packageSummaries
}

$traceIndex = [ordered]@{
    package_id = "pkg-r1"
    generated_at = $generatedAt
    upstream_threshold_checks = @($packageSummaries | ForEach-Object {
        [ordered]@{
            package_id = $_.package_id
            phase = $_.phase
            run_id = $_.run_id
            threshold_check = "artifacts/mapscreen/$($_.phase)/$($_.package_id)/$($_.run_id)/threshold_check.json"
        }
    })
}

$promotionDecision = if ($nonGreen.Count -eq 0) { "ready_for_promotion" } else { "blocked_failed_thresholds" }
$gateResult = [ordered]@{
    package_id = "pkg-r1"
    phase = "phase4"
    generated_at = $generatedAt
    upstream_package_gates = @($packageSummaries | ForEach-Object {
        [ordered]@{
            package_id = $_.package_id
            run_id = $_.run_id
            status = $_.promotion_decision
            failed_slo_count = $_.failed_slo_count
            pending_slo_count = $_.pending_slo_count
        }
    })
    promotion_decision = $promotionDecision
    rollback_recommendation = if ($promotionDecision -eq "ready_for_promotion") {
        "No rollback action needed; all upstream package lanes are green."
    } else {
        "Do not promote; resolve non-green upstream package gates and rerun release finalize."
    }
}

$timebaseCitations = @'
# Timebase Citations

Required citations:

1. `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
2. `app/src/main/java/com/example/xcpro/di/TimeModule.kt`
'@

$summaryText = @(
    "# Mapscreen Release Package Summary",
    "",
    "Generated at: $generatedAt",
    "Release package: pkg-r1/$RunId",
    "",
    "Upstream package decisions:",
    ""
) + @($packageSummaries | ForEach-Object {
    "- $($_.package_id) [$($_.phase)/$($_.run_id)]: $($_.promotion_decision) (failed_slo=$($_.failed_slo_count), pending_slo=$($_.pending_slo_count))"
}) + @(
    "",
    "Overall promotion decision: $promotionDecision"
)

$manifest | ConvertTo-Json -Depth 20 | Set-Content -Path (Join-Path $releaseArtifactRoot "manifest.json") -Encoding UTF8
$metrics | ConvertTo-Json -Depth 20 | Set-Content -Path (Join-Path $releaseArtifactRoot "metrics.json") -Encoding UTF8
$traceIndex | ConvertTo-Json -Depth 20 | Set-Content -Path (Join-Path $releaseArtifactRoot "trace_index.json") -Encoding UTF8
$gateResult | ConvertTo-Json -Depth 20 | Set-Content -Path (Join-Path $releaseArtifactRoot "gate_result.json") -Encoding UTF8
$timebaseCitations | Set-Content -Path (Join-Path $releaseArtifactRoot "timebase_citations.md") -Encoding UTF8
($summaryText -join "`r`n") | Set-Content -Path (Join-Path $releaseArtifactRoot "release_summary.md") -Encoding UTF8

Write-Host "[mapscreen-release] Finalized release package artifact: $releaseArtifactRoot"
Write-Host "[mapscreen-release] Promotion decision: $promotionDecision"
if ($promotionDecision -ne "ready_for_promotion") {
    if ($AllowNonGreenPackages) {
        Write-Host "[mapscreen-release] Non-green release package allowed by flag; returning success for reporting workflow."
        exit 0
    }
    exit 1
}
exit 0
