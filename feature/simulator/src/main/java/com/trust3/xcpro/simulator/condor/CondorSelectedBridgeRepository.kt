package com.trust3.xcpro.simulator.condor

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.simulator.CondorBridgeRef
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PersistedCondorBridge(
    val stableId: String,
    val displayNameSnapshot: String?
)

internal interface CondorSelectedBridgeStorage {
    fun read(): PersistedCondorBridge?

    fun write(value: PersistedCondorBridge)

    fun clear()
}

@Singleton
internal class CondorSelectedBridgeRepository private constructor(
    private val storage: CondorSelectedBridgeStorage
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesCondorSelectedBridgeStorage(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    )

    internal constructor(
        storage: CondorSelectedBridgeStorage,
        unused: Unit = Unit
    ) : this(storage)

    private val mutableSelectedBridge = MutableStateFlow(storage.read())

    val selectedBridge: StateFlow<PersistedCondorBridge?> = mutableSelectedBridge.asStateFlow()

    suspend fun setSelectedBridge(bridge: CondorBridgeRef) {
        val persisted = PersistedCondorBridge(
            stableId = bridge.stableId,
            displayNameSnapshot = bridge.displayName
        )
        storage.write(persisted)
        mutableSelectedBridge.value = persisted
    }

    suspend fun clearSelection() {
        storage.clear()
        mutableSelectedBridge.value = null
    }

    private class SharedPreferencesCondorSelectedBridgeStorage(
        private val sharedPreferences: SharedPreferences
    ) : CondorSelectedBridgeStorage {
        override fun read(): PersistedCondorBridge? {
            val stableId = sharedPreferences.getString(KEY_SELECTED_BRIDGE_ID, null)
                ?: return null
            return PersistedCondorBridge(
                stableId = stableId,
                displayNameSnapshot = sharedPreferences.getString(KEY_SELECTED_BRIDGE_NAME, null)
            )
        }

        override fun write(value: PersistedCondorBridge) {
            sharedPreferences.edit()
                .putString(KEY_SELECTED_BRIDGE_ID, value.stableId)
                .putString(KEY_SELECTED_BRIDGE_NAME, value.displayNameSnapshot)
                .apply()
        }

        override fun clear() {
            sharedPreferences.edit()
                .remove(KEY_SELECTED_BRIDGE_ID)
                .remove(KEY_SELECTED_BRIDGE_NAME)
                .apply()
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "condor_bridge_settings"
        private const val KEY_SELECTED_BRIDGE_ID = "selected_bridge_id"
        private const val KEY_SELECTED_BRIDGE_NAME = "selected_bridge_name"
    }
}
