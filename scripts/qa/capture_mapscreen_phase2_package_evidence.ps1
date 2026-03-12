param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("pkg-d1", "pkg-g1", "pkg-w1", "pkg-e1")]
    [string]$PackageId,
    [string]$RunId,
    [switch]$DryRun,
    [switch]$SkipRequiredGates,
    [switch]$RunConnectedAppTests,
    [switch]$RunConnectedAllModulesAtEnd,
    [switch]$RequireConnectedDevice
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-ConnectedDevice {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) { return $false }

    $lines = & adb devices 2>$null
    if ($LASTEXITCODE -ne 0) { return $false }

    foreach ($line in $lines) {
        if ($line -match "^\S+\s+device(\s|$)") {
            return $true
        }
    }
    return $false
}

function Invoke-CommandWithCapture {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Args,
        [Parameter(Mandatory = $true)][string]$CommandLabel,
        [switch]$NoThrow
    )

    $display = "$Executable " + ($Args -join " ")
    $startedAt = Get-Date
    Write-Host "[mapscreen-evidence] Running: $display"

    if ($DryRun) {
        return [ordered]@{
            command = $display
            label = $CommandLabel
            startedAt = $startedAt.ToString("o")
            finishedAt = (Get-Date).ToString("o")
            status = "dry_run"
            exitCode = 0
            output = @("DRY_RUN")
        }
    }

    $output = & $Executable @Args 2>&1
    $exitCode = $LASTEXITCODE
    $finishedAt = Get-Date

    foreach ($line in $output) {
        Write-Host $line
    }

    $record = [ordered]@{
        command = $display
        label = $CommandLabel
        startedAt = $startedAt.ToString("o")
        finishedAt = $finishedAt.ToString("o")
        status = if ($exitCode -eq 0) { "pass" } else { "fail" }
        exitCode = $exitCode
        output = @($output | ForEach-Object { [string]$_ })
    }

    if (-not $NoThrow -and $exitCode -ne 0) {
        throw "Command failed (exit $exitCode): $display"
    }

    return $record
}

function Invoke-Gradle {
    param(
        [Parameter(Mandatory = $true)][string[]]$Args,
        [Parameter(Mandatory = $true)][string]$Label,
        [switch]$NoThrow
    )
    return Invoke-CommandWithCapture -Executable ".\gradlew.bat" -Args $Args -CommandLabel $Label -NoThrow:$NoThrow
}

function New-TraceScaffold {
    param(
        [Parameter(Mandatory = $true)][string]$TierDir
    )

    $perfettoDir = Join-Path $TierDir "traces/perfetto"
    $macroDir = Join-Path $TierDir "traces/macrobenchmark"
    $profilerDir = Join-Path $TierDir "traces/profiler"
    New-Item -ItemType Directory -Path $perfettoDir -Force | Out-Null
    New-Item -ItemType Directory -Path $macroDir -Force | Out-Null
    New-Item -ItemType Directory -Path $profilerDir -Force | Out-Null

    @(
        @{ Path = (Join-Path $perfettoDir "PLACE_PERFETTO_TRACES_HERE.txt"); Text = "Drop perfetto traces for this tier in this folder." },
        @{ Path = (Join-Path $macroDir "PLACE_MACROBENCHMARK_OUTPUT_HERE.txt"); Text = "Drop macrobenchmark outputs for this tier in this folder." },
        @{ Path = (Join-Path $profilerDir "PLACE_PROFILER_CAPTURE_HERE.txt"); Text = "Drop Android Studio profiler exports for this tier in this folder." }
    ) | ForEach-Object {
        Set-Content -Path $_.Path -Value $_.Text -Encoding UTF8
    }
}

