package com.trust3.xcpro.map.ui.effects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapComposeEffectsTest {

    @Test
    fun shouldRunComposeDisplayPoseLoop_returnsFalse_whenRenderFrameSyncIsEnabled() {
        assertFalse(shouldRunComposeDisplayPoseLoop(useRenderFrameSync = true))
    }

    @Test
    fun shouldRunComposeDisplayPoseLoop_returnsTrue_whenRenderFrameSyncIsDisabled() {
        assertTrue(shouldRunComposeDisplayPoseLoop(useRenderFrameSync = false))
    }
}
