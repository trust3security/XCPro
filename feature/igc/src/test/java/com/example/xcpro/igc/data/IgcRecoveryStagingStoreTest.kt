package com.example.xcpro.igc.data

import android.content.Context
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IgcRecoveryStagingStoreTest {

    @Test
    fun write_read_delete_roundTripsStagedPayload() {
        val filesDir = Files.createTempDirectory("igc-staging-store").toFile()
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        val store = IgcRecoveryStagingStore(context)

        val writeSucceeded = store.write(
            sessionId = 77L,
            payload = "AXCP000077\nHFDTEDATE:090326,01".toByteArray()
        )

        assertTrue(writeSucceeded)
        assertTrue(store.exists(77L))
        assertEquals(
            listOf("AXCP000077", "HFDTEDATE:090326,01"),
            store.readLines(77L)
        )

        store.delete(77L)

        assertFalse(store.exists(77L))
        assertEquals(null, store.readLines(77L))
    }
}
