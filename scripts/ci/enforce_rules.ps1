param(
    [switch]$Verbose
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

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
    $stdoutFile = [System.IO.Path]::GetTempFileName()
    $stderrFile = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process `
            -FilePath "rg" `
            -ArgumentList $RgArgs `
            -NoNewWindow `
            -Wait `
            -PassThru `
            -RedirectStandardOutput $stdoutFile `
            -RedirectStandardError $stderrFile
        $code = $proc.ExitCode
        $output = @()
        if (Test-Path $stdoutFile) {
            $output = @(Get-Content $stdoutFile)
        }
        $stderr = @()
        if (Test-Path $stderrFile) {
            $stderr = @(Get-Content $stderrFile)
        }
    }
    finally {
        Remove-Item -Path $stdoutFile -ErrorAction SilentlyContinue
        Remove-Item -Path $stderrFile -ErrorAction SilentlyContinue
    }

    $lines = @($output | ForEach-Object { "$_" })
    $stderrLines = @($stderr | ForEach-Object { "$_" })
    $noFilesMatches = @(@($lines + $stderrLines) | Where-Object { $_ -match "No files were searched" })
    $noFilesSearched = $code -eq 2 -and $noFilesMatches.Count -gt 0
    if ($noFilesSearched) {
        return [pscustomobject]@{
            Output          = @()
            Code            = 1
            NoFilesSearched = $true
            ErrorOutput     = @()
        }
    }
    return [pscustomobject]@{
        Output          = $lines
        Code            = $code
        NoFilesSearched = $false
        ErrorOutput     = $stderrLines
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
        $details = ($result.ErrorOutput -join "; ")
        throw "rg failed for '$Name' (exit $($result.Code)). $details"
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
        $details = ($discover.ErrorOutput -join "; ")
        throw "rg failed while discovering composable files for '$Name' (exit $($discover.Code)). $details"
    }
    if ($discover.NoFilesSearched) {
        return
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
        $details = ($matches.ErrorOutput -join "; ")
        throw "rg failed while scanning composable files for '$Name' (exit $($matches.Code)). $details"
    }
    if ($matches.Code -eq 0) {
        Write-Host ""
        Write-Host "FAIL: $Name"
        $matches.Output | ForEach-Object { Write-Host $_ }
        $script:HadFailures = $true
    }
}

function Assert-MaxLines {
    param(
        [string]$Name,
        [string]$FilePath,
        [int]$MaxLines
    )
    if (-not (Test-Path $FilePath)) {
        Write-Host ""
        Write-Host "FAIL: $Name"
        Write-Host "Missing file: $FilePath"
        $script:HadFailures = $true
        return
    }
    $lineCount = (Get-Content $FilePath | Measure-Object -Line).Lines
    if ($lineCount -gt $MaxLines) {
        Write-Host ""
        Write-Host "FAIL: $Name"
        Write-Host "$FilePath has $lineCount lines (max $MaxLines)."
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
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/ui/task/**/*.kt",
    "--glob", "!feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCompat.kt"
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
    "--glob", "feature/map/src/test/java/**/*.kt",
    "--glob", "app/src/test/java/**/*.kt",
    "--glob", "app/src/androidTest/java/**/*.kt"
)
Assert-NoMatches -Name "Ignored/disabled tests in map/task test scopes" -RgArgs $ignoredMapTaskTestsArgs

# 16A) Task composables must not resolve runtime manager owners directly.
$taskManagerCompatBypassArgs = @(
    "-n",
    "rememberTaskManagerCoordinator\(",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/ui/task/**/*.kt",
    "--glob", "!feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCompat.kt"
)
Assert-NoMatches -Name "rememberTaskManagerCoordinator bypass in task/map-task composable surfaces" -RgArgs $taskManagerCompatBypassArgs

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

# 19) Persisted OZ parsing must use typed contract (no raw string key indexing).
$rawOzParamsArgs = @(
    "-n",
    'ozParams\["',
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt"
)
Assert-NoMatches -Name "Raw ozParams string-key indexing in task production code" -RgArgs $rawOzParamsArgs

# 20) Task custom-parameter contract: no raw custom-parameter string literals in map builders.
$rawTaskParamLiteralPairsArgs = @(
    "-n",
    '"(targetLat|targetLon|targetParam|targetLocked|keyholeInnerRadius|keyholeAngle|faiQuadrantOuterRadius|aatMinimumTimeSeconds|aatMaximumTimeSeconds|radiusMeters|outerRadiusMeters|innerRadiusMeters|startAngleDegrees|endAngleDegrees|lineWidthMeters|isTargetPointCustomized)"\s+to',
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt"
)
Assert-NoMatches -Name "Raw task custom-parameter literals in task production code" -RgArgs $rawTaskParamLiteralPairsArgs

# 21) Task custom-parameter contract: no direct customParameters string-key indexing.
$rawCustomParametersIndexArgs = @(
    "-n",
    'customParameters\s*\[\s*"',
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt"
)
Assert-NoMatches -Name "Raw customParameters string-key indexing in task production code" -RgArgs $rawCustomParametersIndexArgs

# 22) SI drift: no km-returning AAT distance helper promoted to meters in task/replay internals.
$aatKmDistancePromotionArgs = @(
    "-n",
    "AATMathUtils\.calculateDistance\([^\)]*\)\s*\*\s*METERS_PER_KILOMETER",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/replay/**/*.kt"
)
Assert-NoMatches -Name "SI drift: AAT km distance helper promoted to meter contract in internals" -RgArgs $aatKmDistancePromotionArgs

# 23) Replay movement contract: distanceMeters must not be assigned from speedMs.
$replayDistanceFieldArgs = @(
    "-n",
    "distanceMeters\s*=\s*speedMs",
    "--glob", "feature/map/src/main/java/com/example/xcpro/replay/**/*.kt"
)
Assert-NoMatches -Name "Replay movement contract: distanceMeters assigned from speedMs" -RgArgs $replayDistanceFieldArgs

# 24) OGN internals must use meter-first helpers only.
$ognKmHelperArgs = @(
    "-n",
    "(haversineKm\(|shouldReconnectByCenterMove\(|isWithinReceiveRadiusKm\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/ogn/**/*.kt"
)
Assert-NoMatches -Name "OGN km helper reintroduction in production internals" -RgArgs $ognKmHelperArgs

# 25) Distance display surfaces must not hard-code non-SI distance labels.
$hardCodedDistanceLabelsArgs = @(
    "-n",
    '\"[^\"]*(km|NM|mi)[^\"]*\"',
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/DistanceCirclesCanvas.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/CommonTaskComponents.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/BottomSheetState.kt"
)
Assert-NoMatches -Name "Hard-coded distance-unit labels in shared distance display surfaces" -RgArgs $hardCodedDistanceLabelsArgs

# 26) Meter-labeled variables must not source from km-returning AAT helpers.
$aatMeterVariableSourceArgs = @(
    "-n",
    "(distanceMeters|crossTrackDistanceMeters|alongTrackToCenterMeters)\s*=\s*AATMathUtils\.calculate(Distance|CrossTrackDistance|AlongTrackDistance)\(",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt"
)
Assert-NoMatches -Name "SI drift: meter-labeled variables assigned from km-returning AAT helpers" -RgArgs $aatMeterVariableSourceArgs

# 27) Closed residual guard: dead legacy helper files must not reappear.
$legacyHelperFilesArgs = @(
    "-n",
    ".",
    "--glob", "feature/map/src/main/java/com/example/xcpro/gestures/AirspaceGestureMath.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/KeyholeVerification.kt"
)
Assert-NoMatches -Name "Legacy dead helper files reintroduced (AirspaceGestureMath/KeyholeVerification)" -RgArgs $legacyHelperFilesArgs

# 28) Closed residual guard: AATEditGeometry must remain meter-only.
$aatEditGeometryKmWrappersArgs = @(
    "-n",
    "(fun\s+generateCircleCoordinates\(|fun\s+generateSectorCoordinates\(|fun\s+calculateDestinationPoint\(|fun\s+haversineDistance\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/interaction/AATEditGeometry.kt"
)
Assert-NoMatches -Name "AATEditGeometry km compatibility wrappers reintroduced" -RgArgs $aatEditGeometryKmWrappersArgs

# 29) Closed residual guard: AATGeometryGenerator must remain meter-only internally.
$aatGeometryGeneratorKmWrappersArgs = @(
    "-n",
    "(fun\s+generateCircleCoordinates\(|fun\s+generateStartLine\(|fun\s+generateFinishLine\(|fun\s+calculateDestinationPoint\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt"
)
Assert-NoMatches -Name "AATGeometryGenerator km compatibility wrappers reintroduced" -RgArgs $aatGeometryGeneratorKmWrappersArgs

# 30) Closed residual guard: no local km haversine helper in AATLongPressOverlay.
$aatLongPressLocalHaversineArgs = @(
    "-n",
    "private\s+fun\s+haversineDistance\(",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATLongPressOverlay.kt"
)
Assert-NoMatches -Name "AATLongPressOverlay local km haversine helper reintroduced" -RgArgs $aatLongPressLocalHaversineArgs

# 31) Area-size validation must use SI-internal m2 contract (km2 only for display formatting).
$aatQuickValidationKm2ContractArgs = @(
    "-n",
    "calculateAreaSizeKm2\(",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt"
)
Assert-NoMatches -Name "AAT quick-validation area-size km2 internal contract reintroduced" -RgArgs $aatQuickValidationKm2ContractArgs

# 32) Area-size warning labels must indicate squared distance units.
$aatQuickValidationLinearKmLabelArgs = @(
    "-n",
    "areaSize\w*\)\}\s*km\)",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt"
)
Assert-NoMatches -Name "AAT quick-validation area-size warnings missing squared unit label" -RgArgs $aatQuickValidationLinearKmLabelArgs

# 32A) #18 closure guard: no legacy unsuffixed AAT point-type update route.
$aatPointTypeLegacyRouteArgs = @(
    "-n",
    "(onUpdateAATWaypointPointType\(|updateAATWaypointPointType\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetCoordinatorUseCase.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/AATCoordinatorDelegate.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt"
)
Assert-NoMatches -Name "#18 guard: legacy AAT point-type wrapper route reintroduced" -RgArgs $aatPointTypeLegacyRouteArgs

# 32B) #18 closure guard: AAT edit gesture/camera contracts must remain meter-first.
$aatGestureRadiusKmContractArgs = @(
    "-n",
    "(\bradiusKm\b|turnpointRadiusKm\b)",
    "--glob", "feature/map/src/main/java/com/example/xcpro/gestures/TaskGestureHandler.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/gestures/AatGestureHandler.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt"
)
Assert-NoMatches -Name "#18 guard: km-based AAT gesture/camera radius contracts reintroduced" -RgArgs $aatGestureRadiusKmContractArgs

# 32C) #18 closure guard: dead km wrapper helpers must not reappear.
$legacyKmWrapperSurfaceArgs = @(
    "-n",
    "(resolvedCustomRadiusKm\(|fun\s+calculateDistance\(|fun\s+calculateDistanceKm\(|fun\s+calculateCrossTrackDistance\(|fun\s+calculateAlongTrackDistance\(|fun\s+calculateAreaSizeKm2\(|fun\s+haversineDistance\()",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/core/Models.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATMathUtils.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaCalculator.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingGeometryUtils.kt"
)
Assert-NoMatches -Name "#18 guard: removed km compatibility wrapper surfaces reintroduced" -RgArgs $legacyKmWrapperSurfaceArgs

# 32D) #18 closure guard: deprecated km-only racing waypoint view properties must remain removed.
$racingWaypointKmViewArgs = @(
    "-n",
    "(val\s+gateWidth:\s*Double|val\s+keyholeInnerRadius:\s*Double|val\s+faiQuadrantOuterRadius:\s*Double)",
    "--glob", "feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt"
)
Assert-NoMatches -Name "#18 guard: deprecated racing km view properties reintroduced" -RgArgs $racingWaypointKmViewArgs

# 33) Maintainability size budget for map/task hotspots.
Assert-MaxLines `
    -Name "MapCameraManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "MapScreenReplayCoordinator line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "MapScreenViewModel line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "MapScreenRoot line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt" `
    -MaxLines 250
Assert-MaxLines `
    -Name "MapScreenRootStateBindings line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt" `
    -MaxLines 120
Assert-MaxLines `
    -Name "MapScreenRootHelpers line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt" `
    -MaxLines 250
Assert-MaxLines `
    -Name "MapScreenRootEffects line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt" `
    -MaxLines 220
Assert-MaxLines `
    -Name "MapScreenScaffoldInputs line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt" `
    -MaxLines 320
Assert-MaxLines `
    -Name "MapScreenContentOverlays line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentOverlays.kt" `
    -MaxLines 250
Assert-MaxLines `
    -Name "MapTrafficDebugPanels line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels.kt" `
    -MaxLines 250
Assert-MaxLines `
    -Name "MapReplayDiagnosticsLogger line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapReplayDiagnosticsLogger.kt" `
    -MaxLines 120
Assert-MaxLines `
    -Name "LocationManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "FlightDataManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt" `
    -MaxLines 320
Assert-MaxLines `
    -Name "BlueLocationOverlay line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingNavigationEngine line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTaskManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingTaskManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "TaskSheetViewModel line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt" `
    -MaxLines 320
Assert-MaxLines `
    -Name "RulesBTTab line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/RulesBTTab.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RulesBTTabComponents line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/RulesBTTabComponents.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RulesBTTabParameters line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/RulesBTTabParameters.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATDistanceCalculator line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTaskDisplay line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplay.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTaskCalculator line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskCalculator.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTaskValidator line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskValidator.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTaskQuickValidationEngine line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATInteractiveDistanceCalculator line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATInteractiveDistanceCalculator.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "MapActionButtons line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/components/MapActionButtons.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "MapActionButtonItems line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/components/MapActionButtonItems.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATEditModeOverlay line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATEditModeOverlay.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATEditModeHeader line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATEditModeHeader.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATEditModeInfoCard line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATEditModeInfoCard.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATEditModeActions line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATEditModeActions.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "SectorAreaCalculator line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaCalculator.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "SectorAreaGeometrySupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingReplayLogBuilder line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayLogBuilder.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingReplayAnchorBuilder line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayAnchorBuilder.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingTask model line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingTaskValidationModels line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTaskValidationModels.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingTaskResultModels line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTaskResultModels.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "SnailTrailOverlay line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailOverlay.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "SnailTrailTailRenderer line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailTailRenderer.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATManageList line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageList.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATManageListItems line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageListItems.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingWaypointList line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointList.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingWaypointListItems line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointListItems.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATPathOptimizer line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizer.kt" `
    -MaxLines 250
Assert-MaxLines `
    -Name "AATPathOptimizerSupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizerSupport.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATInteractiveTurnpointManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATInteractiveTurnpointManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATInteractiveTurnpointIntegration line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATInteractiveTurnpointIntegration.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATManageListTypeInference line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageListTypeInference.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATAreaTapDetector line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATAreaTapDetector.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMapInteractionHandler line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATMapInteractionHandler.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMovablePointManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATMovablePointManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMovablePointGeometrySupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATMovablePointGeometrySupport.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMovablePointStrategySupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATMovablePointStrategySupport.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMapRenderer line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATMapRenderer.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTargetPointPinRenderer line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATTargetPointPinRenderer.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMapVisualIndicators line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATMapVisualIndicators.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATMapVisualStatusIndicators line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATMapVisualStatusIndicators.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "FAIComplianceRules line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceRules.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "FAIComplianceTaskRules line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceTaskRules.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "FAIComplianceAreaRules line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceAreaRules.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATWaypointManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/waypoints/AATWaypointManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATWaypointInitializationSupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/waypoints/AATWaypointInitializationSupport.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATWaypointMutationSupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/aat/waypoints/AATWaypointMutationSupport.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "DefaultAATTaskEngine line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultAATTaskEngine.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "AATTaskWaypointCodec line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/AATTaskWaypointCodec.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingBoundaryCrossingPlanner line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/boundary/RacingBoundaryCrossingPlanner.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "RacingBoundaryCrossingMath line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/boundary/RacingBoundaryCrossingMath.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "KeyholeGeometry line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/turnpoints/KeyholeGeometry.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "KeyholeShapeSupport line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/tasks/racing/turnpoints/KeyholeShapeSupport.kt" `
    -MaxLines 350

# 34) Top-20 hotspot line budgets (RULES-20260302-LINEBUDGET500).
Assert-MaxLines `
    -Name "Top20: AdsbTrafficRepositoryTest line budget" `
    -FilePath "feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt" `
    -MaxLines 450
Assert-MaxLines `
    -Name "Top20: MapScreenViewModelTest line budget" `
    -FilePath "feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt" `
    -MaxLines 450
Assert-MaxLines `
    -Name "Top20: OgnTrafficRepository line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "Top20: MapOverlayManager line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "Top20: AdsbTrafficRepository line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "Top20: MapScreenContent line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt" `
    -MaxLines 300
Assert-MaxLines `
    -Name "Top20: CalculateFlightMetricsUseCaseTest line budget" `
    -FilePath "feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt" `
    -MaxLines 450
Assert-MaxLines `
    -Name "Top20: OgnThermalRepositoryTest line budget" `
    -FilePath "feature/map/src/test/java/com/example/xcpro/ogn/OgnThermalRepositoryTest.kt" `
    -MaxLines 450
Assert-MaxLines `
    -Name "Top20: ForecastRasterOverlay line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt" `
    -MaxLines 300
Assert-MaxLines `
    -Name "Top20: OgnThermalRepository line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "Top20: IgcReplayController line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "Top20: ForecastOverlayRepositoryTest line budget" `
    -FilePath "feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt" `
    -MaxLines 450
Assert-MaxLines `
    -Name "Top20: Settings-df line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt" `
    -MaxLines 300
Assert-MaxLines `
    -Name "Top20: ForecastOverlayRepository line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt" `
    -MaxLines 320
Assert-MaxLines `
    -Name "Top20: ForecastOverlayBottomSheet line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt" `
    -MaxLines 300
Assert-MaxLines `
    -Name "Top20: WeatherSettingsScreen line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt" `
    -MaxLines 300
Assert-MaxLines `
    -Name "Top20: CalculateFlightMetricsUseCase line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt" `
    -MaxLines 350
Assert-MaxLines `
    -Name "Top20: AdsbSettingsScreen line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt" `
    -MaxLines 500
Assert-MaxLines `
    -Name "Top20: CardPreferences line budget" `
    -FilePath "dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt" `
    -MaxLines 500
Assert-MaxLines `
    -Name "Top20: HawkVarioSettingsScreen line budget" `
    -FilePath "feature/map/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsScreen.kt" `
    -MaxLines 300

if ($script:HadFailures) {
    Write-Host ""
    Write-Host "Rule enforcement failed."
    exit 1
}

Write-Host "Rule enforcement passed."
exit 0
