package com.trust3.xcpro.livefollow.identity

import com.trust3.xcpro.livefollow.model.LiveFollowAircraftAlias
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityAmbiguityReason
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityResolution

class LiveFollowIdentityResolver {

    fun resolve(
        target: LiveFollowIdentityProfile,
        candidates: List<LiveFollowIdentityProfile>
    ): LiveFollowIdentityResolution {
        val exactMatches = resolveExactMatches(target = target, candidates = candidates)
        when {
            exactMatches.size == 1 -> {
                return LiveFollowIdentityResolution.ExactVerifiedMatch(
                    profile = exactMatches.single()
                )
            }

            exactMatches.size > 1 -> {
                return LiveFollowIdentityResolution.Ambiguous(
                    reason = LiveFollowIdentityAmbiguityReason.MULTIPLE_EXACT_MATCHES,
                    candidates = exactMatches
                )
            }
        }

        val aliasMatches = resolveAliasMatches(target = target, candidates = candidates)
        when {
            aliasMatches.size == 1 -> {
                val match = aliasMatches.single()
                return LiveFollowIdentityResolution.AliasVerifiedMatch(
                    profile = match.profile,
                    matchedAlias = match.alias
                )
            }

            aliasMatches.size > 1 -> {
                return LiveFollowIdentityResolution.Ambiguous(
                    reason = LiveFollowIdentityAmbiguityReason.MULTIPLE_ALIAS_MATCHES,
                    candidates = aliasMatches.map { it.profile }
                )
            }
        }

        return LiveFollowIdentityResolution.NoMatch
    }

    private fun resolveExactMatches(
        target: LiveFollowIdentityProfile,
        candidates: List<LiveFollowIdentityProfile>
    ): List<LiveFollowIdentityProfile> {
        val targetIdentity = target.canonicalIdentity
        if (targetIdentity?.verified != true) return emptyList()
        return candidates
            .filter { candidate ->
                val candidateIdentity = candidate.canonicalIdentity
                candidateIdentity?.verified == true &&
                    candidateIdentity.canonicalKey == targetIdentity.canonicalKey
            }
            .distinct()
    }

    private fun resolveAliasMatches(
        target: LiveFollowIdentityProfile,
        candidates: List<LiveFollowIdentityProfile>
    ): List<AliasMatch> {
        val verifiedTargetAliases = target.aliases.filter { it.verified }.toSet()
        if (verifiedTargetAliases.isEmpty()) return emptyList()

        return candidates
            .mapNotNull { candidate ->
                val matchedAlias = candidate.aliases.firstOrNull { alias ->
                    alias.verified && verifiedTargetAliases.contains(alias)
                } ?: return@mapNotNull null
                AliasMatch(profile = candidate, alias = matchedAlias)
            }
            .distinctBy { it.profile }
    }

    private data class AliasMatch(
        val profile: LiveFollowIdentityProfile,
        val alias: LiveFollowAircraftAlias
    )
}
