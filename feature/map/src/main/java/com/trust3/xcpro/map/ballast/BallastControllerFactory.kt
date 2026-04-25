package com.trust3.xcpro.map.ballast

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.glider.GliderRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class BallastControllerFactory @Inject constructor(
    private val gliderRepository: GliderRepository,
    private val externalFlightSettingsReadPort: ExternalFlightSettingsReadPort,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    fun create(scope: CoroutineScope): BallastController =
        BallastController(
            repository = gliderRepository,
            externalFlightSettingsReadPort = externalFlightSettingsReadPort,
            scope = scope,
            dispatcher = defaultDispatcher
        )
}