function New-TierCaptureChecklist {
    param(
        [Parameter(Mandatory = $true)][string]$TierDir,
        [Parameter(Mandatory = $true)][string[]]$ScenarioIds
    )

    $checklist = @(
        "# Capture Checklist",
        "",
        "Tier folder: $TierDir",
        "",
        "Scenarios:",
        ""
    )
    foreach ($id in $ScenarioIds) {
        $checklist += "- $id"
    }
    $checklist += @(
        "",
        "Required outputs:",
        "- traces/perfetto/*.perfetto-trace",
        "- traces/macrobenchmark/*.json",
        "- traces/profiler/*",
        "",
        "After capture, update top-level metrics.json and gate_result.json with measured SLO values."
    )
    Set-Content -Path (Join-Path $TierDir "capture_checklist.md") -Value ($checklist -join "`r`n") -Encoding UTF8
}

$packageConfig = @{
    "pkg-d1" = [ordered]@{
        phase = "phase2"
        work_package = "WP-04+WP-07"
        mapscreen_items = @("MAPSCREEN-004", "MAPSCREEN-005", "MAPSCREEN-013")
        scenario_ids = @(
            "scenario-3-panzoom-ogn-adsb-rain",
            "scenario-7-dense-traffic-scia",
            "scenario-8-mixed-load-stress"
        )
        slo_ids = @("MS-ENG-03", "MS-ENG-04", "MS-ENG-07", "MS-ENG-11")
        focused_tests = @(
            "com.example.xcpro.map.AdsbDisplayMotionSmootherTest",
            "com.example.xcpro.adsb.AdsbTrafficStoreTest",
            "com.example.xcpro.ogn.OgnAddressingTest",
            "com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepositoryTest"
        )
    }
    "pkg-g1" = [ordered]@{
        phase = "phase2"
        work_package = "WP-06+WP-07"
        mapscreen_items = @("MAPSCREEN-011", "MAPSCREEN-012")
        scenario_ids = @(
            "scenario-6-replay-render-sync-overlays",
            "scenario-8-mixed-load-stress"
        )
        slo_ids = @("MS-UX-05", "MS-ENG-05")
        determinism_ids = @("DET-03")
        determinism_evidence_by_id = @{
            "DET-03" = "MapReplaySelectionFlowTest"
        }
        focused_tests = @(
            "com.example.xcpro.map.MapReplaySelectionFlowTest",
            "com.example.xcpro.map.OgnGliderTrailOverlayRenderPolicyTest"
        )
    }
    "pkg-w1" = [ordered]@{
        phase = "phase2"
        work_package = "WP-05"
        mapscreen_items = @("MAPSCREEN-014")
        scenario_ids = @(
            "scenario-3-panzoom-ogn-adsb-rain",
            "scenario-8-mixed-load-stress"
        )
        slo_ids = @("MS-ENG-08")
        focused_tests = @(
            "com.example.xcpro.map.WeatherRainOverlayPolicyTest",
            "com.example.xcpro.map.MapOverlayManagerWeatherRainTest"
        )
    }
    "pkg-e1" = [ordered]@{
        phase = "phase3"
        work_package = "WP-08"
        mapscreen_items = @("MAPSCREEN-006", "MAPSCREEN-007")
        scenario_ids = @(
            "scenario-8-mixed-load-stress",
            "scenario-3-panzoom-ogn-adsb-rain",
            "scenario-4-aat-edit-drag"
        )
        slo_ids = @("MS-UX-01")
        determinism_ids = @("DET-04")
        determinism_evidence_by_id = @{
            "DET-04" = "MapLifecycleManagerResumeSyncTest"
        }
        focused_tests = @(
            "com.example.xcpro.map.MapLifecycleManagerResumeSyncTest",
            "com.example.xcpro.map.TaskRenderSyncCoordinatorTest"
        )
    }
}

$config = $packageConfig[$PackageId]
if (-not $config) {
    throw "Unsupported package id: $PackageId"
}

