package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.core.time.Clock
import com.example.xcpro.livefollow.arbitration.LiveFollowSourceArbitrator
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.identity.LiveFollowIdentityResolver
import com.example.xcpro.livefollow.model.LiveFollowAircraftAlias
import com.example.xcpro.livefollow.model.LiveFollowAircraftAliasType
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.example.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.example.xcpro.livefollow.model.LiveFollowSourceArbitrationDecision
import com.example.xcpro.livefollow.model.LiveFollowSourceArbitrationPolicy
import com.example.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.example.xcpro.livefollow.model.LiveFollowSourceSample
import com.example.xcpro.livefollow.model.LiveFollowSourceSelectionReason
import com.example.xcpro.livefollow.model.LiveFollowSourceState
import com.example.xcpro.livefollow.model.LiveFollowSourceType
import com.example.xcpro.livefollow.state.LiveFollowSessionStateDecision
import com.example.xcpro.livefollow.state.LiveFollowSessionStateInput
import com.example.xcpro.livefollow.state.LiveFollowSessionStateMachine
import com.example.xcpro.livefollow.state.LiveFollowSessionStatePolicy
import com.example.xcpro.ogn.OgnAddressType
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficTarget
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class WatchTrafficRepository(
    scope: CoroutineScope,
    private val clock: Clock,
    private val sessionState: StateFlow<LiveFollowSessionSnapshot>,
    private val ognTrafficRepository: OgnTrafficRepository,
    private val directWatchTrafficSource: DirectWatchTrafficSource,
    private val identityResolver: LiveFollowIdentityResolver = LiveFollowIdentityResolver(),
    arbitrationPolicy: LiveFollowSourceArbitrationPolicy = LiveFollowSourceArbitrationPolicy(),
    sessionStatePolicy: LiveFollowSessionStatePolicy = LiveFollowSessionStatePolicy(),
    evaluationIntervalMs: Long = DEFAULT_EVALUATION_INTERVAL_MS
) {
    private val arbitrator = LiveFollowSourceArbitrator(
        clock = clock,
        policy = arbitrationPolicy
    )
    private val stateMachine = LiveFollowSessionStateMachine(
        clock = clock,
        policy = sessionStatePolicy
    )
    private var activeWatchSessionKey: String? = null

    init {
        require(evaluationIntervalMs > 0L) { "evaluationIntervalMs must be > 0" }
    }

    private val mutableState = MutableStateFlow(
        stoppedWatchTrafficSnapshot(
            directTransportAvailability = directWatchTrafficSource.transportAvailability.value
        )
    )
    val state: StateFlow<WatchTrafficSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                sessionState,
                ognTrafficRepository.targets,
                directWatchTrafficSource.aircraft,
                directWatchTrafficSource.transportAvailability,
                monotonicTickFlow(evaluationIntervalMs)
            ) { sessionSnapshot, ognTargets, directAircraft, directTransportAvailability, _ ->
                evaluateWatchState(
                    sessionSnapshot = sessionSnapshot,
                    ognTargets = ognTargets,
                    directAircraft = directAircraft,
                    directTransportAvailability = directTransportAvailability
                )
            }.collect { watchSnapshot ->
                mutableState.value = watchSnapshot
            }
        }
    }

    private fun evaluateWatchState(
        sessionSnapshot: LiveFollowSessionSnapshot,
        ognTargets: List<OgnTrafficTarget>,
        directAircraft: DirectWatchAircraftSample?,
        directTransportAvailability: com.example.xcpro.livefollow.model.LiveFollowTransportAvailability
    ): WatchTrafficSnapshot {
        val watcherActive = sessionSnapshot.role == LiveFollowSessionRole.WATCHER &&
            sessionSnapshot.lifecycle in setOf(
                LiveFollowSessionLifecycle.JOINING,
                LiveFollowSessionLifecycle.ACTIVE
            )
        if (!watcherActive) {
            clearRuntimeState()
            return stoppedWatchTrafficSnapshot(
                directTransportAvailability = directTransportAvailability
            )
        }

        resetIfSessionChanged(sessionTrackingKey(sessionSnapshot))

        val ognResolution = sessionSnapshot.watchIdentity?.let { watchIdentity ->
            resolveOgnTarget(
                watchIdentity = watchIdentity,
                ognTargets = ognTargets
            )
        } ?: OgnWatchResolution(
            identityResolution = null,
            sourceSample = null,
            aircraft = null
        )
        val arbitrationDecision = arbitrator.evaluate(
            ognSample = ognResolution.sourceSample,
            directSample = directAircraft?.toSourceSample(
                sessionAuthorized = sessionSnapshot.directWatchAuthorized
            )
        )
        val stateDecision = stateMachine.evaluate(
            LiveFollowSessionStateInput(
                arbitrationDecision = arbitrationDecision,
                ognIdentityResolution = ognResolution.identityResolution
            )
        )
        return watchSnapshot(
            stateDecision = stateDecision,
            arbitrationDecision = arbitrationDecision,
            identityResolution = ognResolution.identityResolution,
            ognAircraft = ognResolution.aircraft,
            directAircraft = directAircraft,
            directTransportAvailability = directTransportAvailability
        )
    }

    private fun watchSnapshot(
        stateDecision: LiveFollowSessionStateDecision,
        arbitrationDecision: LiveFollowSourceArbitrationDecision,
        identityResolution: LiveFollowIdentityResolution?,
        ognAircraft: WatchAircraftSnapshot?,
        directAircraft: DirectWatchAircraftSample?,
        directTransportAvailability: com.example.xcpro.livefollow.model.LiveFollowTransportAvailability
    ): WatchTrafficSnapshot {
        val aircraft = when (stateDecision.activeSource ?: stateDecision.lastLiveSource) {
            LiveFollowSourceType.OGN -> ognAircraft
            LiveFollowSourceType.DIRECT -> directAircraft?.toWatchAircraftSnapshot()
            null -> null
        }
        return WatchTrafficSnapshot(
            sourceState = stateDecision.state,
            activeSource = stateDecision.activeSource,
            aircraft = aircraft,
            ageMs = stateDecision.ageMs,
            ognEligibility = arbitrationDecision.ognEligibility,
            directEligibility = arbitrationDecision.directEligibility,
            directTransportAvailability = directTransportAvailability,
            identityResolution = identityResolution
        )
    }

    private fun resolveOgnTarget(
        watchIdentity: LiveFollowIdentityProfile,
        ognTargets: List<OgnTrafficTarget>
    ): OgnWatchResolution {
        if (ognTargets.isEmpty()) {
            return OgnWatchResolution(
                identityResolution = null,
                sourceSample = null,
                aircraft = null
            )
        }

        val candidates = ognTargets.map { target ->
            OgnWatchCandidate(
                target = target,
                profile = target.toIdentityProfile()
            )
        }
        val resolution = identityResolver.resolve(
            target = watchIdentity,
            candidates = candidates.map { it.profile }
        )
        val matchedTarget = when (resolution) {
            is LiveFollowIdentityResolution.ExactVerifiedMatch -> {
                candidates.firstOrNull { it.profile == resolution.profile }?.target
            }

            is LiveFollowIdentityResolution.AliasVerifiedMatch -> {
                candidates.firstOrNull { it.profile == resolution.profile }?.target
            }

            is LiveFollowIdentityResolution.Ambiguous,
            LiveFollowIdentityResolution.NoMatch -> null
        }
        val referenceFixMonoMs = matchedTarget?.lastSeenMillis
            ?: ognTargets.maxOfOrNull { it.lastSeenMillis }

        return OgnWatchResolution(
            identityResolution = resolution,
            sourceSample = referenceFixMonoMs?.let { fixMonoMs ->
                LiveFollowSourceSample(
                    source = LiveFollowSourceType.OGN,
                    state = LiveFollowSourceState.VALID,
                    confidence = LiveFollowConfidence.HIGH,
                    fixMonoMs = fixMonoMs,
                    identityResolution = resolution
                )
            },
            aircraft = matchedTarget?.toWatchAircraftSnapshot()
        )
    }

    private fun OgnTrafficTarget.toIdentityProfile(): LiveFollowIdentityProfile {
        return LiveFollowIdentityProfile(
            canonicalIdentity = canonicalIdentity(),
            aliases = buildSet {
                LiveFollowAircraftAlias.create(
                    type = LiveFollowAircraftAliasType.CALLSIGN,
                    rawValue = callsign,
                    verified = false
                )?.let(::add)
                identity?.registration?.let { registration ->
                    LiveFollowAircraftAlias.create(
                        type = LiveFollowAircraftAliasType.REGISTRATION,
                        rawValue = registration,
                        verified = false
                    )?.let(::add)
                }
                identity?.competitionNumber?.let { competitionNumber ->
                    LiveFollowAircraftAlias.create(
                        type = LiveFollowAircraftAliasType.COMPETITION_NUMBER,
                        rawValue = competitionNumber,
                        verified = false
                    )?.let(::add)
                }
            }
        )
    }

    private fun OgnTrafficTarget.canonicalIdentity(): LiveFollowAircraftIdentity? {
        val identityType = when (addressType) {
            OgnAddressType.FLARM -> LiveFollowAircraftIdentityType.FLARM
            OgnAddressType.ICAO -> LiveFollowAircraftIdentityType.ICAO
            OgnAddressType.UNKNOWN -> return null
        }
        return LiveFollowAircraftIdentity.create(
            type = identityType,
            rawValue = addressHex.orEmpty(),
            verified = true
        )
    }

    private fun OgnTrafficTarget.toWatchAircraftSnapshot(): WatchAircraftSnapshot =
        WatchAircraftSnapshot(
            latitudeDeg = latitude,
            longitudeDeg = longitude,
            altitudeMslMeters = altitudeMeters,
            groundSpeedMs = groundSpeedMps,
            trackDeg = trackDegrees,
            verticalSpeedMs = verticalSpeedMps,
            fixMonoMs = lastSeenMillis,
            fixWallMs = sourceTimestampWallMs ?: timestampMillis.takeIf { it > 0L },
            canonicalIdentity = canonicalIdentity(),
            displayLabel = displayLabel
        )

    private fun DirectWatchAircraftSample.toSourceSample(
        sessionAuthorized: Boolean
    ): LiveFollowSourceSample = LiveFollowSourceSample(
        source = LiveFollowSourceType.DIRECT,
        state = state,
        confidence = confidence,
        fixMonoMs = fixMonoMs,
        sessionAuthorized = sessionAuthorized
    )

    private fun DirectWatchAircraftSample.toWatchAircraftSnapshot(): WatchAircraftSnapshot =
        WatchAircraftSnapshot(
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            altitudeMslMeters = altitudeMslMeters,
            groundSpeedMs = groundSpeedMs,
            trackDeg = trackDeg,
            verticalSpeedMs = verticalSpeedMs,
            fixMonoMs = fixMonoMs,
            fixWallMs = fixWallMs,
            canonicalIdentity = canonicalIdentity,
            displayLabel = displayLabel
        )

    private fun monotonicTickFlow(evaluationIntervalMs: Long): Flow<Long> = flow {
        emit(clock.nowMonoMs())
        while (currentCoroutineContext().isActive) {
            delay(evaluationIntervalMs)
            emit(clock.nowMonoMs())
        }
    }

    private fun resetIfSessionChanged(nextSessionKey: String?) {
        if (activeWatchSessionKey == nextSessionKey) return
        clearRuntimeState()
        activeWatchSessionKey = nextSessionKey
    }

    private fun clearRuntimeState() {
        activeWatchSessionKey = null
        arbitrator.clear()
        stateMachine.clear()
    }

    private data class OgnWatchCandidate(
        val target: OgnTrafficTarget,
        val profile: LiveFollowIdentityProfile
    )

    private data class OgnWatchResolution(
        val identityResolution: LiveFollowIdentityResolution?,
        val sourceSample: LiveFollowSourceSample?,
        val aircraft: WatchAircraftSnapshot?
    )

    private companion object {
        private const val DEFAULT_EVALUATION_INTERVAL_MS = 1_000L
    }
}

private fun sessionTrackingKey(
    sessionSnapshot: LiveFollowSessionSnapshot
): String? {
    if (sessionSnapshot.role != LiveFollowSessionRole.WATCHER) return null
    val identityKey = sessionSnapshot.watchIdentity?.canonicalIdentity?.canonicalKey
    return listOfNotNull(sessionSnapshot.sessionId, identityKey)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "|")
}

private fun noSourceDecision(): LiveFollowSourceArbitrationDecision =
    LiveFollowSourceArbitrationDecision(
        selectedSource = null,
        selectedSample = null,
        reason = LiveFollowSourceSelectionReason.NO_USABLE_SOURCE,
        switched = false,
        lastSwitchMonoMs = null,
        ognEligibility = LiveFollowSourceEligibility.UNAVAILABLE,
        directEligibility = LiveFollowSourceEligibility.UNAVAILABLE
    )
