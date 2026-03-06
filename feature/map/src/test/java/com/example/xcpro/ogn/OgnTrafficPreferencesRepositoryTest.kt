package com.example.xcpro.ogn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OgnTrafficPreferencesRepositoryTest {

    private lateinit var storeScope: CoroutineScope
    private lateinit var storeDispatcher: ExecutorCoroutineDispatcher
    private lateinit var repository: OgnTrafficPreferencesRepository

    @Before
    fun setUp() {
        storeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        storeScope = CoroutineScope(SupervisorJob() + storeDispatcher)
        val storeFile = File(
            Files.createTempDirectory("ogn-traffic-prefs-test-").toFile(),
            "ogn_traffic_preferences.preferences_pb"
        )
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { storeFile }
        )
        repository = OgnTrafficPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        runBlocking {
            storeScope.coroutineContext.job.cancelAndJoin()
        }
        storeDispatcher.close()
    }

    @Test
    fun iconSizePolicy_matches124To248Contract() {
        assertEquals(124, OGN_ICON_SIZE_MIN_PX)
        assertEquals(124, OGN_ICON_SIZE_DEFAULT_PX)
        assertEquals(248, OGN_ICON_SIZE_MAX_PX)
    }

    @Test
    fun iconSizeFlow_defaultsToConfiguredDefaultPx() = runBlocking {
        repository.setIconSizePx(OGN_ICON_SIZE_DEFAULT_PX)
        val current = repository.iconSizePxFlow.first()
        assertEquals(OGN_ICON_SIZE_DEFAULT_PX, current)
    }

    @Test
    fun setIconSizePx_clampsBelowMinimum() = runBlocking {
        repository.setIconSizePx(1)
        val current = repository.iconSizePxFlow.first()
        assertEquals(OGN_ICON_SIZE_MIN_PX, current)
    }

    @Test
    fun setIconSizePx_clampsAboveMaximum() = runBlocking {
        repository.setIconSizePx(999)
        val current = repository.iconSizePxFlow.first()
        assertEquals(OGN_ICON_SIZE_MAX_PX, current)
    }

    @Test
    fun setIconSizePx_persistsValidValue() = runBlocking {
        repository.setIconSizePx(240)
        val current = repository.iconSizePxFlow.first()
        assertEquals(240, current)
    }

    @Test
    fun receiveRadiusFlow_defaultsToConfiguredDefaultKm() = runBlocking {
        repository.setReceiveRadiusKm(OGN_RECEIVE_RADIUS_DEFAULT_KM)
        val current = repository.receiveRadiusKmFlow.first()
        assertEquals(OGN_RECEIVE_RADIUS_DEFAULT_KM, current)
    }

    @Test
    fun setReceiveRadiusKm_clampsBelowMinimum() = runBlocking {
        repository.setReceiveRadiusKm(1)
        val current = repository.receiveRadiusKmFlow.first()
        assertEquals(OGN_RECEIVE_RADIUS_MIN_KM, current)
    }

    @Test
    fun setReceiveRadiusKm_clampsAboveMaximum() = runBlocking {
        repository.setReceiveRadiusKm(999)
        val current = repository.receiveRadiusKmFlow.first()
        assertEquals(OGN_RECEIVE_RADIUS_MAX_KM, current)
    }

    @Test
    fun setReceiveRadiusKm_persistsValidValue() = runBlocking {
        repository.setReceiveRadiusKm(220)
        val current = repository.receiveRadiusKmFlow.first()
        assertEquals(220, current)
    }

    @Test
    fun autoReceiveRadiusFlow_defaultsToDisabled() = runBlocking {
        assertEquals(false, repository.autoReceiveRadiusEnabledFlow.first())
    }

    @Test
    fun setAutoReceiveRadiusEnabled_persistsValue() = runBlocking {
        repository.setAutoReceiveRadiusEnabled(true)
        assertEquals(true, repository.autoReceiveRadiusEnabledFlow.first())
    }

    @Test
    fun displayUpdateMode_defaultsToRealTime() = runBlocking {
        repository.setDisplayUpdateMode(OgnDisplayUpdateMode.REAL_TIME)
        assertEquals(OgnDisplayUpdateMode.REAL_TIME, repository.displayUpdateModeFlow.first())
    }

    @Test
    fun setDisplayUpdateMode_persistsValue() = runBlocking {
        repository.setDisplayUpdateMode(OgnDisplayUpdateMode.BATTERY)
        assertEquals(OgnDisplayUpdateMode.BATTERY, repository.displayUpdateModeFlow.first())
    }

    @Test
    fun showSciaFlow_defaultsToDisabled() = runBlocking {
        repository.setShowSciaEnabled(false)
        assertEquals(false, repository.showSciaEnabledFlow.first())
    }

    @Test
    fun setShowSciaEnabled_persistsValue() = runBlocking {
        repository.setShowSciaEnabled(true)
        assertEquals(true, repository.showSciaEnabledFlow.first())
    }

    @Test
    fun setOverlayAndSciaEnabled_updatesBothFlags() = runBlocking {
        repository.setOverlayAndSciaEnabled(
            overlayEnabled = true,
            showSciaEnabled = true
        )

        assertEquals(true, repository.enabledFlow.first())
        assertEquals(true, repository.showSciaEnabledFlow.first())
    }

    @Test
    fun thermalRetentionFlow_defaultsToAllDay() = runBlocking {
        repository.setThermalRetentionHours(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        assertEquals(
            OGN_THERMAL_RETENTION_DEFAULT_HOURS,
            repository.thermalRetentionHoursFlow.first()
        )
    }

    @Test
    fun setThermalRetentionHours_clampsBelowMinimum() = runBlocking {
        repository.setThermalRetentionHours(-99)
        assertEquals(
            OGN_THERMAL_RETENTION_MIN_HOURS,
            repository.thermalRetentionHoursFlow.first()
        )
    }

    @Test
    fun setThermalRetentionHours_clampsAboveMaximum() = runBlocking {
        repository.setThermalRetentionHours(999)
        assertEquals(
            OGN_THERMAL_RETENTION_ALL_DAY_HOURS,
            repository.thermalRetentionHoursFlow.first()
        )
    }

    @Test
    fun setThermalRetentionHours_persistsValue() = runBlocking {
        repository.setThermalRetentionHours(6)
        assertEquals(6, repository.thermalRetentionHoursFlow.first())
    }

    @Test
    fun hotspotsDisplayPercentFlow_defaultsToConfiguredDefault() = runBlocking {
        repository.setHotspotsDisplayPercent(OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT)
        assertEquals(
            OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT,
            repository.hotspotsDisplayPercentFlow.first()
        )
    }

    @Test
    fun setHotspotsDisplayPercent_clampsBelowMinimum() = runBlocking {
        repository.setHotspotsDisplayPercent(1)
        assertEquals(
            OGN_HOTSPOTS_DISPLAY_PERCENT_MIN,
            repository.hotspotsDisplayPercentFlow.first()
        )
    }

    @Test
    fun setHotspotsDisplayPercent_clampsAboveMaximum() = runBlocking {
        repository.setHotspotsDisplayPercent(999)
        assertEquals(
            OGN_HOTSPOTS_DISPLAY_PERCENT_MAX,
            repository.hotspotsDisplayPercentFlow.first()
        )
    }

    @Test
    fun setHotspotsDisplayPercent_persistsValue() = runBlocking {
        repository.setHotspotsDisplayPercent(35)
        assertEquals(35, repository.hotspotsDisplayPercentFlow.first())
    }

    @Test
    fun targetSelection_defaultsToDisabledAndNoAircraft() = runBlocking {
        assertEquals(false, repository.targetEnabledFlow.first())
        assertNull(repository.targetAircraftKeyFlow.first())
    }

    @Test
    fun setTargetSelection_enablesAndNormalizesAircraftKey() = runBlocking {
        repository.setTargetSelection(enabled = true, aircraftKey = " flarm:ab12cd ")

        assertEquals(true, repository.targetEnabledFlow.first())
        assertEquals("FLARM:AB12CD", repository.targetAircraftKeyFlow.first())
    }

    @Test
    fun clearTargetSelection_disablesAndClearsAircraftKey() = runBlocking {
        repository.clearTargetSelection()

        assertEquals(false, repository.targetEnabledFlow.first())
        assertNull(repository.targetAircraftKeyFlow.first())
    }

    @Test
    fun setTargetSelection_enableWithoutValidKey_preservesPreviousState() = runBlocking {
        repository.setTargetSelection(enabled = true, aircraftKey = "FLARM:DDA85C")
        repository.setTargetSelection(enabled = true, aircraftKey = "   ")

        assertEquals(true, repository.targetEnabledFlow.first())
        assertEquals("FLARM:DDA85C", repository.targetAircraftKeyFlow.first())
    }

    @Test
    fun ownshipHexFlows_defaultToNull() = runBlocking {
        repository.setOwnFlarmHex(null)
        repository.setOwnIcaoHex(null)
        assertNull(repository.ownFlarmHexFlow.first())
        assertNull(repository.ownIcaoHexFlow.first())
    }

    @Test
    fun setOwnFlarmHex_normalizesAndPersists() = runBlocking {
        repository.setOwnFlarmHex("  ddA85c ")
        assertEquals("DDA85C", repository.ownFlarmHexFlow.first())
    }

    @Test
    fun setOwnIcaoHex_invalidNonBlankIsIgnored() = runBlocking {
        repository.setOwnIcaoHex("4ca6a4")
        repository.setOwnIcaoHex("not-hex")
        assertEquals("4CA6A4", repository.ownIcaoHexFlow.first())
    }

    @Test
    fun setOwnFlarmHex_blankClearsValue() = runBlocking {
        repository.setOwnFlarmHex(" ")
        assertNull(repository.ownFlarmHexFlow.first())
    }

    @Test
    fun clientCallsignFlow_defaultsToNull() = runBlocking {
        assertNull(repository.clientCallsignFlow.first())
    }

    @Test
    fun setClientCallsign_normalizesAndPersists() = runBlocking {
        repository.setClientCallsign(" xcpa1b2c3 ")
        assertEquals("XCPA1B2C3", repository.clientCallsignFlow.first())
    }

    @Test
    fun setClientCallsign_invalidIsIgnored() = runBlocking {
        repository.setClientCallsign("XCPA1B2C3")
        repository.setClientCallsign("bad-callsign")
        assertEquals("XCPA1B2C3", repository.clientCallsignFlow.first())
    }
}
