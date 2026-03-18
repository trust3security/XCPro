package com.example.xcpro.livefollow.identity

import com.example.xcpro.livefollow.model.LiveFollowAircraftAlias
import com.example.xcpro.livefollow.model.LiveFollowAircraftAliasType
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowIdentityAmbiguityReason
import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.example.xcpro.livefollow.model.LiveFollowIdentityResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFollowIdentityResolverTest {

    private val resolver = LiveFollowIdentityResolver()

    @Test
    fun resolve_exactVerifiedIdentity_returnsExactMatch() {
        val target = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.FLARM,
                value = "ABC123",
                verified = true
            )
        )
        val candidate = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.FLARM,
                value = "ABC123",
                verified = true
            )
        )

        val resolution = resolver.resolve(target = target, candidates = listOf(candidate))

        assertEquals(
            LiveFollowIdentityResolution.ExactVerifiedMatch(candidate),
            resolution
        )
    }

    @Test
    fun resolve_verifiedAlias_returnsAliasMatchWhenExactMissing() {
        val target = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.XC_CUSTOM,
                value = "PILOT-ALPHA",
                verified = false
            ),
            aliases = setOf(verifiedAlias(LiveFollowAircraftAliasType.REGISTRATION, "D-1234"))
        )
        val candidate = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.FLARM,
                value = "CDE456",
                verified = true
            ),
            aliases = setOf(verifiedAlias(LiveFollowAircraftAliasType.REGISTRATION, "D-1234"))
        )

        val resolution = resolver.resolve(target = target, candidates = listOf(candidate))

        assertEquals(
            LiveFollowIdentityResolution.AliasVerifiedMatch(
                profile = candidate,
                matchedAlias = verifiedAlias(
                    type = LiveFollowAircraftAliasType.REGISTRATION,
                    value = "D-1234"
                )
            ),
            resolution
        )
    }

    @Test
    fun resolve_mismatch_returnsNoMatch() {
        val target = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.ICAO,
                value = "ABC123",
                verified = true
            )
        )
        val candidate = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.ICAO,
                value = "DEF456",
                verified = true
            )
        )

        val resolution = resolver.resolve(target = target, candidates = listOf(candidate))

        assertEquals(LiveFollowIdentityResolution.NoMatch, resolution)
    }

    @Test
    fun resolve_multipleVerifiedAliases_returnsAmbiguous() {
        val target = profile(
            aliases = setOf(
                verifiedAlias(LiveFollowAircraftAliasType.COMPETITION_NUMBER, "AA1")
            )
        )
        val first = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.FLARM,
                value = "ABC123",
                verified = true
            ),
            aliases = setOf(
                verifiedAlias(LiveFollowAircraftAliasType.COMPETITION_NUMBER, "AA1")
            )
        )
        val second = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.ICAO,
                value = "DEF456",
                verified = true
            ),
            aliases = setOf(
                verifiedAlias(LiveFollowAircraftAliasType.COMPETITION_NUMBER, "AA1")
            )
        )

        val resolution = resolver.resolve(target = target, candidates = listOf(first, second))

        assertTrue(resolution is LiveFollowIdentityResolution.Ambiguous)
        val ambiguous = resolution as LiveFollowIdentityResolution.Ambiguous
        assertEquals(
            LiveFollowIdentityAmbiguityReason.MULTIPLE_ALIAS_MATCHES,
            ambiguous.reason
        )
        assertEquals(listOf(first, second), ambiguous.candidates)
    }

    @Test
    fun resolve_noCandidates_returnsNoMatch() {
        val target = profile(
            canonicalIdentity = typedIdentity(
                type = LiveFollowAircraftIdentityType.FLARM,
                value = "ABC123",
                verified = true
            )
        )

        val resolution = resolver.resolve(target = target, candidates = emptyList())

        assertEquals(LiveFollowIdentityResolution.NoMatch, resolution)
    }

    private fun profile(
        canonicalIdentity: LiveFollowAircraftIdentity? = null,
        aliases: Set<LiveFollowAircraftAlias> = emptySet()
    ): LiveFollowIdentityProfile = LiveFollowIdentityProfile(
        canonicalIdentity = canonicalIdentity,
        aliases = aliases
    )

    private fun typedIdentity(
        type: LiveFollowAircraftIdentityType,
        value: String,
        verified: Boolean
    ): LiveFollowAircraftIdentity =
        checkNotNull(LiveFollowAircraftIdentity.create(type = type, rawValue = value, verified = verified))

    private fun verifiedAlias(
        type: LiveFollowAircraftAliasType,
        value: String
    ): LiveFollowAircraftAlias =
        checkNotNull(LiveFollowAircraftAlias.create(type = type, rawValue = value, verified = true))
}
