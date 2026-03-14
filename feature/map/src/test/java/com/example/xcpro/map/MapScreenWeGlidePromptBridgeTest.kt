package com.example.xcpro.map

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.weglide.domain.EnqueueWeGlideUploadForFinalizedFlightUseCase
import com.example.xcpro.weglide.domain.EnqueueWeGlideUploadResult
import com.example.xcpro.weglide.domain.WeGlideAuthMode
import com.example.xcpro.weglide.domain.WeGlideFinalizedFlightUploadRequest
import com.example.xcpro.weglide.domain.WeGlidePostFlightPromptCoordinator
import com.example.xcpro.weglide.domain.WeGlidePostFlightUploadPrompt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import com.example.xcpro.weglide.notifications.WeGlidePostFlightPromptNotificationController
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenWeGlidePromptBridgeTest {

    @Test
    fun bind_mapsPendingPromptToUiState() = runTest {
        val coordinator = WeGlidePostFlightPromptCoordinator()
        val bridge = MapScreenWeGlidePromptBridge(
            promptCoordinator = coordinator,
            enqueueUseCase = null,
            notificationController = null
        )
        var observedFileName: String? = null

        bridge.bind(this) { promptUiState ->
            observedFileName = promptUiState?.fileName
        }
        coordinator.show(samplePrompt())
        advanceUntilIdle()

        assertEquals("finalized.igc", observedFileName)
        coroutineContext.cancelChildren()
    }

    @Test
    fun confirmCurrentPrompt_enqueuesDismissesAndEmitsToast() = runTest {
        val coordinator = WeGlidePostFlightPromptCoordinator()
        val enqueueUseCase = Mockito.mock(EnqueueWeGlideUploadForFinalizedFlightUseCase::class.java)
        val notificationController =
            Mockito.mock(WeGlidePostFlightPromptNotificationController::class.java)
        val prompt = samplePrompt(localFlightId = "flight-42")
        coordinator.show(prompt)
        Mockito.doReturn(
            EnqueueWeGlideUploadResult.Enqueued(
                localFlightId = "flight-42",
                localProfileId = "profile-a",
                authMode = WeGlideAuthMode.OAUTH
            )
        ).`when`(enqueueUseCase).invoke(prompt.request)
        val bridge = MapScreenWeGlidePromptBridge(
            promptCoordinator = coordinator,
            enqueueUseCase = enqueueUseCase,
            notificationController = notificationController
        )
        val emittedEffects = mutableListOf<MapUiEffect>()

        bridge.confirmCurrentPrompt { effect ->
            emittedEffects.add(effect)
        }

        assertEquals(
            listOf(MapUiEffect.ShowToast("Queued for WeGlide upload")),
            emittedEffects
        )
        assertNull(coordinator.pendingPrompt.value)
        Mockito.verify(notificationController).onPromptResolved("flight-42")
    }

    @Test
    fun dismissCurrentPrompt_clearsPromptAndResolvesNotification() {
        val coordinator = WeGlidePostFlightPromptCoordinator()
        val notificationController =
            Mockito.mock(WeGlidePostFlightPromptNotificationController::class.java)
        coordinator.show(samplePrompt(localFlightId = "flight-99"))
        val bridge = MapScreenWeGlidePromptBridge(
            promptCoordinator = coordinator,
            enqueueUseCase = null,
            notificationController = notificationController
        )

        bridge.dismissCurrentPrompt()

        assertNull(coordinator.pendingPrompt.value)
        Mockito.verify(notificationController).onPromptResolved("flight-99")
    }

    private fun samplePrompt(localFlightId: String = "flight-1"): WeGlidePostFlightUploadPrompt {
        return WeGlidePostFlightUploadPrompt(
            request = WeGlideFinalizedFlightUploadRequest(
                localFlightId = localFlightId,
                document = DocumentRef(uri = "content://igc/finalized.igc")
            ),
            profileId = "profile-a",
            profileName = "Pilot A",
            aircraftName = "Discus",
            fileName = "finalized.igc"
        )
    }
}
