param(
    [string[]]$Tasks = @(':feature:map:compileDebugUnitTestKotlin'),
    [int]$WarmupIterations = 1,
    [int]$MeasuredIterations = 3,
    [switch]$NoDaemon,
    [switch]$NoBuildCache,
    [switch]$CleanFirst
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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

    if ($null -eq $values -or $values.Count -eq 0) {
        return [pscustomobject]@{Count=0; AvgMs=0; MinMs=0; MaxMs=0; MedianMs=0}
    }

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

if ($MeasuredIterations -lt 1) {
    throw 'MeasuredIterations must be at least 1.'
}
if ($WarmupIterations -lt 0) {
    throw 'WarmupIterations must be 0 or greater.'
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

$taskDesc = $Tasks -join ' '
Write-Host "Starting build timing: $taskDesc"
Write-Host "Warmup iterations: $WarmupIterations"
Write-Host "Measured iterations: $MeasuredIterations"

if ($CleanFirst) {
    Write-Host 'Cleaning workspace before measurement (requested).'
    & .\gradlew.bat clean
    if ($LASTEXITCODE -ne 0) {
        throw 'clean failed.'
    }
}

for ($i = 1; $i -le $WarmupIterations; $i++) {
    Write-Host "Warmup #$i"
    [void](Invoke-GradleBuild -BuildTasks $Tasks -EnableBuildCache (-not $NoBuildCache.IsPresent) -DisableDaemon $NoDaemon.IsPresent)
}

$samples = New-Object System.Collections.Generic.List[double]
for ($i = 1; $i -le $MeasuredIterations; $i++) {
    Write-Host "Measured run #$i"
    $elapsed = Invoke-GradleBuild -BuildTasks $Tasks -EnableBuildCache (-not $NoBuildCache.IsPresent) -DisableDaemon $NoDaemon.IsPresent
    $ms = $elapsed.TotalMilliseconds
    $samples.Add($ms)
    Write-Host ("  run #{0}: {1:N1} ms" -f $i, $ms)
}

$stats = Get-Stats -ValuesMs $samples.ToArray()
Write-Host "Summary (ms): count=$($stats.Count), avg=$([math]::Round($stats.AvgMs,1)), median=$([math]::Round($stats.MedianMs,1)), min=$([math]::Round($stats.MinMs,1)), max=$([math]::Round($stats.MaxMs,1))"
