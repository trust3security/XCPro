package com.example.xcpro.map.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapWidgetLayoutUseCaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun loadLayout_defaultsIncludeSettingsShortcutPosition() {
        val repository = MapWidgetLayoutRepository(context)
        val useCase = MapWidgetLayoutUseCase(repository)

        val offsets = useCase.loadLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 2f, fontScale = 1f)
        )

        assertEquals(16f, offsets.settingsShortcut.x, 0.001f)
        assertEquals(280f, offsets.settingsShortcut.y, 0.001f)
    }

    @Test
    fun saveOffset_persistsSettingsShortcutOffset() {
        val repository = MapWidgetLayoutRepository(context)
        val useCase = MapWidgetLayoutUseCase(repository)
        val expected = OffsetPx(x = 222f, y = 333f)

        useCase.saveOffset(MapWidgetId.SETTINGS_SHORTCUT, expected)

        val offsets = useCase.loadLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 3f, fontScale = 1f)
        )

        assertEquals(expected.x, offsets.settingsShortcut.x, 0.001f)
        assertEquals(expected.y, offsets.settingsShortcut.y, 0.001f)
    }
}
