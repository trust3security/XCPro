param(
    [ValidateRange(1, 10)]
    [int]$FromPhase = 1,
    [ValidateRange(1, 10)]
    [int]$ToPhase = 10,
    [string]$PlanPath = "docs/RACING_TASK/CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md",
    [string]$RunId,
    [switch]$DryRun,
    [switch]$NoAppendEvidence,
    [switch]$SkipWorkspaceCheck,
    [switch]$RequireCleanWorkspace,
    [switch]$RunArchGatePerPhase,
    [switch]$RunFinalGatesAtEnd,
    [switch]$ContinueOnUnrelatedBlocker
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    # Native tools may emit non-fatal stderr output. We gate on exit codes.
    $PSNativeCommandUseErrorActionPreference = $false
}
$script:SelfPath = $PSCommandPath

if ($FromPhase -gt $ToPhase) {
    throw "FromPhase ($FromPhase) must be <= ToPhase ($ToPhase)."
}

function Resolve-RepoRoot {
    $resolvedScriptPath = $script:SelfPath
    if ([string]::IsNullOrWhiteSpace($resolvedScriptPath)) {
        $resolvedScriptPath = $MyInvocation.ScriptName
    }
    if ([string]::IsNullOrWhiteSpace($resolvedScriptPath)) {
        throw "Unable to resolve script path for repository root detection."
    }
    $scriptRoot = Split-Path -Path $resolvedScriptPath -Parent
    return (Resolve-Path (Join-Path $scriptRoot "..\..")).Path
}

function Resolve-PythonExecutable {
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) { return "python" }
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) { return "py" }
    return $null
}

function Get-PhaseConfig {
    param([Parameter(Mandatory = $true)][int]$PhaseId)

    $map = @{
        1 = [ordered]@{
            Name = "Phase 1 - Canonical Model Consolidation"
            Area = 2
            PendingPack = "P1"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.TaskNavigationControllerTest",
                "--tests", "com.example.xcpro.tasks.TaskManagerCoordinatorTest",
                "--tests", "com.example.xcpro.tasks.TaskManagerCanonicalHydrateTest",
                "--tests", "com.example.xcpro.map.replay.RacingReplayLogBuilderTest",
                "--tests", "com.example.xcpro.map.RacingReplayTaskHelpersTest"
            )
        }
        2 = [ordered]@{
            Name = "Phase 2 - RT Structure and Profile Validator"
            Area = 1
            PendingPack = "P2"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.racing.RacingTaskStructureRulesTest",
                "--tests", "com.example.xcpro.tasks.domain.engine.DefaultRacingTaskEngineTest",
                "--tests", "com.example.xcpro.tasks.racing.RacingTaskManagerRulePersistenceTest",
                "--tests", "com.example.xcpro.tasks.TaskManagerCanonicalHydrateTest",
                "--tests", "com.example.xcpro.map.RacingReplayTaskHelpersTest",
                "--tests", "com.example.xcpro.map.replay.RacingReplayLogBuilderTest"
            )
        }
        3 = [ordered]@{
            Name = "Phase 3 - Start Procedure Compliance"
            Area = 3
            PendingPack = "P3"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingStartEvaluatorTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingNavigationEngineTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingReplayValidationTest"
            )
        }
        4 = [ordered]@{
            Name = "Phase 4 - Turnpoint and OZ Compliance"
            Area = 4
            PendingPack = "P4"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingNavigationEnginePhase4Test",
                "--tests", "com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlannerTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingReplayValidationTest"
            )
        }
        5 = [ordered]@{
            Name = "Phase 5 - Finish Procedure Compliance"
            Area = 5
            PendingPack = "P5"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingNavigationEngineTest",
                "--tests", "com.example.xcpro.tasks.core.TaskWaypointCustomParamsTest"
            )
        }
        6 = [ordered]@{
            Name = "Phase 6 - Navigation Evidence and Timing Hardening"
            Area = 6
            PendingPack = "P6"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlannerTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingNavigationEngineTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingReplayValidationTest"
            )
        }
        7 = [ordered]@{
            Name = "Phase 7 - UI Rules Editor and Typed Commands"
            Area = 7
            PendingPack = "P7"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.TaskSheetViewModelRacingRulesCommandTest",
                "--tests", "com.example.xcpro.tasks.RulesRacingTaskParametersTest",
                "--tests", "com.example.xcpro.tasks.racing.RacingTaskManagerRulePersistenceTest"
            )
        }
        8 = [ordered]@{
            Name = "Phase 8 - Persistence/Import/Export v2 Fidelity"
            Area = 8
            PendingPack = "P8-A/P8-B/P8-C"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.TaskPersistSerializerFidelityTest",
                "--tests", "com.example.xcpro.tasks.TaskSheetViewModelImportTest",
                "--tests", "com.example.xcpro.tasks.data.persistence.TaskPersistenceAdaptersDeterministicIdTest"
            )
        }
        9 = [ordered]@{
            Name = "Phase 9 - Replay Parity and Preconditions"
            Area = 9
            PendingPack = "P9-A/P9-B/P9-C/P9-D"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.map.replay.RacingReplayLogBuilderTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingReplayValidationTest",
                "--tests", "com.example.xcpro.tasks.TaskNavigationControllerTest",
                "--tests", "com.example.xcpro.map.RacingReplayTaskHelpersTest"
            )
        }
        10 = [ordered]@{
            Name = "Phase 10 - Test Net Expansion and Release Hardening"
            Area = 10
            PendingPack = "P10-A/P10-B/P10-C/P10-D"
            TestArgs = @(
                ":feature:map:testDebugUnitTest",
                "--tests", "com.example.xcpro.tasks.TaskPersistSerializerFidelityTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingReplayValidationTest",
                "--tests", "com.example.xcpro.tasks.TaskNavigationControllerTest",
                "--tests", "com.example.xcpro.tasks.racing.navigation.RacingNavigationEngineTest"
            )
        }
    }

    if (-not $map.ContainsKey($PhaseId)) {
        throw "Unsupported phase id: $PhaseId"
    }
    return $map[$PhaseId]
}

