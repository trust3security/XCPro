package com.example.xcpro.map

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "qnh_preferences"
private val Context.qnhDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
private val KEY_QNH_HPA = doublePreferencesKey("qnh_hpa")

@Singleton
class QnhPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val qnhHpaFlow: Flow<Double?> = context.qnhDataStore.data.map { preferences ->
        preferences[KEY_QNH_HPA]
    }

    suspend fun setManualQnh(qnhHpa: Double) {
        context.qnhDataStore.edit { preferences ->
            preferences[KEY_QNH_HPA] = qnhHpa
        }
    }

    suspend fun clearManualQnh() {
        context.qnhDataStore.edit { preferences ->
            preferences.remove(KEY_QNH_HPA)
        }
    }
}
