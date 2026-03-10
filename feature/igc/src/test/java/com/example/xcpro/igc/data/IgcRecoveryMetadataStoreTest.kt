package com.example.xcpro.igc.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgcRecoveryMetadataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun saveLoadAndClear_roundTripsMetadataBySessionId() {
        val store = SharedPrefsIgcRecoveryMetadataStore(context)
        val metadata = IgcRecoveryMetadata(
            manufacturerId = "XCP",
            sessionSerial = "000321",
            sessionStartWallTimeMs = 1_741_483_200_000L,
            firstValidFixWallTimeMs = 1_741_483_212_000L,
            signatureProfile = IgcSecuritySignatureProfile.NONE
        )

        store.clearMetadata(321L)
        store.clearMetadata(322L)
        store.saveMetadata(sessionId = 321L, metadata = metadata)

        assertEquals(metadata, store.loadMetadata(321L))
        assertNull(store.loadMetadata(322L))

        store.clearMetadata(321L)

        assertNull(store.loadMetadata(321L))
    }
}
