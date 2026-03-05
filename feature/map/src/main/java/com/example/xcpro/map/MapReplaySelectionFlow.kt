package com.example.xcpro.map

import com.example.xcpro.replay.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun Flow<SessionState>.mapReplaySelectionActive(): Flow<Boolean> =
    map { it.selection != null }.distinctUntilChanged()
