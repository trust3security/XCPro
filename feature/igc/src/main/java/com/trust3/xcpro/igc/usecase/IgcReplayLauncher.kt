package com.trust3.xcpro.igc.usecase

import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.replay.IgcReplayUseCase
import javax.inject.Inject
import javax.inject.Singleton

interface IgcReplayLauncher {
    suspend fun loadDocument(document: DocumentRef)
    fun play()
}

@Singleton
class IgcReplayUseCaseLauncher @Inject constructor(
    private val replayUseCase: IgcReplayUseCase
) : IgcReplayLauncher {
    override suspend fun loadDocument(document: DocumentRef) {
        replayUseCase.loadDocument(document)
    }

    override fun play() {
        replayUseCase.play()
    }
}
