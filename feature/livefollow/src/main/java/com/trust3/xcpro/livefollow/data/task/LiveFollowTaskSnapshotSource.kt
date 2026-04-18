package com.trust3.xcpro.livefollow.data.task

import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import kotlinx.coroutines.flow.Flow

interface LiveFollowTaskSnapshotSource {
    val taskSnapshot: Flow<LiveFollowTaskSnapshot?>
}
