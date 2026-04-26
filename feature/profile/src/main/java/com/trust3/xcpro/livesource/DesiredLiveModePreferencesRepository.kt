package com.trust3.xcpro.livesource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trust3.xcpro.common.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

private const val DESIRED_LIVE_MODE_DATASTORE = "desired_live_mode_preferences"
private val Context.desiredLiveModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DESIRED_LIVE_MODE_DATASTORE
)
private val KEY_DESIRED_LIVE_MODE = stringPreferencesKey("desired_live_mode")

@Singleton
class DesiredLiveModePreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    dispatcher: CoroutineDispatcher
) : DesiredLiveModePort {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val desiredLiveModeFlow = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            preferences[KEY_DESIRED_LIVE_MODE]
                ?.let(::parseDesiredLiveMode)
                ?: DesiredLiveMode.PHONE_ONLY
        }
        .distinctUntilChanged()
    private val initialDesiredLiveMode = runBlocking {
        desiredLiveModeFlow.first()
    }

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @DefaultDispatcher dispatcher: CoroutineDispatcher
    ) : this(context.desiredLiveModeDataStore, dispatcher)

    override val desiredLiveMode: StateFlow<DesiredLiveMode> = desiredLiveModeFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = initialDesiredLiveMode
        )

    suspend fun setDesiredLiveMode(mode: DesiredLiveMode) {
        dataStore.edit { preferences ->
            preferences[KEY_DESIRED_LIVE_MODE] = mode.name
        }
    }

    private fun parseDesiredLiveMode(raw: String): DesiredLiveMode =
        runCatching { DesiredLiveMode.valueOf(raw) }
            .getOrElse { DesiredLiveMode.PHONE_ONLY }
}