function Invoke-CommandCapture {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Args,
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$LogFile
    )

    $display = "$Executable " + ($Args -join " ")
    $started = Get-Date
    Write-Host "[$Label] Running: $display"

    if ($DryRun) {
        return [ordered]@{
            label = $Label
            command = $display
            status = "dry_run"
            exitCode = 0
            startedAt = $started.ToString("o")
            finishedAt = (Get-Date).ToString("o")
            output = @("DRY_RUN")
        }
    }

    $output = & $Executable @Args 2>&1
    $exitCode = $LASTEXITCODE
    $finished = Get-Date
    $lines = @($output | ForEach-Object { [string]$_ })
    $lines | Set-Content -Path $LogFile -Encoding UTF8
    foreach ($line in $lines) { Write-Host $line }

    return [ordered]@{
        label = $Label
        command = $display
        status = if ($exitCode -eq 0) { "pass" } else { "fail" }
        exitCode = $exitCode
        startedAt = $started.ToString("o")
        finishedAt = $finished.ToString("o")
        output = $lines
    }
}

function Invoke-GitLines {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Args
    )

    if ($DryRun) {
        return [ordered]@{
            ExitCode = 0
            Output = @()
            ErrorOutput = @()
        }
    }

    $stdoutFile = [System.IO.Path]::GetTempFileName()
    $stderrFile = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process `
            -FilePath "git" `
            -ArgumentList $Args `
            -NoNewWindow `
            -Wait `
            -PassThru `
            -RedirectStandardOutput $stdoutFile `
            -RedirectStandardError $stderrFile

        $stdout = @()
        if (Test-Path $stdoutFile) {
            $stdout = @(Get-Content $stdoutFile | ForEach-Object { [string]$_ })
        }
        $stderr = @()
        if (Test-Path $stderrFile) {
            $stderr = @(Get-Content $stderrFile | ForEach-Object { [string]$_ })
        }
        return [ordered]@{
            ExitCode = $proc.ExitCode
            Output = $stdout
            ErrorOutput = $stderr
        }
    } finally {
        Remove-Item -Path $stdoutFile -ErrorAction SilentlyContinue
        Remove-Item -Path $stderrFile -ErrorAction SilentlyContinue
    }
}

function Get-GitStatusShort {
    $result = Invoke-GitLines -Args @("status", "--short")
    if ($result.ExitCode -ne 0) {
        return @("git status unavailable (exit $($result.ExitCode))")
    }
    return @($result.Output)
}

function Get-GitChangedFiles {
    $trackedResult = Invoke-GitLines -Args @("diff", "--name-only")
    $stagedResult = Invoke-GitLines -Args @("diff", "--name-only", "--cached")
    $untrackedResult = Invoke-GitLines -Args @("ls-files", "--others", "--exclude-standard")

    $tracked = if ($trackedResult.ExitCode -eq 0) { $trackedResult.Output } else { @() }
    $staged = if ($stagedResult.ExitCode -eq 0) { $stagedResult.Output } else { @() }
    $untracked = if ($untrackedResult.ExitCode -eq 0) { $untrackedResult.Output } else { @() }

    return @(
        @($tracked) + @($staged) + @($untracked) |
            ForEach-Object { [string]$_ } |
            Where-Object { $_.Trim().Length -gt 0 } |
            Sort-Object -Unique
    )
}

function Get-FirstFailingLines {
    param([string[]]$Lines)

    $matches = @(
        $Lines |
            Where-Object { $_ -match "(?i)(^FAIL:|FAILED|error|exception|What went wrong|BUILD FAILED|Execution failed)" } |
            Select-Object -First 6
    )
    if ($matches.Count -gt 0) { return $matches }
    return @($Lines | Select-Object -Last 6)
}

function Classify-Blocker {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$FailedCommand
    )

    $text = ($FailedCommand.output -join "`n")
    if ($text -match "(?i)(no connected devices|no devices|device.*offline|adb.*not found)") {
        return "environment"
    }
    if ($text -match "(?i)(line budget|MapScreenViewModel\.kt has 351 lines)") {
        return "unrelated_branch"
    }
    if ($text -match "(?i)(No tests found for given includes)") {
        return "phase_or_config"
    }
    return "phase_caused"
}

