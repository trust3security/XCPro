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
    private val defaultProfileId = "default-profile"
    private val secondProfileId = "pilot-b"

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
            profileId = defaultProfileId,
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

        useCase.saveOffset(defaultProfileId, MapWidgetId.SETTINGS_SHORTCUT, expected)

        val offsets = useCase.loadLayout(
            profileId = defaultProfileId,
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

        useCase.saveSizePx(defaultProfileId, MapWidgetId.SETTINGS_SHORTCUT, 145f)

        val offsets = useCase.loadLayout(
            profileId = defaultProfileId,
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
            profileId = defaultProfileId,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = density
        )

        val nearEdge = useCase.commitOffset(
            profileId = defaultProfileId,
            current = initial,
            widgetId = MapWidgetId.SETTINGS_SHORTCUT,
            offset = OffsetPx(x = 1000f, y = 1900f),
            screenWidthPx = 1080f,
            screenHeightPx = 1920f
        )

        val resized = useCase.commitSize(
            profileId = defaultProfileId,
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

    @Test
    fun loadLayout_isolatedBetweenProfiles() {
        val repository = MapWidgetLayoutRepository(context)
        val useCase = MapWidgetLayoutUseCase(repository)

        useCase.saveOffset(defaultProfileId, MapWidgetId.SIDE_HAMBURGER, OffsetPx(220f, 330f))
        useCase.saveOffset(secondProfileId, MapWidgetId.SIDE_HAMBURGER, OffsetPx(40f, 60f))

        val defaultOffsets = useCase.loadLayout(
            profileId = defaultProfileId,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 2f, fontScale = 1f)
        )
        val secondOffsets = useCase.loadLayout(
            profileId = secondProfileId,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 2f, fontScale = 1f)
        )

        assertEquals(220f, defaultOffsets.sideHamburger.x, 0.001f)
        assertEquals(330f, defaultOffsets.sideHamburger.y, 0.001f)
        assertEquals(40f, secondOffsets.sideHamburger.x, 0.001f)
        assertEquals(60f, secondOffsets.sideHamburger.y, 0.001f)
    }

    @Test
    fun loadLayout_legacyFallbackAppliesOnlyToDefaultProfile() {
        context.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("side_hamburger_x", 123f)
            .putFloat("side_hamburger_y", 456f)
            .commit()
        val repository = MapWidgetLayoutRepository(context)
        val useCase = MapWidgetLayoutUseCase(repository)

        val defaultOffsets = useCase.loadLayout(
            profileId = defaultProfileId,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 2f, fontScale = 1f)
        )
        val secondOffsets = useCase.loadLayout(
            profileId = secondProfileId,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            density = DensityScale(density = 2f, fontScale = 1f)
        )

        assertEquals(123f, defaultOffsets.sideHamburger.x, 0.001f)
        assertEquals(456f, defaultOffsets.sideHamburger.y, 0.001f)
        assertEquals(16f, secondOffsets.sideHamburger.x, 0.001f)
        assertEquals(32f, secondOffsets.sideHamburger.y, 0.001f)
    }
}
