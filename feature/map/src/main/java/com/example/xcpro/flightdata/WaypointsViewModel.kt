package com.example.xcpro.flightdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui1.screens.FileItem
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.common.waypoint.WaypointData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WaypointsUiState(
    val files: List<DocumentRef> = emptyList(),
    val checkedStates: Map<String, Boolean> = emptyMap(),
    val fileItems: List<FileItem> = emptyList(),
    val allWaypoints: List<WaypointData> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class WaypointsViewModel @Inject constructor(
    private val useCase: WaypointFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaypointsUiState(isLoading = true))
    val uiState: StateFlow<WaypointsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (files, checks) = useCase.loadWaypointFiles()
            updateState(files, checks)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun importFile(document: DocumentRef) {
        viewModelScope.launch {
            when (val result = useCase.importWaypointFile(document)) {
                is WaypointImportResult.Success -> {
                    val fileName = result.fileName
                    val updatedFiles = _uiState.value.files.toMutableList()
                    if (updatedFiles.none { it.fileName() == fileName }) {
                        updatedFiles.add(result.document)
                    }
                    val updatedChecks = _uiState.value.checkedStates.toMutableMap()
                    updatedChecks[fileName] = true
                    useCase.saveWaypointFiles(updatedFiles, updatedChecks)
                    updateState(updatedFiles, updatedChecks)
                }
                is WaypointImportResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.reason, isLoading = false) }
                }
            }
        }
    }

    fun toggleFile(fileName: String) {
        viewModelScope.launch {
            val updatedChecks = _uiState.value.checkedStates.toMutableMap()
            val newValue = !(updatedChecks[fileName] ?: false)
            updatedChecks[fileName] = newValue
            useCase.saveWaypointFiles(_uiState.value.files, updatedChecks)
            updateState(_uiState.value.files, updatedChecks)
        }
    }

    fun deleteFile(fileName: String) {
        viewModelScope.launch {
            val updatedFiles = _uiState.value.files.filterNot { it.fileName() == fileName }
            val updatedChecks = _uiState.value.checkedStates.toMutableMap().apply { remove(fileName) }
            useCase.deleteWaypointFile(fileName)
            useCase.saveWaypointFiles(updatedFiles, updatedChecks)
            updateState(updatedFiles, updatedChecks)
        }
    }

    private suspend fun updateState(
        files: List<DocumentRef>,
        checkedStates: Map<String, Boolean>
    ) {
        val fileItems = files.map { document ->
            val fileName = document.fileName()
            val enabled = checkedStates[fileName] ?: false
            val count = useCase.getWaypointCount(document)
            FileItem(
                name = fileName,
                enabled = enabled,
                count = count,
                status = if (enabled) "Loaded" else "Disabled",
                document = document
            )
        }
        val allWaypoints = useCase.loadAllWaypoints(files, checkedStates)
        _uiState.update {
            it.copy(
                files = files,
                checkedStates = checkedStates,
                fileItems = fileItems,
                allWaypoints = allWaypoints,
                isLoading = false,
                errorMessage = null
            )
        }
    }
}