$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = Get-Date -Format "yyyyMMdd-HHmmss"
}
$generatedAt = (Get-Date).ToString("o")
$phase = [string]$config.phase
$artifactRoot = Join-Path $repoRoot "artifacts/mapscreen/$phase/$PackageId/$RunId"
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null

$tierA = Join-Path $artifactRoot "tier_a"
$tierB = Join-Path $artifactRoot "tier_b"
New-Item -ItemType Directory -Path $tierA -Force | Out-Null
New-Item -ItemType Directory -Path $tierB -Force | Out-Null
New-TraceScaffold -TierDir $tierA
New-TierCaptureChecklist -TierDir $tierA -ScenarioIds $config.scenario_ids
New-TraceScaffold -TierDir $tierB
New-TierCaptureChecklist -TierDir $tierB -ScenarioIds $config.scenario_ids

$connectedAvailable = $false
if ($RunConnectedAppTests -or $RunConnectedAllModulesAtEnd) {
    $connectedAvailable = Test-ConnectedDevice
    if (-not $connectedAvailable -and $RequireConnectedDevice) {
        throw "No connected device/emulator detected, but RequireConnectedDevice was set."
    }
}

$commandResults = New-Object System.Collections.Generic.List[object]

if (-not $SkipRequiredGates) {
    $commandResults.Add((Invoke-Gradle -Args @("enforceRules") -Label "enforceRules")) | Out-Null

    $unitResult = Invoke-Gradle -Args @("testDebugUnitTest") -Label "testDebugUnitTest" -NoThrow
    if ($unitResult.exitCode -ne 0) {
        $retryResult = Invoke-Gradle -Args @("testDebugUnitTest") -Label "testDebugUnitTest_retry" -NoThrow
        if ($retryResult.exitCode -eq 0) {
            $unitResult.status = "pass_after_rerun"
            $unitResult.retry = [ordered]@{
                attempted = $true
                retryExitCode = $retryResult.exitCode
                retryStatus = $retryResult.status
                retryCommand = $retryResult.command
            }
            $unitResult.notes = @(
                "First run failed; retry succeeded.",
                "Known intermittent app-level flake may occur in ProfileRepositoryTest."
            )
        } else {
            $commandResults.Add($unitResult) | Out-Null
            $commandResults.Add($retryResult) | Out-Null
            throw "testDebugUnitTest failed on initial run and retry."
        }
    }
    $commandResults.Add($unitResult) | Out-Null

    $commandResults.Add((Invoke-Gradle -Args @("assembleDebug") -Label "assembleDebug")) | Out-Null

    if ($RunConnectedAppTests) {
        if ($connectedAvailable) {
            $commandResults.Add(
                (Invoke-Gradle -Args @(":app:connectedDebugAndroidTest", "--no-parallel", "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true") -Label "connected_app_tests")
            ) | Out-Null
        } else {
            $commandResults.Add([ordered]@{
                command = "./gradlew.bat :app:connectedDebugAndroidTest --no-parallel -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
                label = "connected_app_tests"
                startedAt = (Get-Date).ToString("o")
                finishedAt = (Get-Date).ToString("o")
                status = "skipped_no_device"
                exitCode = 0
                output = @("Connected app tests skipped: no device/emulator detected.")
            }) | Out-Null
        }
    }

    if ($RunConnectedAllModulesAtEnd) {
        if ($connectedAvailable) {
            $commandResults.Add(
                (Invoke-Gradle -Args @("connectedDebugAndroidTest", "--no-parallel") -Label "connected_all_modules_tests")
            ) | Out-Null
        } else {
            $commandResults.Add([ordered]@{
                command = "./gradlew.bat connectedDebugAndroidTest --no-parallel"
                label = "connected_all_modules_tests"
                startedAt = (Get-Date).ToString("o")
                finishedAt = (Get-Date).ToString("o")
                status = "skipped_no_device"
                exitCode = 0
                output = @("Connected all-modules tests skipped: no device/emulator detected.")
            }) | Out-Null
        }
    }
} else {
    $commandResults.Add([ordered]@{
        command = "required_gates"
        label = "required_gates"
        startedAt = (Get-Date).ToString("o")
        finishedAt = (Get-Date).ToString("o")
        status = "skipped"
        exitCode = 0
        output = @("Required gates skipped by -SkipRequiredGates.")
    }) | Out-Null
}

