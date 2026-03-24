package com.example.xcpro.livefollow.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.core.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class FriendsFlyingViewModel @Inject constructor(
    private val useCase: FriendsFlyingUseCase,
    private val clock: Clock
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FriendsFlyingUiState())
    private val mutableEvents = MutableSharedFlow<FriendsFlyingEvent>(extraBufferCapacity = 1)
    private var initialRefreshRequested = false

    val uiState: StateFlow<FriendsFlyingUiState> = mutableUiState.asStateFlow()
    val events: SharedFlow<FriendsFlyingEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                useCase.publicState,
                useCase.followingState,
                useCase.accountState
            ) { publicSnapshot, followingSnapshot, accountSnapshot ->
                buildFriendsFlyingUiState(
                    publicSnapshot = publicSnapshot,
                    followingSnapshot = followingSnapshot,
                    accountSnapshot = accountSnapshot,
                    nowWallMs = clock.nowWallMs()
                )
            }.collectLatest { state ->
                mutableUiState.value = state
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
            useCase.refreshPublic()
            useCase.refreshFollowing()
        }
    }

    fun selectPilot(pilot: FriendsFlyingPilotSelection) {
        mutableEvents.tryEmit(FriendsFlyingEvent.OpenWatch(pilot))
    }
}

sealed interface FriendsFlyingEvent {
    data class OpenWatch(
        val pilot: FriendsFlyingPilotSelection
    ) : FriendsFlyingEvent
}
