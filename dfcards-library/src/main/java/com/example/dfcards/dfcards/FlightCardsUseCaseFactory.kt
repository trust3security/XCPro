package com.example.dfcards.dfcards

import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class FlightCardsUseCaseFactory @Inject constructor(
    private val clock: Clock
) {
    fun create(scope: CoroutineScope): FlightCardsUseCase =
        FlightCardsUseCase(scope, clock)
}
