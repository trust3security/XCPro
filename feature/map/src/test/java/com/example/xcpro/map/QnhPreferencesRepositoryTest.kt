package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun setUp() {
        repository = QnhPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
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

    @Test
    fun autoQnhEnabled_defaultsFalse_andPersists() = runTest {
        val initial = repository.autoQnhEnabledFlow.first()
        assertFalse(initial)

        repository.setAutoQnhEnabled(true)
        assertTrue(repository.autoQnhEnabledFlow.first())

        repository.setAutoQnhEnabled(false)
        assertFalse(repository.autoQnhEnabledFlow.first())
    }
}
