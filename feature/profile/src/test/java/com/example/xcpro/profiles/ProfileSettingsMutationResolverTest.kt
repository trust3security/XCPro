package com.example.xcpro.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSettingsMutationResolverTest {

    @Test
    fun noPendingMutation_doesNothing() {
        val result = resolvePendingProfileMutation(
            pendingMutation = null,
            isLoading = false,
            hasError = false,
            profileExists = true
        )

        assertNull(result.pendingMutation)
        assertFalse(result.shouldPopBackStack)
    }

    @Test
    fun loadingMarksPendingMutationAsObserved() {
        val result = resolvePendingProfileMutation(
            pendingMutation = PendingProfileMutation(PendingProfileMutationType.SAVE, sawLoading = false),
            isLoading = true,
            hasError = false,
            profileExists = true
        )

        assertEquals(PendingProfileMutationType.SAVE, result.pendingMutation?.type)
        assertTrue(result.pendingMutation?.sawLoading == true)
        assertFalse(result.shouldPopBackStack)
    }

    @Test
    fun errorAfterLoading_clearsPendingWithoutNavigation() {
        val result = resolvePendingProfileMutation(
            pendingMutation = PendingProfileMutation(PendingProfileMutationType.DELETE, sawLoading = true),
            isLoading = false,
            hasError = true,
            profileExists = true
        )

        assertNull(result.pendingMutation)
        assertFalse(result.shouldPopBackStack)
    }

    @Test
    fun saveSuccessAfterLoading_navigatesBack() {
        val result = resolvePendingProfileMutation(
            pendingMutation = PendingProfileMutation(PendingProfileMutationType.SAVE, sawLoading = true),
            isLoading = false,
            hasError = false,
            profileExists = true
        )

        assertNull(result.pendingMutation)
        assertTrue(result.shouldPopBackStack)
    }

    @Test
    fun deleteSuccessAfterLoading_requiresProfileRemovalBeforeNavigation() {
        val stillPresent = resolvePendingProfileMutation(
            pendingMutation = PendingProfileMutation(PendingProfileMutationType.DELETE, sawLoading = true),
            isLoading = false,
            hasError = false,
            profileExists = true
        )
        assertEquals(PendingProfileMutationType.DELETE, stillPresent.pendingMutation?.type)
        assertFalse(stillPresent.shouldPopBackStack)

        val removed = resolvePendingProfileMutation(
            pendingMutation = PendingProfileMutation(PendingProfileMutationType.DELETE, sawLoading = true),
            isLoading = false,
            hasError = false,
            profileExists = false
        )
        assertNull(removed.pendingMutation)
        assertTrue(removed.shouldPopBackStack)
    }
}
