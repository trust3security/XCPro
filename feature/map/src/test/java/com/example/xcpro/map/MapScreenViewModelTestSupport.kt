package com.example.xcpro.map

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbProximityTier
import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnGliderTrailRepository
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnThermalHotspotState
import com.example.xcpro.ogn.OgnThermalRepository
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.qnh.QnhConfidence
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.qnh.QnhSource
import com.example.xcpro.qnh.QnhValue
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.flow.MutableStateFlow

fun defaultGps(
    latitude: Double = 46.0,
    longitude: Double = 7.0,
    altitudeMeters: Double = 1000.0,
    speedMs: Double = 0.0,
    bearingDeg: Double = 0.0,
    accuracyMeters: Float = 5f,
    timestampMillis: Long = 1_000L,
    monotonicTimestampMillis: Long = 0L
): GPSData = GPSData(
    position = GeoPoint(latitude, longitude),
    altitude = AltitudeM(altitudeMeters),
    speed = SpeedMs(speedMs),
    bearing = bearingDeg,
    accuracy = accuracyMeters,
    timestamp = timestampMillis,
    monotonicTimestampMillis = monotonicTimestampMillis
)

fun buildCompleteFlightData(
    gps: GPSData? = defaultGps(),
    baroAltitudeMeters: Double = 1_000.0,
    verticalSpeedMs: Double = 0.0,
    displayVarioMs: Double = 0.0,
    nettoMs: Double = 0.0,
    displayNettoMs: Double = 0.0,
    nettoValid: Boolean = false,
    baselineDisplayVarioMs: Double = 0.0,
    baselineVarioValid: Boolean = false,
    realIgcVarioMs: Double? = null,
    isCircling: Boolean = false,
    currentThermalValid: Boolean = false,
    thermalAverageValid: Boolean = false,
    timestampMillis: Long = 1_000L
): CompleteFlightData {
    return CompleteFlightData(
        gps = gps,
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(baroAltitudeMeters),
        qnh = PressureHpa(1_013.25),
        isQNHCalibrated = false,
        verticalSpeed = VerticalSpeedMs(verticalSpeedMs),
        displayVario = VerticalSpeedMs(displayVarioMs),
        displayNeedleVario = VerticalSpeedMs(0.0),
        displayNeedleVarioFast = VerticalSpeedMs(0.0),
        audioVario = VerticalSpeedMs(0.0),
        baselineVario = VerticalSpeedMs(0.0),
        baselineDisplayVario = VerticalSpeedMs(baselineDisplayVarioMs),
        baselineVarioValid = baselineVarioValid,
        bruttoVario = VerticalSpeedMs(0.0),
        bruttoAverage30s = VerticalSpeedMs(0.0),
        bruttoAverage30sValid = false,
        nettoAverage30s = VerticalSpeedMs(0.0),
        varioSource = "TEST",
        varioValid = true,
        pressureAltitude = AltitudeM(0.0),
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.LOW,
        qnhCalibrationAgeSeconds = -1,
        agl = AltitudeM(0.0),
        thermalAverage = VerticalSpeedMs(0.0),
        thermalAverageCircle = VerticalSpeedMs(0.0),
        thermalAverageTotal = VerticalSpeedMs(0.0),
        thermalGain = AltitudeM(0.0),
        thermalGainValid = false,
        currentThermalLiftRate = VerticalSpeedMs(0.0),
        currentThermalValid = currentThermalValid,
        currentLD = 0f,
        netto = VerticalSpeedMs(nettoMs),
        displayNetto = VerticalSpeedMs(displayNettoMs),
        nettoValid = nettoValid,
        trueAirspeed = SpeedMs(0.0),
        indicatedAirspeed = SpeedMs(0.0),
        airspeedSource = "UNKNOWN",
        tasValid = true,
        varioOptimized = VerticalSpeedMs(0.0),
        varioLegacy = VerticalSpeedMs(0.0),
        varioRaw = VerticalSpeedMs(0.0),
        varioGPS = VerticalSpeedMs(0.0),
        varioComplementary = VerticalSpeedMs(0.0),
        realIgcVario = realIgcVarioMs?.let { VerticalSpeedMs(it) },
        teAltitude = AltitudeM(0.0),
        macCready = 0.0,
        macCreadyRisk = 0.0,
        isCircling = isCircling,
        thermalAverageValid = thermalAverageValid,
        timestamp = timestampMillis,
        dataQuality = "TEST"
    )
}

fun sampleAdsbTarget(
    id: Icao24,
    distanceMeters: Double = 1500.0,
    usesOwnshipReference: Boolean = true,
    proximityTier: AdsbProximityTier = AdsbProximityTier.AMBER,
    isClosing: Boolean = true,
    closingRateMps: Double? = 0.7,
    isEmergencyCollisionRisk: Boolean = false
): AdsbTrafficUiModel = AdsbTrafficUiModel(
    id = id,
    callsign = "TEST01",
    lat = -35.0,
    lon = 149.0,
    altitudeM = 1000.0,
    speedMps = 70.0,
    trackDeg = 180.0,
    climbMps = 0.5,
    ageSec = 2,
    isStale = false,
    distanceMeters = distanceMeters,
    bearingDegFromUser = 220.0,
    usesOwnshipReference = usesOwnshipReference,
    positionSource = 0,
    category = 3,
    lastContactEpochSec = null,
    proximityTier = proximityTier,
    isClosing = isClosing,
    closingRateMps = closingRateMps,
    isEmergencyCollisionRisk = isEmergencyCollisionRisk
)

