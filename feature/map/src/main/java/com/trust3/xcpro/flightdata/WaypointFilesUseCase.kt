package com.trust3.xcpro.flightdata

import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.documents.DocumentRef
import javax.inject.Inject

class WaypointFilesUseCase @Inject constructor(
    private val repository: WaypointFilesRepository
) {
    suspend fun loadWaypointFiles(): Pair<List<DocumentRef>, MutableMap<String, Boolean>> =
        repository.loadWaypointFiles()

    suspend fun saveWaypointFiles(files: List<DocumentRef>, checkedStates: Map<String, Boolean>) {
        repository.saveWaypointFiles(files, checkedStates)
    }

    suspend fun importWaypointFile(document: DocumentRef): WaypointImportResult =
        repository.importWaypointFile(document)

    suspend fun deleteWaypointFile(fileName: String): Boolean =
        repository.deleteWaypointFile(fileName)

    suspend fun getWaypointCount(document: DocumentRef): Int =
        repository.getWaypointCount(document)

    suspend fun loadAllWaypoints(
        files: List<DocumentRef>,
        checkedStates: Map<String, Boolean>
    ): List<WaypointData> = repository.loadAllWaypoints(files, checkedStates)
}
