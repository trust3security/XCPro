package com.example.xcpro.map

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class FlightDataManagerFactory @Inject constructor() {
    fun create(scope: CoroutineScope): FlightDataManager =
        FlightDataManager(scope)
}
