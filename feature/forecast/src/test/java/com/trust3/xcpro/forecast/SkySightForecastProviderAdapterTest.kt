package com.trust3.xcpro.forecast

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import com.trust3.xcpro.testing.OkHttpClientRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkySightForecastProviderAdapterTest {
    private val okHttpClients = OkHttpClientRegistry()

    @After
    fun tearDown() {
        okHttpClients.shutdownAll()
    }

    private fun createAdapter(): SkySightForecastProviderAdapter {
        return SkySightForecastProviderAdapter(
            httpClient = okHttpClients.register(OkHttpClient()),
            skySightApiKey = "test-key",
            dispatcher = Dispatchers.IO
        )
    }

    @Test
    fun timeSlots_coverCurrentRegionDayOnly() {
        val adapter = createAdapter()
        val nowUtcMs = 1_700_000_123_456L
        val regionCode = "WEST_US"
        val zoneId = ZoneId.of("America/Los_Angeles")
        val expectedDate = Instant.ofEpochMilli(nowUtcMs).atZone(zoneId).toLocalDate()

        val slots = adapter.getTimeSlots(
            nowUtcMs = nowUtcMs,
            regionCode = regionCode
        )

        assertEquals(29, slots.size)
        assertTrue(
            slots.all { slot ->
                Instant.ofEpochMilli(slot.validTimeUtcMs).atZone(zoneId).toLocalDate() == expectedDate
            }
        )
        assertEquals(1_800_000L, slots[1].validTimeUtcMs - slots[0].validTimeUtcMs)

        val firstLocal = Instant.ofEpochMilli(slots.first().validTimeUtcMs).atZone(zoneId).toLocalTime()
        val lastLocal = Instant.ofEpochMilli(slots.last().validTimeUtcMs).atZone(zoneId).toLocalTime()
        assertEquals(6, firstLocal.hour)
        assertEquals(0, firstLocal.minute)
        assertEquals(20, lastLocal.hour)
        assertEquals(0, lastLocal.minute)
    }

    @Test
    fun windTileSpec_usesWindPathAndVectorPointFormat() = runTest {
        val adapter = createAdapter()
        val slot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L) // 2025-02-15T12:00:00Z

        val tileSpec = adapter.getTileSpec(
            parameterId = ForecastParameterId("sfcwind0"),
            timeSlot = slot,
            regionCode = "WEST_US"
        )

        assertEquals(ForecastTileFormat.VECTOR_WIND_POINTS, tileSpec.format)
        assertTrue(tileSpec.urlTemplate.contains("/wind/{z}/{x}/{y}/sfcwind0"))
        assertEquals("sfcwind0", tileSpec.sourceLayer)
        assertEquals("spd", tileSpec.speedProperty)
        assertEquals("dir", tileSpec.directionProperty)
        assertEquals(16, tileSpec.maxZoom)
    }

    @Test
    fun thermalTileSpec_usesBsratioSourceLayer() = runTest {
        val adapter = createAdapter()
        val slot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L) // 2025-02-15T12:00:00Z

        val tileSpec = adapter.getTileSpec(
            parameterId = ForecastParameterId("wstar_bsratio"),
            timeSlot = slot,
            regionCode = "WEST_US"
        )

        assertEquals(ForecastTileFormat.VECTOR_INDEXED_FILL, tileSpec.format)
        assertEquals("bsratio", tileSpec.sourceLayer)
        assertTrue(tileSpec.sourceLayerCandidates.contains("bsratio"))
        assertEquals("idx", tileSpec.valueProperty)
    }

    @Test
    fun parameters_includeConvergence_withPointValueDisabled() = runTest {
        val adapter = createAdapter()

        val convergence = adapter.getParameters().firstOrNull { meta ->
            meta.id.value == "wblmaxmin"
        }

        assertNotNull(convergence)
        assertEquals("Convergence", convergence?.name)
        assertFalse(convergence?.supportsPointValue ?: true)
    }

    @Test
    fun convergenceTileSpec_usesIndexedFillPath() = runTest {
        val adapter = createAdapter()
        val slot = ForecastTimeSlot(validTimeUtcMs = 1_739_448_000_000L) // 2025-02-15T12:00:00Z

        val tileSpec = adapter.getTileSpec(
            parameterId = ForecastParameterId("wblmaxmin"),
            timeSlot = slot,
            regionCode = "WEST_US"
        )

        assertEquals(ForecastTileFormat.VECTOR_INDEXED_FILL, tileSpec.format)
        assertTrue(tileSpec.urlTemplate.contains("/wblmaxmin/{z}/{x}/{y}"))
        assertEquals(5, tileSpec.maxZoom)
    }
}
