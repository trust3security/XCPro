package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.core.time.FakeClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkySightForecastProviderAdapterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun timeSlots_coverCurrentRegionDayOnly() {
        val adapter = SkySightForecastProviderAdapter(
            preferencesRepository = ForecastPreferencesRepository(context),
            clock = FakeClock(monoMs = 0L, wallMs = 1_700_000_000_000L),
            httpClient = OkHttpClient(),
            dispatcher = Dispatchers.IO
        )
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
}
