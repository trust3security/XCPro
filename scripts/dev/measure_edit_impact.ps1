param(
    [string[]]$Tasks = @(':app:assembleDebug'),
    [int]$Iterations = 3,
    [string[]]$ScenarioNames = @(),
    [switch]$NoDaemon,
    [switch]$NoBuildCache,
    [switch]$RepairOnFailure
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Iterations -lt 1) {
    throw 'Iterations must be at least 1.'
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

function Invoke-GradleBuild {
    param(
        [string[]]$BuildTasks,
        [bool]$EnableBuildCache,
        [bool]$DisableDaemon
    )

    $args = @()
    if (-not $EnableBuildCache) {
        $args += '--no-build-cache'
    } else {
        $args += '--build-cache'
    }
    if ($DisableDaemon) {
        $args += '--no-daemon'
    }
    $args += @('-q', '--console=plain')
    $args += $BuildTasks

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & .\gradlew.bat @args
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }
    $sw.Stop()
    return $sw.Elapsed
}

function Get-Stats {
    param(
        [double[]]$ValuesMs
    )

    $values = @($ValuesMs)
    $sorted = @($values | Sort-Object)
    $count = $sorted.Length
    $median = if ($count % 2 -eq 0) {
        ($sorted[$count / 2 - 1] + $sorted[$count / 2]) / 2
    } else {
        $sorted[[int][math]::Floor($count / 2)]
    }

    return [pscustomobject]@{
        Count = $count
        AvgMs = ($values | Measure-Object -Average).Average
        MinMs = ($values | Measure-Object -Minimum).Minimum
        MaxMs = ($values | Measure-Object -Maximum).Maximum
        MedianMs = $median
    }
}

function Invoke-RepairState {
    $repairCommand = '.\repair-build.bat all none'
    cmd /c $repairCommand
    if ($LASTEXITCODE -ne 0) {
        throw "Repair command failed with exit code $LASTEXITCODE."
    }
}

function Set-ToggledValue {
    param(
        [string]$Path,
        [string]$Marker,
        [int]$Value
    )

    $content = Get-Content -Path $Path -Raw
    $pattern = "(?m)^(\s*.*\b${Marker}: Int = )\d+"
    $updated = [regex]::Replace($content, $pattern, "`${1}$Value", 1)
    if ($updated -eq $content) {
        throw "Unable to update marker '$Marker' in $Path."
    }
    Set-Content -Path $Path -Value $updated -NoNewline
}

$scenarios = @(
    @{
        Name = 'app-impl'
        Path = 'app/src/main/java/com/trust3/xcpro/benchmark/AppBuildBenchmarkApi.kt'
        Marker = 'IMPL_VERSION'
    },
    @{
        Name = 'map-impl'
        Path = 'feature/map/src/main/java/com/trust3/xcpro/map/benchmark/MapBuildBenchmarkApi.kt'
        Marker = 'IMPL_VERSION'
    },
    @{
        Name = 'map-abi'
        Path = 'feature/map/src/main/java/com/trust3/xcpro/map/benchmark/MapBuildBenchmarkApi.kt'
        Marker = 'MAP_ABI_VERSION'
    },
    @{
        Name = 'core-impl'
        Path = 'core/common/src/main/java/com/trust3/xcpro/benchmark/CoreBuildBenchmarkApi.kt'
        Marker = 'IMPL_VERSION'
    },
    @{
        Name = 'core-abi'
        Path = 'core/common/src/main/java/com/trust3/xcpro/benchmark/CoreBuildBenchmarkApi.kt'
        Marker = 'ABI_VERSION'
    }
)

if ($ScenarioNames.Count -gt 0) {
    $scenarioLookup = @{}
    foreach ($scenario in $scenarios) {
        $scenarioLookup[$scenario.Name] = $scenario
    }
    $selectedScenarios = New-Object System.Collections.Generic.List[object]
    foreach ($scenarioName in $ScenarioNames) {
        if (-not $scenarioLookup.ContainsKey($scenarioName)) {
            throw "Unknown scenario '$scenarioName'."
        }
        $selectedScenarios.Add($scenarioLookup[$scenarioName])
    }
    $scenarios = $selectedScenarios.ToArray()
}

$originalContentByPath = @{}
foreach ($scenario in $scenarios) {
    $path = $scenario.Path
    if (-not $originalContentByPath.ContainsKey($path)) {
        $originalContentByPath[$path] = Get-Content -Path $path -Raw
    }
}

$taskDesc = $Tasks -join ' '
Write-Host "Edit impact benchmark for: $taskDesc"
Write-Host "Iterations per scenario: $Iterations"
Write-Host 'Warm baseline run'
[void](Invoke-GradleBuild -BuildTasks $Tasks -EnableBuildCache (-not $NoBuildCache.IsPresent) -DisableDaemon $NoDaemon.IsPresent)

$results = New-Object System.Collections.Generic.List[object]

try {
    foreach ($scenario in $scenarios) {
        Write-Host "Scenario: $($scenario.Name)"
        $samples = New-Object System.Collections.Generic.List[double]
        for ($i = 0; $i -lt $Iterations; $i++) {
            $value = if ($i % 2 -eq 0) { 2 } else { 1 }
            Set-ToggledValue -Path $scenario.Path -Marker $scenario.Marker -Value $value
            try {
                $elapsed = Invoke-GradleBuild -BuildTasks $Tasks -EnableBuildCache (-not $NoBuildCache.IsPresent) -DisableDaemon $NoDaemon.IsPresent
            }
            catch {
                if (-not $RepairOnFailure.IsPresent) {
                    throw
                }
                Write-Host '  build failed; running repair and retrying once'
                Invoke-RepairState
                Set-ToggledValue -Path $scenario.Path -Marker $scenario.Marker -Value $value
                $elapsed = Invoke-GradleBuild -BuildTasks $Tasks -EnableBuildCache (-not $NoBuildCache.IsPresent) -DisableDaemon $NoDaemon.IsPresent
            }
            $ms = $elapsed.TotalMilliseconds
            $samples.Add($ms)
            Write-Host ("  run #{0}: {1:N1} ms" -f ($i + 1), $ms)
        }
        $stats = Get-Stats -ValuesMs $samples.ToArray()
        $results.Add([pscustomobject]@{
            Scenario = $scenario.Name
            Count = $stats.Count
            AvgMs = [math]::Round($stats.AvgMs, 1)
            MedianMs = [math]::Round($stats.MedianMs, 1)
            MinMs = [math]::Round($stats.MinMs, 1)
            MaxMs = [math]::Round($stats.MaxMs, 1)
        })
    }
}
finally {
    foreach ($path in $originalContentByPath.Keys) {
        Set-Content -Path $path -Value $originalContentByPath[$path] -NoNewline
    }
}

Write-Host ''
Write-Host 'Summary (ms):'
$results | Format-Table -AutoSize
