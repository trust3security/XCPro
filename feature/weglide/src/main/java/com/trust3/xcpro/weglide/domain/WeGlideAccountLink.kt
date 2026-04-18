package com.trust3.xcpro.weglide.domain

data class WeGlideAccountLink(
    val userId: Long?,
    val displayName: String?,
    val email: String?,
    val connectedAtEpochMs: Long,
    val authMode: WeGlideAuthMode
)
