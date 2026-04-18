package com.trust3.xcpro.tasks.aat

import com.trust3.xcpro.tasks.TaskObservationZoneResolver
import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.domain.logic.AATTargetOptimizer
import com.trust3.xcpro.tasks.domain.model.GeoPoint
import com.trust3.xcpro.tasks.domain.model.TaskPointDef

internal class AATTargetStateMutator {

    fun updateTargetParam(task: SimpleAATTask, index: Int, targetParam: Double): SimpleAATTask =
        updateTurnpoint(task, index) { waypoint, waypoints ->
            val resolvedParam = targetParam.coerceIn(0.0, 1.0)
            val unlockedProjection = if (waypoint.targetLocked) {
                null
            } else {
                computeTargetFromParam(task, waypoints, index, resolvedParam)
            }
            waypoint.copy(
                targetParam = unlockedProjection?.first ?: resolvedParam,
                targetPoint = unlockedProjection?.second ?: waypoint.targetPoint,
                isTargetPointCustomized = waypoint.targetLocked && waypoint.isTargetPointCustomized
            )
        }

    fun toggleTargetLock(task: SimpleAATTask, index: Int): SimpleAATTask {
        val waypoint = task.waypoints.getOrNull(index) ?: return task
        return setTargetLock(task, index, !waypoint.targetLocked)
    }

    fun setTargetLock(task: SimpleAATTask, index: Int, locked: Boolean): SimpleAATTask =
        updateTurnpoint(task, index) { waypoint, waypoints ->
            val unlockedProjection = if (locked) {
                null
            } else {
                computeTargetFromParam(task, waypoints, index, waypoint.targetParam)
            }
            waypoint.copy(
                targetLocked = locked,
                targetParam = unlockedProjection?.first ?: waypoint.targetParam,
                targetPoint = unlockedProjection?.second ?: waypoint.targetPoint,
                isTargetPointCustomized = locked && (
                    waypoint.isTargetPointCustomized ||
                        waypoint.targetPoint != AATLatLng(waypoint.lat, waypoint.lon)
                    )
            )
        }

    fun applyTargetState(
        task: SimpleAATTask,
        index: Int,
        targetParam: Double,
        targetLocked: Boolean,
        targetLat: Double?,
        targetLon: Double?
    ): SimpleAATTask = updateTurnpoint(task, index) { waypoint, waypoints ->
        val resolvedParam = targetParam.coerceIn(0.0, 1.0)
        val unlockedProjection = if (targetLocked) {
            null
        } else {
            computeTargetFromParam(task, waypoints, index, resolvedParam)
        }
        val explicitTarget = if (targetLocked && targetLat != null && targetLon != null) {
            AATLatLng(targetLat, targetLon)
        } else {
            null
        }
        waypoint.copy(
            targetParam = unlockedProjection?.first ?: resolvedParam,
            targetLocked = targetLocked,
            targetPoint = explicitTarget ?: unlockedProjection?.second ?: waypoint.targetPoint,
            isTargetPointCustomized = targetLocked && explicitTarget != null
        )
    }

    fun applyEditedTargetPoint(
        task: SimpleAATTask,
        index: Int,
        editedWaypoint: AATWaypoint
    ): SimpleAATTask = updateTurnpoint(task, index) { waypoint, waypoints ->
        val estimatedParam = estimateTargetParam(
            task = task,
            waypoints = waypoints,
            index = index,
            lat = editedWaypoint.targetPoint.latitude,
            lon = editedWaypoint.targetPoint.longitude
        ) ?: waypoint.targetParam
        editedWaypoint.copy(
            targetParam = estimatedParam,
            targetLocked = true,
            isTargetPointCustomized = true
        )
    }

    private fun updateTurnpoint(
        task: SimpleAATTask,
        index: Int,
        transform: (AATWaypoint, List<AATWaypoint>) -> AATWaypoint
    ): SimpleAATTask {
        val currentWaypoints = task.waypoints.toMutableList()
        val waypoint = currentWaypoints.getOrNull(index) ?: return task
        if (waypoint.role != AATWaypointRole.TURNPOINT) {
            return task
        }
        currentWaypoints[index] = transform(waypoint, currentWaypoints)
        return task.copy(waypoints = currentWaypoints)
    }

    private fun computeTargetFromParam(
        task: SimpleAATTask,
        waypoints: List<AATWaypoint>,
        index: Int,
        targetParam: Double
    ): Pair<Double, AATLatLng>? {
        val coreTask = task.toCoreTask(waypoints)
        if (index !in 1 until coreTask.waypoints.lastIndex) {
            return null
        }
        val previous = coreTask.waypoints[index - 1]
        val current = coreTask.waypoints[index]
        val next = coreTask.waypoints[index + 1]
        val zone = TaskObservationZoneResolver.resolve(
            taskType = TaskType.AAT,
            waypoint = current,
            role = current.role
        )
        val result = AATTargetOptimizer.moveTarget(
            prev = GeoPoint(previous.lat, previous.lon),
            current = TaskPointDef(
                id = current.id,
                name = current.title,
                role = current.role,
                location = GeoPoint(current.lat, current.lon),
                zone = zone,
                allowsTarget = true,
                targetParam = targetParam
            ),
            next = GeoPoint(next.lat, next.lon),
            clamp = targetParam
        )
        return result.rangeParam to AATLatLng(result.target.lat, result.target.lon)
    }

    private fun estimateTargetParam(
        task: SimpleAATTask,
        waypoints: List<AATWaypoint>,
        index: Int,
        lat: Double,
        lon: Double
    ): Double? {
        val coreTask = task.toCoreTask(waypoints)
        if (index !in 1 until coreTask.waypoints.lastIndex) {
            return null
        }
        val previous = GeoPoint(coreTask.waypoints[index - 1].lat, coreTask.waypoints[index - 1].lon)
        val current = coreTask.waypoints[index]
        val next = GeoPoint(coreTask.waypoints[index + 1].lat, coreTask.waypoints[index + 1].lon)
        val zone = TaskObservationZoneResolver.resolve(
            taskType = TaskType.AAT,
            waypoint = current,
            role = current.role
        )
        val currentPoint = TaskPointDef(
            id = current.id,
            name = current.title,
            role = current.role,
            location = GeoPoint(current.lat, current.lon),
            zone = zone,
            allowsTarget = true
        )
        val target = GeoPoint(lat, lon)
        var searchMin = 0.0
        var searchMax = 1.0
        var bestParam = 0.5
        var bestDistance = Double.MAX_VALUE

        repeat(4) {
            val step = (searchMax - searchMin) / 20.0
            for (sample in 0..20) {
                val candidate = searchMin + sample * step
                val projected = AATTargetOptimizer.moveTarget(previous, currentPoint, next, candidate)
                val distance = AATMathUtils.calculateDistanceMeters(
                    projected.target.lat,
                    projected.target.lon,
                    target.lat,
                    target.lon
                )
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestParam = projected.rangeParam
                }
            }
            searchMin = maxOf(0.0, bestParam - step)
            searchMax = minOf(1.0, bestParam + step)
        }

        return bestParam.coerceIn(0.0, 1.0)
    }
}
