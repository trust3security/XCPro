package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class QnhPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: QnhPreferencesRepository

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        repository = QnhPreferencesRepository(context)
        repository.clearManualQnh()
    }

    @Test
    fun setManualQnh_persistsValue() = runTest {
        val expected = 1013.25

        repository.setManualQnh(expected)
        val stored = repository.qnhHpaFlow.first()

        assertEquals(expected, stored)
    }

    @Test
    fun clearManualQnh_erasesStoredValue() = runTest {
        repository.setManualQnh(999.0)
        repository.clearManualQnh()

        val stored = repository.qnhHpaFlow.first()
        assertNull(stored)
    }
}
