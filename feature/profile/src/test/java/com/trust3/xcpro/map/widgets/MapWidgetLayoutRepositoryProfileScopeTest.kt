package com.trust3.xcpro.map.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.core.common.geometry.OffsetPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapWidgetLayoutRepositoryProfileScopeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = MapWidgetLayoutRepository(context)

    @Before
    fun setUp() {
        context.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun deleteProfileLayout_clearsOnlyTargetProfileWidgetState() {
        val defaultProfileId = "default-profile"
        val targetProfileId = "pilot-target"
        val widgetId = MapWidgetId.SETTINGS_SHORTCUT

        repository.saveOffset(defaultProfileId, widgetId, OffsetPx(16f, 280f))
        repository.saveSizePx(defaultProfileId, widgetId, 112f)
        repository.saveOffset(targetProfileId, widgetId, OffsetPx(222f, 333f))
        repository.saveSizePx(targetProfileId, widgetId, 145f)

        repository.deleteProfileLayout(targetProfileId)

        val defaultOffset = repository.readOffset(defaultProfileId, widgetId)

        assertEquals(16f, defaultOffset!!.x, 0.001f)
        assertEquals(280f, defaultOffset.y, 0.001f)
        assertEquals(112f, repository.readSizePx(defaultProfileId, widgetId) ?: 0f, 0.001f)
        assertNull(repository.readOffset(targetProfileId, widgetId))
        assertNull(repository.readSizePx(targetProfileId, widgetId))
    }
}
