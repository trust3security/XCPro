package com.trust3.xcpro.airspace

import com.trust3.xcpro.AirspaceImportResult
import com.trust3.xcpro.AirspaceRepository
import com.trust3.xcpro.common.documents.DocumentRef
import javax.inject.Inject

class AirspaceUseCase @Inject constructor(
    private val repository: AirspaceRepository
) {
    suspend fun loadAirspaceFiles(): Pair<List<DocumentRef>, MutableMap<String, Boolean>> =
        repository.loadAirspaceFiles()

    suspend fun saveAirspaceFiles(files: List<DocumentRef>, checkedStates: Map<String, Boolean>) {
        repository.saveAirspaceFiles(files, checkedStates)
    }

    suspend fun loadSelectedClasses(): MutableMap<String, Boolean>? =
        repository.loadSelectedClasses()

    suspend fun saveSelectedClasses(selectedClasses: Map<String, Boolean>) {
        repository.saveSelectedClasses(selectedClasses)
    }

    suspend fun parseClasses(files: List<DocumentRef>): List<String> =
        repository.parseClasses(files)

    suspend fun buildGeoJson(files: List<DocumentRef>, selectedClasses: Set<String>): String =
        repository.buildGeoJson(files, selectedClasses)

    suspend fun countZones(document: DocumentRef): Int = repository.countZones(document)

    suspend fun importAirspaceFile(document: DocumentRef, maxSizeMb: Int = 5): AirspaceImportResult =
        repository.importAirspaceFile(document, maxSizeMb)

    suspend fun deleteAirspaceFile(fileName: String): Boolean =
        repository.deleteAirspaceFile(fileName)
}
