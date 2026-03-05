param(
    [ValidateRange(1, 50)]
    [int]$MaxStrictAttempts = 6,
    [ValidateRange(0, 180)]
    [int]$CooldownMinutesBetweenAttempts = 10,
    [bool]$RequireConnectedDevice = $true,
    [bool]$FinalizeNonGreenOnExhaustedStrictAttempts = $true,
    [switch]$RunConnectedAppTestsForPkgE1,
    [switch]$RunConnectedAppTestsAtEnd,
    [switch]$RunConnectedAllModulesAtEnd,
    [switch]$SkipPhase2RefreshEvidenceTests,
    [switch]$SkipRequiredGatesForPkgE1,
    [string]$SessionId,
    [string]$StateFile,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    # Child tools can emit benign stderr lines; rely on exit codes.
    $PSNativeCommandUseErrorActionPreference = $false
}

$scriptDir = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$completionContractScript = Join-Path $scriptDir "run_mapscreen_completion_contract.ps1"
if (-not (Test-Path $completionContractScript)) {
    throw "Required script not found: $completionContractScript"
}

if ([string]::IsNullOrWhiteSpace($SessionId)) {
    $SessionId = Get-Date -Format "yyyyMMdd-HHmmss"
}

$sessionRoot = Join-Path $repoRoot ("logs\phase-runner\overnight\" + $SessionId)
if (-not (Test-Path $sessionRoot)) {
    New-Item -ItemType Directory -Path $sessionRoot -Force | Out-Null
}

$sessionLogPath = Join-Path $sessionRoot "session.log"
if (-not (Test-Path $sessionLogPath)) {
    New-Item -ItemType File -Path $sessionLogPath -Force | Out-Null
}

if ([string]::IsNullOrWhiteSpace($StateFile)) {
    $StateFile = Join-Path $sessionRoot "contract-state.json"
}

$summaryPath = Join-Path $sessionRoot "summary.json"
$sessionStartedAt = Get-Date
$attemptRecords = @()

function Write-SessionLog {
    param([Parameter(Mandatory = $true)][string]$Message)
    $line = "[mapscreen-overnight][$((Get-Date).ToString("o"))] $Message"
    Write-Host $line
    Add-Content -Path $sessionLogPath -Value $line
}

function Invoke-CompletionContractAttempt {
    param(
        [Parameter(Mandatory = $true)][string]$AttemptType,
        [Parameter(Mandatory = $true)][int]$AttemptNumber,
        [switch]$AllowFailureFlow
    )

    $runId = Get-Date -Format "yyyyMMdd-HHmmss"
    $args = @(
        "-ExecutionPolicy", "Bypass",
        "-File", $completionContractScript,
        "-RunId", $runId,
        "-StateFile", $StateFile
    )

    if ($RequireConnectedDevice) { $args += "-RequireConnectedDevice" }
    if ($RunConnectedAppTestsForPkgE1) { $args += "-RunConnectedAppTestsForPkgE1" }
    if ($RunConnectedAppTestsAtEnd) { $args += "-RunConnectedAppTestsAtEnd" }
    if ($RunConnectedAllModulesAtEnd) { $args += "-RunConnectedAllModulesAtEnd" }
    if ($SkipPhase2RefreshEvidenceTests) { $args += "-SkipPhase2RefreshEvidenceTests" }
    if ($SkipRequiredGatesForPkgE1) { $args += "-SkipRequiredGatesForPkgE1" }
    if ($DryRun) {
        $args += "-DryRun"
        # Contract dry-run mode does not synthesize phase-3 capture outputs; cap at scaffold phase.
        $args += "-FromPhase"
        $args += "0"
        $args += "-ToPhase"
        $args += "2"
    }

    if ($AllowFailureFlow) {
        $args += "-AllowPkgE1ThresholdFailure"
        $args += "-AllowThresholdRollupFailure"
        $args += "-AllowNonGreenReleasePackage"
    }

    Write-SessionLog "Attempt $AttemptNumber ($AttemptType) starting runId=$runId."
    $startedAt = Get-Date

    $output = & powershell @args 2>&1
    $exitCode = $LASTEXITCODE
    $finishedAt = Get-Date

    $failedPhase = $null
    foreach ($line in $output) {
        $text = [string]$line
        Write-Host $text
        Add-Content -Path $sessionLogPath -Value $text
        if ($text -match "FAILED at phase\s+([0-9]+)") {
            $failedPhase = [int]$Matches[1]
        }
    }

    $status = if ($exitCode -eq 0) { "pass" } else { "fail" }
    Write-SessionLog (
        "Attempt $AttemptNumber ($AttemptType) finished status=$status exitCode=$exitCode " +
        "failedPhase=$failedPhase durationSec=$([int](($finishedAt - $startedAt).TotalSeconds))."
    )

    return [ordered]@{
        attempt = $AttemptNumber
        type = $AttemptType
        runId = $runId
        startedAt = $startedAt.ToString("o")
        finishedAt = $finishedAt.ToString("o")
        status = $status
        exitCode = $exitCode
        failedPhase = $failedPhase
    }
}

Write-SessionLog "Session started. sessionId=$SessionId stateFile=$StateFile"
Write-SessionLog (
    "Config: maxStrictAttempts=$MaxStrictAttempts cooldownMin=$CooldownMinutesBetweenAttempts " +
    "requireConnectedDevice=$RequireConnectedDevice finalizeNonGreenOnExhaustedStrictAttempts=$FinalizeNonGreenOnExhaustedStrictAttempts dryRun=$DryRun"
)

$strictPassed = $false
for ($attempt = 1; $attempt -le $MaxStrictAttempts; $attempt += 1) {
    $record = Invoke-CompletionContractAttempt -AttemptType "strict" -AttemptNumber $attempt
    $attemptRecords += @($record)
    if ($record.exitCode -eq 0) {
        $strictPassed = $true
        break
    }
    if ($attempt -lt $MaxStrictAttempts) {
        Write-SessionLog "Sleeping $CooldownMinutesBetweenAttempts minute(s) before next strict attempt."
        if (-not $DryRun -and $CooldownMinutesBetweenAttempts -gt 0) {
            Start-Sleep -Seconds ($CooldownMinutesBetweenAttempts * 60)
        }
    }
}

$fallbackRecord = $null
if (-not $strictPassed -and $FinalizeNonGreenOnExhaustedStrictAttempts) {
    $fallbackAttempt = $attemptRecords.Count + 1
    Write-SessionLog "Strict attempts exhausted. Starting fallback non-green finalization."
    $fallbackRecord = Invoke-CompletionContractAttempt `
        -AttemptType "fallback_non_green" `
        -AttemptNumber $fallbackAttempt `
        -AllowFailureFlow
    $attemptRecords += @($fallbackRecord)
}

$overallStatus = "failed"
if ($strictPassed) {
    $overallStatus = "strict_green"
} elseif ($fallbackRecord -ne $null -and $fallbackRecord.exitCode -eq 0) {
    $overallStatus = "fallback_non_green_finalized"
}

$sessionFinishedAt = Get-Date
$summary = [ordered]@{
    sessionId = $SessionId
    startedAt = $sessionStartedAt.ToString("o")
    finishedAt = $sessionFinishedAt.ToString("o")
    overallStatus = $overallStatus
    strictPassed = $strictPassed
    maxStrictAttempts = $MaxStrictAttempts
    cooldownMinutesBetweenAttempts = $CooldownMinutesBetweenAttempts
    requireConnectedDevice = $RequireConnectedDevice
    finalizeNonGreenOnExhaustedStrictAttempts = $FinalizeNonGreenOnExhaustedStrictAttempts
    dryRun = [bool]$DryRun
    stateFile = $StateFile
    logPath = $sessionLogPath
    attempts = @($attemptRecords)
}

$summary | ConvertTo-Json -Depth 10 | Set-Content -Path $summaryPath -Encoding UTF8
Write-SessionLog "Session finished with overallStatus=$overallStatus summary=$summaryPath"

if ($overallStatus -eq "failed") {
    exit 1
}
exit 0
