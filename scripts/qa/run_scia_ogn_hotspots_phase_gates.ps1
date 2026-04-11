param(
    [ValidateRange(0, 4)]
    [int]$FromPhase = 0,
    [ValidateRange(0, 4)]
    [int]$ToPhase = 4,
    [switch]$Resume,
    [switch]$ResetState,
    [switch]$DryRun,
    [switch]$SkipFinalFullGate,
    [switch]$RunConnectedAppTestsAtEnd,
    [switch]$RunConnectedAllModulesAtEnd,
    [switch]$RequireConnectedDevice,
    [string]$StateFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($FromPhase -gt $ToPhase) {
    throw "FromPhase ($FromPhase) must be less than or equal to ToPhase ($ToPhase)."
}

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
        [Parameter(Mandatory = $true)]
        [string]$Executable,
        [Parameter(Mandatory = $true)]
        [string[]]$Args,
        [Parameter(Mandatory = $true)]
        [string]$PhaseName
    )

    $commandDisplay = "$Executable " + ($Args -join " ")
    $startedAt = Get-Date
    Write-Host "[$PhaseName] Running: $commandDisplay"

    if ($DryRun) {
        return [ordered]@{
            command = $commandDisplay
            startedAt = $startedAt.ToString("o")
            finishedAt = (Get-Date).ToString("o")
            status = "dry_run"
            exitCode = 0
        }
    }

    & $Executable @Args | ForEach-Object { Write-Host $_ }
    $exitCode = $LASTEXITCODE
    $finishedAt = Get-Date

    $result = [ordered]@{
        command = $commandDisplay
        startedAt = $startedAt.ToString("o")
        finishedAt = $finishedAt.ToString("o")
        status = if ($exitCode -eq 0) { "passed" } else { "failed" }
        exitCode = $exitCode
    }

    if ($exitCode -ne 0) {
        throw "Command failed (exit $exitCode): $commandDisplay"
    }

    return $result
}

function Invoke-GradleTaskSet {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args,
        [Parameter(Mandatory = $true)]
        [string]$PhaseName
    )

    return Invoke-CommandSet -Executable ".\gradlew.bat" -Args $Args -PhaseName $PhaseName
}

function Save-State {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$State,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $State.updatedAt = (Get-Date).ToString("o")
    $directory = Split-Path -Path $Path -Parent
    if (-not (Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $State | ConvertTo-Json -Depth 12 | Set-Content -Path $Path -Encoding UTF8
}

function Upsert-PhaseRecord {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$State,
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Record
    )

    $remaining = @($State.phases | Where-Object { $_.id -ne $Record.id })
    $State.phases = @($remaining + @($Record))
    $State.phases = @($State.phases | Sort-Object -Property { [int]$_.id })
}

function Assert-AllPhasesComplete {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$State
    )

    $completedIds = @($State.phases | Where-Object { $_.status -eq "completed" } | ForEach-Object { [int]$_.id })
    for ($id = 0; $id -le 4; $id += 1) {
        if (-not ($completedIds -contains $id)) {
            throw "Final full gate requires phases 0..4 completed. Missing phase $id."
        }
    }
}

$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($StateFile)) {
    $StateFile = Join-Path -Path $repoRoot -ChildPath "logs/phase-runner/scia-ogn-hotspots-phase-state.json"
}

$contractPath = "docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/AGENT_EXECUTION_CONTRACT_SCIA_GENERAL_OGN_HOTSPOTS_2026-03-05.md"
$phaseNames = [ordered]@{
    0 = "Phase 0 - Baseline lock and test net"
    1 = "Phase 1 - OGN settings state and intent wiring"
    2 = "Phase 2 - OGN sheet parity and SCIA top placement"
    3 = "Phase 3 - Map OGN tab duplicate toggle removal"
    4 = "Phase 4 - Hardening and docs sync"
}

$basicBuildTaskSet = @(":app:assembleDebug")

$phaseFocusedTaskSets = [ordered]@{
    0 = @(
        ":feature:map:testDebugUnitTest --tests com.example.xcpro.map.ui.MapBottomSheetTabsTest",
        ":feature:map:testDebugUnitTest --tests com.example.xcpro.ogn.OgnTrafficPreferencesRepositoryTest"
    )
    1 = @(
        ":feature:map:testDebugUnitTest --tests com.example.xcpro.ogn.OgnTrafficPreferencesRepositoryTest"
    )
    2 = @(
        ":feature:map:testDebugUnitTest --tests com.example.xcpro.map.ui.MapBottomSheetTabsTest"
    )
    3 = @(
        ":feature:map:testDebugUnitTest --tests com.example.xcpro.map.ui.MapBottomSheetTabsTest"
    )
    4 = @(
        ":feature:map:testDebugUnitTest"
    )
}

