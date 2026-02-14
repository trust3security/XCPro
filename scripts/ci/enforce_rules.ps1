param(
    [switch]$Verbose
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-Rg {
    $rg = Get-Command rg -ErrorAction SilentlyContinue
    if (-not $rg) {
        throw "ripgrep (rg) is required for this script."
    }
}

function Invoke-Rg {
    param(
        [string[]]$RgArgs
    )
    $output = & rg @RgArgs 2>$null
    $code = $LASTEXITCODE
    return [pscustomobject]@{
        Output = $output
        Code   = $code
    }
}

function Assert-NoMatches {
    param(
        [string]$Name,
        [string[]]$RgArgs,
        [scriptblock]$Filter = $null
    )
    $result = Invoke-Rg -RgArgs $RgArgs
    if ($result.Code -gt 1) {
        throw "rg failed for '$Name' (exit $($result.Code))."
    }
    if ($result.Code -eq 0) {
        $lines = $result.Output
        if ($Filter -ne $null) {
            $lines = $lines | Where-Object $Filter
        }
        if ($lines) {
            Write-Host ""
            Write-Host "FAIL: $Name"
            $lines | ForEach-Object { Write-Host $_ }
            $script:HadFailures = $true
        }
    }
}

function Assert-NoMatchesInComposableFiles {
    param(
        [string]$Name,
        [string[]]$FileGlobs,
        [string]$Pattern
    )

    $discoverArgs = @("-l", "@Composable") + $FileGlobs
    $discover = Invoke-Rg -RgArgs $discoverArgs
    if ($discover.Code -gt 1) {
        throw "rg failed while discovering composable files for '$Name' (exit $($discover.Code))."
    }
    if ($discover.Code -eq 1) {
        return
    }

    $files = @($discover.Output | Where-Object { $_ -and $_.Trim().Length -gt 0 })
    if (-not $files -or $files.Count -eq 0) {
        return
    }

    $matchArgs = @("-n", $Pattern) + $files
    $matches = Invoke-Rg -RgArgs $matchArgs
    if ($matches.Code -gt 1) {
        throw "rg failed while scanning composable files for '$Name' (exit $($matches.Code))."
    }
    if ($matches.Code -eq 0) {
        Write-Host ""
        Write-Host "FAIL: $Name"
        $matches.Output | ForEach-Object { Write-Host $_ }
        $script:HadFailures = $true
    }
}

Require-Rg

$script:HadFailures = $false
$repoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Set-Location $repoRoot

# 1) Timebase: no direct system time in domain/fusion logic.
$timebaseArgs = @(
    "-n",
    "-P",
    "(System\.currentTimeMillis|SystemClock|Date\(|Instant\.now)",
    "--glob", "feature/**/src/main/java/**/sensors/**/*.kt",
    "--glob", "feature/**/src/main/java/**/domain/**/*.kt",
    "--glob", "core/**/src/main/java/**/domain/**/*.kt",
    "--glob", "dfcards-library/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Timebase usage in domain/fusion" -RgArgs $timebaseArgs

# 2) DI: core pipeline constructed inside managers (heuristic).
$diArgs = @(
    "-n",
    "FlightDataCalculator\(",
    "--glob", "**/src/main/java/**/*Manager.kt",
    "--glob", "**/src/main/java/**/*Service*.kt"
)
Assert-NoMatches -Name "DI: pipeline constructed inside managers" -RgArgs $diArgs

# 3) ViewModel purity: no SharedPreferences or UI types in ViewModels.
$vmArgs = @(
    "-n",
    "(SharedPreferences|getSharedPreferences|androidx\\.compose\\.ui)",
    "--glob", "**/src/main/java/**/*ViewModel.kt"
)
Assert-NoMatches -Name "ViewModel purity violations" -RgArgs $vmArgs

# 4) Compose lifecycle: ban collectAsState without lifecycle awareness.
$collectArgs = @(
    "-n",
    "collectAsState\(",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "collectAsState without lifecycle" -RgArgs $collectArgs -Filter { $_ -notmatch "collectAsStateWithLifecycle" }

# 5) Vendor strings: no xcsoar in production Kotlin source.
$vendorArgs = @(
    "-n",
    "-i",
    "xcsoar",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Vendor strings in production Kotlin source" -RgArgs $vendorArgs

# 6) ASCII hygiene: no non-ASCII characters in production Kotlin source.
$asciiArgs = @(
    "-n",
    "-P",
    "[^\x00-\x7F]",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Non-ASCII characters in production Kotlin source" -RgArgs $asciiArgs

# 7) Task coordinator boundary: no manager escape-hatch APIs/calls in production source.
$taskBoundaryArgs = @(
    "-n",
    "(getRacingTaskManager\(|getAATTaskManager\()",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Task coordinator manager escape hatches in production source" -RgArgs $taskBoundaryArgs

# 8) Task manager boundary: no MapLibre imports in AAT/Racing task managers.
$taskManagerMapLibreImportsArgs = @(
    "-n",
    "org\.maplibre\.android",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt"
)
Assert-NoMatches -Name "MapLibre imports in task managers" -RgArgs $taskManagerMapLibreImportsArgs

# 9) Task manager boundary: no legacy map-render/edit APIs on task managers.
$taskManagerMapApiArgs = @(
    "-n",
    "(fun\s+plotRacingOnMap\(|fun\s+clearRacingFromMap\(|fun\s+plotAATOnMap\(|fun\s+clearAATFromMap\(|fun\s+plotAATEditOverlay\(|fun\s+clearAATEditOverlay\(|fun\s+checkTargetPointHit\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt"
)
Assert-NoMatches -Name "Legacy map APIs in task managers" -RgArgs $taskManagerMapApiArgs

# 10) Task domain purity: no Android/Compose/MapLibre imports in task domain packages.
$taskDomainUiImportsArgs = @(
    "-n",
    "^import\s+(android\.|androidx\.|org\.maplibre\.android)",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/domain/**/*.kt"
)
Assert-NoMatches -Name "Android/UI imports in task domain packages" -RgArgs $taskDomainUiImportsArgs

# 11) Task UI boundary: no TaskManagerCoordinator type leaks in composable task/map-task surfaces.
$taskComposableGlobs = @(
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/ui/task/**/*.kt"
)
Assert-NoMatchesInComposableFiles `
    -Name "TaskManagerCoordinator type leaks in task composable surfaces" `
    -FileGlobs $taskComposableGlobs `
    -Pattern "TaskManagerCoordinator"

# 12) Task coordinator cleanup: do not reintroduce deprecated helper escape hatches.
$taskCoordinatorLegacyHelperArgs = @(
    "-n",
    "(fun\s+getTaskSpecificWaypoint\(|fun\s+haversineDistance\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt"
)
Assert-NoMatches -Name "Legacy TaskManagerCoordinator helper escape hatches" -RgArgs $taskCoordinatorLegacyHelperArgs

# 13) Task UI boundary: no direct coordinator instance calls from composable task/map-task surfaces.
Assert-NoMatchesInComposableFiles `
    -Name "Direct coordinator calls in task composable surfaces" `
    -FileGlobs $taskComposableGlobs `
    -Pattern "\b(taskManager|taskCoordinator|coordinator)\s*\."

# 14) Runtime DI boundary: no manual entrypoint lookups in production Kotlin.
$runtimeEntryPointArgs = @(
    "-n",
    "(EntryPoints\.get\(|EntryPointAccessors\.fromApplication\()",
    "--glob", "**/src/main/java/**/*.kt"
)
Assert-NoMatches -Name "Runtime entrypoint lookups in production Kotlin source" -RgArgs $runtimeEntryPointArgs

# 15) Task rendering trigger-owner boundary: direct router calls are restricted.
$taskRenderRouterBypassArgs = @(
    "-n",
    "TaskMapRenderRouter\.(syncTaskVisuals|clearAllTaskVisuals|plotCurrentTask)\(",
    "--glob", "feature/map/src/main/java/**/*.kt",
    "--glob", "!feature/map/src/main/java/com/example/xcpro/tasks/TaskMapRenderRouter.kt",
    "--glob", "!feature/map/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt"
)
Assert-NoMatches -Name "Direct task render-router calls outside sync coordinator owner" -RgArgs $taskRenderRouterBypassArgs

# 16) No ignored/disabled tests in map/task critical unit/instrumentation scopes.
$ignoredMapTaskTestsArgs = @(
    "-n",
    "(@Ignore|@Disabled)",
    "--glob", "feature/map/src/test/java/com/example/xcpro/map/**/*.kt",
    "--glob", "feature/map/src/test/java/com/example/xcpro/tasks/**/*.kt",
    "--glob", "app/src/test/java/**/*.kt",
    "--glob", "app/src/androidTest/java/**/*.kt"
)
Assert-NoMatches -Name "Ignored/disabled tests in map/task test scopes" -RgArgs $ignoredMapTaskTestsArgs

# 17) Non-UI manager/state model: no Compose runtime state in runtime manager/state classes.
$mapManagerComposeStateArgs = @(
    "-n",
    "(mutableStateOf\(|derivedStateOf\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/MapModalManager.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATInteractiveTurnpointManager.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATEditModeState.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATTargetPointDragHandler.kt"
)
Assert-NoMatches -Name "Compose runtime state in non-UI runtime manager/state classes" -RgArgs $mapManagerComposeStateArgs

# 18) No TODO markers in production map/task code paths.
$mapTaskTodoArgs = @(
    "-n",
    "TODO",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/**/*.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt"
)
Assert-NoMatches -Name "TODO markers in production map/task code paths" -RgArgs $mapTaskTodoArgs

if ($script:HadFailures) {
    Write-Host ""
    Write-Host "Rule enforcement failed."
    exit 1
}

Write-Host "Rule enforcement passed."
exit 0
