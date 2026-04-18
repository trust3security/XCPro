package com.trust3.xcpro.livefollow.data.ownship

import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.flow.StateFlow

interface LiveOwnshipSnapshotSource {
    val snapshot: StateFlow<LiveOwnshipSnapshot?>
    val runtimeMode: StateFlow<LiveFollowRuntimeMode>
}
