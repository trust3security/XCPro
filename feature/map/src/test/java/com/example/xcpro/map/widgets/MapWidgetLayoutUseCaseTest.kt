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
        assertEquals(180f, offsets.sideHamburgerSizePx, 0.001f)
        assertEquals(112f, offsets.settingsShortcutSizePx, 0.001f)
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

    @Test
    fun saveSize_persistsSettingsShortcutSize() {
        val repository = MapWidgetLayoutRepository(context)
        val useCase = MapWidgetLayoutUseCase(repository)

        useCase.saveSizePx(MapWidgetId.SETTINGS_SHORTCUT, 145f)

        val offsets = useCase.loadLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 3f, fontScale = 1f)
        )

        assertEquals(145f, offsets.settingsShortcutSizePx, 0.001f)
    }

    @Test
    fun commitSize_clampsAndRepositionsSettingsShortcut() {
        val repository = MapWidgetLayoutRepository(context)
        val useCase = MapWidgetLayoutUseCase(repository)
        val density = DensityScale(density = 2f, fontScale = 1f)

        val initial = useCase.loadLayout(
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = density
        )

        val nearEdge = useCase.commitOffset(
            current = initial,
            widgetId = MapWidgetId.SETTINGS_SHORTCUT,
            offset = OffsetPx(x = 1000f, y = 1900f),
            screenWidthPx = 1080f,
            screenHeightPx = 1920f
        )

        val resized = useCase.commitSize(
            current = nearEdge,
            widgetId = MapWidgetId.SETTINGS_SHORTCUT,
            sizePx = 10_000f,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = density
        )

        assertEquals(192f, resized.settingsShortcutSizePx, 0.001f)
        assertEquals(888f, resized.settingsShortcut.x, 0.001f)
        assertEquals(1728f, resized.settingsShortcut.y, 0.001f)
    }
}