$finalGradleTaskSets = @(
    @("enforceRules"),
    @("testDebugUnitTest"),
    @("assembleDebug")
)
$connectedAppTaskSet = @(":app:connectedDebugAndroidTest", "--no-parallel", "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true")
$connectedAllModulesTaskSet = @("connectedDebugAndroidTest", "--no-parallel")

if ($ResetState -and (Test-Path $StateFile)) {
    Remove-Item -Path $StateFile -Force
}

$state = $null
if ($Resume -and (Test-Path $StateFile)) {
    $loaded = Get-Content -Path $StateFile -Raw | ConvertFrom-Json
    $state = ConvertTo-Hashtable -InputObject $loaded
} else {
    $state = [ordered]@{
        plan = "scia-ogn-hotspots-phased-execution"
        contract = $contractPath
        createdAt = (Get-Date).ToString("o")
        updatedAt = (Get-Date).ToString("o")
        phases = @()
        finalGate = [ordered]@{
            status = "not_run"
            startedAt = $null
            finishedAt = $null
            commands = @()
            notes = @()
        }
    }
}

if (-not $state.Contains("phases") -or $null -eq $state.phases) {
    $state.phases = @()
}
if (-not $state.Contains("finalGate") -or $null -eq $state.finalGate) {
    $state.finalGate = [ordered]@{
        status = "not_run"
        startedAt = $null
        finishedAt = $null
        commands = @()
        notes = @()
    }
}

$completedPhaseIds = @($state.phases | Where-Object { $_.status -eq "completed" } | ForEach-Object { [int]$_.id })

$connectedAvailable = $false
if ($RunConnectedAppTestsAtEnd -or $RunConnectedAllModulesAtEnd) {
    $connectedAvailable = Test-ConnectedDevice
    if (-not $connectedAvailable -and $RequireConnectedDevice) {
        throw "No connected device/emulator detected, but RequireConnectedDevice was set."
    }
}

Write-Host "SCIA/OGN/Hotspots phase gates runner"
Write-Host "Repo: $repoRoot"
Write-Host "Contract: $contractPath"
Write-Host "State file: $StateFile"
Write-Host "Phase range: $FromPhase..$ToPhase"
Write-Host "Mode: $(if ($DryRun) { 'dry-run' } else { 'execute' })"

