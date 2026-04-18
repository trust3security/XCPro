package com.trust3.xcpro.common.units

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class UnitsRepositoryProfileScopeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun profileScopedUnits_areIsolatedAcrossProfiles() = runTest {
        val repository = UnitsRepository(context)
        val profileA = UnitsPreferences(
            altitude = AltitudeUnit.FEET,
            speed = SpeedUnit.KNOTS
        )
        val profileB = UnitsPreferences(
            altitude = AltitudeUnit.METERS,
            speed = SpeedUnit.MILES_PER_HOUR
        )

        repository.writeProfileUnits("profile-a", profileA)
        repository.writeProfileUnits("profile-b", profileB)

        repository.setActiveProfileId("profile-a")
        assertEquals(profileA, repository.unitsFlow.first())

        repository.setActiveProfileId("profile-b")
        assertEquals(
            profileB,
            repository.unitsFlow.first { candidate ->
                candidate.altitude == profileB.altitude && candidate.speed == profileB.speed
            }
        )
    }

    @Test
    fun blankProfileId_defaultsToCanonicalDefaultProfile() = runTest {
        val repository = UnitsRepository(context)
        val expected = UnitsPreferences(
            altitude = AltitudeUnit.FEET,
            speed = SpeedUnit.KNOTS
        )

        repository.setActiveProfileId("")
        repository.setUnits(expected)

        assertEquals(expected, repository.readProfileUnits("default-profile"))
    }

    @Test
    fun legacyDefaultAliases_resolveToCanonicalDefaultProfile() = runTest {
        val repository = UnitsRepository(context)
        val expected = UnitsPreferences(
            altitude = AltitudeUnit.FEET,
            speed = SpeedUnit.KNOTS
        )

        repository.writeProfileUnits("default", expected)

        assertEquals(expected, repository.readProfileUnits("default-profile"))
        assertEquals(expected, repository.readProfileUnits("__default_profile__"))
    }

    @Test
    fun clearProfile_removesScopedValues() = runTest {
        val repository = UnitsRepository(context)
        repository.writeProfileUnits(
            profileId = "pilot-c",
            preferences = UnitsPreferences(altitude = AltitudeUnit.FEET)
        )

        repository.clearProfile("pilot-c")

        assertEquals(UnitsPreferences(), repository.readProfileUnits("pilot-c"))
    }
}
