param(
    [ValidateRange(0, 8)]
    [int]$FromPhase = 0,
    [ValidateRange(0, 8)]
    [int]$ToPhase = 8,
    [switch]$Resume,
    [switch]$ResetState,
    [string]$RunId,
    [switch]$DryRun,
    [switch]$SkipRequiredGatesForPkgE1,
    [switch]$RunConnectedAppTestsForPkgE1,
    [switch]$RequireConnectedDevice,
    [switch]$SkipPhase2RefreshEvidenceTests,
    [switch]$RunConnectedAppTestsAtEnd,
    [switch]$RunConnectedAllModulesAtEnd,
    [switch]$AllowPkgE1ThresholdFailure,
    [switch]$AllowThresholdRollupFailure,
    [switch]$AllowNonGreenReleasePackage,
    [switch]$SkipFinalizeThresholdCheck,
    [int]$TierCaptureDurationSeconds = 90,
    [int]$SwipePauseMs = 350,
    [string]$ApplicationId = "com.example.openxcpro.debug",
    [string]$Activity = "com.example.xcpro.MainActivity",
    [switch]$NoGestures,
    [string]$StateFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    # Child tools (adb/gradle) may emit benign stderr lines; contract gating uses exit codes.
    $PSNativeCommandUseErrorActionPreference = $false
}

if ($FromPhase -gt $ToPhase) {
    throw "FromPhase ($FromPhase) must be less than or equal to ToPhase ($ToPhase)."
}

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

function Invoke-CommandSet {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$CommandArgs,
        [Parameter(Mandatory = $true)][string]$PhaseName,
        [switch]$NoThrow
    )

    $display = "$Executable " + ($CommandArgs -join " ")
    $startedAt = Get-Date
    Write-Host "[$PhaseName] Running: $display"

    if ($DryRun) {
        return [ordered]@{
            command = $display
            startedAt = $startedAt.ToString("o")
            finishedAt = (Get-Date).ToString("o")
            status = "dry_run"
            exitCode = 0
            output = @("DRY_RUN")
        }
    }

    $output = & $Executable @CommandArgs 2>&1
    $exitCode = $LASTEXITCODE
    $finishedAt = Get-Date

    foreach ($line in $output) {
        Write-Host $line
    }

    $record = [ordered]@{
        command = $display
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

function Invoke-ScriptFile {
    param(
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$ScriptArgs,
        [Parameter(Mandatory = $true)][string]$PhaseName,
        [switch]$NoThrow
    )

    $display = "powershell -ExecutionPolicy Bypass -File $ScriptPath " + ($ScriptArgs -join " ")
    $startedAt = Get-Date
    Write-Host "[$PhaseName] Running: $display"

    if ($DryRun) {
        return [ordered]@{
            command = $display
            startedAt = $startedAt.ToString("o")
            finishedAt = (Get-Date).ToString("o")
            status = "dry_run"
            exitCode = 0
            output = @("DRY_RUN")
        }
    }

    $invocationArgs = @("-ExecutionPolicy", "Bypass", "-File", $ScriptPath) + $ScriptArgs
    $output = & powershell @invocationArgs 2>&1
    $exitCode = $LASTEXITCODE
    $finishedAt = Get-Date

    foreach ($line in $output) {
        Write-Host $line
    }

    $record = [ordered]@{
        command = $display
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

function Invoke-GradleTaskSet {
    param(
        [Parameter(Mandatory = $true)][string[]]$GradleArgs,
        [Parameter(Mandatory = $true)][string]$PhaseName
    )

    return Invoke-CommandSet -Executable ".\gradlew.bat" -CommandArgs $GradleArgs -PhaseName $PhaseName
}

function Save-State {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$State,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $State.updatedAt = (Get-Date).ToString("o")
    $directory = Split-Path -Path $Path -Parent
    if (-not (Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $State | ConvertTo-Json -Depth 15 | Set-Content -Path $Path -Encoding UTF8
}

function Upsert-PhaseRecord {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$State,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Record
    )

    $remaining = @($State.phases | Where-Object { $_.id -ne $Record.id })
    $State.phases = @($remaining + @($Record))
    $State.phases = @($State.phases | Sort-Object -Property { [int]$_.id })
}

function Add-PhaseCommand {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$PhaseRecord,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$CommandRecord
    )
    $PhaseRecord.commands = @($PhaseRecord.commands + @($CommandRecord))
}

function Add-PhaseNote {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$PhaseRecord,
        [Parameter(Mandatory = $true)][string]$Note
    )
    $PhaseRecord.notes = @($PhaseRecord.notes + @($Note))
}

function Read-GfxSummaryMetrics {
    param([Parameter(Mandatory = $true)][string]$SummaryPath)

    if (-not (Test-Path $SummaryPath)) {
        throw "Frame metrics summary not found: $SummaryPath"
    }

    $summary = Get-Content -Path $SummaryPath -Raw | ConvertFrom-Json
    if ($null -eq $summary) {
        throw "Invalid JSON frame metrics summary: $SummaryPath"
    }

    return [ordered]@{
        frame_time_p95_ms = [double]$summary.frame_time_p95_ms
        frame_time_p99_ms = [double]$summary.frame_time_p99_ms
        jank_percent = [double]$summary.jank_percent
    }
}

function Ensure-ScriptPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path)) {
        throw "Required script not found: $Path"
    }
}

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($StateFile)) {
    $StateFile = Join-Path $repoRoot "logs/phase-runner/mapscreen-completion-contract-state.json"
}

