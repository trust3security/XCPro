package com.trust3.xcpro.map

import com.trust3.xcpro.weglide.domain.EnqueueSkipReason
import com.trust3.xcpro.weglide.domain.EnqueueWeGlideUploadForFinalizedFlightUseCase
import com.trust3.xcpro.weglide.domain.EnqueueWeGlideUploadResult
import com.trust3.xcpro.weglide.domain.WeGlidePostFlightPromptCoordinator
import com.trust3.xcpro.weglide.notifications.WeGlidePostFlightPromptNotificationController
import com.trust3.xcpro.weglide.ui.WeGlideUploadPromptUiState
import com.trust3.xcpro.weglide.ui.toUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MapScreenWeGlidePromptBridge @Inject constructor(
    private val promptCoordinator: WeGlidePostFlightPromptCoordinator?,
    private val enqueueUseCase: EnqueueWeGlideUploadForFinalizedFlightUseCase?,
    private val notificationController: WeGlidePostFlightPromptNotificationController?
) {
    fun bind(
        scope: CoroutineScope,
        onPromptChanged: (WeGlideUploadPromptUiState?) -> Unit
    ) {
        val coordinator = promptCoordinator ?: return
        scope.launch {
            coordinator.pendingPrompt.collect { prompt ->
                onPromptChanged(prompt?.toUiState())
            }
        }
    }

    fun onMapVisibilityChanged(isVisible: Boolean) {
        notificationController?.onMapVisibilityChanged(isVisible)
    }

    suspend fun confirmCurrentPrompt(
        emitUiEffect: suspend (MapUiEffect) -> Unit
    ) {
        val coordinator = promptCoordinator ?: return
        val enqueue = enqueueUseCase ?: return
        val prompt = coordinator.pendingPrompt.value ?: return
        val message = when (val result = enqueue(prompt.request)) {
            is EnqueueWeGlideUploadResult.Enqueued -> "Queued for WeGlide upload"
            is EnqueueWeGlideUploadResult.AlreadyQueued -> "WeGlide upload already queued"
            is EnqueueWeGlideUploadResult.Skipped -> result.reason.toPromptUserMessage()
        }
        coordinator.dismiss(prompt.request.localFlightId)
        notificationController?.onPromptResolved(prompt.request.localFlightId)
        emitUiEffect(MapUiEffect.ShowToast(message))
    }

    fun dismissCurrentPrompt() {
        val localFlightId = promptCoordinator?.pendingPrompt?.value?.request?.localFlightId
        promptCoordinator?.dismiss()
        notificationController?.onPromptResolved(localFlightId)
    }
}

private fun EnqueueSkipReason.toPromptUserMessage(): String = when (this) {
    EnqueueSkipReason.ACCOUNT_DISCONNECTED -> "WeGlide account disconnected"
    EnqueueSkipReason.AUTO_UPLOAD_DISABLED -> "WeGlide post-flight upload disabled"
    EnqueueSkipReason.MAPPING_MISSING -> "No WeGlide aircraft selected for this profile"
    EnqueueSkipReason.DOCUMENT_UNREADABLE -> "Unable to read finalized IGC file"
    EnqueueSkipReason.DUPLICATE_FLIGHT -> "WeGlide upload skipped: duplicate flight"
}
