package com.example.xcpro.xcprov1.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HawkGaugeAppearanceTest {

    @Test
    fun defaultGaugeSizeMatchesInstrumentSpec() {
        assertEquals("Default gauge diameter should be 260dp", 260f, HawkGaugeDefaultSize.value, 0.01f)
    }

    @Test
    fun paletteMaintainsContrastInLightTheme() {
        verifyContrast(lightColorScheme(), "light")
    }

    @Test
    fun paletteMaintainsContrastInDarkTheme() {
        verifyContrast(darkColorScheme(), "dark")
    }

    private fun verifyContrast(scheme: ColorScheme, label: String) {
        val surface = if (scheme.surface.luminance() > 0.45f) Color(0xFF101317) else scheme.surface
        val accents = listOf(
            HawkGaugePalette.ActualNeedle,
            HawkGaugePalette.PotentialNeedle,
            HawkGaugePalette.Confidence,
            HawkGaugePalette.Climb,
            HawkGaugePalette.ClimbStrong,
            HawkGaugePalette.Slip
        )
        accents.forEach { accent ->
            val ratio = contrastRatio(surface, accent)
            assertTrue("Contrast between surface and ${accent.toHex()} is $ratio in $label theme", ratio >= 2.4)
        }
    }

    private fun contrastRatio(background: Color, foreground: Color): Double {
        val back = relativeLuminance(background)
        val fore = relativeLuminance(foreground)
        return (max(back, fore) + 0.05) / (min(back, fore) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)

    private fun channel(component: Float): Double =
        if (component <= 0.03928f) {
            (component / 12.92f).toDouble()
        } else {
            ((component + 0.055f) / 1.055f).toDouble().pow(2.4)
        }

    private fun Color.toHex(): String =
        "#${toArgb().and(0xFFFFFF).toString(16).padStart(6, '0')}"
}
