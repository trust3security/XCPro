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

# 11) Task UI boundary: no TaskManagerCoordinator type leaks in task composable surfaces
# that should be driven by TaskUiState + TaskSheetViewModel intents.
$taskUiCoordinatorTypeLeakArgs = @(
    "-n",
    "TaskManagerCoordinator",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskTopDropdownPanel.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/SwipeableTaskBottomSheet.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/ManageBTTabRouter.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskBottomSheetComponents.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/QrTaskDialogs.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingManageBTTab.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointList.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingTaskPointTypeSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingStartPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingTurnPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageBTTab.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageContent.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageList.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATTaskPointTypeSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATStartPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATTurnPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt"
)
Assert-NoMatches -Name "TaskManagerCoordinator type leaks in task composable surfaces" -RgArgs $taskUiCoordinatorTypeLeakArgs

# 12) Task coordinator cleanup: do not reintroduce deprecated helper escape hatches.
$taskCoordinatorLegacyHelperArgs = @(
    "-n",
    "(fun\s+getTaskSpecificWaypoint\(|fun\s+haversineDistance\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt"
)
Assert-NoMatches -Name "Legacy TaskManagerCoordinator helper escape hatches" -RgArgs $taskCoordinatorLegacyHelperArgs

# 13) Task UI boundary: no direct coordinator instance calls from scoped task composable surfaces.
$taskUiDirectCoordinatorCallsArgs = @(
    "-n",
    "\b(taskManager|taskCoordinator|coordinator)\s*\.",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskTopDropdownPanel.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/SwipeableTaskBottomSheet.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/ManageBTTabRouter.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskBottomSheetComponents.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/QrTaskDialogs.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingManageBTTab.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointList.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingTaskPointTypeSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingStartPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingTurnPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageBTTab.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageContent.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageList.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATTaskPointTypeSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATStartPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATTurnPointSelector.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt"
)
Assert-NoMatches -Name "Direct coordinator calls in task composable surfaces" -RgArgs $taskUiDirectCoordinatorCallsArgs

if ($script:HadFailures) {
    Write-Host ""
    Write-Host "Rule enforcement failed."
    exit 1
}

Write-Host "Rule enforcement passed."
exit 0