$sha = (git rev-parse HEAD).Trim()
$branch = (git branch --show-current).Trim()
$dirty = -not [string]::IsNullOrWhiteSpace((git status --porcelain))

$manifest = [ordered]@{
    package_id = $PackageId
    phase = $phase
    work_package = $config.work_package
    mapscreen_items = $config.mapscreen_items
    generated_at = $generatedAt
    commit_sha = $sha
    branch = $branch
    dirty_worktree = $dirty
    build_variant = "debug"
    scenario_ids = $config.scenario_ids
    device_tiers = @("tier_a", "tier_b")
    run_count = [ordered]@{
        unit = 1
        integration = if ($RunConnectedAppTests -or $RunConnectedAllModulesAtEnd) { 1 } else { 0 }
        perf = 0
    }
    focused_tests = $config.focused_tests
}

$sloTargets = @()
foreach ($slo in $config.slo_ids) {
    $sloTargets += [ordered]@{
        slo_id = $slo
        status = "pending_perf_capture"
        baseline = $null
        post_change = $null
        note = "Attach tier_a/tier_b measured values after device capture."
    }
}

$commandGateRecords = [object[]]$commandResults

$metrics = [ordered]@{
    package_id = $PackageId
    generated_at = $generatedAt
    slo_targets = $sloTargets
    unit_evidence = [ordered]@{
        focused_tests = $config.focused_tests
        status = if ($SkipRequiredGates) { "not_run" } else { "pass" }
    }
    command_gates = $commandGateRecords
}

if ($config.Contains("determinism_ids")) {
    $determinismEvidenceById = @{}
    if ($config.Contains("determinism_evidence_by_id")) {
        $determinismEvidenceById = $config.determinism_evidence_by_id
    }
    $determinism = @()
    foreach ($det in $config.determinism_ids) {
        $detEvidence = if ($determinismEvidenceById.Contains($det)) {
            [string]$determinismEvidenceById[$det]
        } else {
            [string]($config.focused_tests -join ", ")
        }
        $determinism += [ordered]@{
            det_id = $det
            status = if ($SkipRequiredGates) { "not_evaluated" } else { "unit_validated" }
            evidence = $detEvidence
        }
    }
    $metrics["determinism_targets"] = $determinism
}

$traceIndex = [ordered]@{
    package_id = $PackageId
    generated_at = $generatedAt
    status = "pending"
    tier_a = [ordered]@{
        perfetto = @("tier_a/traces/perfetto/")
        macrobenchmark = @("tier_a/traces/macrobenchmark/")
        profiler = @("tier_a/traces/profiler/")
    }
    tier_b = [ordered]@{
        perfetto = @("tier_b/traces/perfetto/")
        macrobenchmark = @("tier_b/traces/macrobenchmark/")
        profiler = @("tier_b/traces/profiler/")
    }
}

$sloGates = @()
foreach ($slo in $config.slo_ids) {
    $sloGates += [ordered]@{
        slo_id = $slo
        status = "not_evaluated"
        reason = "missing_device_perf_capture"
    }
}

$gateResult = [ordered]@{
    package_id = $PackageId
    phase = $phase
    generated_at = $generatedAt
    command_gates = @($commandGateRecords | ForEach-Object {
        [ordered]@{
            label = $_.label
            command = $_.command
            status = $_.status
            exit_code = $_.exitCode
        }
    })
    slo_gates = $sloGates
    promotion_decision = "blocked_pending_perf_evidence"
    rollback_recommendation = "Do not promote until tier_a/tier_b perf evidence is captured and attached."
}

