package com.example.xcpro.livefollow.watch

import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.model.LiveFollowSourceType
import com.example.xcpro.livefollow.state.LiveFollowSessionState

enum class LiveFollowTaskRenderPolicy {
    HIDDEN,
    READ_ONLY_UNAVAILABLE,
    BLOCKED_AMBIGUOUS
}

data class LiveFollowMapRenderState(
    val isVisible: Boolean = false,
    val sessionId: String? = null,
    val lifecycle: LiveFollowSessionLifecycle = LiveFollowSessionLifecycle.IDLE,
    val sourceState: LiveFollowSessionState = LiveFollowSessionState.STOPPED,
    val activeSource: LiveFollowSourceType? = null,
    val displayLabel: String? = null,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val trackDeg: Double? = null,
    val ageMs: Long? = null,
    val taskRenderPolicy: LiveFollowTaskRenderPolicy = LiveFollowTaskRenderPolicy.HIDDEN
)
