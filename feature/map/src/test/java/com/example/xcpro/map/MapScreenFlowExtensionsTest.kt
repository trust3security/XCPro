package com.example.xcpro.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenFlowExtensionsTest {

    @Test
    fun sampleWithImmediateFirst_emitsLeadingEventPerBurst() = runTest {
        val source = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val collected = mutableListOf<Int>()
        val sampled = source.sampleWithImmediateFirst(windowMs = 100L, scope = backgroundScope)

        val job = launch {
            sampled.collect { collected += it }
        }
        runCurrent()

        source.emit(1)
        runCurrent()
        source.emit(2)
        source.emit(3)
        runCurrent()

        advanceTimeBy(120L)
        runCurrent()
        source.emit(4)
        runCurrent()

        assertEquals(listOf(1, 4), collected)
        job.cancel()
    }
}