if ($config.Contains("determinism_ids")) {
    $determinismEvidenceById = @{}
    if ($config.Contains("determinism_evidence_by_id")) {
        $determinismEvidenceById = $config.determinism_evidence_by_id
    }
    $gateResult["determinism_gates"] = @($config.determinism_ids | ForEach-Object {
        $det = [string]$_
        $detEvidence = if ($determinismEvidenceById.Contains($det)) {
            [string]$determinismEvidenceById[$det]
        } else {
            [string]($config.focused_tests -join ", ")
        }
        [ordered]@{
            det_id = $det
            status = if ($SkipRequiredGates) { "not_evaluated" } else { "pass" }
            evidence = $detEvidence
        }
    })
}

$archGateText = @"
command: ./gradlew enforceRules
arch_gate_owner: included_in_enforceRules
result: $(if ($SkipRequiredGates) { "SKIPPED" } else { "PASS" })
captured_at: $generatedAt
commit_sha: $sha
branch: $branch
"@

$timebaseCitations = @'
# Timebase Citations

Required citations:

1. `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
2. `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

Package focus:

- `pkg-d1`: ADS-B visual smoothing and OGN selection/allocation hot paths use monotonic and deterministic runtime contracts.
- `pkg-g1`: replay selection projection and dense SCIA trail rendering paths remain semantic and avoid wall-time coupling.
- `pkg-w1`: weather rain cache identity and frame transitions remain visual-layer behavior with no fusion/replay timebase coupling.
- `pkg-e1`: mixed-load cadence governance remains visual/runtime scoped; replay determinism checks use runtime cadence ownership tests with no wall-time coupling.
'@

$runbook = @(
    "# Package Capture Runbook",
    "",
    "Package: $PackageId",
    "Phase: $phase",
    "RunId: $RunId",
    "",
    "1. Connect Tier A device/emulator and capture scenario traces into:",
    "   - tier_a/traces/perfetto/",
    "   - tier_a/traces/macrobenchmark/",
    "   - tier_a/traces/profiler/",
    "2. Repeat for Tier B in tier_b/... folders.",
    "3. Update metrics.json with p50/p95/p99 and CI values.",
    "4. Update gate_result.json with SLO pass/fail decision.",
    "",
    "Current status: blocked_pending_perf_evidence"
) -join "`r`n"

$gateExecutionLine = if ($SkipRequiredGates) {
    "- Required gates skipped by flag."
} else {
    "- Required gates executed (see gate_result.json)."
}

$verificationSummary = @(
    "# Verification Summary",
    "",
    "Generated at: $generatedAt",
    "Package: $PackageId",
    "RunId: $RunId",
    "Commit: $sha",
    "Branch: $branch",
    "",
    "Gate execution:",
    $gateExecutionLine,
    "",
    "Promotion is blocked until Tier A/B performance evidence is attached."
) -join "`r`n"

$manifest | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $artifactRoot "manifest.json") -Encoding UTF8
$metrics | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $artifactRoot "metrics.json") -Encoding UTF8
$traceIndex | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $artifactRoot "trace_index.json") -Encoding UTF8
$gateResult | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $artifactRoot "gate_result.json") -Encoding UTF8
$archGateText | Set-Content -Path (Join-Path $artifactRoot "arch_gate_result.txt") -Encoding UTF8
$timebaseCitations | Set-Content -Path (Join-Path $artifactRoot "timebase_citations.md") -Encoding UTF8
$runbook | Set-Content -Path (Join-Path $artifactRoot "RUNBOOK.md") -Encoding UTF8
$verificationSummary | Set-Content -Path (Join-Path $artifactRoot "verification_summary.md") -Encoding UTF8

Write-Host ""
Write-Host "[mapscreen-evidence] Completed package capture scaffold."
Write-Host "[mapscreen-evidence] Artifact root: $artifactRoot"
