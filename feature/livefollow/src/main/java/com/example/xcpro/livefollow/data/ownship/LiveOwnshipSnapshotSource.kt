package com.example.xcpro.livefollow.data.ownship

import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.flow.StateFlow

interface LiveOwnshipSnapshotSource {
    val snapshot: StateFlow<LiveOwnshipSnapshot?>
    val runtimeMode: StateFlow<LiveFollowRuntimeMode>
}
