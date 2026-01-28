package com.example.xcpro.map.ballast

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.glider.GliderRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class BallastControllerFactory @Inject constructor(
    private val gliderRepository: GliderRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    fun create(scope: CoroutineScope): BallastController =
        BallastController(
            repository = gliderRepository,
            scope = scope,
            dispatcher = defaultDispatcher
        )
}
