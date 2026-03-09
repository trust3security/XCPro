package com.example.xcpro.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIdResolverTest {

    @Test
    fun canonicalOrDefault_mapsLegacyAliasesToCanonicalDefault() {
        assertEquals(
            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            ProfileIdResolver.canonicalOrDefault("default")
        )
        assertEquals(
            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            ProfileIdResolver.canonicalOrDefault("__default_profile__")
        )
        assertEquals(
            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            ProfileIdResolver.canonicalOrDefault("default-profile")
        )
    }

    @Test
    fun canonicalOrDefault_usesCanonicalDefaultForNullOrBlank() {
        assertEquals(
            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            ProfileIdResolver.canonicalOrDefault(null)
        )
        assertEquals(
            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
            ProfileIdResolver.canonicalOrDefault("   ")
        )
    }

    @Test
    fun normalizeOrNull_returnsNullForBlankAndPreservesNonAliasIds() {
        assertNull(ProfileIdResolver.normalizeOrNull("  "))
        assertEquals("pilot-1", ProfileIdResolver.normalizeOrNull("pilot-1"))
    }

    @Test
    fun isCanonicalDefault_trueForCanonicalAndAliases() {
        assertTrue(ProfileIdResolver.isCanonicalDefault("default-profile"))
        assertTrue(ProfileIdResolver.isCanonicalDefault("default"))
        assertTrue(ProfileIdResolver.isCanonicalDefault("__default_profile__"))
    }
}