$contractPath = "docs/MAPSCREEN/06_MAPSCREEN_COMPLETION_CONTRACT_2026-03-05.md"
$phaseNames = [ordered]@{
    0 = "Phase 0 - Preflight and Contract Lock"
    1 = "Phase 1 - Refresh Phase 2 Package Gates"
    2 = "Phase 2 - Capture pkg-e1 Scaffold"
    3 = "Phase 3 - Tier A Frame Metrics Capture and Apply"
    4 = "Phase 4 - Tier B Frame Metrics Capture and Apply"
    5 = "Phase 5 - Verify pkg-e1 Thresholds"
    6 = "Phase 6 - Verify All Package Thresholds"
    7 = "Phase 7 - Required Verification Gates"
    8 = "Phase 8 - Finalize Release Package pkg-r1"
}

$prepareDeviceScript = Join-Path $repoRoot "scripts/qa/prepare_connected_device_for_tests.ps1"
$applyPhase2Script = Join-Path $repoRoot "scripts/qa/apply_mapscreen_phase2_perf_metrics.ps1"
$capturePkgE1Script = Join-Path $repoRoot "scripts/qa/run_mapscreen_pkg_e1_evidence_capture.ps1"
$captureFrameMetricsScript = Join-Path $repoRoot "scripts/qa/capture_mapscreen_frame_metrics.ps1"
$applyPkgE1TierScript = Join-Path $repoRoot "scripts/qa/apply_pkg_e1_tier_metrics.ps1"
$verifyPackageScript = Join-Path $repoRoot "scripts/qa/verify_mapscreen_package_evidence.ps1"
$thresholdsScript = Join-Path $repoRoot "scripts/qa/run_mapscreen_evidence_threshold_checks.ps1"
$finalizeScript = Join-Path $repoRoot "scripts/qa/finalize_mapscreen_release_package.ps1"

$requiredScripts = @(
    $prepareDeviceScript,
    $applyPhase2Script,
    $capturePkgE1Script,
    $captureFrameMetricsScript,
    $applyPkgE1TierScript,
    $verifyPackageScript,
    $thresholdsScript,
    $finalizeScript
)

if ($ResetState -and (Test-Path $StateFile)) {
    Remove-Item -Path $StateFile -Force
}

$state = $null
if ($Resume -and (Test-Path $StateFile)) {
    $loaded = Get-Content -Path $StateFile -Raw | ConvertFrom-Json
    $state = ConvertTo-Hashtable -InputObject $loaded
} else {
    $state = [ordered]@{
        plan = "mapscreen-completion-contract"
        contract = $contractPath
        run_id = $null
        createdAt = (Get-Date).ToString("o")
        updatedAt = (Get-Date).ToString("o")
        phases = @()
    }
}

if (-not $state.Contains("phases") -or $null -eq $state.phases) {
    $state.phases = @()
}

$completedPhaseIds = @($state.phases | Where-Object { $_.status -eq "completed" } | ForEach-Object { [int]$_.id })

