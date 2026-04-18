package com.trust3.xcpro.tasks.racing

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.RacingWaypointTypeUpdate
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimpleRacingTask(
    val id: String = "",
    val waypoints: List<RacingWaypoint> = emptyList()
)

data class CourseLineValidation(
    val isValid: Boolean,
    val message: String,
    val touchPointResults: List<TouchPointResult> = emptyList()
)

data class TouchPointResult(
    val isValid: Boolean,
    val message: String,
    val distanceFromCenter: Double = 0.0
)

class RacingTaskManager {
    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }

    private val racingTaskCalculator = RacingTaskCalculator()
    private var validationProfile: RacingTaskStructureRules.Profile = RacingTaskStructureRules.Profile.FAI_STRICT

    private val waypointManager = RacingWaypointManager()

    private val racingTaskInitializer = RacingTaskInitializer()

    private val currentCoreTaskState = MutableStateFlow(Task(id = "", waypoints = emptyList()))
    private val currentRacingTaskState = MutableStateFlow(SimpleRacingTask())
    private val currentLegState = MutableStateFlow(0)
    val currentTaskFlow: StateFlow<Task> = currentCoreTaskState.asStateFlow()
    val currentRacingTaskFlow: StateFlow<SimpleRacingTask> = currentRacingTaskState.asStateFlow()
    val currentLegFlow: StateFlow<Int> = currentLegState.asStateFlow()
    internal var _currentTask: Task
        get() = currentCoreTaskState.value
        set(value) {
            currentCoreTaskState.value = value
            currentRacingTaskState.value = value.toSimpleRacingTask()
        }
    internal var _currentLeg: Int
        get() = currentLegState.value
        set(value) {
            currentLegState.value = value
        }

    val currentRacingTask: SimpleRacingTask get() = currentRacingTaskState.value
    val currentTask: Task get() = _currentTask
    val currentLeg: Int get() = _currentLeg

    fun getCoreTask(): Task = _currentTask

    private fun currentSimpleTask(): SimpleRacingTask = _currentTask.toSimpleRacingTask()

    private fun updateTaskFromSimple(task: SimpleRacingTask) {
        val previousTask = _currentTask
        val previousCustomById = previousTask.waypoints.associate { it.id to it.customParameters }
        val startRules = previousTask.extractRoleRules(WaypointRole.START, RACING_START_RULE_KEYS)
        val finishRules = previousTask.extractRoleRules(WaypointRole.FINISH, RACING_FINISH_RULE_KEYS)
        val mappedTask = task.toCoreTask(existingCustomParametersById = previousCustomById)
        _currentTask = mappedTask.applyRoleRules(startRules, finishRules).withValidationProfile(validationProfile)
    }

    private fun currentRacingWaypoints(): List<RacingWaypoint> = _currentTask.toRacingWaypoints()
    private fun currentValidation(): RacingTaskStructureRules.ValidationResult =
        RacingTaskStructureRules.validate(_currentTask, validationProfile)

    fun setRacingValidationProfile(profile: RacingTaskStructureRules.Profile) {
        validationProfile = profile
        _currentTask = _currentTask.withValidationProfile(validationProfile)
    }

    fun getRacingValidationProfile(): RacingTaskStructureRules.Profile = validationProfile

    internal fun updateRacingValidationRules(command: UpdateRacingValidationRulesCommand) {
        setRacingValidationProfile(command.profile)
    }

    fun initializeRacingTask(waypoints: List<SearchWaypoint>) {
        updateTaskFromSimple(racingTaskInitializer.initializeRacingTask(waypoints))
        _currentLeg = 0
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<com.trust3.xcpro.tasks.core.TaskWaypoint>) {
        updateTaskFromSimple(racingTaskInitializer.initializeFromGenericWaypoints(genericWaypoints))
        _currentLeg = 0
    }

    fun initializeFromCoreTask(task: Task, activeLegIndex: Int = 0) {
        validationProfile = resolveRacingValidationProfile(task)
        _currentTask = task.withValidationProfile(validationProfile)
        _currentLeg = if (_currentTask.waypoints.isEmpty()) {
            0
        } else {
            activeLegIndex.coerceIn(0, _currentTask.waypoints.lastIndex)
        }
    }

    fun calculateRacingDistanceMeters(): Double {
        val waypoints = currentRacingWaypoints()
        if (!currentValidation().isValid) return 0.0

        val optimalPath = racingTaskCalculator.findOptimalFAIPath(waypoints)

        if (optimalPath.size < 2) return 0.0

        var totalDistanceMeters = 0.0
        for (i in 0 until optimalPath.size - 1) {
            val from = optimalPath[i]
            val to = optimalPath[i + 1]
            totalDistanceMeters += RacingGeometryUtils.haversineDistanceMeters(from.first, from.second, to.first, to.second)
        }
        return totalDistanceMeters
    }

    private fun calculateRacingDistanceKmBoundary(): Double =
        calculateRacingDistanceMeters() / METERS_PER_KILOMETER

    fun getOptimalRacingPath(): List<Pair<Double, Double>> {
        return racingTaskCalculator.findOptimalFAIPath(currentRacingWaypoints())
    }

    fun findOptimalFAIPath(waypoints: List<RacingWaypoint>): List<Pair<Double, Double>> {
        return racingTaskCalculator.findOptimalFAIPath(waypoints)
    }

    fun addRacingWaypoint(searchWaypoint: SearchWaypoint) {
        updateTaskFromSimple(waypointManager.addWaypoint(currentSimpleTask(), searchWaypoint))
        _currentLeg = 0
    }

    fun removeRacingWaypoint(index: Int) {
        val waypointCountBefore = _currentTask.waypoints.size
        updateTaskFromSimple(waypointManager.removeWaypoint(currentSimpleTask(), index))
        val waypointCountAfter = _currentTask.waypoints.size

        if (waypointCountAfter < waypointCountBefore) {
            _currentLeg = waypointManager.calculateLegAfterRemoval(_currentLeg, index, waypointCountAfter)
        }
    }

    fun updateRacingWaypointType(update: RacingWaypointTypeUpdate) {
        updateTaskFromSimple(
            waypointManager.updateWaypointType(
                currentSimpleTask(),
                update.index,
                update.startType,
                update.finishType,
                update.turnType,
                update.gateWidthMeters,
                update.keyholeInnerRadiusMeters,
                update.keyholeAngle,
                update.faiQuadrantOuterRadiusMeters
            )
        )
    }

    fun updateRacingWaypointType(
        index: Int,
        startType: RacingStartPointType? = null,
        finishType: RacingFinishPointType? = null,
        turnType: RacingTurnPointType? = null,
        gateWidthMeters: Double? = null,
        keyholeInnerRadiusMeters: Double? = null,
        keyholeAngle: Double? = null,
        faiQuadrantOuterRadiusMeters: Double? = null
    ) = updateRacingWaypointType(
        RacingWaypointTypeUpdate(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidthMeters = gateWidthMeters,
            keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadiusMeters = faiQuadrantOuterRadiusMeters
        )
    )

    internal fun updateRacingStartRules(command: UpdateRacingStartRulesCommand) {
        updateRoleRules(role = WaypointRole.START, keys = RACING_START_RULE_KEYS) { destination ->
            command.rules.applyTo(destination)
        }
    }

    internal fun updateRacingFinishRules(command: UpdateRacingFinishRulesCommand) {
        updateRoleRules(role = WaypointRole.FINISH, keys = RACING_FINISH_RULE_KEYS) { destination ->
            command.rules.applyTo(destination)
        }
    }

    fun calculateOptimalLineCrossingPoint(
        lineLat: Double,
        lineLon: Double,
        targetLat: Double,
        targetLon: Double,
        lineWidthMeters: Double
    ): Pair<Double, Double> {
        return RacingGeometryUtils.calculateOptimalLineCrossingPoint(
            lineLat = lineLat,
            lineLon = lineLon,
            targetLat = targetLat,
            targetLon = targetLon,
            lineWidthMeters = lineWidthMeters
        )
    }

    fun replaceRacingWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        updateTaskFromSimple(waypointManager.replaceWaypoint(currentSimpleTask(), index, newWaypoint))
    }

    fun reorderRacingWaypoints(fromIndex: Int, toIndex: Int) {
        updateTaskFromSimple(waypointManager.reorderWaypoints(currentSimpleTask(), fromIndex, toIndex))
    }

    fun validateRacingCourse(): CourseLineValidation {
        val waypoints = currentRacingWaypoints()
        val validation = currentValidation()
        return if (validation.isValid) {
            CourseLineValidation(
                isValid = true,
                message = RacingTaskStructureRules.summarize(validation),
                touchPointResults = waypoints.map {
                    TouchPointResult(isValid = true, message = "Waypoint ${it.title} is valid")
                }
            )
        } else {
            CourseLineValidation(
                isValid = false,
                message = RacingTaskStructureRules.summarize(validation),
                touchPointResults = emptyList()
            )
        }
    }

    fun clearRacingTask() {
        _currentTask = Task(id = "", waypoints = emptyList())
        _currentLeg = 0
    }

    fun advanceToNextLeg() {
        val task = _currentTask
        if (task.waypoints.isNotEmpty() && _currentLeg < task.waypoints.size - 1) {
            _currentLeg++
        }
    }

    fun goToPreviousLeg() {
        if (_currentLeg > 0) {
            _currentLeg--
        }
    }

    fun setRacingLeg(index: Int) {
        if (_currentTask.waypoints.isEmpty()) return
        val clamped = index.coerceIn(0, _currentTask.waypoints.lastIndex)
        _currentLeg = clamped
    }

    fun getRacingTaskSummary(): String {
        val waypoints = currentRacingWaypoints().size
        val distance = if (waypoints >= 2) {
            String.format("%.1f km", calculateRacingDistanceKmBoundary())
        } else {
            "No distance"
        }
        return "Racing Task: $waypoints waypoints, $distance"
    }

    fun calculateRacingTaskDistanceMeters(): Double =
        if (currentRacingWaypoints().size >= 2) {
            calculateRacingDistanceKmBoundary() * METERS_PER_KILOMETER
        } else {
            0.0
        }

    fun isRacingTaskValid(): Boolean {
        return currentValidation().isValid
    }

    fun calculateSegmentDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        RacingGeometryUtils.haversineDistanceMeters(lat1, lon1, lat2, lon2)

    fun calculateDistanceToCurrentWaypointEntryMeters(gpsLat: Double, gpsLon: Double): Double? {
        return racingTaskCalculator.calculateDistanceToOptimalEntryMeters(
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            waypointIndex = currentLeg,
            waypoints = currentRacingWaypoints()
        )
    }

    fun getRacingTaskParameters(): String {
        return buildString {
            append("Racing Task Parameters:\n")
            currentRacingWaypoints().forEachIndexed { index, waypoint ->
                append("${index + 1}. ${waypoint.title} (${waypoint.currentPointType})\n")
            }
            if (currentRacingWaypoints().size >= 2) {
                append("Total Distance: ${String.format("%.2f", calculateRacingDistanceKmBoundary())} km")
            }
        }
    }

    private fun updateRoleRules(
        role: WaypointRole,
        keys: Set<String>,
        applyUpdate: (MutableMap<String, Any>) -> Unit
    ) {
        val index = _currentTask.waypoints.indexOfFirst { it.role == role }
        if (index < 0) return
        val waypoints = _currentTask.waypoints.toMutableList()
        val current = waypoints[index]
        val updatedParameters = current.customParameters.toMutableMap()
        keys.forEach { key -> updatedParameters.remove(key) }
        applyUpdate(updatedParameters)
        waypoints[index] = current.copy(customParameters = updatedParameters)
        _currentTask = _currentTask.copy(waypoints = waypoints).withValidationProfile(validationProfile)
    }
}
