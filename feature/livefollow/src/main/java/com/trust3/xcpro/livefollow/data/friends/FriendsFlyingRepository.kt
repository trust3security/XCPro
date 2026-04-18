package com.trust3.xcpro.livefollow.data.friends

import com.trust3.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.trust3.xcpro.livefollow.model.LiveFollowActivePilot
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.trust3.xcpro.livefollow.state.LiveFollowReplayPolicy
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FriendsFlyingRepository(
    scope: CoroutineScope,
    runtimeModeSource: LiveOwnshipSnapshotSource,
    private val dataSource: ActivePilotsDataSource,
    private val replayPolicy: LiveFollowReplayPolicy = LiveFollowReplayPolicy()
) {
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(
        friendsFlyingSnapshotFor(runtimeModeSource.runtimeMode.value, replayPolicy)
    )

    val state: StateFlow<FriendsFlyingSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runtimeModeSource.runtimeMode.collect { runtimeMode ->
                val replayDecision = replayPolicy.evaluate(runtimeMode)
                mutableState.value = mutableState.value.copy(
                    runtimeMode = runtimeMode,
                    sideEffectsAllowed = replayDecision.sideEffectsAllowed,
                    replayBlockReason = replayDecision.blockReason,
                    isLoading = if (replayDecision.sideEffectsAllowed) {
                        mutableState.value.isLoading
                    } else {
                        false
                    },
                    items = if (replayDecision.sideEffectsAllowed) {
                        mutableState.value.items
                    } else {
                        emptyList()
                    },
                    lastError = if (replayDecision.sideEffectsAllowed) {
                        mutableState.value.lastError
                    } else {
                        null
                    },
                    transportAvailability = if (replayDecision.sideEffectsAllowed) {
                        mutableState.value.transportAvailability
                    } else {
                        liveFollowAvailableTransport()
                    }
                )
            }
        }
    }

    suspend fun refresh() {
        refreshMutex.withLock {
            val current = mutableState.value
            if (!current.sideEffectsAllowed) {
                mutableState.value = current.copy(
                    isLoading = false,
                    items = emptyList(),
                    lastError = null,
                    transportAvailability = liveFollowAvailableTransport()
                )
                return
            }

            mutableState.value = current.copy(
                isLoading = true,
                lastError = null
            )
            when (val result = dataSource.fetchActivePilots()) {
                is ActivePilotsFetchResult.Success -> {
                    if (!mutableState.value.sideEffectsAllowed) {
                        mutableState.value = mutableState.value.copy(
                            isLoading = false,
                            items = emptyList(),
                            lastError = null,
                            transportAvailability = liveFollowAvailableTransport()
                        )
                        return
                    }
                    mutableState.value = mutableState.value.copy(
                        items = sortActivePilots(result.items),
                        isLoading = false,
                        lastError = null,
                        transportAvailability = liveFollowAvailableTransport()
                    )
                }

                is ActivePilotsFetchResult.Failure -> {
                    mutableState.value = mutableState.value.copy(
                        items = emptyList(),
                        isLoading = false,
                        lastError = result.message,
                        transportAvailability = result.availability
                    )
                }
            }
        }
    }
}

data class FriendsFlyingSnapshot(
    val items: List<LiveFollowActivePilot> = emptyList(),
    val runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
    val sideEffectsAllowed: Boolean = true,
    val replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE,
    val transportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport(),
    val isLoading: Boolean = false,
    val lastError: String? = null
)

private fun friendsFlyingSnapshotFor(
    runtimeMode: LiveFollowRuntimeMode,
    replayPolicy: LiveFollowReplayPolicy
): FriendsFlyingSnapshot {
    val replayDecision = replayPolicy.evaluate(runtimeMode)
    return FriendsFlyingSnapshot(
        runtimeMode = runtimeMode,
        sideEffectsAllowed = replayDecision.sideEffectsAllowed,
        replayBlockReason = replayDecision.blockReason
    )
}

internal fun sortActivePilots(
    items: List<LiveFollowActivePilot>
): List<LiveFollowActivePilot> {
    return items.sortedWith(
        compareBy<LiveFollowActivePilot>(::activePilotStatusRank)
            .thenByDescending { it.lastPositionWallMs ?: Long.MIN_VALUE }
            .thenBy { it.displayLabel }
            .thenBy { it.shareCode }
    )
}

private fun activePilotStatusRank(item: LiveFollowActivePilot): Int {
    return when (item.status.trim().lowercase()) {
        "active" -> 0
        "stale" -> 1
        else -> 2
    }
}