function Get-ChangedFileSummary {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$ChangedFiles,
        [int]$TopGroups = 8
    )

    $files = @(
        $ChangedFiles |
            ForEach-Object { [string]$_ } |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_.Length -gt 0 }
    )

    $counts = @{}
    foreach ($file in $files) {
        $parts = $file -split "/"
        $group = if ($parts.Count -ge 2 -and ($parts[0] -eq "feature" -or $parts[0] -eq "core" -or $parts[0] -eq "docs")) {
            "$($parts[0])/$($parts[1])"
        } elseif ($parts.Count -ge 1) {
            $parts[0]
        } else {
            "(root)"
        }
        if (-not $counts.ContainsKey($group)) {
            $counts[$group] = 0
        }
        $counts[$group] = [int]$counts[$group] + 1
    }

    $bt = [char]96
    $top = @(
        $counts.GetEnumerator() |
            Sort-Object -Property @{ Expression = 'Value'; Descending = $true }, @{ Expression = 'Name'; Descending = $false } |
            Select-Object -First $TopGroups |
            ForEach-Object { "$bt$($_.Name)$bt=$($_.Value)" }
    )

    $topSummary = if ($top.Count -gt 0) { $top -join ", " } else { "none" }
    return [ordered]@{
        total = ($files | Measure-Object).Count
        top = $topSummary
    }
}

function New-EvidenceBlock {
    param(
        [Parameter(Mandatory = $true)][int]$PhaseId,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$PhaseConfig,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$PhaseResult,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$ChangedFiles,
        [Parameter(Mandatory = $true)][string]$RunId
    )

    $today = (Get-Date).ToString("yyyy-MM-dd")
    $bt = [char]96
    $phaseLogsRelative = "logs/phase-runner/racing-task/" + $RunId + "/phase-" + $PhaseId.ToString("00")
    $changedSummary = Get-ChangedFileSummary -ChangedFiles $ChangedFiles
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("- Automation runner phase $PhaseId update ($today):")
    $lines.Add("  - Runner: $bt" + "scripts/ci/racing_phase_runner.ps1" + "$bt.")
    $lines.Add("  - Run ID: ${bt}$RunId${bt}.")
    $lines.Add("  - Logs: ${bt}$phaseLogsRelative${bt}.")
    $lines.Add("  - Scope: $($PhaseConfig.Name).")
    $lines.Add("  - Area mapping: Area $($PhaseConfig.Area).")
    $lines.Add("  - Workspace pre-check: $($PhaseResult.workspaceCheck).")
    $lines.Add("  - Commands:")
    foreach ($command in $PhaseResult.commands) {
        $lines.Add("    - ${bt}$($command.command)${bt}: $($command.status.ToUpper()) (exit $($command.exitCode)).")
    }
    $lines.Add("  - Basic build gate (" + $bt + ":feature:map:assembleDebug" + $bt + "): $($PhaseResult.buildGateStatus).")
    $lines.Add("  - Changed files summary (workspace snapshot, not phase-scoped):")
    $lines.Add("    - total files: $($changedSummary.total)")
    $lines.Add("    - top path groups: $($changedSummary.top)")
    $lines.Add("  - Score update:")
    $lines.Add("    - Area $($PhaseConfig.Area): TODO /100 (apply 40/30/20/10 rubric).")
    if ($PhaseResult.status -eq "blocked") {
        $lines.Add("  - Blocker:")
        $lines.Add("    - classification: $($PhaseResult.blockerClassification)")
        $lines.Add("    - next action pack: $($PhaseConfig.PendingPack)")
        foreach ($line in $PhaseResult.blockerExcerpt) {
            $safe = $line.Replace("`r", "").Replace("`n", "")
            $lines.Add("    - evidence: $safe")
        }
    }
    return $lines
}