fun sampleOgnTarget(id: String): OgnTrafficTarget = OgnTrafficTarget(
    id = id,
    callsign = "OGNTEST",
    destination = "APRS",
    latitude = -35.0,
    longitude = 149.0,
    altitudeMeters = 1200.0,
    trackDegrees = 180.0,
    groundSpeedMps = 40.0,
    verticalSpeedMps = 1.1,
    deviceIdHex = "ABC123",
    signalDb = 12.0,
    displayLabel = id,
    identity = null,
    rawComment = "sample",
    rawLine = "sample line",
    timestampMillis = 1_000L,
    lastSeenMillis = 1_000L
)

fun sampleThermalHotspot(
    id: String,
    sourceTargetId: String
): OgnThermalHotspot = OgnThermalHotspot(
    id = id,
    sourceTargetId = sourceTargetId,
    sourceLabel = sourceTargetId,
    latitude = -35.0,
    longitude = 149.0,
    startedAtMonoMs = 1_000L,
    startedAtWallMs = 1_000L,
    updatedAtMonoMs = 2_000L,
    updatedAtWallMs = 2_000L,
    startAltitudeMeters = 900.0,
    maxAltitudeMeters = 1200.0,
    maxAltitudeAtMonoMs = 2_000L,
    maxClimbRateMps = 2.3,
    averageClimbRateMps = 1.6,
    averageBottomToTopClimbRateMps = 1.2,
    snailColorIndex = 15,
    state = OgnThermalHotspotState.ACTIVE
)

class FakeOgnTrafficRepository : OgnTrafficRepository {
    override val targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
    override val suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
    override val snapshot = MutableStateFlow(
        OgnTrafficSnapshot(
            targets = emptyList(),
            connectionState = OgnConnectionState.DISCONNECTED,
            lastError = null,
            subscriptionCenterLat = null,
            subscriptionCenterLon = null,
            receiveRadiusKm = 150,
            ddbCacheAgeMs = null,
            reconnectBackoffMs = null,
            lastReconnectWallMs = null
        )
    )
    override val isEnabled = MutableStateFlow(false)
    var lastCenterLat: Double? = null
    var lastCenterLon: Double? = null

    override fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        lastCenterLat = latitude
        lastCenterLon = longitude
    }

    override fun start() {
        setEnabled(true)
    }

    override fun stop() {
        setEnabled(false)
    }
}

class FakeOgnThermalRepository : OgnThermalRepository {
    override val hotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList())
}

class FakeOgnGliderTrailRepository : OgnGliderTrailRepository {
    override val segments = MutableStateFlow<List<OgnGliderTrailSegment>>(emptyList())
}

class FakeAdsbTrafficRepository : AdsbTrafficRepository {
    override val targets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList())
    override val snapshot = MutableStateFlow(
        AdsbTrafficSnapshot(
            targets = emptyList(),
            connectionState = AdsbConnectionState.Disabled,
            centerLat = null,
            centerLon = null,
            receiveRadiusKm = 20,
            fetchedCount = 0,
            withinRadiusCount = 0,
            displayedCount = 0,
            lastHttpStatus = null,
            remainingCredits = null,
            lastPollMonoMs = null,
            lastSuccessMonoMs = null,
            lastError = null
        )
    )
    override val isEnabled = MutableStateFlow(false)
    var lastCenterLat: Double? = null
    var lastCenterLon: Double? = null
    var lastOwnshipLat: Double? = null
    var lastOwnshipLon: Double? = null
    var clearOwnshipOriginCalls: Int = 0
    var clearTargetsCalls: Int = 0

    override fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }

    override fun clearTargets() {
        clearTargetsCalls += 1
        targets.value = emptyList()
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        lastCenterLat = latitude
        lastCenterLon = longitude
    }

    override fun updateOwnshipOrigin(latitude: Double, longitude: Double) {
        lastOwnshipLat = latitude
        lastOwnshipLon = longitude
    }

    override fun clearOwnshipOrigin() {
        clearOwnshipOriginCalls += 1
        lastOwnshipLat = null
        lastOwnshipLon = null
    }

    override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) = Unit

    override fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ) = Unit

    override fun reconnectNow() = Unit

    override fun start() {
        setEnabled(true)
    }

    override fun stop() {
        setEnabled(false)
    }
}

class FakeQnhRepository : QnhRepository {
    private val initialValue = QnhValue(
        hpa = 1013.25,
        source = QnhSource.STANDARD,
        calibratedAtMillis = 0L,
        confidence = QnhConfidence.LOW
    )
    private val qnhFlow = MutableStateFlow(initialValue)
    private val calibrationFlow = MutableStateFlow<QnhCalibrationState>(QnhCalibrationState.Idle)
    override val qnhState = qnhFlow
    override val calibrationState = calibrationFlow

    override suspend fun setManualQnh(hpa: Double) {
        qnhFlow.value = initialValue.copy(hpa = hpa, source = QnhSource.MANUAL)
    }

    override suspend fun resetToStandard() {
        qnhFlow.value = initialValue
    }

    override suspend fun applyAutoQnh(value: QnhValue) {
        qnhFlow.value = value
    }

    override fun updateCalibrationState(state: QnhCalibrationState) {
        calibrationFlow.value = state
    }
}
