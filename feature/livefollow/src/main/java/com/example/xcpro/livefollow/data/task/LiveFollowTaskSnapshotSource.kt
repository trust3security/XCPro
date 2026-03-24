package com.example.xcpro.livefollow.data.task

import com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot
import kotlinx.coroutines.flow.Flow

interface LiveFollowTaskSnapshotSource {
    val taskSnapshot: Flow<LiveFollowTaskSnapshot?>
}
