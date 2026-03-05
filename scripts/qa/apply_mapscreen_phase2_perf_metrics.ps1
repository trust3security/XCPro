param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("pkg-d1", "pkg-g1", "pkg-w1")]
    [string]$PackageId,
    [string]$RunId,
    [switch]$SkipPerfEvidenceTests,
    [switch]$UpdateGateResult
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

function Resolve-RunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Package,
        [string]$SpecificRunId
    )

    $packageRoot = Join-Path $RepoRoot "artifacts/mapscreen/phase2/$Package"
    if (-not (Test-Path $packageRoot)) {
        throw "Package root not found: $packageRoot"
    }

    if (-not [string]::IsNullOrWhiteSpace($SpecificRunId)) {
        $candidate = Join-Path $packageRoot $SpecificRunId
        if (-not (Test-Path $candidate)) {
            throw "RunId '$SpecificRunId' not found for $Package"
        }
        return (Resolve-Path $candidate).Path
    }

    $latest = @(Get-ChildItem -Path $packageRoot -Directory |
        Where-Object { $_.Name -match "^\d{8}-\d{6}$" } |
        Sort-Object -Property Name -Descending |
        Select-Object -First 1)
    if ($latest.Count -eq 0) {
        throw "No canonical run directories found for $Package in $packageRoot"
    }
    return $latest[0].FullName
}

function Invoke-PerfEvidenceTest {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$TestClass
    )

    Write-Host "[mapscreen-perf] Running evidence test: $TestClass"
    & .\gradlew.bat :feature:map:testDebugUnitTest --tests $TestClass
    if ($LASTEXITCODE -ne 0) {
        throw "Evidence test failed: $TestClass"
    }
}

function Resolve-EvidenceReportPath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Package
    )

    $candidatePaths = @(
        (Join-Path $RepoRoot "feature/map/build/reports/perf/mapscreen/$Package-evidence.json"),
        (Join-Path $RepoRoot "build/reports/perf/mapscreen/$Package-evidence.json")
    )
    foreach ($path in $candidatePaths) {
        if (Test-Path $path) {
            return (Resolve-Path $path).Path
        }
    }

    $searched = @(Get-ChildItem -Path (Join-Path $RepoRoot "feature/map") -Recurse -File -Filter "$Package-evidence.json" -ErrorAction SilentlyContinue |
        Sort-Object -Property LastWriteTime -Descending)
    if ($searched.Count -gt 0) {
        return $searched[0].FullName
    }

    throw "Evidence report not found for $Package. Expected one of: $($candidatePaths -join ', ')"
}

$testClassByPackage = @{
    "pkg-d1" = "com.example.xcpro.map.MapscreenPkgD1PerfEvidenceTest"
    "pkg-g1" = "com.example.xcpro.map.MapscreenPkgG1PerfEvidenceTest"
    "pkg-w1" = "com.example.xcpro.map.MapscreenPkgW1PerfEvidenceTest"
}

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")
Set-Location $repoRoot

$runDir = Resolve-RunDirectory -RepoRoot $repoRoot -Package $PackageId -SpecificRunId $RunId
$testClass = $testClassByPackage[$PackageId]
if ([string]::IsNullOrWhiteSpace($testClass)) {
    throw "Unsupported package id: $PackageId"
}

if (-not $SkipPerfEvidenceTests) {
    Invoke-PerfEvidenceTest -RepoRoot $repoRoot -TestClass $testClass
}

$reportPath = Resolve-EvidenceReportPath -RepoRoot $repoRoot -Package $PackageId
$evidence = ConvertTo-Hashtable -InputObject (Get-Content -Raw $reportPath | ConvertFrom-Json)
if ($null -eq $evidence -or -not $evidence.Contains("slo_metrics")) {
    throw "Invalid evidence report (missing slo_metrics): $reportPath"
}

$sloMetrics = $evidence["slo_metrics"]
$metricsPath = Join-Path $runDir "metrics.json"
if (-not (Test-Path $metricsPath)) {
    throw "metrics.json not found: $metricsPath"
}

$metrics = ConvertTo-Hashtable -InputObject (Get-Content -Raw $metricsPath | ConvertFrom-Json)
if ($null -eq $metrics.slo_targets) {
    throw "slo_targets missing in $metricsPath"
}

$updatedTargets = @()
foreach ($target in @($metrics.slo_targets)) {
    $sloId = [string]$target.slo_id
    if (-not $sloMetrics.Contains($sloId)) {
        $updatedTargets += @($target)
        continue
    }

    $metricValues = ConvertTo-Hashtable -InputObject $sloMetrics[$sloId]
    if ($null -eq $metricValues) {
        throw "Empty metric payload for $sloId in $reportPath"
    }

    if (-not $target.Contains("post_change") -or $null -eq $target.post_change) {
        $target.post_change = [ordered]@{}
    }
    foreach ($tier in @("tier_a", "tier_b")) {
        $tierPayload = [ordered]@{}
        foreach ($key in $metricValues.Keys) {
            $tierPayload[$key] = $metricValues[$key]
        }
        $target.post_change[$tier] = $tierPayload
    }
    $target.status = "captured"
    $target.note = "Measured by $testClass and applied to tier_a/tier_b from host perf evidence."
    $updatedTargets += @($target)
}
$metrics.slo_targets = $updatedTargets

$metrics | ConvertTo-Json -Depth 20 | Set-Content -Path $metricsPath -Encoding UTF8
Write-Host "[mapscreen-perf] Applied metrics from: $reportPath"
Write-Host "[mapscreen-perf] Updated artifact metrics: $metricsPath"

if ($UpdateGateResult) {
    $verifyScript = Join-Path $PSScriptRoot "verify_mapscreen_package_evidence.ps1"
    & $verifyScript -PackageId $PackageId -RunId (Split-Path -Leaf $runDir) -UpdateGateResult -AllowPending
    if ($LASTEXITCODE -ne 0) {
        throw "Threshold verification reported failure for $PackageId/$((Split-Path -Leaf $runDir))."
    }
}

exit 0
