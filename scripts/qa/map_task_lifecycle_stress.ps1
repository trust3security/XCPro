param(
    [string]$ApplicationId = "com.example.openxcpro.debug",
    [string]$Activity = "com.example.xcpro.MainActivity",
    [int]$Iterations = 10,
    [int]$BackgroundDelayMs = 800
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-Adb {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        throw "adb is required but was not found in PATH."
    }
}

function Get-LaunchTiming {
    param([string[]]$OutputLines)
    $totalLine = $OutputLines | Where-Object { $_ -match "TotalTime:\s+\d+" } | Select-Object -First 1
    $waitLine = $OutputLines | Where-Object { $_ -match "WaitTime:\s+\d+" } | Select-Object -First 1
    $totalMs = if ($totalLine) { [int](($totalLine -replace ".*TotalTime:\s+", "").Trim()) } else { 0 }
    $waitMs = if ($waitLine) { [int](($waitLine -replace ".*WaitTime:\s+", "").Trim()) } else { 0 }
    $observedMs = if ($totalMs -gt 0) { $totalMs } else { $waitMs }
    return [pscustomobject]@{
        TotalMs = $totalMs
        WaitMs = $waitMs
        ObservedMs = $observedMs
    }
}

function Start-AppAndMeasure {
    param(
        [string]$AppId,
        [string]$ActivityName
    )
    $component = "$AppId/$ActivityName"
    $output = adb shell am start -W -n $component
    return Get-LaunchTiming -OutputLines $output
}

Require-Adb

$devices = adb devices | Select-String -Pattern "\sdevice$"
if (-not $devices) {
    throw "No attached adb device found."
}

Write-Host "Running map/task lifecycle stress scenario..."
Write-Host "App: $ApplicationId  Activity: $Activity  Iterations: $Iterations"

$resumeTimes = New-Object System.Collections.Generic.List[int]

# Ensure the app is foregrounded once before loop.
$initial = Start-AppAndMeasure -AppId $ApplicationId -ActivityName $Activity
if ($null -ne $initial) {
    Write-Host ("Initial start timing(ms): observed={0}, total={1}, wait={2}" -f $initial.ObservedMs, $initial.TotalMs, $initial.WaitMs)
}

for ($i = 1; $i -le $Iterations; $i++) {
    adb shell input keyevent 3 | Out-Null
    Start-Sleep -Milliseconds $BackgroundDelayMs

    $timing = Start-AppAndMeasure -AppId $ApplicationId -ActivityName $Activity
    if ($null -ne $timing) {
        $resumeTimes.Add($timing.ObservedMs)
    }
    $displayTime = if ($null -ne $timing) {
        "observed=$($timing.ObservedMs), total=$($timing.TotalMs), wait=$($timing.WaitMs)"
    } else {
        "n/a"
    }
    Write-Host ("Cycle {0}/{1} resume timing(ms): {2}" -f $i, $Iterations, $displayTime)
}

if ($resumeTimes.Count -eq 0) {
    Write-Host "No TotalTime values captured."
    exit 0
}

$max = ($resumeTimes | Measure-Object -Maximum).Maximum
$min = ($resumeTimes | Measure-Object -Minimum).Minimum
$avg = [Math]::Round((($resumeTimes | Measure-Object -Average).Average), 2)

Write-Host ""
Write-Host "Lifecycle stress summary (ms)"
Write-Host "  min: $min"
Write-Host "  avg: $avg"
Write-Host "  max: $max"
