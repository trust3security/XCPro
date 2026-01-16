package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySmoothingProfileTest {

    @Test
    fun smoothProfileMatchesDefaults() {
        val config = DisplaySmoothingProfile.SMOOTH.config
        assertEquals(DisplayPoseSmoothingConfig.DEFAULT_POS_SMOOTH_MS, config.posSmoothMs, 1e-6)
        assertEquals(DisplayPoseSmoothingConfig.DEFAULT_HEADING_SMOOTH_MS, config.headingSmoothMs, 1e-6)
        assertEquals(DisplayPoseSmoothingConfig.DEFAULT_DEAD_RECKON_LIMIT_MS, config.deadReckonLimitMs)
        assertEquals(DisplayPoseSmoothingConfig.DEFAULT_STALE_FIX_TIMEOUT_MS, config.staleFixTimeoutMs)
    }

    @Test
    fun responsiveProfileIsFasterThanSmooth() {
        val smooth = DisplaySmoothingProfile.SMOOTH.config
        val responsive = DisplaySmoothingProfile.RESPONSIVE.config

        assertEquals(150.0, responsive.posSmoothMs, 1e-6)
        assertEquals(120.0, responsive.headingSmoothMs, 1e-6)
        assertEquals(250L, responsive.deadReckonLimitMs)

        assertEquals(DisplayPoseSmoothingConfig.DEFAULT_STALE_FIX_TIMEOUT_MS, responsive.staleFixTimeoutMs)

        assertEquals(true, responsive.posSmoothMs < smooth.posSmoothMs)
        assertEquals(true, responsive.headingSmoothMs < smooth.headingSmoothMs)
        assertEquals(true, responsive.deadReckonLimitMs < smooth.deadReckonLimitMs)
    }
}
