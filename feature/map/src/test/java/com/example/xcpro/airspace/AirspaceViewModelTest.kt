package com.example.xcpro.airspace

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AirspaceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun toggleFile_disablingLastFile_preservesClassStates() = runTest {
        val useCase: AirspaceUseCase = mock()
        val document = DocumentRef(uri = "file:///tmp/a.txt", displayName = "a.txt")
        val initialClassStates = mutableMapOf("D" to true, "C" to false)

        whenever(useCase.loadAirspaceFiles()).thenReturn(
            Pair(listOf(document), mutableMapOf("a.txt" to true))
        )
        whenever(useCase.loadSelectedClasses()).thenReturn(initialClassStates)
        whenever(useCase.parseClasses(eq(listOf(document)))).thenReturn(listOf("C", "D"))
        whenever(useCase.countZones(document)).thenReturn(5)
        whenever(useCase.saveAirspaceFiles(any(), any())).thenReturn(Unit)
        whenever(useCase.saveSelectedClasses(any())).thenReturn(Unit)

        val viewModel = AirspaceViewModel(useCase)
        advanceUntilIdle()

        assertEquals(initialClassStates, viewModel.uiState.value.classStates)
        assertEquals(1, viewModel.uiState.value.enabledFiles.size)

        viewModel.toggleFile("a.txt")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.enabledFiles.isEmpty())
        assertEquals(initialClassStates, viewModel.uiState.value.classStates)
    }
}
