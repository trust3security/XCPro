package com.example.xcpro.map.ui

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.SelectedOgnThermalOverlayContext
import com.example.xcpro.map.TrafficMapCoordinate
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.sampleAdsbTarget
import com.example.xcpro.map.sampleOgnTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapTrafficOverlayRuntimeBindingsTest {

    @Test
    fun bindMapTrafficOverlayRuntime_hidesOwnshipInputsInSpectatorMode() = runTest {
        val inputs = sampleRuntimeInputs()
        val port = RecordingTrafficOverlayRenderPort()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(false)
        )
        runCurrent()

        assertNull(port.lastOgnTrafficTargetsRequest?.ownshipAltitudeMeters)
        assertNull(port.lastAdsbTrafficTargetsRequest?.ownshipAltitudeMeters)
        assertNull(port.lastOgnTargetVisualRequest?.ownshipCoordinate)
        assertNull(port.lastOgnTargetVisualRequest?.ownshipAltitudeMeters)
    }

    @Test
    fun bindMapTrafficOverlayRuntime_keepsOwnshipInputsWhileFlying() = runTest {
        val inputs = sampleRuntimeInputs()
        val port = RecordingTrafficOverlayRenderPort()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(true)
        )
        runCurrent()

        assertEquals(1_234.0, port.lastOgnTrafficTargetsRequest?.ownshipAltitudeMeters ?: Double.NaN, 0.0)
        assertEquals(1_234.0, port.lastAdsbTrafficTargetsRequest?.ownshipAltitudeMeters ?: Double.NaN, 0.0)
        assertEquals(
            TrafficMapCoordinate(latitude = -33.9, longitude = 151.2),
            port.lastOgnTargetVisualRequest?.ownshipCoordinate
        )
    }

    @Test
    fun bindMapTrafficOverlayRuntime_gatesTargetsToEmptyWhenOverlaysDisabled() = runTest {
        val inputs = sampleRuntimeInputs(
            ognOverlayEnabled = false,
            adsbOverlayEnabled = false
        )
        val port = RecordingTrafficOverlayRenderPort()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(true)
        )
        runCurrent()

        assertTrue(port.lastOgnTrafficTargetsRequest?.targets?.isEmpty() == true)
        assertTrue(port.lastAdsbTrafficTargetsRequest?.targets?.isEmpty() == true)
        assertEquals(inputs.selectedOgnTargetKey.value, port.lastOgnTrafficTargetsRequest?.selectedTargetKey)
        assertEquals(Icao24("abcdef"), port.lastAdsbTrafficTargetsRequest?.selectedTargetId)
        assertFalse(port.lastOgnTargetVisualRequest?.enabled == true)
    }

    @Test
    fun bindMapTrafficOverlayRuntime_updatesConfigCollectorsWithoutRestartingTargetCollectors() = runTest {
        val inputs = sampleRuntimeInputs()
        val port = RecordingTrafficOverlayRenderPort()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(true)
        )
        runCurrent()

        val initialOgnTrafficUpdates = port.ognTrafficTargetsUpdateCount
        val initialAdsbTrafficUpdates = port.adsbTrafficTargetsUpdateCount
        inputs.ognIconSizePx.value = 72
        inputs.adsbEmergencyFlashEnabled.value = true
        inputs.ognDisplayUpdateMode.value = OgnDisplayUpdateMode.BALANCED
        runCurrent()

        assertEquals(initialOgnTrafficUpdates, port.ognTrafficTargetsUpdateCount)
        assertEquals(initialAdsbTrafficUpdates, port.adsbTrafficTargetsUpdateCount)
        assertEquals(72, port.lastOgnIconSizePx)
        assertTrue(port.lastAdsbEmergencyFlashEnabled)
        assertEquals(OgnDisplayUpdateMode.BALANCED, port.lastOgnDisplayUpdateMode)
    }

    @Test
    fun bindMapTrafficOverlayRuntime_dedupesOgnTrafficWhenOnlyNonRenderedFieldsChange() = runTest {
        val inputs = sampleRuntimeInputs()
        val port = RecordingTrafficOverlayRenderPort()
        val telemetry = RecordingCollectorTelemetrySink()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            telemetrySink = telemetry,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(true)
        )
        runCurrent()

        inputs.ognTargets.value = listOf(inputs.ognTargets.value.single().copy(rawLine = "changed-raw-line"))
        runCurrent()

        assertEquals(1, port.ognTrafficTargetsUpdateCount)
        assertEquals(2, telemetry.ognTrafficCollectorEmissionCount)
        assertEquals(1, telemetry.ognTrafficCollectorDedupedCount)
        assertEquals(1, telemetry.ognTrafficPortUpdateCount)
    }

    @Test
    fun bindMapTrafficOverlayRuntime_dedupesAdsbTrafficWhenOnlyNonRenderedFieldsChange() = runTest {
        val inputs = sampleRuntimeInputs()
        val port = RecordingTrafficOverlayRenderPort()
        val telemetry = RecordingCollectorTelemetrySink()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            telemetrySink = telemetry,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(true)
        )
        runCurrent()

        inputs.adsbTargets.value = listOf(
            inputs.adsbTargets.value.single().copy(lastContactEpochSec = 999L)
        )
        runCurrent()

        assertEquals(1, port.adsbTrafficTargetsUpdateCount)
        assertEquals(2, telemetry.adsbTrafficCollectorEmissionCount)
        assertEquals(1, telemetry.adsbTrafficCollectorDedupedCount)
        assertEquals(1, telemetry.adsbTrafficPortUpdateCount)
    }

    @Test
    fun bindMapTrafficOverlayRuntime_dedupesTargetVisualsWhenLocationFieldsDoNotAffectCoordinate() = runTest {
        val inputs = sampleRuntimeInputs()
        val port = RecordingTrafficOverlayRenderPort()
        val telemetry = RecordingCollectorTelemetrySink()

        bindMapTrafficOverlayRuntime(
            scope = backgroundScope,
            port = port,
            telemetrySink = telemetry,
            inputs = inputs.toRuntimeInputs(),
            renderLocalOwnship = MutableStateFlow(true)
        )
        runCurrent()

        inputs.currentLocation.value = sampleLocation().copy(
            speedMs = 22.0,
            bearingDeg = 210.0,
            accuracyMeters = 8.0,
            timestampMs = 2_000L
        )
        runCurrent()

        assertEquals(1, port.ognTargetVisualUpdateCount)
        assertEquals(1, telemetry.ognTargetVisualCollectorEmissionCount)
        assertEquals(0, telemetry.ognTargetVisualCollectorDedupedCount)
        assertEquals(1, telemetry.ognTargetVisualPortUpdateCount)
    }

    @Test
    fun createMapReadyTrafficOverlayConfig_readsCurrentRuntimeValues() {
        val inputs = sampleRuntimeInputs()

        val config = createMapReadyTrafficOverlayConfig(inputs.toRuntimeInputs())

        assertEquals(OgnDisplayUpdateMode.DEFAULT, config.ognDisplayUpdateMode)
        assertEquals(48, config.ognIconSizePx)
        assertEquals(44, config.adsbIconSizePx)
        assertFalse(config.adsbEmergencyFlashEnabled)
        assertTrue(config.adsbDefaultMediumUnknownIconEnabled)
    }

    private fun sampleRuntimeInputs(
        ognOverlayEnabled: Boolean = true,
        adsbOverlayEnabled: Boolean = true
    ): MutableRuntimeInputs {
        val ognTarget = sampleOgnTarget("OGN123")
        return MutableRuntimeInputs(
            ognTargets = MutableStateFlow(listOf(ognTarget)),
            ognOverlayEnabled = MutableStateFlow(ognOverlayEnabled),
            ognIconSizePx = MutableStateFlow(48),
            ognDisplayUpdateMode = MutableStateFlow(OgnDisplayUpdateMode.DEFAULT),
            ognThermalHotspots = MutableStateFlow(emptyList()),
            showOgnSciaEnabled = MutableStateFlow(false),
            ognTargetEnabled = MutableStateFlow(true),
            ognResolvedTarget = MutableStateFlow<com.example.xcpro.map.OgnTrafficTarget?>(ognTarget),
            showOgnThermalsEnabled = MutableStateFlow(false),
            ognGliderTrailSegments = MutableStateFlow(emptyList()),
            overlayOwnshipAltitudeMeters = MutableStateFlow<Double?>(1_234.0),
            ognAltitudeUnit = MutableStateFlow(AltitudeUnit.METERS),
            adsbTargets = MutableStateFlow(
                listOf(
                    sampleAdsbTarget(
                        id = Icao24("abcdef"),
                        distanceMeters = 500.0
                    )
                )
            ),
            adsbOverlayEnabled = MutableStateFlow(adsbOverlayEnabled),
            adsbIconSizePx = MutableStateFlow(44),
            adsbEmergencyFlashEnabled = MutableStateFlow(false),
            adsbDefaultMediumUnknownIconEnabled = MutableStateFlow(true),
            selectedOgnTargetKey = MutableStateFlow<String?>(ognTarget.canonicalKey),
            selectedAdsbTargetId = MutableStateFlow<Icao24?>(Icao24("abcdef")),
            selectedOgnThermalContext = MutableStateFlow<com.example.xcpro.map.SelectedOgnThermalContext?>(null),
            unitsPreferences = MutableStateFlow(sampleUnitsPreferences()),
            currentLocation = MutableStateFlow<MapLocationUiModel?>(sampleLocation())
        )
    }

    private fun sampleLocation(): MapLocationUiModel = MapLocationUiModel(
        latitude = -33.9,
        longitude = 151.2,
        speedMs = 18.0,
        bearingDeg = 180.0,
        accuracyMeters = 4.0,
        timestampMs = 1_000L
    )

    private fun sampleUnitsPreferences(): UnitsPreferences = UnitsPreferences(
        altitude = AltitudeUnit.METERS,
        speed = SpeedUnit.METERS_PER_SECOND,
        distance = DistanceUnit.KILOMETERS
    )

    private data class MutableRuntimeInputs(
        val ognTargets: MutableStateFlow<List<com.example.xcpro.map.OgnTrafficTarget>>,
        val ognOverlayEnabled: MutableStateFlow<Boolean>,
        val ognIconSizePx: MutableStateFlow<Int>,
        val ognDisplayUpdateMode: MutableStateFlow<OgnDisplayUpdateMode>,
        val ognThermalHotspots: MutableStateFlow<List<com.example.xcpro.map.OgnThermalHotspot>>,
        val showOgnSciaEnabled: MutableStateFlow<Boolean>,
        val ognTargetEnabled: MutableStateFlow<Boolean>,
        val ognResolvedTarget: MutableStateFlow<com.example.xcpro.map.OgnTrafficTarget?>,
        val showOgnThermalsEnabled: MutableStateFlow<Boolean>,
        val ognGliderTrailSegments: MutableStateFlow<List<com.example.xcpro.map.OgnGliderTrailSegment>>,
        val overlayOwnshipAltitudeMeters: MutableStateFlow<Double?>,
        val ognAltitudeUnit: MutableStateFlow<AltitudeUnit>,
        val adsbTargets: MutableStateFlow<List<com.example.xcpro.map.AdsbTrafficUiModel>>,
        val adsbOverlayEnabled: MutableStateFlow<Boolean>,
        val adsbIconSizePx: MutableStateFlow<Int>,
        val adsbEmergencyFlashEnabled: MutableStateFlow<Boolean>,
        val adsbDefaultMediumUnknownIconEnabled: MutableStateFlow<Boolean>,
        val selectedOgnTargetKey: MutableStateFlow<String?>,
        val selectedAdsbTargetId: MutableStateFlow<Icao24?>,
        val selectedOgnThermalContext: MutableStateFlow<com.example.xcpro.map.SelectedOgnThermalContext?>,
        val unitsPreferences: MutableStateFlow<UnitsPreferences>,
        val currentLocation: MutableStateFlow<MapLocationUiModel?>
    ) {
        fun toRuntimeInputs(): MapTrafficOverlayRuntimeInputs = MapTrafficOverlayRuntimeInputs(
            ognTargets = ognTargets,
            ognOverlayEnabled = ognOverlayEnabled,
            ognIconSizePx = ognIconSizePx,
            ognDisplayUpdateMode = ognDisplayUpdateMode,
            ognThermalHotspots = ognThermalHotspots,
            showOgnSciaEnabled = showOgnSciaEnabled,
            ognTargetEnabled = ognTargetEnabled,
            ognResolvedTarget = ognResolvedTarget,
            showOgnThermalsEnabled = showOgnThermalsEnabled,
            ognGliderTrailSegments = ognGliderTrailSegments,
            overlayOwnshipAltitudeMeters = overlayOwnshipAltitudeMeters,
            ognAltitudeUnit = ognAltitudeUnit,
            adsbTargets = adsbTargets,
            adsbOverlayEnabled = adsbOverlayEnabled,
            adsbIconSizePx = adsbIconSizePx,
            adsbEmergencyFlashEnabled = adsbEmergencyFlashEnabled,
            adsbDefaultMediumUnknownIconEnabled = adsbDefaultMediumUnknownIconEnabled,
            selectedOgnTargetKey = selectedOgnTargetKey,
            selectedAdsbTargetId = selectedAdsbTargetId,
            selectedOgnThermalContext = selectedOgnThermalContext,
            unitsPreferences = unitsPreferences,
            currentLocation = currentLocation
        )
    }

    private class RecordingTrafficOverlayRenderPort : TrafficOverlayRenderPort {
        var lastOgnDisplayUpdateMode: OgnDisplayUpdateMode? = null
        var lastOgnTrafficTargetsRequest: OgnTrafficTargetsRenderRequest? = null
        var lastOgnThermalContext: SelectedOgnThermalOverlayContext? = null
        var lastOgnTargetVisualRequest: OgnTargetVisualRenderRequest? = null
        var lastOgnIconSizePx: Int? = null
        var lastAdsbTrafficTargetsRequest: AdsbTrafficTargetsRenderRequest? = null
        var lastAdsbIconSizePx: Int? = null
        var lastAdsbEmergencyFlashEnabled: Boolean = false
        var lastAdsbDefaultMediumUnknownIconEnabled: Boolean = false
        var ognTrafficTargetsUpdateCount: Int = 0
        var ognTargetVisualUpdateCount: Int = 0
        var adsbTrafficTargetsUpdateCount: Int = 0

        override fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
            lastOgnDisplayUpdateMode = mode
        }

        override fun updateOgnTrafficTargets(
            targets: List<com.example.xcpro.map.OgnTrafficTarget>,
            selectedTargetKey: String?,
            ownshipAltitudeMeters: Double?,
            altitudeUnit: AltitudeUnit,
            unitsPreferences: UnitsPreferences
        ) {
            ognTrafficTargetsUpdateCount += 1
            lastOgnTrafficTargetsRequest = OgnTrafficTargetsRenderRequest(
                targets = targets,
                selectedTargetKey = selectedTargetKey,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                unitsPreferences = unitsPreferences
            )
        }

        override fun updateOgnThermalHotspots(hotspots: List<com.example.xcpro.map.OgnThermalHotspot>) = Unit

        override fun updateOgnGliderTrailSegments(segments: List<com.example.xcpro.map.OgnGliderTrailSegment>) = Unit

        override fun updateSelectedOgnThermalContext(context: SelectedOgnThermalOverlayContext?) {
            lastOgnThermalContext = context
        }

        override fun updateOgnTargetVisuals(
            enabled: Boolean,
            resolvedTarget: com.example.xcpro.map.OgnTrafficTarget?,
            ownshipCoordinate: TrafficMapCoordinate?,
            ownshipAltitudeMeters: Double?,
            altitudeUnit: AltitudeUnit,
            unitsPreferences: UnitsPreferences
        ) {
            ognTargetVisualUpdateCount += 1
            lastOgnTargetVisualRequest = OgnTargetVisualRenderRequest(
                enabled = enabled,
                resolvedTarget = resolvedTarget,
                ownshipCoordinate = ownshipCoordinate,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                unitsPreferences = unitsPreferences
            )
        }

        override fun setOgnIconSizePx(iconSizePx: Int) {
            lastOgnIconSizePx = iconSizePx
        }

        override fun updateAdsbTrafficTargets(
            targets: List<com.example.xcpro.map.AdsbTrafficUiModel>,
            selectedTargetId: Icao24?,
            ownshipAltitudeMeters: Double?,
            unitsPreferences: UnitsPreferences
        ) {
            adsbTrafficTargetsUpdateCount += 1
            lastAdsbTrafficTargetsRequest = AdsbTrafficTargetsRenderRequest(
                targets = targets,
                selectedTargetId = selectedTargetId,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                unitsPreferences = unitsPreferences
            )
        }

        override fun setAdsbIconSizePx(iconSizePx: Int) {
            lastAdsbIconSizePx = iconSizePx
        }

        override fun setAdsbEmergencyFlashEnabled(enabled: Boolean) {
            lastAdsbEmergencyFlashEnabled = enabled
        }

        override fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean) {
            lastAdsbDefaultMediumUnknownIconEnabled = enabled
        }
    }

    private class RecordingCollectorTelemetrySink : TrafficOverlayCollectorTelemetrySink {
        var ognTrafficCollectorEmissionCount: Int = 0
        var ognTrafficCollectorDedupedCount: Int = 0
        var ognTrafficPortUpdateCount: Int = 0
        var ognTargetVisualCollectorEmissionCount: Int = 0
        var ognTargetVisualCollectorDedupedCount: Int = 0
        var ognTargetVisualPortUpdateCount: Int = 0
        var adsbTrafficCollectorEmissionCount: Int = 0
        var adsbTrafficCollectorDedupedCount: Int = 0
        var adsbTrafficPortUpdateCount: Int = 0

        override fun onOgnTrafficCollectorEmission() {
            ognTrafficCollectorEmissionCount += 1
        }

        override fun onOgnTrafficCollectorDeduped() {
            ognTrafficCollectorDedupedCount += 1
        }

        override fun onOgnTrafficPortUpdate() {
            ognTrafficPortUpdateCount += 1
        }

        override fun onOgnTargetVisualCollectorEmission() {
            ognTargetVisualCollectorEmissionCount += 1
        }

        override fun onOgnTargetVisualCollectorDeduped() {
            ognTargetVisualCollectorDedupedCount += 1
        }

        override fun onOgnTargetVisualPortUpdate() {
            ognTargetVisualPortUpdateCount += 1
        }

        override fun onAdsbTrafficCollectorEmission() {
            adsbTrafficCollectorEmissionCount += 1
        }

        override fun onAdsbTrafficCollectorDeduped() {
            adsbTrafficCollectorDedupedCount += 1
        }

        override fun onAdsbTrafficPortUpdate() {
            adsbTrafficPortUpdateCount += 1
        }

        override fun onOgnThermalCollectorEmission() = Unit

        override fun onOgnTrailCollectorEmission() = Unit

        override fun onSelectedOgnThermalCollectorEmission() = Unit
    }
}
