package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap

internal data class AdsbStoreSelection(
    val withinRadiusCount: Int,
    val withinVerticalCount: Int,
    val filteredByVerticalCount: Int,
    val cappedCount: Int,
    val displayed: List<AdsbTrafficUiModel>,
    val emergencyAudioCandidateId: Icao24?
)

internal class AdsbTrafficStore(
    private val proximityTrendEvaluator: AdsbProximityTrendEvaluator = AdsbProximityTrendEvaluator(),
    private val collisionRiskEvaluator: AdsbCollisionRiskEvaluator = AdsbCollisionRiskEvaluator(),
    private val emergencyRiskStabilizer: AdsbEmergencyRiskStabilizer = AdsbEmergencyRiskStabilizer(),
    private val targetTrackEstimator: AdsbTargetTrackEstimator = AdsbTargetTrackEstimator()
) {
    private val targetsById = ConcurrentHashMap<Icao24, AdsbTarget>()
    private val distanceTierStateByTargetId = ConcurrentHashMap<Icao24, AdsbProximityTier>()
    private val resolvedTierStateByTargetId = ConcurrentHashMap<Icao24, AdsbProximityTier>()
    private val proximityTierResolver = AdsbProximityTierResolver(
        distanceTierStateByTargetId = distanceTierStateByTargetId,
        resolvedTierStateByTargetId = resolvedTierStateByTargetId
    )

    fun clear() {
        targetsById.clear()
        proximityTrendEvaluator.clear()
        emergencyRiskStabilizer.clear()
        targetTrackEstimator.clear()
        distanceTierStateByTargetId.clear()
        resolvedTierStateByTargetId.clear()
    }

    fun upsertAll(targets: List<AdsbTarget>) {
        for (target in targets) {
            val existing = targetsById[target.id]
            targetsById[target.id] = if (existing == null) {
                target
            } else {
                mergeTarget(existing = existing, incoming = target)
            }
        }
    }

    fun purgeExpired(nowMonoMs: Long, expiryAfterSec: Int): Boolean {
        var removed = false
        val iterator = targetsById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val ageSec = positionAgeSec(entry.value, nowMonoMs)
            if (ageSec >= expiryAfterSec) {
                iterator.remove()
                proximityTrendEvaluator.removeTarget(entry.key)
                emergencyRiskStabilizer.removeTarget(entry.key)
                targetTrackEstimator.removeTarget(entry.key)
                distanceTierStateByTargetId.remove(entry.key)
                resolvedTierStateByTargetId.remove(entry.key)
                removed = true
            }
        }
        return removed
    }

    fun select(
        nowMonoMs: Long,
        nowWallEpochSec: Long? = null,
        queryCenterLat: Double,
        queryCenterLon: Double,
        referenceLat: Double,
        referenceLon: Double,
        ownshipAltitudeMeters: Double?,
        referenceSampleMonoMs: Long? = null,
        ownshipTrackDeg: Double? = null,
        ownshipSpeedMps: Double? = null,
        usesOwnshipReference: Boolean,
        radiusMeters: Double,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double,
        ownshipIsCircling: Boolean = false,
        circlingFeatureEnabled: Boolean = false,
        maxDisplayed: Int,
        staleAfterSec: Int
    ): AdsbStoreSelection {
        var withinHorizontalCount = 0
        var filteredByVerticalCount = 0
        val withinVertical = buildList {
            for (target in targetsById.values) {
                val distanceFromQueryCenterMeters = AdsbGeoMath.haversineMeters(
                    lat1 = queryCenterLat,
                    lon1 = queryCenterLon,
                    lat2 = target.lat,
                    lon2 = target.lon
                )
                if (distanceFromQueryCenterMeters > radiusMeters) continue
                withinHorizontalCount += 1

                val altitudeDeltaMeters = ownshipAltitudeMeters?.let { ownshipAlt ->
                    val targetAltitude = target.altitudeM?.takeIf { it.isFinite() } ?: return@let null
                    targetAltitude - ownshipAlt
                }
                if (altitudeDeltaMeters != null) {
                    val above = altitudeDeltaMeters
                    val below = -altitudeDeltaMeters
                    if (above > verticalAboveMeters || below > verticalBelowMeters) {
                        filteredByVerticalCount += 1
                        continue
                    }
                }

                val positionAgeSec = positionAgeSec(target, nowMonoMs)
                val contactAgeSec = effectiveContactAgeSec(
                    target = target,
                    nowMonoMs = nowMonoMs,
                    nowWallEpochSec = nowWallEpochSec
                )
                val ageSec = effectiveAgeSec(
                    positionAgeSec = positionAgeSec,
                    contactAgeSec = contactAgeSec
                )
                val distanceMeters = AdsbGeoMath.haversineMeters(
                    lat1 = referenceLat,
                    lon1 = referenceLon,
                    lat2 = target.lat,
                    lon2 = target.lon
                )
                val bearingDegFromUser = AdsbGeoMath.bearingDegrees(
                    fromLat = referenceLat,
                    fromLon = referenceLon,
                    toLat = target.lat,
                    toLon = target.lon
                )
                val trendSampleMonoMs = trendSampleMonoMs(
                    targetReceivedMonoMs = target.receivedMonoMs,
                    ownshipReferenceSampleMonoMs = referenceSampleMonoMs,
                    usesOwnshipReference = usesOwnshipReference
                )
                val trendAssessment = proximityTrendEvaluator.evaluate(
                    id = target.id,
                    distanceMeters = distanceMeters,
                    sampleMonoMs = trendSampleMonoMs,
                    nowMonoMs = nowMonoMs,
                    hasOwnshipReference = usesOwnshipReference
                )
                val resolvedTargetTrackDeg = targetTrackEstimator.resolveTrackDeg(
                    id = target.id,
                    lat = target.lat,
                    lon = target.lon,
                    sampleMonoMs = trendSampleMonoMs,
                    reportedTrackDeg = target.trackDeg
                )
                val collisionRiskAssessment = collisionRiskEvaluator.evaluate(
                    distanceMeters = distanceMeters,
                    trackDeg = resolvedTargetTrackDeg,
                    targetSpeedMps = target.speedMps,
                    bearingDegFromUser = bearingDegFromUser,
                    ownshipTrackDeg = ownshipTrackDeg,
                    ownshipSpeedMps = ownshipSpeedMps,
                    altitudeDeltaMeters = altitudeDeltaMeters,
                    verticalAboveMeters = verticalAboveMeters,
                    verticalBelowMeters = verticalBelowMeters,
                    hasOwnshipReference = usesOwnshipReference,
                    isClosing = trendAssessment.isClosing,
                    ageSec = ageSec
                )
                val circlingEmergencyContextEnabled =
                    usesOwnshipReference && ownshipIsCircling && circlingFeatureEnabled
                val circlingEmergencyVerticalAboveMeters = minOf(
                    verticalAboveMeters,
                    ADSB_CIRCLING_EMERGENCY_VERTICAL_CAP_METERS
                )
                val circlingEmergencyVerticalBelowMeters = minOf(
                    verticalBelowMeters,
                    ADSB_CIRCLING_EMERGENCY_VERTICAL_CAP_METERS
                )
                val isCirclingEmergencyRedRule = isCirclingEmergencyRedRule(
                    distanceMeters = distanceMeters,
                    altitudeDeltaMeters = altitudeDeltaMeters,
                    enabled = circlingEmergencyContextEnabled &&
                        trendAssessment.isClosing &&
                        ageSec <= ADSB_EMERGENCY_MAX_AGE_SEC,
                    verticalAboveMeters = circlingEmergencyVerticalAboveMeters,
                    verticalBelowMeters = circlingEmergencyVerticalBelowMeters
                )
                val isEmergencyCollisionRisk = collisionRiskAssessment.isEmergencyCollisionRisk &&
                    !isCirclingEmergencyRedRule
                val isVerticalNonThreat = isVerticalNonThreat(
                    altitudeDeltaMeters = altitudeDeltaMeters,
                    hasOwnshipReference = usesOwnshipReference
                )
                val isEmergencyCollisionRiskCapped = emergencyRiskStabilizer.stabilize(
                    id = target.id,
                    candidateEmergencyRisk = isEmergencyCollisionRisk && !isVerticalNonThreat,
                    hasFreshTrendSample = trendAssessment.hasFreshTrendSample,
                    forceClear = !usesOwnshipReference ||
                        isVerticalNonThreat ||
                        ageSec > ADSB_EMERGENCY_MAX_AGE_SEC
                )
                val isEmergencyAudioEligible =
                    isEmergencyCollisionRiskCapped || isCirclingEmergencyRedRule
                val emergencyAudioIneligibilityReason = emergencyAudioIneligibilityReason(
                    isEmergencyAudioEligible = isEmergencyAudioEligible,
                    hasOwnshipReference = usesOwnshipReference,
                    isVerticalNonThreat = isVerticalNonThreat,
                    trendAssessment = trendAssessment,
                    collisionRiskReason = collisionRiskAssessment.ineligibilityReason
                )
                val proximityTier = proximityTierResolver.resolve(
                    targetId = target.id,
                    distanceMeters = distanceMeters,
                    hasOwnshipReference = usesOwnshipReference,
                    isVerticalNonThreat = isVerticalNonThreat,
                    hasFreshTrendSample = trendAssessment.hasFreshTrendSample,
                    showClosingAlert = trendAssessment.showClosingAlert,
                    postPassDivergingSampleCount = trendAssessment.postPassDivergingSampleCount,
                    isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                    isEmergencyCollisionRisk = isEmergencyCollisionRiskCapped
                )
                val proximityReason = proximityReason(
                    hasOwnshipReference = usesOwnshipReference,
                    isVerticalNonThreat = isVerticalNonThreat,
                    isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                    isEmergencyCollisionRisk = isEmergencyCollisionRiskCapped,
                    hasTrendSample = trendAssessment.hasTrendSample,
                    isClosing = trendAssessment.isClosing,
                    showClosingAlert = trendAssessment.showClosingAlert
                )
                add(
                    AdsbTrafficUiModel(
                        id = target.id,
                        callsign = target.callsign,
                        lat = target.lat,
                        lon = target.lon,
                        altitudeM = target.altitudeM,
                        speedMps = target.speedMps,
                        trackDeg = resolvedTargetTrackDeg,
                        climbMps = target.climbMps,
                        ageSec = ageSec,
                        isStale = ageSec >= staleAfterSec,
                        distanceMeters = distanceMeters,
                        bearingDegFromUser = bearingDegFromUser,
                        usesOwnshipReference = usesOwnshipReference,
                        positionSource = target.positionSource,
                        category = target.category,
                        lastContactEpochSec = target.lastContactEpochSec,
                        proximityTier = proximityTier,
                        proximityReason = proximityReason,
                        isClosing = trendAssessment.isClosing && !isVerticalNonThreat,
                        closingRateMps = trendAssessment.closingRateMps,
                        isEmergencyCollisionRisk = isEmergencyCollisionRiskCapped,
                        isEmergencyAudioEligible = isEmergencyAudioEligible,
                        emergencyAudioIneligibilityReason = emergencyAudioIneligibilityReason,
                        isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                        positionAgeSec = positionAgeSec,
                        contactAgeSec = contactAgeSec,
                        isPositionStale = positionAgeSec >= staleAfterSec,
                        positionTimestampEpochSec = target.positionTimestampEpochSec,
                        effectivePositionEpochSec = target.effectivePositionEpochSec,
                        positionFreshnessSource = target.positionFreshnessSource
                    )
                )
            }
        }

        val emergencyAudioCandidateId = selectEmergencyAudioCandidate(withinVertical)?.id
        val displayed = withinVertical
            .sortedWith(ADSB_DISPLAY_PRIORITY_COMPARATOR)
            .take(maxDisplayed)
        val cappedCount = (withinVertical.size - displayed.size).coerceAtLeast(0)
        return AdsbStoreSelection(
            withinRadiusCount = withinHorizontalCount,
            withinVerticalCount = withinVertical.size,
            filteredByVerticalCount = filteredByVerticalCount,
            cappedCount = cappedCount,
            displayed = displayed,
            emergencyAudioCandidateId = emergencyAudioCandidateId
        )
    }

    private fun positionAgeSec(target: AdsbTarget, nowMonoMs: Long): Int =
        advancedAgeSec(
            nowMonoMs = nowMonoMs,
            sampleReceivedMonoMs = target.receivedMonoMs,
            ageAtReceiptSec = target.positionAgeAtReceiptSec
        )

    private fun effectiveContactAgeSec(
        target: AdsbTarget,
        nowMonoMs: Long,
        nowWallEpochSec: Long?
    ): Int? {
        val advancedContactAgeSec = advancedNullableAgeSec(
            nowMonoMs = nowMonoMs,
            sampleReceivedMonoMs = normalizedContactReceivedMonoMs(target),
            ageAtReceiptSec = target.contactAgeAtReceiptSec
        )
        val providerContactAgeSec = contactAgeSec(
            nowWallEpochSec = nowWallEpochSec,
            lastContactEpochSec = target.lastContactEpochSec
        )
        return when {
            advancedContactAgeSec != null && providerContactAgeSec != null ->
                maxOf(advancedContactAgeSec, providerContactAgeSec)
            advancedContactAgeSec != null -> advancedContactAgeSec
            else -> providerContactAgeSec
        }
    }

    private fun effectiveAgeSec(positionAgeSec: Int, contactAgeSec: Int?): Int =
        maxOf(positionAgeSec, contactAgeSec ?: positionAgeSec)

    private fun mergeTarget(existing: AdsbTarget, incoming: AdsbTarget): AdsbTarget {
        val positionCompare = comparePositionAuthority(
            incoming = incoming,
            existing = existing
        )
        return when {
            positionCompare > 0 -> incoming.withMergedMetadata(existing)
            positionCompare < 0 -> existing.withNewerContactMetadataFrom(incoming)
            hasSamePositionGeometry(existing = existing, incoming = incoming) ->
                existing.withNewerContactMetadataFrom(incoming)

            else -> incoming.withMergedMetadata(existing)
        }
    }

    private fun comparePositionAuthority(incoming: AdsbTarget, existing: AdsbTarget): Int {
        val incomingPositionEpochSec = incoming.effectivePositionEpochSec
        val existingPositionEpochSec = existing.effectivePositionEpochSec
        return when {
            incomingPositionEpochSec != null && existingPositionEpochSec != null ->
                incomingPositionEpochSec.compareTo(existingPositionEpochSec)

            incomingPositionEpochSec != null -> 1
            existingPositionEpochSec != null -> -1
            else -> incoming.receivedMonoMs.compareTo(existing.receivedMonoMs)
        }
    }

    private fun hasSamePositionGeometry(existing: AdsbTarget, incoming: AdsbTarget): Boolean =
        existing.lat == incoming.lat &&
            existing.lon == incoming.lon &&
            existing.altitudeM == incoming.altitudeM

    private fun AdsbTarget.withMergedMetadata(previous: AdsbTarget): AdsbTarget {
        val newerContact = newerContactTarget(
            incumbent = previous,
            candidate = this
        )
        return copy(
            callsign = callsign ?: previous.callsign,
            trackDeg = trackDeg ?: previous.trackDeg,
            climbMps = climbMps ?: previous.climbMps,
            positionSource = positionSource ?: previous.positionSource,
            category = category ?: previous.category,
            lastContactEpochSec = newerContact.lastContactEpochSec,
            contactReceivedMonoMs = normalizedContactReceivedMonoMs(newerContact),
            responseTimestampEpochSec = maxNullableLong(
                responseTimestampEpochSec,
                previous.responseTimestampEpochSec
            ),
            contactAgeAtReceiptSec = newerContact.contactAgeAtReceiptSec
        )
    }

    private fun AdsbTarget.withNewerContactMetadataFrom(candidate: AdsbTarget): AdsbTarget {
        val newerContact = newerContactTarget(
            incumbent = this,
            candidate = candidate
        )
        return copy(
            callsign = candidate.callsign ?: callsign,
            trackDeg = trackDeg ?: candidate.trackDeg,
            climbMps = climbMps ?: candidate.climbMps,
            positionSource = candidate.positionSource ?: positionSource,
            category = candidate.category ?: category,
            lastContactEpochSec = newerContact.lastContactEpochSec,
            contactReceivedMonoMs = normalizedContactReceivedMonoMs(newerContact),
            responseTimestampEpochSec = maxNullableLong(
                responseTimestampEpochSec,
                candidate.responseTimestampEpochSec
            ),
            contactAgeAtReceiptSec = newerContact.contactAgeAtReceiptSec
        )
    }

    private fun newerContactTarget(incumbent: AdsbTarget, candidate: AdsbTarget): AdsbTarget {
        val incumbentContactEpochSec = incumbent.lastContactEpochSec
        val candidateContactEpochSec = candidate.lastContactEpochSec
        return when {
            incumbentContactEpochSec != null && candidateContactEpochSec != null -> when {
                candidateContactEpochSec > incumbentContactEpochSec -> candidate
                candidateContactEpochSec < incumbentContactEpochSec -> incumbent
                normalizedContactReceivedMonoMs(candidate) >=
                    normalizedContactReceivedMonoMs(incumbent) -> candidate
                else -> incumbent
            }

            candidateContactEpochSec != null -> candidate
            incumbentContactEpochSec != null -> incumbent
            normalizedContactReceivedMonoMs(candidate) >=
                normalizedContactReceivedMonoMs(incumbent) -> candidate
            else -> incumbent
        }
    }

    private fun normalizedContactReceivedMonoMs(target: AdsbTarget): Long =
        maxOf(target.contactReceivedMonoMs, target.receivedMonoMs)

    private fun maxNullableLong(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }
}
