param(
    [string]$Package = "com.example.xcpro.debug",
    [string]$Activity = "com.example.xcpro.MainActivity",
    [int]$DurationSec = 20,
    [switch]$AutoTap,
    [int]$TapX,
    [int]$TapY,
    [switch]$NoLaunch,
    [switch]$NoForceStop
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $output = & adb @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        $joined = $AdbArgs -join " "
        throw "adb failed: adb $joined`n$output"
    }
    return $output
}

function Try-FindReplayFabBounds {
    $dumpPath = "/sdcard/xcpro_ui.xml"
    Invoke-Adb @("shell", "uiautomator", "dump", $dumpPath) | Out-Null
    $xml = Invoke-Adb @("shell", "cat", $dumpPath)
    if (-not $xml) {
        return $null
    }

    function Get-Score([string]$text, [string]$source) {
        $score = 0
        if ($text -match "(?i)racing") { $score += 100 }
        if ($text -match "(?i)task") { $score += 90 }
        if ($text -match "(?i)tas") { $score += 80 }
        if ($text -match "(?i)replay") { $score += 50 }
        if ($source -eq "content-desc") { $score += 10 }
        return $score
    }

    $candidates = @()
    $matches = [regex]::Matches($xml, 'content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    foreach ($m in $matches) {
        $desc = $m.Groups[1].Value
        if ($desc -match "(?i)replay|racing|task|tas") {
            $candidates += [pscustomobject]@{
                X1 = [int]$m.Groups[2].Value
                Y1 = [int]$m.Groups[3].Value
                X2 = [int]$m.Groups[4].Value
                Y2 = [int]$m.Groups[5].Value
                Desc = $desc
                Score = Get-Score $desc "content-desc"
            }
        }
    }

    $idMatches = [regex]::Matches($xml, 'resource-id="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    foreach ($m in $idMatches) {
        $resId = $m.Groups[1].Value
        if ($resId -match "(?i)replay|racing|task|tas") {
            $candidates += [pscustomobject]@{
                X1 = [int]$m.Groups[2].Value
                Y1 = [int]$m.Groups[3].Value
                X2 = [int]$m.Groups[4].Value
                Y2 = [int]$m.Groups[5].Value
                Desc = $resId
                Score = Get-Score $resId "resource-id"
            }
        }
    }

    if ($candidates.Count -eq 0) {
        return $null
    }

    $best = $candidates | Sort-Object -Property Score -Descending | Select-Object -First 1
    return @{ X1 = $best.X1; Y1 = $best.Y1; X2 = $best.X2; Y2 = $best.Y2; Desc = $best.Desc }
}

function Tap-ReplayFab {
    param([int]$X, [int]$Y)
    Invoke-Adb @("shell", "input", "tap", "$X", "$Y") | Out-Null
}

Write-Host "XCPro replay validation" -ForegroundColor Cyan
Invoke-Adb @("logcat", "-c") | Out-Null

if (-not $NoForceStop) {
    Invoke-Adb @("shell", "am", "force-stop", $Package) | Out-Null
}

if (-not $NoLaunch) {
    Invoke-Adb @("shell", "am", "start", "-n", "$Package/$Activity") | Out-Null
    Start-Sleep -Seconds 2
}

if ($AutoTap) {
    if ($PSBoundParameters.ContainsKey('TapX') -and $PSBoundParameters.ContainsKey('TapY')) {
        Write-Host "AutoTap using provided coordinates: $TapX,$TapY"
        Tap-ReplayFab -X $TapX -Y $TapY
    } else {
        $bounds = Try-FindReplayFabBounds
        if ($bounds -ne $null) {
            $cx = [int](($bounds.X1 + $bounds.X2) / 2)
            $cy = [int](($bounds.Y1 + $bounds.Y2) / 2)
            Write-Host "AutoTap FAB ($($bounds.Desc)) at $cx,$cy"
            Tap-ReplayFab -X $cx -Y $cy
        } else {
            Write-Host "AutoTap failed to locate replay FAB. Please tap it manually now." -ForegroundColor Yellow
            Start-Sleep -Seconds 3
        }
    }
} else {
    Write-Host "Tap the replay FAB now..." -ForegroundColor Yellow
    Start-Sleep -Seconds 3
}

Write-Host "Capturing logcat for $DurationSec seconds..."
Start-Sleep -Seconds $DurationSec

$raw = Invoke-Adb @("logcat", "-d")
$logPath = Join-Path $PSScriptRoot "replay_validate_last.log"
$raw | Out-File -FilePath $logPath -Encoding utf8

$lines = $raw -split "`r?`n"
$signal = $lines | Where-Object { $_ -match "RACING_REPLAY|RACING_EVENT|REPLAY_SESSION_UI|TaskNavigation" }

Write-Host "Filtered log written to $logPath" -ForegroundColor DarkGray

$eventLines = $signal | Where-Object { $_ -match "RACING_REPLAY" -and $_ -match "Event" }
$events = @()
foreach ($line in $eventLines) {
    if ($line -match "Event\s+\d+:\s+([A-Z_]+).+t=(\d+)") {
        $events += [pscustomobject]@{ Type = $matches[1]; TimeMs = [long]$matches[2]; Line = $line }
    }
}

if ($events.Count -eq 0) {
    $noEvents = $signal | Where-Object { $_ -match "no navigation events captured" }
    if ($noEvents) {
        Write-Host "FAIL: Replay ran but no navigation events were captured. Ensure an active racing task exists (start/turnpoint/finish) before running replay." -ForegroundColor Red
    } else {
        Write-Host "FAIL: No racing events found." -ForegroundColor Red
    }
    exit 1
}

$expected = @("START","TURNPOINT","FINISH")
$idx = 0
foreach ($evt in $events) {
    if ($idx -lt $expected.Count -and $evt.Type -eq $expected[$idx]) {
        $idx++
    }
}

$monotonic = $true
for ($i = 1; $i -lt $events.Count; $i++) {
    if ($events[$i].TimeMs -lt $events[$i - 1].TimeMs) {
        $monotonic = $false
        break
    }
}

if ($idx -eq $expected.Count -and $monotonic) {
    Write-Host "PASS: START -> TURNPOINT -> FINISH in order (monotonic timestamps)." -ForegroundColor Green
    $events | ForEach-Object { Write-Host $_.Line }
    exit 0
}

Write-Host "FAIL: Missing or out-of-order events." -ForegroundColor Red
$events | ForEach-Object { Write-Host $_.Line }
exit 1
