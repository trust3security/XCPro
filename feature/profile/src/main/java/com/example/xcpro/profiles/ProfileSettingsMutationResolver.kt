package com.example.xcpro.profiles

internal enum class PendingProfileMutationType {
    SAVE,
    DELETE
}

internal data class PendingProfileMutation(
    val type: PendingProfileMutationType,
    val sawLoading: Boolean = false
)

internal data class PendingProfileMutationResolution(
    val pendingMutation: PendingProfileMutation?,
    val shouldPopBackStack: Boolean
)

internal fun resolvePendingProfileMutation(
    pendingMutation: PendingProfileMutation?,
    isLoading: Boolean,
    hasError: Boolean,
    profileExists: Boolean
): PendingProfileMutationResolution {
    val pending = pendingMutation ?: return PendingProfileMutationResolution(
        pendingMutation = null,
        shouldPopBackStack = false
    )
    if (isLoading) {
        return PendingProfileMutationResolution(
            pendingMutation = if (pending.sawLoading) pending else pending.copy(sawLoading = true),
            shouldPopBackStack = false
        )
    }
    if (!pending.sawLoading) {
        return PendingProfileMutationResolution(
            pendingMutation = pending,
            shouldPopBackStack = false
        )
    }
    if (hasError) {
        return PendingProfileMutationResolution(
            pendingMutation = null,
            shouldPopBackStack = false
        )
    }
    return when (pending.type) {
        PendingProfileMutationType.SAVE -> PendingProfileMutationResolution(
            pendingMutation = null,
            shouldPopBackStack = true
        )
        PendingProfileMutationType.DELETE -> {
            if (profileExists) {
                PendingProfileMutationResolution(
                    pendingMutation = pending,
                    shouldPopBackStack = false
                )
            } else {
                PendingProfileMutationResolution(
                    pendingMutation = null,
                    shouldPopBackStack = true
                )
            }
        }
    }
}
