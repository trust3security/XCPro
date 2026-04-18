package com.trust3.xcpro.airspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui1.screens.AirspaceClassItem
import com.example.ui1.screens.FileItem
import com.trust3.xcpro.AirspaceImportResult
import com.trust3.xcpro.common.documents.DocumentRef
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AirspaceUiState(
    val files: List<DocumentRef> = emptyList(),
    val checkedStates: Map<String, Boolean> = emptyMap(),
    val fileItems: List<FileItem> = emptyList(),
    val enabledFiles: List<DocumentRef> = emptyList(),
    val classStates: Map<String, Boolean> = emptyMap(),
    val classItems: List<AirspaceClassItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AirspaceViewModel @Inject constructor(
    private val useCase: AirspaceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AirspaceUiState(isLoading = true))
    val uiState: StateFlow<AirspaceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (files, checks) = useCase.loadAirspaceFiles()
            val classStates = useCase.loadSelectedClasses() ?: emptyMap()
            updateState(files, checks, classStates)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun importFile(document: DocumentRef) {
        viewModelScope.launch {
            when (val result = useCase.importAirspaceFile(document)) {
                is AirspaceImportResult.Success -> {
                    val fileName = result.fileName
                    val updatedFiles = _uiState.value.files
                        .toMutableList()
                        .apply {
                            if (none { it.fileName() == fileName }) {
                                add(result.document)
                            }
                        }
                    val updatedChecks = _uiState.value.checkedStates.toMutableMap()
                    updatedChecks[fileName] = true
                    useCase.saveAirspaceFiles(updatedFiles, updatedChecks)
                    updateState(updatedFiles, updatedChecks, _uiState.value.classStates)
                }
                is AirspaceImportResult.Failure -> {
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
            useCase.saveAirspaceFiles(_uiState.value.files, updatedChecks)
            updateState(_uiState.value.files, updatedChecks, _uiState.value.classStates)
        }
    }

    fun toggleClass(className: String) {
        viewModelScope.launch {
            val updated = _uiState.value.classStates.toMutableMap()
            updated[className] = !(updated[className] ?: true)
            useCase.saveSelectedClasses(updated)
            updateState(_uiState.value.files, _uiState.value.checkedStates, updated)
        }
    }

    fun deleteFile(fileName: String) {
        viewModelScope.launch {
            val updatedFiles = _uiState.value.files.filterNot { it.fileName() == fileName }
            val updatedChecks = _uiState.value.checkedStates.toMutableMap().apply { remove(fileName) }
            useCase.deleteAirspaceFile(fileName)
            useCase.saveAirspaceFiles(updatedFiles, updatedChecks)
            updateState(updatedFiles, updatedChecks, _uiState.value.classStates)
        }
    }

    private suspend fun updateState(
        files: List<DocumentRef>,
        checkedStates: Map<String, Boolean>,
        classStates: Map<String, Boolean>
    ) {
        val fileItems = files.map { document ->
            val fileName = document.fileName()
            val enabled = checkedStates[fileName] ?: false
            val count = useCase.countZones(document)
            FileItem(
                name = fileName,
                enabled = enabled,
                count = count,
                status = if (enabled) "Loaded" else "Disabled",
                document = document
            )
        }
        val enabledFiles = fileItems.filter { it.enabled }.map { it.document }
        val reconciledClasses = reconcileClassStates(enabledFiles, classStates)
        if (reconciledClasses != classStates) {
            useCase.saveSelectedClasses(reconciledClasses)
        }
        val classItems = if (enabledFiles.isEmpty()) {
            emptyList()
        } else {
            val classes = useCase.parseClasses(enabledFiles)
            classes.map { className ->
                AirspaceClassItem(
                    className = className,
                    enabled = reconciledClasses[className] ?: true,
                    color = airspaceClassColor(className),
                    description = airspaceClassDescription(className)
                )
            }
        }
        _uiState.update {
            it.copy(
                files = files,
                checkedStates = checkedStates,
                fileItems = fileItems,
                enabledFiles = enabledFiles,
                classStates = reconciledClasses,
                classItems = classItems,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    private suspend fun reconcileClassStates(
        enabledFiles: List<DocumentRef>,
        existing: Map<String, Boolean>
    ): Map<String, Boolean> {
        if (enabledFiles.isEmpty()) return existing
        val latestClasses = useCase.parseClasses(enabledFiles).toSet()
        val updated = existing.filterKeys { it in latestClasses }.toMutableMap()
        latestClasses.forEach { className ->
            if (!updated.containsKey(className)) {
                updated[className] = defaultClassEnabled(className)
            }
        }
        return updated
    }
}

private fun airspaceClassColor(className: String): String = when (className.uppercase()) {
    "A" -> "#FF0000"
    "B" -> "#0000FF"
    "C" -> "#FF00FF"
    "D" -> "#0000FF"
    "E" -> "#FF00FF"
    "F" -> "#808080"
    "G" -> "#FFFFFF"
    "CTR" -> "#0000FF"
    "RMZ" -> "#00FF00"
    "RESTRICTED" -> "#FF0000"
    "DANGER" -> "#FFA500"
    else -> "#8E8E93"
}

private fun airspaceClassDescription(className: String): String = when (className.uppercase()) {
    "A" -> "IFR only"
    "B" -> "IFR and VFR"
    "C" -> "IFR and VFR with clearance"
    "D" -> "Radio communication required"
    "E" -> "IFR controlled, VFR not"
    "F" -> "Advisory airspace"
    "G" -> "Uncontrolled airspace"
    "CTR" -> "Control Zone"
    "RMZ" -> "Radio Mandatory Zone"
    "RESTRICTED" -> "Restricted area"
    "DANGER" -> "Danger area"
    else -> "Unknown class"
}

private fun defaultClassEnabled(className: String): Boolean = when (className.uppercase()) {
    "R", "D", "C", "CTR" -> true
    else -> false
}
