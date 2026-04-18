package com.trust3.xcpro.tasks

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.core.PersistedOzParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.domain.model.TaskTargetSnapshot
import javax.inject.Inject

class TaskSheetPersistedTaskImporter @Inject constructor() {

    fun import(json: String, taskManager: TaskManagerCoordinator) {
        val persisted = TaskPersistSerializer.deserialize(json)
        val (importedTask, targets) = TaskPersistSerializer.toTask(persisted)
        taskManager.setTaskType(persisted.taskType)
        taskManager.clearTask()
        importWaypoints(importedTask, taskManager)
        applyImportedTargets(persisted.taskType, targets, taskManager)
        applyImportedObservationZones(persisted, persisted.taskType, importedTask, taskManager)
        taskManager.setActiveLeg(0)
    }

    private fun importWaypoints(importedTask: Task, taskManager: TaskManagerCoordinator) {
        importedTask.waypoints.forEach { waypoint ->
            taskManager.addWaypoint(
                SearchWaypoint(
                    id = waypoint.id,
                    title = waypoint.title,
                    subtitle = waypoint.subtitle,
                    lat = waypoint.lat,
                    lon = waypoint.lon
                )
            )
        }
    }

    private fun applyImportedTargets(
        taskType: TaskType,
        targets: List<TaskTargetSnapshot>,
        taskManager: TaskManagerCoordinator
    ) {
        if (taskType != TaskType.AAT) return
        targets.forEach { targetSnapshot ->
            taskManager.applyAATTargetState(
                index = targetSnapshot.index,
                targetParam = targetSnapshot.targetParam,
                targetLocked = targetSnapshot.isLocked,
                targetLat = targetSnapshot.target?.lat,
                targetLon = targetSnapshot.target?.lon
            )
        }
    }

    private fun applyImportedObservationZones(
        persisted: TaskPersistSerializer.PersistedTask,
        taskType: TaskType,
        importedTask: Task,
        taskManager: TaskManagerCoordinator
    ) {
        persisted.waypoints.forEachIndexed { index, waypoint ->
            val ozParams = PersistedOzParams.from(waypoint.ozParams)
            val radiusMeters = ozParams.effectiveRadiusMeters()
            if (taskType == TaskType.AAT) {
                applyAatObservationZone(index, ozParams, radiusMeters, taskManager)
            }
            if (taskType == TaskType.RACING) {
                applyRacingObservationZone(index, radiusMeters, importedTask, taskManager)
            }
        }
    }

    private fun applyAatObservationZone(
        index: Int,
        ozParams: PersistedOzParams,
        radiusMeters: Double?,
        taskManager: TaskManagerCoordinator
    ) {
        if (radiusMeters != null) {
            taskManager.updateAATArea(index, radiusMeters)
        }
        taskManager.updateAATWaypointPointType(
            AATWaypointTypeUpdate(
                index = index,
                gateWidthMeters = radiusMeters,
                keyholeInnerRadiusMeters = ozParams.innerRadiusMeters,
                keyholeAngle = ozParams.angleDeg,
                sectorOuterRadiusMeters = ozParams.outerRadiusMeters
            )
        )
    }

    private fun applyRacingObservationZone(
        index: Int,
        radiusMeters: Double?,
        importedTask: Task,
        taskManager: TaskManagerCoordinator
    ) {
        if (radiusMeters == null) return
        if (index <= 0 || index >= importedTask.waypoints.lastIndex) return
        taskManager.updateWaypointPointType(
            RacingWaypointTypeUpdate(
                index = index,
                gateWidthMeters = radiusMeters
            )
        )
    }
}
