param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fast-loop", "slice-terrain", "slice-profile", "slice-replay", "pr-ready", "release-ready")]
    [string]$Profile,
    [switch]$NoDaemon,
    [switch]$NoConfigurationCache,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

function Get-SharedFlags {
    $flags = @("--console=plain")
    if ($NoDaemon) {
        $flags += "--no-daemon"
    }
    if ($NoConfigurationCache) {
        $flags += "--no-configuration-cache"
    }
    return ,$flags
}

function Invoke-GradleStep {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $display = ".\gradlew.bat " + ($Args -join " ")
    Write-Host "[$Label] $display"

    if ($DryRun) {
        return
    }

    & .\gradlew.bat @Args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed (exit $LASTEXITCODE): $display"
    }
}

function Invoke-ReliableRootUnitTests {
    Write-Host "[root-unit-tests] .\scripts\qa\run_root_unit_tests_reliable.bat"

    if ($DryRun) {
        return
    }

    $args = @()
    if ($NoDaemon) {
        $args += "--NoDaemon"
    }
    if ($NoConfigurationCache) {
        $args += "--NoConfigurationCache"
    }

    & .\scripts\qa\run_root_unit_tests_reliable.bat @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "Reliable root unit-test wrapper failed (exit $LASTEXITCODE)."
    }
}

$sharedFlags = Get-SharedFlags

$profiles = @{
    "fast-loop" = @(
        @{ kind = "gradle"; label = "fast-arch"; args = @("enforceArchitectureFast") + $sharedFlags },
        @{ kind = "gradle"; label = "fast-assemble"; args = @(":feature:map:assembleDebug", ":dfcards-library:assembleDebug") + $sharedFlags }
    )
    "slice-terrain" = @(
        @{ kind = "gradle"; label = "rules"; args = @("enforceRules") + $sharedFlags },
        @{ kind = "gradle"; label = "qnh"; args = @(":feature:map:testDebugUnitTest", "--tests", "com.example.xcpro.qnh.CalibrateQnhUseCaseTest") + $sharedFlags },
        @{ kind = "gradle"; label = "terrain-repo"; args = @(":feature:map:testDebugUnitTest", "--tests", "com.example.xcpro.terrain.TerrainElevationRepositoryTest") + $sharedFlags },
        @{ kind = "gradle"; label = "replay-gate"; args = @(":feature:flight-runtime:testDebugUnitTest", "--tests", "com.example.xcpro.sensors.FlightDataCalculatorEngineReplayTerrainGateTest") + $sharedFlags },
        @{ kind = "gradle"; label = "agl-calculator"; args = @(":dfcards-library:testDebugUnitTest", "--tests", "com.example.dfcards.dfcards.calculations.SimpleAglCalculatorTest") + $sharedFlags },
        @{ kind = "gradle"; label = "assemble"; args = @("assembleDebug") + $sharedFlags }
    )
    "slice-profile" = @(
        @{ kind = "gradle"; label = "rules"; args = @("enforceRules") + $sharedFlags },
        @{ kind = "gradle"; label = "profile-app"; args = @(":app:testDebugUnitTest", "--tests", "com.example.xcpro.profiles.*") + $sharedFlags },
        @{ kind = "gradle"; label = "profile-feature"; args = @(":feature:profile:testDebugUnitTest") + $sharedFlags },
        @{ kind = "gradle"; label = "assemble"; args = @("assembleDebug") + $sharedFlags }
    )
    "slice-replay" = @(
        @{ kind = "gradle"; label = "rules"; args = @("enforceRules") + $sharedFlags },
        @{ kind = "gradle"; label = "replay-map"; args = @(":feature:map:testDebugUnitTest", "--tests", "com.example.xcpro.replay.*") + $sharedFlags },
        @{ kind = "gradle"; label = "replay-igc"; args = @(":feature:igc:testDebugUnitTest", "--tests", "com.example.xcpro.replay.*") + $sharedFlags },
        @{ kind = "gradle"; label = "assemble"; args = @("assembleDebug") + $sharedFlags }
    )
    "pr-ready" = @(
        @{ kind = "gradle"; label = "rules"; args = @("enforceRules") + $sharedFlags },
        @{ kind = "reliable-root-tests"; label = "root-unit-tests" },
        @{ kind = "gradle"; label = "assemble"; args = @("assembleDebug") + $sharedFlags }
    )
    "release-ready" = @(
        @{ kind = "gradle"; label = "rules"; args = @("enforceRules") + $sharedFlags },
        @{ kind = "reliable-root-tests"; label = "root-unit-tests" },
        @{ kind = "gradle"; label = "assemble"; args = @("assembleDebug") + $sharedFlags },
        @{ kind = "gradle"; label = "connected"; args = @("connectedDebugAndroidTest", "--no-parallel") + $(if ($NoDaemon) { @("--no-daemon") } else { @() }) + $(if ($NoConfigurationCache) { @("--no-configuration-cache") } else { @() }) }
    )
}

Write-Host "XCPro verification profile: $Profile"
Write-Host "Repo: $repoRoot"
Write-Host "Mode: $(if ($DryRun) { 'dry-run' } else { 'execute' })"

foreach ($step in $profiles[$Profile]) {
    switch ($step.kind) {
        "gradle" {
            Invoke-GradleStep -Args $step.args -Label $step.label
        }
        "reliable-root-tests" {
            Invoke-ReliableRootUnitTests
        }
        default {
            throw "Unknown step kind '$($step.kind)'."
        }
    }
}

Write-Host "Profile '$Profile' completed successfully."
