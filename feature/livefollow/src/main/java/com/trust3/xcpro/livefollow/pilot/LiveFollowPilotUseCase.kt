package com.trust3.xcpro.livefollow.pilot

import com.trust3.xcpro.livefollow.account.XcAccountRepository
import com.trust3.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.trust3.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionVisibility
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRepository
import com.trust3.xcpro.livefollow.data.session.StartPilotLiveFollowSession
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftAlias
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftAliasType
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class LiveFollowPilotUseCase @Inject constructor(
    private val sessionRepository: LiveFollowSessionRepository,
    private val ownshipSnapshotSource: LiveOwnshipSnapshotSource,
    private val xcAccountRepository: XcAccountRepository,
    private val ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository
) {
    val sessionState = sessionRepository.state
    val ownshipSnapshot: StateFlow<LiveOwnshipSnapshot?> = ownshipSnapshotSource.snapshot
    val accountState = xcAccountRepository.state

    suspend fun startSharing(
        visibility: LiveFollowSessionVisibility = LiveFollowSessionVisibility.PUBLIC
    ): LiveFollowCommandResult {
        val ownshipSnapshot = ownshipSnapshotSource.snapshot.value
            ?: return LiveFollowCommandResult.Failure(
                "Ownship position unavailable. Wait for live flight data before starting LiveFollow."
            )
        val canonicalIdentity = ownshipSnapshot.canonicalIdentity
            ?: return LiveFollowCommandResult.Failure(
                "Ownship identity unavailable. Configure FLARM or ICAO identity before starting LiveFollow."
            )
        val callsign = ognTrafficPreferencesRepository.clientCallsignFlow.first()
        val aliases = buildSet {
            LiveFollowAircraftAlias.create(
                type = LiveFollowAircraftAliasType.CALLSIGN,
                rawValue = callsign.orEmpty(),
                verified = false
            )?.let(::add)
        }
        if (!xcAccountRepository.state.value.isSignedIn &&
            visibility != LiveFollowSessionVisibility.PUBLIC
        ) {
            return LiveFollowCommandResult.Failure(
                "Sign in is required for follower-only or off live visibility."
            )
        }
        return sessionRepository.startPilotSession(
            request = StartPilotLiveFollowSession(
                pilotIdentity = LiveFollowIdentityProfile(
                    canonicalIdentity = canonicalIdentity,
                    aliases = aliases
                ),
                visibility = visibility,
                taskId = null
            )
        )
    }

    suspend fun updateSharingVisibility(
        visibility: LiveFollowSessionVisibility
    ): LiveFollowCommandResult = sessionRepository.updatePilotVisibility(visibility)

    suspend fun stopSharing(): LiveFollowCommandResult = sessionRepository.stopCurrentSession()
}
