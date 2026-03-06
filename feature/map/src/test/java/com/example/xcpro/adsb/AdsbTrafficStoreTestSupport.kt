package com.example.xcpro.adsb

internal fun runDeterministicScenario(store: AdsbTrafficStore): List<TransitionSnapshot> {
    val steps = listOf(
        ScenarioStep(nowMonoMs = 800_000L, lon = 151.2200, receivedMonoMs = 800_000L, usesOwnshipReference = true),
        ScenarioStep(nowMonoMs = 802_000L, lon = 151.2140, receivedMonoMs = 802_000L, usesOwnshipReference = true),
        ScenarioStep(nowMonoMs = 803_000L, lon = 151.2145, receivedMonoMs = 803_000L, usesOwnshipReference = true),
        ScenarioStep(nowMonoMs = 807_200L, lon = 151.2145, receivedMonoMs = 803_000L, usesOwnshipReference = true),
        ScenarioStep(nowMonoMs = 808_300L, lon = 151.2130, receivedMonoMs = 808_300L, usesOwnshipReference = true),
        ScenarioStep(nowMonoMs = 809_400L, lon = 151.2130, receivedMonoMs = 809_400L, usesOwnshipReference = false),
        ScenarioStep(nowMonoMs = 810_500L, lon = 151.2120, receivedMonoMs = 810_500L, usesOwnshipReference = true)
    )
    val prototype = target(
        index = 42,
        lat = -33.8688,
        lon = 151.2200,
        receivedMonoMs = steps.first().receivedMonoMs
    ).copy(trackDeg = null)

    return steps.map { step ->
        store.upsertAll(
            listOf(
                prototype.copy(
                    lon = step.lon,
                    receivedMonoMs = step.receivedMonoMs
                )
            )
        )
        val ui = selectAt(
            store = store,
            nowMonoMs = step.nowMonoMs,
            usesOwnshipReference = step.usesOwnshipReference
        ).displayed.first()
        TransitionSnapshot(
            proximityTier = ui.proximityTier,
            isClosing = ui.isClosing,
            isEmergencyCollisionRisk = ui.isEmergencyCollisionRisk,
            usesOwnshipReference = ui.usesOwnshipReference
        )
    }
}

internal fun selectAt(
    store: AdsbTrafficStore,
    nowMonoMs: Long,
    usesOwnshipReference: Boolean = true,
    ownshipAltitudeMeters: Double? = 1_000.0,
    referenceSampleMonoMs: Long? = null
): AdsbStoreSelection = store.select(
    nowMonoMs = nowMonoMs,
    queryCenterLat = -33.8688,
    queryCenterLon = 151.2093,
    referenceLat = -33.8688,
    referenceLon = 151.2093,
    ownshipAltitudeMeters = ownshipAltitudeMeters,
    referenceSampleMonoMs = referenceSampleMonoMs,
    usesOwnshipReference = usesOwnshipReference,
    radiusMeters = 20_000.0,
    verticalAboveMeters = 5_000.0,
    verticalBelowMeters = 5_000.0,
    maxDisplayed = 30,
    staleAfterSec = 60
)

internal data class ScenarioStep(
    val nowMonoMs: Long,
    val lon: Double,
    val receivedMonoMs: Long,
    val usesOwnshipReference: Boolean
)

internal data class TransitionSnapshot(
    val proximityTier: AdsbProximityTier,
    val isClosing: Boolean,
    val isEmergencyCollisionRisk: Boolean,
    val usesOwnshipReference: Boolean
)

internal fun target(
    index: Int,
    lat: Double = -33.8688,
    lon: Double = 151.2093,
    receivedMonoMs: Long
): AdsbTarget {
    val id = Icao24.from("%06x".format(index)) ?: error("invalid test id")
    return AdsbTarget(
        id = id,
        callsign = "T$index",
        lat = lat,
        lon = lon,
        altitudeM = 1200.0,
        speedMps = 30.0,
        trackDeg = 180.0,
        climbMps = 0.5,
        positionSource = 0,
        category = 2,
        lastContactEpochSec = 1_710_000_000L,
        receivedMonoMs = receivedMonoMs
    )
}
