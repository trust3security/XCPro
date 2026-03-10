package com.example.xcpro.profiles

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AircraftProfileFileNamesTest {

    @Test
    fun buildExportFileName_singleProfile_usesAircraftProfileConvention() {
        val profile = UserProfile(
            id = "hang-1",
            name = "Weekend Wing",
            aircraftType = AircraftType.HANG_GLIDER,
            aircraftModel = "Moyes Litespeed RS"
        )

        val fileName = AircraftProfileFileNames.buildExportFileName(
            profile = profile,
            nowWallMs = Instant.parse("2026-03-10T00:00:00Z").toEpochMilli()
        )

        assertEquals(
            "xcpro-aircraft-profile-hang-glider-moyes-litespeed-rs-2026-03-10.json",
            fileName
        )
    }

    @Test
    fun buildExportFileName_allProfiles_usesBackupConvention() {
        val fileName = AircraftProfileFileNames.buildExportFileName(
            profile = null,
            nowWallMs = Instant.parse("2026-03-10T00:00:00Z").toEpochMilli()
        )

        assertEquals("xcpro-aircraft-profiles-2026-03-10.json", fileName)
    }
}
