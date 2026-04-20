package com.trust3.xcpro.livefollow.data.following

import com.trust3.xcpro.livefollow.account.XcAccountRepository
import com.trust3.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.trust3.xcpro.livefollow.model.LiveFollowFollowingPilot
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FollowingLiveRepository(
    scope: CoroutineScope,
    runtimeModeSource: LiveOwnshipSnapshotSource,
    private val accountRepository: XcAccountRepository,
    private val dataSource: FollowingActivePilotsDataSource,
    private val replayPolicy: LiveFollowReplayPolicy = LiveFollowReplayPolicy()
) {
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(
        followingLiveSnapshotFor(
            runtimeMode = runtimeModeSource.runtimeMode.value,
            liveSourceKind = runtimeModeSource.liveSourceKind.value,
            isSignedIn = accountRepository.state.value.isSignedIn,
            replayPolicy = replayPolicy
        )
    )

    val state: StateFlow<FollowingLiveSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.flow.combine(
                runtimeModeSource.runtimeMode,
                runtimeModeSource.liveSourceKind
            ) { runtimeMode, liveSourceKind ->
                runtimeMode to replayPolicy.evaluate(runtimeMode, liveSourceKind)
            }.collectLatest { (runtimeMode, replayDecision) ->
                val signedIn = accountRepository.state.value.isSignedIn
                mutableState.value = mutableState.value.copy(
                    runtimeMode = runtimeMode,
                    sideEffectsAllowed = replayDecision.sideEffectsAllowed,
                    replayBlockReason = replayDecision.blockReason,
                    isSignedIn = signedIn,
                    signInRequired = !signedIn,
                    isLoading = if (replayDecision.sideEffectsAllowed && signedIn) {
                        mutableState.value.isLoading
                    } else {
                        false
                    },
                    items = if (replayDecision.sideEffectsAllowed && signedIn) {
                        mutableState.value.items
                    } else {
                        emptyList()
                    },
                    lastError = if (replayDecision.sideEffectsAllowed && signedIn) {
                        mutableState.value.lastError
                    } else {
                        null
                    },
                    transportAvailability = if (replayDecision.sideEffectsAllowed && signedIn) {
                        mutableState.value.transportAvailability
                    } else {
                        liveFollowAvailableTransport()
                    }
                )
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            accountRepository.state.collectLatest { snapshot ->
                val isSignedIn = snapshot.isSignedIn
                mutableState.value = mutableState.value.copy(
                    isSignedIn = isSignedIn,
                    signInRequired = !isSignedIn,
                    items = if (isSignedIn) mutableState.value.items else emptyList(),
                    lastError = if (isSignedIn) mutableState.value.lastError else null,
                    transportAvailability = if (isSignedIn) {
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
            if (!current.isSignedIn) {
                mutableState.value = current.copy(
                    isLoading = false,
                    items = emptyList(),
                    signInRequired = true,
                    lastError = null,
                    transportAvailability = liveFollowAvailableTransport()
                )
                return
            }

            mutableState.value = current.copy(
                isLoading = true,
                lastError = null,
                signInRequired = false
            )
            when (val result = dataSource.fetchFollowingActivePilots()) {
                is FollowingActivePilotsFetchResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        items = sortFollowingPilots(result.items),
                        isLoading = false,
                        lastError = null,
                        signInRequired = false,
                        transportAvailability = liveFollowAvailableTransport()
                    )
                }

                FollowingActivePilotsFetchResult.SignedOut -> {
                    mutableState.value = mutableState.value.copy(
                        items = emptyList(),
                        isLoading = false,
                        signInRequired = true,
                        lastError = null,
                        transportAvailability = liveFollowAvailableTransport()
                    )
                }

                is FollowingActivePilotsFetchResult.Failure -> {
                    mutableState.value = mutableState.value.copy(
                        items = emptyList(),
                        isLoading = false,
                        signInRequired = false,
                        lastError = result.message,
                        transportAvailability = result.availability
                    )
                }
            }
        }
    }
}

data class FollowingLiveSnapshot(
    val items: List<LiveFollowFollowingPilot> = emptyList(),
    val runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
    val sideEffectsAllowed: Boolean = true,
    val replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE,
    val transportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport(),
    val isSignedIn: Boolean = false,
    val signInRequired: Boolean = true,
    val isLoading: Boolean = false,
    val lastError: String? = null
)

private fun followingLiveSnapshotFor(
    runtimeMode: LiveFollowRuntimeMode,
    liveSourceKind: com.trust3.xcpro.livesource.LiveSourceKind,
    isSignedIn: Boolean,
    replayPolicy: LiveFollowReplayPolicy
): FollowingLiveSnapshot {
    val replayDecision = replayPolicy.evaluate(runtimeMode, liveSourceKind)
    return FollowingLiveSnapshot(
        runtimeMode = runtimeMode,
        sideEffectsAllowed = replayDecision.sideEffectsAllowed,
        replayBlockReason = replayDecision.blockReason,
        isSignedIn = isSignedIn,
        signInRequired = !isSignedIn
    )
}

internal fun sortFollowingPilots(
    items: List<LiveFollowFollowingPilot>
): List<LiveFollowFollowingPilot> {
    return items.sortedWith(
        compareBy<LiveFollowFollowingPilot>(::followingPilotStatusRank)
            .thenByDescending { it.lastPositionWallMs ?: Long.MIN_VALUE }
            .thenBy { it.displayLabel }
            .thenBy { it.userId }
    )
}

private fun followingPilotStatusRank(item: LiveFollowFollowingPilot): Int {
    return when (item.status.trim().lowercase()) {
        "active" -> 0
        "stale" -> 1
        else -> 2
    }
}