try {
    foreach ($phaseId in $phaseNames.Keys) {
        if ($phaseId -lt $FromPhase -or $phaseId -gt $ToPhase) {
            continue
        }

        if ($Resume -and ($completedPhaseIds -contains [int]$phaseId)) {
            Write-Host "[$($phaseNames[$phaseId])] Skipping (already completed in state file)."
            continue
        }

        $phaseName = $phaseNames[$phaseId]
        $phaseRecord = [ordered]@{
            id = [int]$phaseId
            name = $phaseName
            startedAt = (Get-Date).ToString("o")
            finishedAt = $null
            status = "running"
            commands = @()
            notes = @()
        }

        Upsert-PhaseRecord -State $state -Record $phaseRecord
        Save-State -State $state -Path $StateFile

        $buildResult = Invoke-GradleTaskSet -Args $basicBuildTaskSet -PhaseName $phaseName
        $phaseRecord.commands = @($phaseRecord.commands + @($buildResult))
        Upsert-PhaseRecord -State $state -Record $phaseRecord
        Save-State -State $state -Path $StateFile

        foreach ($taskSet in $phaseFocusedTaskSets[$phaseId]) {
            $taskArgs = @($taskSet -split "\s+")
            $result = Invoke-GradleTaskSet -Args $taskArgs -PhaseName $phaseName
            $phaseRecord.commands = @($phaseRecord.commands + @($result))
            Upsert-PhaseRecord -State $state -Record $phaseRecord
            Save-State -State $state -Path $StateFile
        }

        $phaseRecord.status = "completed"
        $phaseRecord.finishedAt = (Get-Date).ToString("o")
        Upsert-PhaseRecord -State $state -Record $phaseRecord
        Save-State -State $state -Path $StateFile
        Write-Host "[$phaseName] Completed."
    }

    $shouldRunFinalGate = (-not $SkipFinalFullGate) -and ($ToPhase -eq 4)
    if ($shouldRunFinalGate) {
        Assert-AllPhasesComplete -State $state

        $state.finalGate.status = "running"
        $state.finalGate.startedAt = (Get-Date).ToString("o")
        $state.finalGate.finishedAt = $null
        $state.finalGate.commands = @()
        Save-State -State $state -Path $StateFile

        foreach ($taskSet in $finalGradleTaskSets) {
            $result = Invoke-GradleTaskSet -Args $taskSet -PhaseName "Final Full Gate"
            $state.finalGate.commands = @($state.finalGate.commands + @($result))
            Save-State -State $state -Path $StateFile
        }

        if ($RunConnectedAppTestsAtEnd) {
            if ($connectedAvailable) {
                $result = Invoke-GradleTaskSet -Args $connectedAppTaskSet -PhaseName "Final Full Gate"
                $state.finalGate.commands = @($state.finalGate.commands + @($result))
            } else {
                $state.finalGate.notes = @($state.finalGate.notes + @("Connected app tests skipped: no device/emulator detected."))
                Write-Host "[Final Full Gate] Connected app tests skipped (no device/emulator)."
            }
            Save-State -State $state -Path $StateFile
        }

        if ($RunConnectedAllModulesAtEnd) {
            if ($connectedAvailable) {
                $result = Invoke-GradleTaskSet -Args $connectedAllModulesTaskSet -PhaseName "Final Full Gate"
                $state.finalGate.commands = @($state.finalGate.commands + @($result))
            } else {
                $state.finalGate.notes = @($state.finalGate.notes + @("Connected all-modules tests skipped: no device/emulator detected."))
                Write-Host "[Final Full Gate] Connected all-modules tests skipped (no device/emulator)."
            }
            Save-State -State $state -Path $StateFile
        }

        $state.finalGate.status = "completed"
        $state.finalGate.finishedAt = (Get-Date).ToString("o")
        Save-State -State $state -Path $StateFile
        Write-Host "[Final Full Gate] Completed."
    } else {
        Write-Host "Final full gate not run (either SkipFinalFullGate was set or ToPhase != 4)."
    }

    $state.summary = [ordered]@{
        finishedAt = (Get-Date).ToString("o")
        fromPhase = $FromPhase
        toPhase = $ToPhase
        dryRun = [bool]$DryRun
        finalGateExecuted = [bool]$shouldRunFinalGate
        status = "success"
    }
    Save-State -State $state -Path $StateFile

    Write-Host ""
    Write-Host "Requested SCIA/OGN/Hotspots phases completed successfully."
    Write-Host "State saved: $StateFile"
    exit 0
}
catch {
    $errorMessage = $_.Exception.Message
    Write-Error $errorMessage

    $failedPhase = @($state.phases | Where-Object { $_.status -eq "running" } | Select-Object -First 1)
    if ($failedPhase.Count -gt 0) {
        $record = $failedPhase[0]
        $record.status = "failed"
        $record.finishedAt = (Get-Date).ToString("o")
        $record.notes = @($record.notes + @("Failure: $errorMessage"))
        Upsert-PhaseRecord -State $state -Record $record
    } elseif ($state.finalGate.status -eq "running") {
        $state.finalGate.status = "failed"
        $state.finalGate.finishedAt = (Get-Date).ToString("o")
        $state.finalGate.notes = @($state.finalGate.notes + @("Failure: $errorMessage"))
    }

    $state.summary = [ordered]@{
        finishedAt = (Get-Date).ToString("o")
        fromPhase = $FromPhase
        toPhase = $ToPhase
        dryRun = [bool]$DryRun
        finalGateExecuted = [bool](($ToPhase -eq 4) -and (-not $SkipFinalFullGate))
        status = "failed"
        error = $errorMessage
    }
    Save-State -State $state -Path $StateFile

    Write-Host ""
    Write-Host "SCIA/OGN/Hotspots phase runner stopped on failure."
    Write-Host "Inspect state and resume with:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts/qa/run_scia_ogn_hotspots_phase_gates.ps1 -Resume"
    exit 1
}

