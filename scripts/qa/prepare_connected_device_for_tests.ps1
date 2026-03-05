param(
    [switch]$DisableStayAwake,
    [switch]$SkipUnlockGesture
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )

    $output = & adb @Args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($Args -join ' ')`n$output"
    }
    return $output
}

function Get-ConnectedDeviceSerials {
    $lines = Invoke-Adb -Args @("devices")
    $serials = @()
    foreach ($line in $lines) {
        if ($line -match "^\s*$") { continue }
        if ($line -match "^List of devices attached") { continue }
        if ($line -match "^(\S+)\s+device$") {
            $serials += $Matches[1]
        }
    }
    return $serials
}

$connected = @(Get-ConnectedDeviceSerials)
if ($connected.Count -eq 0) {
    throw "[mapscreen-device] No connected devices in 'device' state."
}

Write-Host "[mapscreen-device] Connected device(s): $($connected -join ', ')"

if ($DisableStayAwake) {
    Invoke-Adb -Args @("shell", "settings", "put", "global", "stay_on_while_plugged_in", "0") | Out-Null
    Write-Host "[mapscreen-device] Disabled stay-awake while plugged in (stay_on_while_plugged_in=0)."
    exit 0
}

# Wake and unlock before instrumentation to avoid Compose hierarchy loss when device is dozing/locked.
Invoke-Adb -Args @("shell", "input", "keyevent", "KEYCODE_WAKEUP") | Out-Null
Invoke-Adb -Args @("shell", "wm", "dismiss-keyguard") | Out-Null
if (-not $SkipUnlockGesture) {
    Invoke-Adb -Args @("shell", "input", "swipe", "540", "1800", "540", "400", "300") | Out-Null
}

Invoke-Adb -Args @("shell", "settings", "put", "global", "stay_on_while_plugged_in", "3") | Out-Null

$powerDump = Invoke-Adb -Args @("shell", "dumpsys", "power")
$wakeLine = ($powerDump | Select-String -Pattern "mWakefulness=").Line | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($wakeLine)) {
    Write-Warning "[mapscreen-device] Could not read mWakefulness from dumpsys power."
} else {
    Write-Host "[mapscreen-device] $wakeLine"
    if ($wakeLine -notmatch "Awake") {
        Write-Warning "[mapscreen-device] Device is not fully awake; unlock screen manually before connected tests."
    }
}

Write-Host "[mapscreen-device] Device preflight complete."
