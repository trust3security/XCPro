param(
    [string]$ProjectRoot = (Get-Location).Path
)

$resolvedRoot = (Resolve-Path $ProjectRoot).Path
$escapedRoot = [Regex]::Escape($resolvedRoot)

$candidatePatterns = @(
    "GradleWorkerMain",
    "GradleWrapperMain"
)

$javaProcesses = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
if (-not $javaProcesses) {
    Write-Host "No Java processes found."
    exit 0
}

$killedCount = 0
foreach ($proc in $javaProcesses) {
    $cmd = [string]$proc.CommandLine
    if ([string]::IsNullOrWhiteSpace($cmd)) {
        continue
    }

    $isGradleProcess = $false
    foreach ($pattern in $candidatePatterns) {
        if ($cmd -match $pattern) {
            $isGradleProcess = $true
            break
        }
    }
    if (-not $isGradleProcess) {
        continue
    }

    if ($cmd -notmatch $escapedRoot) {
        continue
    }

    try {
        Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
        $killedCount++
    } catch {
        # Best-effort cleanup only; retry path in caller will handle failures.
    }
}

if ($killedCount -gt 0) {
    Write-Host "Killed stale Gradle Java processes: $killedCount"
} else {
    Write-Host "No stale Gradle Java processes found for this repo."
}