if ([string]::IsNullOrWhiteSpace($RunId)) {
    if ($state.Contains("run_id") -and -not [string]::IsNullOrWhiteSpace([string]$state.run_id)) {
        $RunId = [string]$state.run_id
    } else {
        $RunId = Get-Date -Format "yyyyMMdd-HHmmss"
    }
} else {
    if ($state.Contains("run_id") -and -not [string]::IsNullOrWhiteSpace([string]$state.run_id) -and [string]$state.run_id -ne $RunId) {
        throw "State file run_id ($($state.run_id)) differs from requested RunId ($RunId). Use -ResetState or -RunId $($state.run_id)."
    }
}

$state.run_id = $RunId
Save-State -State $state -Path $StateFile

$needsConnectedDevice = $RequireConnectedDevice -or $RunConnectedAppTestsForPkgE1 -or $RunConnectedAppTestsAtEnd -or $RunConnectedAllModulesAtEnd
if ($RequireConnectedDevice -and -not (Test-ConnectedDevice)) {
    throw "No connected device/emulator detected, but RequireConnectedDevice was set."
}

Write-Host "Mapscreen completion contract runner"
Write-Host "Repo: $repoRoot"
Write-Host "Contract: $contractPath"
Write-Host "RunId: $RunId"
Write-Host "State file: $StateFile"
Write-Host "Phase range: $FromPhase..$ToPhase"
Write-Host "Mode: $(if ($DryRun) { 'dry-run' } else { 'execute' })"

$currentPhaseRecord = $null
$currentPhaseId = -1
$invariant = [System.Globalization.CultureInfo]::InvariantCulture

