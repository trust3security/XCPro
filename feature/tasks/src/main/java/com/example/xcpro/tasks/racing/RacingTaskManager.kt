package com.example.xcpro.tasks.racing

import android.content.Context
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
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

class RacingTaskManager(val context: Context? = null) {
    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }

    private val racingTaskPersistence = context?.let { RacingTaskPersistence(it) }

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
        saveRacingTask()
    }

    fun initializeRacingTask(waypoints: List<SearchWaypoint>) {
        updateTaskFromSimple(racingTaskInitializer.initializeRacingTask(waypoints))
        _currentLeg = 0
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.core.TaskWaypoint>) {
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
        saveRacingTask()
    }

    fun removeRacingWaypoint(index: Int) {
        val waypointCountBefore = _currentTask.waypoints.size
        updateTaskFromSimple(waypointManager.removeWaypoint(currentSimpleTask(), index))
        val waypointCountAfter = _currentTask.waypoints.size

        if (waypointCountAfter < waypointCountBefore) {
            _currentLeg = waypointManager.calculateLegAfterRemoval(_currentLeg, index, waypointCountAfter)
        }

        saveRacingTask()
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
    ) {
        updateTaskFromSimple(
            waypointManager.updateWaypointType(
                currentSimpleTask(),
                index,
                startType,
                finishType,
                turnType,
                gateWidthMeters,
                keyholeInnerRadiusMeters,
                keyholeAngle,
                faiQuadrantOuterRadiusMeters
            )
        )
        saveRacingTask()
    }

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
        saveRacingTask()
    }

    fun reorderRacingWaypoints(fromIndex: Int, toIndex: Int) {
        updateTaskFromSimple(waypointManager.reorderWaypoints(currentSimpleTask(), fromIndex, toIndex))
        saveRacingTask()
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
        saveRacingTask()
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

    fun saveRacingTask() {
        racingTaskPersistence?.saveRacingTask(currentSimpleTask())
    }

    fun loadRacingTask(): SimpleRacingTask? {
        return racingTaskPersistence?.loadRacingTask()?.also { task ->
            updateTaskFromSimple(task)
            _currentLeg = 0
        }
    }

    fun getRacingTaskSummary(): String {
        return racingTaskPersistence?.getRacingTaskSummary(currentSimpleTask()) { calculateRacingDistanceKmBoundary() }
            ?: "Racing Task: No persistence available"
    }

    fun calculateRacingTaskDistanceMeters(): Double =
        (racingTaskPersistence?.calculateRacingTaskDistance(currentSimpleTask()) { calculateRacingDistanceKmBoundary() }
            ?: 0.0) * METERS_PER_KILOMETER

    fun getSavedRacingTasks(): List<String> {
        return racingTaskPersistence?.getSavedRacingTasks() ?: emptyList()
    }

    fun saveRacingTask(taskName: String): Boolean {
        return racingTaskPersistence?.saveRacingTask(currentSimpleTask(), taskName) ?: false
    }

    fun loadRacingTaskFromFile(taskName: String): Boolean {
        return racingTaskPersistence?.loadRacingTaskFromFile(taskName)?.let { task ->
            updateTaskFromSimple(task)
            _currentLeg = 0
            saveRacingTask()
            true
        } ?: false
    }

    fun deleteRacingTask(taskName: String): Boolean {
        return racingTaskPersistence?.deleteRacingTask(taskName) ?: false
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
        return racingTaskPersistence?.getRacingTaskParameters(currentSimpleTask()) { calculateRacingDistanceKmBoundary() }
            ?: "Racing Task: No persistence available"
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
        saveRacingTask()
    }
}
