param(
    [ValidateRange(0, 7)]
    [int]$FromPhase = 0,
    [ValidateRange(0, 7)]
    [int]$ToPhase = 7,
    [switch]$Resume,
    [switch]$ResetState,
    [switch]$RunConnectedAppTests,
    [switch]$RunConnectedAllModulesAtEnd,
    [switch]$RequireConnectedDevice,
    [switch]$DryRun,
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

function Invoke-GradleTaskSet {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args,
        [Parameter(Mandatory = $true)]
        [string]$PhaseName
    )

    $commandDisplay = ".\gradlew.bat " + ($Args -join " ")
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

    & .\gradlew.bat @Args | ForEach-Object { Write-Host $_ }
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

$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($StateFile)) {
    $StateFile = Join-Path -Path $repoRoot -ChildPath "logs/phase-runner/openweathermap-rain-phase-state.json"
}

$phaseNames = [ordered]@{
    0 = "Phase 0 - Baseline and Safety"
    1 = "Phase 1 - Navigation and General Weather Entry"
    2 = "Phase 2 - Secure Weather Settings SSOT"
    3 = "Phase 3 - Weather Settings Screen"
    4 = "Phase 4 - Runtime Map Overlay Implementation"
    5 = "Phase 5 - MapScreen Wiring Without Architecture Drift"
    6 = "Phase 6 - Lifecycle and Runtime Hardening"
    7 = "Phase 7 - Documentation Sync"
}

$requiredTaskSets = @(
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
        plan = "openweathermap-rain"
        createdAt = (Get-Date).ToString("o")
        updatedAt = (Get-Date).ToString("o")
        phases = @()
    }
}

if (-not $state.Contains("phases") -or $null -eq $state.phases) {
    $state.phases = @()
}

$completedPhaseIds = @($state.phases | Where-Object { $_.status -eq "completed" } | ForEach-Object { [int]$_.id })

$runConnectedThisSession = $false
$connectedAvailable = $false
if ($RunConnectedAppTests -or $RunConnectedAllModulesAtEnd) {
    $connectedAvailable = Test-ConnectedDevice
    if (-not $connectedAvailable -and $RequireConnectedDevice) {
        throw "No connected device/emulator detected, but RequireConnectedDevice was set."
    }
}

Write-Host "OpenWeatherMap phase gates runner"
Write-Host "Repo: $repoRoot"
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

        foreach ($taskSet in $requiredTaskSets) {
            $result = Invoke-GradleTaskSet -Args $taskSet -PhaseName $phaseName
            $phaseRecord.commands = @($phaseRecord.commands + @($result))
            Upsert-PhaseRecord -State $state -Record $phaseRecord
            Save-State -State $state -Path $StateFile
        }

        if ($RunConnectedAppTests) {
            if ($connectedAvailable) {
                $result = Invoke-GradleTaskSet -Args $connectedAppTaskSet -PhaseName $phaseName
                $phaseRecord.commands = @($phaseRecord.commands + @($result))
                $runConnectedThisSession = $true
            } else {
                $phaseRecord.notes = @($phaseRecord.notes + @("Connected app tests skipped: no device/emulator detected."))
                Write-Host "[$phaseName] Connected app tests skipped (no device/emulator)."
            }
            Upsert-PhaseRecord -State $state -Record $phaseRecord
            Save-State -State $state -Path $StateFile
        }

        $phaseRecord.status = "completed"
        $phaseRecord.finishedAt = (Get-Date).ToString("o")
        Upsert-PhaseRecord -State $state -Record $phaseRecord
        Save-State -State $state -Path $StateFile
        Write-Host "[$phaseName] Completed."
    }

    if ($RunConnectedAllModulesAtEnd) {
        if ($connectedAvailable) {
            $result = Invoke-GradleTaskSet -Args $connectedAllModulesTaskSet -PhaseName "Final Connected Tests"
            $runConnectedThisSession = $true
            Write-Host "[Final Connected Tests] Completed."
        } else {
            Write-Host "[Final Connected Tests] Skipped (no device/emulator)."
        }
    }

    $state.summary = [ordered]@{
        finishedAt = (Get-Date).ToString("o")
        fromPhase = $FromPhase
        toPhase = $ToPhase
        dryRun = [bool]$DryRun
        connectedTestsExecuted = [bool]$runConnectedThisSession
        status = "success"
    }
    Save-State -State $state -Path $StateFile

    Write-Host ""
    Write-Host "All requested phases completed successfully."
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
    }

    $state.summary = [ordered]@{
        finishedAt = (Get-Date).ToString("o")
        fromPhase = $FromPhase
        toPhase = $ToPhase
        dryRun = [bool]$DryRun
        connectedTestsExecuted = [bool]$runConnectedThisSession
        status = "failed"
        error = $errorMessage
    }
    Save-State -State $state -Path $StateFile

    Write-Host ""
    Write-Host "Phase runner stopped on failure."
    Write-Host "Inspect state and resume with:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts/qa/run_openweathermap_phase_gates.ps1 -Resume"
    exit 1
}