try {
    for ($phaseId = 0; $phaseId -le 8; $phaseId += 1) {
        if ($phaseId -lt $FromPhase -or $phaseId -gt $ToPhase) {
            continue
        }

        if ($Resume -and ($completedPhaseIds -contains [int]$phaseId)) {
            Write-Host "[$($phaseNames[$phaseId])] Skipping (already completed in state file)."
            continue
        }

        $currentPhaseId = $phaseId
        $phaseName = $phaseNames[$phaseId]
        $currentPhaseRecord = [ordered]@{
            id = [int]$phaseId
            name = $phaseName
            startedAt = (Get-Date).ToString("o")
            finishedAt = $null
            status = "running"
            commands = @()
            notes = @()
        }
        Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "Contract run_id: $RunId"
        Upsert-PhaseRecord -State $state -Record $currentPhaseRecord
        Save-State -State $state -Path $StateFile

        switch ($phaseId) {
            0 {
                foreach ($scriptPath in $requiredScripts) {
                    Ensure-ScriptPath -Path $scriptPath
                }
                Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "All required MAPSCREEN contract scripts are present."

                if (Test-ConnectedDevice) {
                    $preflight = Invoke-ScriptFile -ScriptPath $prepareDeviceScript -ScriptArgs @() -PhaseName $phaseName
                    Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $preflight
                } else {
                    if ($needsConnectedDevice) {
                        Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "No connected device detected at preflight; connected phases may fail or skip."
                    } else {
                        Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "No connected device detected at preflight; continuing."
                    }
                }
            }
            1 {
                foreach ($packageId in @("pkg-d1", "pkg-g1", "pkg-w1")) {
                    $phase2Args = @("-PackageId", $packageId, "-UpdateGateResult")
                    if ($SkipPhase2RefreshEvidenceTests) {
                        $phase2Args += @("-SkipPerfEvidenceTests")
                    }
                    $result = Invoke-ScriptFile -ScriptPath $applyPhase2Script -ScriptArgs $phase2Args -PhaseName $phaseName
                    Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $result
                }
            }
            2 {
                $pkgE1Args = @("-RunId", $RunId)
                if ($SkipRequiredGatesForPkgE1) { $pkgE1Args += @("-SkipRequiredGates") }
                if ($RunConnectedAppTestsForPkgE1) { $pkgE1Args += @("-RunConnectedAppTests") }
                if ($RequireConnectedDevice) { $pkgE1Args += @("-RequireConnectedDevice") }
                $result = Invoke-ScriptFile -ScriptPath $capturePkgE1Script -ScriptArgs $pkgE1Args -PhaseName $phaseName
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $result
            }
            3 {
                if (-not (Test-ConnectedDevice) -and -not $DryRun) {
                    throw "Tier A capture requires a connected adb device. Connect a device and rerun with -Resume -FromPhase 3."
                }

                $tierAPath = Join-Path $repoRoot "artifacts/mapscreen/phase3/pkg-e1/$RunId/tier_a/gfxinfo_summary.json"
                $captureArgs = @(
                    "-ApplicationId", $ApplicationId,
                    "-Activity", $Activity,
                    "-DurationSeconds", $TierCaptureDurationSeconds.ToString(),
                    "-SwipePauseMs", $SwipePauseMs.ToString(),
                    "-OutputJsonPath", $tierAPath
                )
                if ($NoGestures) { $captureArgs += @("-NoGestures") }

                if (Test-ConnectedDevice) {
                    $preflight = Invoke-ScriptFile -ScriptPath $prepareDeviceScript -ScriptArgs @() -PhaseName $phaseName
                    Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $preflight
                }

                $capture = Invoke-ScriptFile -ScriptPath $captureFrameMetricsScript -ScriptArgs $captureArgs -PhaseName $phaseName
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $capture

                $tierAMetrics = Read-GfxSummaryMetrics -SummaryPath $tierAPath
                $applyArgs = @(
                    "-RunId", $RunId,
                    "-Tier", "tier_a",
                    "-FrameTimeP95Ms", ([double]$tierAMetrics.frame_time_p95_ms).ToString($invariant),
                    "-FrameTimeP99Ms", ([double]$tierAMetrics.frame_time_p99_ms).ToString($invariant),
                    "-JankPercent", ([double]$tierAMetrics.jank_percent).ToString($invariant)
                )
                $apply = Invoke-ScriptFile -ScriptPath $applyPkgE1TierScript -ScriptArgs $applyArgs -PhaseName $phaseName
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $apply
                Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note ("Tier A metrics applied: p95={0} p99={1} jank={2}" -f $tierAMetrics.frame_time_p95_ms, $tierAMetrics.frame_time_p99_ms, $tierAMetrics.jank_percent)
            }
            4 {
                if (-not (Test-ConnectedDevice) -and -not $DryRun) {
                    throw "Tier B capture requires a connected adb device. Connect a device and rerun with -Resume -FromPhase 4."
                }

                $tierBPath = Join-Path $repoRoot "artifacts/mapscreen/phase3/pkg-e1/$RunId/tier_b/gfxinfo_summary.json"
                $captureArgs = @(
                    "-ApplicationId", $ApplicationId,
                    "-Activity", $Activity,
                    "-DurationSeconds", $TierCaptureDurationSeconds.ToString(),
                    "-SwipePauseMs", $SwipePauseMs.ToString(),
                    "-OutputJsonPath", $tierBPath
                )
                if ($NoGestures) { $captureArgs += @("-NoGestures") }

                if (Test-ConnectedDevice) {
                    $preflight = Invoke-ScriptFile -ScriptPath $prepareDeviceScript -ScriptArgs @() -PhaseName $phaseName
                    Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $preflight
                }

                $capture = Invoke-ScriptFile -ScriptPath $captureFrameMetricsScript -ScriptArgs $captureArgs -PhaseName $phaseName
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $capture

                $tierBMetrics = Read-GfxSummaryMetrics -SummaryPath $tierBPath
                $applyArgs = @(
                    "-RunId", $RunId,
                    "-Tier", "tier_b",
                    "-FrameTimeP95Ms", ([double]$tierBMetrics.frame_time_p95_ms).ToString($invariant),
                    "-FrameTimeP99Ms", ([double]$tierBMetrics.frame_time_p99_ms).ToString($invariant),
                    "-JankPercent", ([double]$tierBMetrics.jank_percent).ToString($invariant)
                )
                $apply = Invoke-ScriptFile -ScriptPath $applyPkgE1TierScript -ScriptArgs $applyArgs -PhaseName $phaseName
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $apply
                Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note ("Tier B metrics applied: p95={0} p99={1} jank={2}" -f $tierBMetrics.frame_time_p95_ms, $tierBMetrics.frame_time_p99_ms, $tierBMetrics.jank_percent)
            }
            5 {
                $verifyArgs = @("-PackageId", "pkg-e1", "-RunId", $RunId, "-UpdateGateResult")
                $verify = Invoke-ScriptFile -ScriptPath $verifyPackageScript -ScriptArgs $verifyArgs -PhaseName $phaseName -NoThrow:$AllowPkgE1ThresholdFailure
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $verify
                if ($verify.exitCode -ne 0 -and $AllowPkgE1ThresholdFailure) {
                    Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "pkg-e1 threshold failure allowed by -AllowPkgE1ThresholdFailure."
                }
            }
            6 {
                $thresholdArgs = @("-UpdateGateResults")
                $threshold = Invoke-ScriptFile -ScriptPath $thresholdsScript -ScriptArgs $thresholdArgs -PhaseName $phaseName -NoThrow:$AllowThresholdRollupFailure
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $threshold
                if ($threshold.exitCode -ne 0 -and $AllowThresholdRollupFailure) {
                    Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "All-package threshold rollup failure allowed by -AllowThresholdRollupFailure."
                }
            }
            7 {
                foreach ($taskSet in @(@("enforceRules"), @("testDebugUnitTest"), @("assembleDebug"))) {
                    $result = Invoke-GradleTaskSet -GradleArgs $taskSet -PhaseName $phaseName
                    Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $result
                }

                if ($RunConnectedAppTestsAtEnd) {
                    if (Test-ConnectedDevice) {
                        $preflight = Invoke-ScriptFile -ScriptPath $prepareDeviceScript -ScriptArgs @() -PhaseName $phaseName
                        Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $preflight
                        $connectedApp = Invoke-GradleTaskSet -GradleArgs @(":app:connectedDebugAndroidTest", "--no-parallel", "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true") -PhaseName $phaseName
                        Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $connectedApp
                    } else {
                        Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "Connected app tests skipped: no adb device/emulator."
                    }
                }

                if ($RunConnectedAllModulesAtEnd) {
                    if (Test-ConnectedDevice) {
                        $preflight = Invoke-ScriptFile -ScriptPath $prepareDeviceScript -ScriptArgs @() -PhaseName $phaseName
                        Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $preflight
                        $connectedAll = Invoke-GradleTaskSet -GradleArgs @("connectedDebugAndroidTest", "--no-parallel") -PhaseName $phaseName
                        Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $connectedAll
                    } else {
                        Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note "Connected all-modules tests skipped: no adb device/emulator."
                    }
                }
            }
            8 {
                $finalizeArgs = @("-RunId", $RunId)
                if ($AllowNonGreenReleasePackage) { $finalizeArgs += @("-AllowNonGreenPackages") }
                if ($SkipFinalizeThresholdCheck) { $finalizeArgs += @("-SkipThresholdCheck") }
                $finalize = Invoke-ScriptFile -ScriptPath $finalizeScript -ScriptArgs $finalizeArgs -PhaseName $phaseName
                Add-PhaseCommand -PhaseRecord $currentPhaseRecord -CommandRecord $finalize
            }
            default {
                throw "Unsupported phase id: $phaseId"
            }
        }

        $currentPhaseRecord.status = "completed"
        $currentPhaseRecord.finishedAt = (Get-Date).ToString("o")
        Upsert-PhaseRecord -State $state -Record $currentPhaseRecord
        Save-State -State $state -Path $StateFile
        Write-Host "[$phaseName] Completed."
    }
} catch {
    $message = $_.Exception.Message
    if ($null -ne $currentPhaseRecord) {
        $currentPhaseRecord.status = "failed"
        $currentPhaseRecord.finishedAt = (Get-Date).ToString("o")
        Add-PhaseNote -PhaseRecord $currentPhaseRecord -Note $message
        Upsert-PhaseRecord -State $state -Record $currentPhaseRecord
        Save-State -State $state -Path $StateFile
    }
    Write-Host "[mapscreen-contract] FAILED at phase $currentPhaseId."
    Write-Host "[mapscreen-contract] Error: $message"
    Write-Host "[mapscreen-contract] Resume command:"
    Write-Host "powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 -Resume -FromPhase $currentPhaseId -ToPhase $ToPhase"
    exit 1
}

Write-Host ""
Write-Host "[mapscreen-contract] Completed requested phase range ($FromPhase..$ToPhase)."
Write-Host "[mapscreen-contract] State saved: $StateFile"
Write-Host "[mapscreen-contract] Active RunId: $RunId"
exit 0
