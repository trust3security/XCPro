package com.example.xcpro.igc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.igc.ui.IGC_FILES_LABEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgcFilesNavigationLabelInstrumentedTest {

    @Test
    fun igcFilesNavigationLabel_matchesPhase5Contract() {
        assertEquals("IGC Files", IGC_FILES_LABEL)
        assertFalse(IGC_FILES_LABEL.contains("Replay", ignoreCase = true))
    }
}
