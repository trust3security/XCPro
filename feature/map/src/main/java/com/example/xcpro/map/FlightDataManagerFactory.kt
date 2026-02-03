package com.example.xcpro.map

import android.content.Context
import com.example.dfcards.CardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class FlightDataManagerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardPreferences: CardPreferences
) {
    fun create(scope: CoroutineScope): FlightDataManager =
        FlightDataManager(context, cardPreferences, scope)
}
