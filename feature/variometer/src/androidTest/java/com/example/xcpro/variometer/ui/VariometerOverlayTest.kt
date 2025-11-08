package com.example.xcpro.variometer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VariometerOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<VariometerTestActivity>()

    @Test
    fun dragCommitsOffsetWhenEditModeEnabled() {
        var committedOffset: Offset? = null
        setOverlayContent(
            startInEditMode = true,
            onOffsetChange = { committedOffset = it }
        )

        composeRule.onNodeWithTag(VariometerTestTags.Overlay)
            .performTouchInput {
                down(center)
                moveBy(Offset(80f, 60f))
                up()
            }

        composeRule.waitForIdle()
        val result = committedOffset
        assertTrue("Offset should update after drag", result != null && result.x > 0f && result.y > 0f)
    }

    @Test
    fun resizeHandleCommitsSize() {
        var committedSize = 0f
        setOverlayContent(
            startInEditMode = true,
            onSizeChange = { committedSize = it }
        )

        composeRule.onNodeWithTag(VariometerTestTags.ResizeHandle)
            .performTouchInput {
                down(center)
                moveBy(Offset(30f, 30f))
                up()
            }

        composeRule.waitForIdle()
        assertTrue("Size should be persisted after resize", committedSize > 0f)
    }

    @Test
    fun longPressInvokesCallback() {
        var longPressTriggered = false
        setOverlayContent(
            startInEditMode = false,
            onEnterEditMode = { longPressTriggered = true }
        )

        composeRule.onNodeWithTag(VariometerTestTags.Overlay)
            .performTouchInput { longClick() }

        composeRule.waitForIdle()
        assertTrue("Long press should trigger callback", longPressTriggered)
    }

    private fun setOverlayContent(
        startInEditMode: Boolean,
        onOffsetChange: (Offset) -> Unit = {},
        onSizeChange: (Float) -> Unit = {},
        onEnterEditMode: () -> Unit = {},
        onExitEditMode: () -> Unit = {}
    ) {
        composeRule.setContent {
            var isEditing by remember { mutableStateOf(startInEditMode) }
            VariometerOverlay(
                needleValue = 0f,
                displayValue = 0f,
                offset = Offset(10f, 10f),
                sizePx = 150f,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f,
                minSizePx = 60f,
                maxSizePx = 200f,
                isEditMode = isEditing,
                onOffsetChange = onOffsetChange,
                onSizeChange = onSizeChange,
                onEnterEditMode = {
                    isEditing = true
                    onEnterEditMode()
                },
                onExitEditMode = {
                    isEditing = false
                    onExitEditMode()
                },
                onBoundsChanged = {}
            )
        }
        composeRule.waitForIdle()
    }
}