function Append-EvidenceBlock {
    param(
        [Parameter(Mandatory = $true)][string]$TargetPath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Lines
    )
    Add-Content -Path $TargetPath -Value $Lines -Encoding UTF8
}

$repoRoot = Resolve-RepoRoot
Set-Location $repoRoot

if (-not (Test-Path $PlanPath)) {
    throw "Plan file not found: $PlanPath"
}

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = Get-Date -Format "yyyyMMdd-HHmmss"
}

$runRoot = Join-Path $repoRoot ("logs/phase-runner/racing-task/" + $RunId)
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null

$baselineStatus = @(Get-GitStatusShort)
$baselineStatusCount = ($baselineStatus | Measure-Object).Count
if ($RequireCleanWorkspace -and $baselineStatusCount -gt 0) {
    throw "Workspace is not clean. Re-run without -RequireCleanWorkspace or clean workspace first."
}

$pythonExecutable = $null
if ($RunArchGatePerPhase) {
    $pythonExecutable = Resolve-PythonExecutable
    if ($null -eq $pythonExecutable) {
        throw "Python executable is required for -RunArchGatePerPhase."
    }
}

Write-Host "Racing Task phase runner"
Write-Host "Repo: $repoRoot"
Write-Host "Plan: $PlanPath"
Write-Host "RunId: $RunId"
Write-Host "Phase range: $FromPhase..$ToPhase"
Write-Host "Mode: $(if ($DryRun) { 'dry-run' } else { 'execute' })"

$hasBlocker = $false
$phaseSummaries = New-Object System.Collections.Generic.List[object]

