param(
    [switch]$NoDaemon,
    [switch]$NoConfigurationCache,
    [switch]$DryRun,
    [ValidateRange(1, 3)]
    [int]$MaxAttempts = 2
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

function New-GradleArgs {
    $args = @("testDebugUnitTest", "--console=plain")
    if ($NoDaemon) {
        $args += "--no-daemon"
    }
    if ($NoConfigurationCache) {
        $args += "--no-configuration-cache"
    }
    return ,$args
}

function Invoke-Gradle {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )

    $display = ".\gradlew.bat " + ($Args -join " ")
    Write-Host "Running: $display"

    if ($DryRun) {
        return [ordered]@{
            ExitCode = 0
            Output = @("DRY RUN: $display")
        }
    }

    $lines = @()
    & .\gradlew.bat @Args 2>&1 | ForEach-Object {
        $lines += $_.ToString()
        Write-Host $_
    }

    return [ordered]@{
        ExitCode = $LASTEXITCODE
        Output = $lines
    }
}

function Test-LockFailure {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ExitCode,
        [Parameter(Mandatory = $true)]
        [string[]]$Output
    )

    if ($ExitCode -eq 0) {
        return $false
    }

    $joined = $Output -join "`n"
    return (
        $joined.Contains("Unable to delete directory") -and
        $joined.Contains("output.bin")
    ) -or $joined.Contains("The process cannot access the file because it is being used by another process.")
}

function Clear-StaleOutputBins {
    $paths = Get-ChildItem -Path $repoRoot -Recurse -Filter output.bin -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*\build\test-results\testDebugUnitTest\binary\output.bin" }

    foreach ($path in $paths) {
        Write-Host "Removing stale lock artifact: $($path.FullName)"
        Remove-Item -LiteralPath $path.FullName -Force -ErrorAction SilentlyContinue
    }
}

$gradleArgs = New-GradleArgs

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    Write-Host "Attempt $attempt of $MaxAttempts"
    $result = Invoke-Gradle -Args $gradleArgs
    if ($result.ExitCode -eq 0) {
        exit 0
    }

    $isLockFailure = Test-LockFailure -ExitCode $result.ExitCode -Output $result.Output
    if (-not $isLockFailure -or $attempt -ge $MaxAttempts) {
        exit $result.ExitCode
    }

    Write-Host "Detected Windows test-results lock churn. Stopping daemons and clearing stale output.bin before one retry."
    if (-not $DryRun) {
        & .\gradlew.bat --stop | Out-Host
    }
    Clear-StaleOutputBins
}

exit 1
