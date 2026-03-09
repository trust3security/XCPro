package com.example.xcpro.variometer.layout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.core.common.geometry.OffsetPx
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VariometerLayoutProfileScopeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun profileSwitch_keepsIndependentVariometerLayouts() {
        val repository = VariometerWidgetRepository(context)
        val useCase = VariometerLayoutUseCase(repository)

        useCase.setActiveProfileId("default-profile")
        useCase.ensureLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            defaultSizePx = 150f,
            minSizePx = 60f,
            maxSizePx = 1080f
        )
        useCase.onOffsetCommitted(
            offset = OffsetPx(200f, 300f),
            screenWidthPx = 1080f,
            screenHeightPx = 1920f
        )

        useCase.setActiveProfileId("pilot-b")
        useCase.ensureLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            defaultSizePx = 150f,
            minSizePx = 60f,
            maxSizePx = 1080f
        )
        val secondProfileState = useCase.state.value

        assertEquals(465f, secondProfileState.offset.x, 0.001f)
        assertEquals(885f, secondProfileState.offset.y, 0.001f)

        useCase.setActiveProfileId("default-profile")
        useCase.ensureLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            defaultSizePx = 150f,
            minSizePx = 60f,
            maxSizePx = 1080f
        )
        val defaultProfileState = useCase.state.value
        assertEquals(200f, defaultProfileState.offset.x, 0.001f)
        assertEquals(300f, defaultProfileState.offset.y, 0.001f)
    }

    @Test
    fun legacyFallback_onlyAppliesToDefaultProfile() {
        context.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("uilevo_x", 111f)
            .putFloat("uilevo_y", 222f)
            .putFloat("uilevo_size", 140f)
            .commit()

        val repository = VariometerWidgetRepository(context)
        val useCase = VariometerLayoutUseCase(repository)

        useCase.setActiveProfileId("default-profile")
        useCase.ensureLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            defaultSizePx = 150f,
            minSizePx = 60f,
            maxSizePx = 1080f
        )
        val defaultProfileState = useCase.state.value
        assertEquals(111f, defaultProfileState.offset.x, 0.001f)
        assertEquals(222f, defaultProfileState.offset.y, 0.001f)
        assertEquals(140f, defaultProfileState.sizePx, 0.001f)

        useCase.setActiveProfileId("pilot-b")
        useCase.ensureLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            defaultSizePx = 150f,
            minSizePx = 60f,
            maxSizePx = 1080f
        )
        val secondProfileState = useCase.state.value
        assertEquals(465f, secondProfileState.offset.x, 0.001f)
        assertEquals(885f, secondProfileState.offset.y, 0.001f)
        assertEquals(150f, secondProfileState.sizePx, 0.001f)
    }
}
