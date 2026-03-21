package com.example.xcpro.livefollow.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.livefollow.normalizeLiveFollowShareCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class FriendsFlyingViewModel @Inject constructor(
    private val useCase: FriendsFlyingUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FriendsFlyingUiState())
    private val mutableEvents = MutableSharedFlow<FriendsFlyingEvent>(extraBufferCapacity = 1)
    private var initialRefreshRequested = false

    val uiState: StateFlow<FriendsFlyingUiState> = mutableUiState.asStateFlow()
    val events: SharedFlow<FriendsFlyingEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            useCase.state.collectLatest { snapshot ->
                mutableUiState.value = buildFriendsFlyingUiState(snapshot)
            }
        }
    }

    fun onSheetShown() {
        if (initialRefreshRequested) return
        initialRefreshRequested = true
        refresh()
    }

    fun refresh() {
        if (mutableUiState.value.isLoading) return
        viewModelScope.launch {
            useCase.refresh()
        }
    }

    fun selectPilot(shareCode: String) {
        val normalizedShareCode = normalizeLiveFollowShareCode(shareCode) ?: return
        val isKnownPilot = useCase.state.value.items.any { it.shareCode == normalizedShareCode }
        if (!isKnownPilot) return
        mutableEvents.tryEmit(FriendsFlyingEvent.OpenWatch(normalizedShareCode))
    }
}

sealed interface FriendsFlyingEvent {
    data class OpenWatch(
        val shareCode: String
    ) : FriendsFlyingEvent
}