for ($phaseId = $FromPhase; $phaseId -le $ToPhase; $phaseId += 1) {
    $config = Get-PhaseConfig -PhaseId $phaseId
    $phaseLabel = "Phase $phaseId"
    $phaseDir = Join-Path $runRoot ("phase-" + $phaseId.ToString("00"))
    New-Item -ItemType Directory -Path $phaseDir -Force | Out-Null

    Write-Host ""
    Write-Host "[$phaseLabel] $($config.Name)"

    $workspaceCheck = "PASS"
    if (-not $SkipWorkspaceCheck) {
        $currentStatus = @(Get-GitStatusShort)
        $statusCount = ($currentStatus | Measure-Object).Count
        if ($RequireCleanWorkspace -and $statusCount -gt 0) {
            $workspaceCheck = "FAIL (workspace not clean)"
        } else {
            $workspaceCheck = if ($statusCount -gt 0) { "PASS (dirty workspace allowed)" } else { "PASS" }
        }
    } else {
        $workspaceCheck = "SKIPPED"
    }

    $commands = New-Object System.Collections.Generic.List[object]

    if ($RunArchGatePerPhase) {
        $archArgs = if ($pythonExecutable -eq "py") { @("-3", "scripts/arch_gate.py") } else { @("scripts/arch_gate.py") }
        $arch = Invoke-CommandCapture `
            -Executable $pythonExecutable `
            -Args $archArgs `
            -Label "$phaseLabel arch_gate" `
            -LogFile (Join-Path $phaseDir "arch_gate.log")
        $commands.Add($arch)
    }

    $tests = Invoke-CommandCapture `
        -Executable ".\gradlew.bat" `
        -Args $config.TestArgs `
        -Label "$phaseLabel targeted_tests" `
        -LogFile (Join-Path $phaseDir "targeted_tests.log")
    $commands.Add($tests)

    $buildAttempts = @(
        @(":feature:map:assembleDebug"),
        @(":feature:map:assembleDebug", "--no-configuration-cache"),
        @(":feature:map:assembleDebug", "--no-configuration-cache")
    )
    $build = $null
    for ($i = 0; $i -lt $buildAttempts.Count; $i += 1) {
        $attemptArgs = $buildAttempts[$i]
        $attemptIndex = $i + 1
        $record = Invoke-CommandCapture `
            -Executable ".\gradlew.bat" `
            -Args $attemptArgs `
            -Label "$phaseLabel build_gate_attempt_$attemptIndex" `
            -LogFile (Join-Path $phaseDir ("build_gate_attempt_" + $attemptIndex + ".log"))
        $commands.Add($record)
        $build = $record
        if ($record.exitCode -eq 0) { break }
    }

    $changedFiles = @(Get-GitChangedFiles)

    $failed = @($commands | Where-Object { $_.exitCode -ne 0 -and $_.status -ne "dry_run" })
    $phaseStatus = "completed"
    $blockerClassification = ""
    $blockerExcerpt = @()
    if ($workspaceCheck -like "FAIL*") {
        $phaseStatus = "blocked"
        $blockerClassification = "workspace_precheck"
        $blockerExcerpt = @("Workspace pre-check failed; clean workspace required by flag.")
    } elseif ($failed.Count -gt 0) {
        $phaseStatus = "blocked"
        $blockerClassification = Classify-Blocker -FailedCommand $failed[0]
        $blockerExcerpt = Get-FirstFailingLines -Lines $failed[0].output
    }

    $buildGateStatus = if ($build.exitCode -eq 0) { "PASS" } else { "FAIL" }
    $phaseResult = [ordered]@{
        phaseId = $phaseId
        area = $config.Area
        status = $phaseStatus
        workspaceCheck = $workspaceCheck
        buildGateStatus = $buildGateStatus
        commands = @($commands.ToArray())
        blockerClassification = $blockerClassification
        blockerExcerpt = $blockerExcerpt
    }
    $phaseSummaries.Add($phaseResult)

    if (-not $NoAppendEvidence) {
        $block = New-EvidenceBlock `
            -PhaseId $phaseId `
            -PhaseConfig $config `
            -PhaseResult $phaseResult `
            -ChangedFiles $changedFiles `
            -RunId $RunId
        Append-EvidenceBlock -TargetPath $PlanPath -Lines $block
        Write-Host "[$phaseLabel] Evidence appended to $PlanPath"
    }

    if ($phaseStatus -eq "blocked") {
        $hasBlocker = $true
        Write-Host "[$phaseLabel] BLOCKED ($blockerClassification)."
        if (-not $ContinueOnUnrelatedBlocker -or ($blockerClassification -ne "unrelated_branch" -and $blockerClassification -ne "environment")) {
            Write-Host "[$phaseLabel] Stopping due to blocker policy."
            break
        }
        Write-Host "[$phaseLabel] Continuing due to -ContinueOnUnrelatedBlocker."
    } else {
        Write-Host "[$phaseLabel] Completed."
    }
}

if ($RunFinalGatesAtEnd -and -not $hasBlocker) {
    Write-Host ""
    Write-Host "[Final Gates] Running enforceRules + testDebugUnitTest + assembleDebug"
    $finalDir = Join-Path $runRoot "final-gates"
    New-Item -ItemType Directory -Path $finalDir -Force | Out-Null

    $finalCommands = @(
        @{ Label = "final enforceRules"; Args = @("enforceRules"); Log = "enforceRules.log" },
        @{ Label = "final testDebugUnitTest"; Args = @("testDebugUnitTest"); Log = "testDebugUnitTest.log" },
        @{ Label = "final assembleDebug"; Args = @("assembleDebug"); Log = "assembleDebug.log" }
    )
    foreach ($final in $finalCommands) {
        $result = Invoke-CommandCapture `
            -Executable ".\gradlew.bat" `
            -Args $final.Args `
            -Label $final.Label `
            -LogFile (Join-Path $finalDir $final.Log)
        if ($result.exitCode -ne 0) {
            Write-Host "[Final Gates] Blocked on '$($final.Label)'."
            exit 1
        }
    }
}

Write-Host ""
Write-Host "Runner complete. RunId: $RunId"
Write-Host "Logs: $runRoot"

if ($hasBlocker) {
    exit 1
}
exit 0
