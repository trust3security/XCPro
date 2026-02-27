package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.normalizeOgnHex6OrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OgnSettingsViewModel @Inject constructor(
    private val useCase: OgnSettingsUseCase
) : ViewModel() {
    private var flarmDraftDirty: Boolean = false
    private var icaoDraftDirty: Boolean = false

    private val _uiState = MutableStateFlow(OgnSettingsUiState())
    val uiState: StateFlow<OgnSettingsUiState> = _uiState.asStateFlow()

    val iconSizePx: StateFlow<Int> = uiState
        .map { state -> state.iconSizePx }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OgnSettingsUiState().iconSizePx
        )

    init {
        viewModelScope.launch {
            val ownshipFlow = combine(
                useCase.ownFlarmHexFlow,
                useCase.ownIcaoHexFlow
            ) { ownFlarmHex, ownIcaoHex ->
                ownFlarmHex to ownIcaoHex
            }
            combine(
                useCase.iconSizePxFlow,
                useCase.receiveRadiusKmFlow,
                useCase.autoReceiveRadiusEnabledFlow,
                useCase.displayUpdateModeFlow,
                ownshipFlow
            ) { iconSizePx, receiveRadiusKm, autoReceiveRadiusEnabled, displayUpdateMode, ownship ->
                OgnSettingsPreferencesSnapshot(
                    iconSizePx = iconSizePx,
                    receiveRadiusKm = receiveRadiusKm,
                    autoReceiveRadiusEnabled = autoReceiveRadiusEnabled,
                    displayUpdateMode = displayUpdateMode,
                    ownFlarmHex = ownship.first,
                    ownIcaoHex = ownship.second
                )
            }.collect { snapshot ->
                _uiState.update { state ->
                    state.copy(
                        iconSizePx = snapshot.iconSizePx,
                        receiveRadiusKm = snapshot.receiveRadiusKm,
                        autoReceiveRadiusEnabled = snapshot.autoReceiveRadiusEnabled,
                        displayUpdateMode = snapshot.displayUpdateMode,
                        ownFlarmDraft = if (flarmDraftDirty) state.ownFlarmDraft else snapshot.ownFlarmHex.orEmpty(),
                        ownIcaoDraft = if (icaoDraftDirty) state.ownIcaoDraft else snapshot.ownIcaoHex.orEmpty(),
                        savedOwnFlarmHex = snapshot.ownFlarmHex,
                        savedOwnIcaoHex = snapshot.ownIcaoHex
                    )
                }
            }
        }
    }

    fun setIconSizePx(iconSizePx: Int) {
        viewModelScope.launch {
            useCase.setIconSizePx(iconSizePx)
        }
    }

    fun setReceiveRadiusKm(radiusKm: Int) {
        viewModelScope.launch {
            useCase.setReceiveRadiusKm(radiusKm)
        }
    }

    fun setAutoReceiveRadiusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setAutoReceiveRadiusEnabled(enabled)
        }
    }

    fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        viewModelScope.launch {
            useCase.setDisplayUpdateMode(mode)
        }
    }

    fun onOwnFlarmDraftChanged(value: String) {
        flarmDraftDirty = true
        _uiState.update { state ->
            state.copy(
                ownFlarmDraft = value,
                ownFlarmError = null
            )
        }
    }

    fun onOwnIcaoDraftChanged(value: String) {
        icaoDraftDirty = true
        _uiState.update { state ->
            state.copy(
                ownIcaoDraft = value,
                ownIcaoError = null
            )
        }
    }

    fun commitOwnFlarmDraft() {
        viewModelScope.launch {
            commitOwnDraft(
                draftValue = _uiState.value.ownFlarmDraft,
                previousSavedValue = _uiState.value.savedOwnFlarmHex,
                onClear = { useCase.setOwnFlarmHex(null) },
                onSave = { normalized -> useCase.setOwnFlarmHex(normalized) },
                applyUiUpdate = { nextDraft, nextError ->
                    _uiState.update { state ->
                        state.copy(
                            ownFlarmDraft = nextDraft,
                            ownFlarmError = nextError,
                            savedOwnFlarmHex = nextDraft.takeIf { it.isNotBlank() }
                        )
                    }
                },
                clearDirtyFlag = { flarmDraftDirty = false }
            )
        }
    }

    fun commitOwnIcaoDraft() {
        viewModelScope.launch {
            commitOwnDraft(
                draftValue = _uiState.value.ownIcaoDraft,
                previousSavedValue = _uiState.value.savedOwnIcaoHex,
                onClear = { useCase.setOwnIcaoHex(null) },
                onSave = { normalized -> useCase.setOwnIcaoHex(normalized) },
                applyUiUpdate = { nextDraft, nextError ->
                    _uiState.update { state ->
                        state.copy(
                            ownIcaoDraft = nextDraft,
                            ownIcaoError = nextError,
                            savedOwnIcaoHex = nextDraft.takeIf { it.isNotBlank() }
                        )
                    }
                },
                clearDirtyFlag = { icaoDraftDirty = false }
            )
        }
    }

    private suspend fun commitOwnDraft(
        draftValue: String,
        previousSavedValue: String?,
        onClear: suspend () -> Unit,
        onSave: suspend (String) -> Unit,
        applyUiUpdate: (String, String?) -> Unit,
        clearDirtyFlag: () -> Unit
    ) {
        val trimmedDraft = draftValue.trim()
        if (trimmedDraft.isEmpty()) {
            onClear()
            clearDirtyFlag()
            applyUiUpdate("", null)
            return
        }

        val normalized = normalizeOgnHex6OrNull(trimmedDraft)
        if (normalized == null) {
            clearDirtyFlag()
            val previous = previousSavedValue.orEmpty()
            applyUiUpdate(previous, OWN_HEX_VALIDATION_ERROR)
            return
        }

        onSave(normalized)
        clearDirtyFlag()
        applyUiUpdate(normalized, null)
    }

    private companion object {
        private const val OWN_HEX_VALIDATION_ERROR = "Enter exactly 6 hex characters (0-9, A-F)."
    }

    private data class OgnSettingsPreferencesSnapshot(
        val iconSizePx: Int,
        val receiveRadiusKm: Int,
        val autoReceiveRadiusEnabled: Boolean,
        val displayUpdateMode: OgnDisplayUpdateMode,
        val ownFlarmHex: String?,
        val ownIcaoHex: String?
    )
}
