param(
    [string]$ProjectRoot = (Get-Location).Path,
    [switch]$Aggressive
)

$resolvedRoot = (Resolve-Path $ProjectRoot).Path
$resolvedRoot = $resolvedRoot.TrimEnd('\')
$buildLockPaths = @(
    "$resolvedRoot\feature\map\build\test-results\testDebugUnitTest\binary\output.bin",
    "$resolvedRoot\app\build\test-results\testDebugUnitTest\binary\output.bin",
    "$resolvedRoot\feature\map\build\kspCaches",
    "$resolvedRoot\feature\map\build\generated\ksp",
    "$resolvedRoot\feature\traffic\build\kspCaches",
    "$resolvedRoot\feature\traffic\build\generated\ksp"
)

function Remove-WithRetry([string]$Path) {
    for ($i = 0; $i -lt 40; $i++) {
        if (-not (Test-Path -LiteralPath $Path)) {
            return $true
        }

        try {
            if ((Get-Item -LiteralPath $Path) -is [System.IO.DirectoryInfo]) {
                Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            } else {
                Remove-Item -LiteralPath $Path -Force -ErrorAction Stop
            }
            return $true
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    return $false
}

Write-Host "[LOCK-RECOVERY] Stopping Gradle daemons..."
& "$resolvedRoot\gradlew.bat" --stop >$null 2>&1

Write-Host "[LOCK-RECOVERY] Killing stale Gradle Java processes in repo..."
$killScript = Join-Path $resolvedRoot "scripts\dev\kill_stale_gradle_processes.ps1"
if (Test-Path $killScript) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $killScript -ProjectRoot $resolvedRoot >$null 2>&1
}

Write-Host "[LOCK-RECOVERY] Removing known lock files..."
$failedCount = 0
foreach ($path in $buildLockPaths) {
    if (-not (Test-Path -LiteralPath $path)) {
        continue
    }

    if (-not (Remove-WithRetry -Path $path)) {
        Write-Host "[LOCK-RECOVERY] WARN: still locked: $path"
        $failedCount++
    }
}

$lockSearchRoots = @(
    "$resolvedRoot\.gradle",
    "$resolvedRoot\build",
    "$env:USERPROFILE\.gradle\wrapper\dists"
)

if ($Aggressive) {
    $lockSearchRoots += "$env:LOCALAPPDATA\JetBrains\IntelliJIdea*\system\caches"
}

foreach ($root in $lockSearchRoots) {
    if (-not (Test-Path $root)) {
        continue
    }

    Get-ChildItem -Path $root -Recurse -Filter "*.lck" -File -ErrorAction SilentlyContinue | ForEach-Object {
        if (-not (Remove-WithRetry -Path $_.FullName)) {
            Write-Host "[LOCK-RECOVERY] WARN: still locked: $($_.FullName)"
            $failedCount++
        }
    }
}

if ($failedCount -gt 0) {
    Write-Host "[LOCK-RECOVERY] Could not remove $failedCount lock paths automatically."
    exit 1
}

Write-Host "[LOCK-RECOVERY] Lock recovery completed."
exit 0
