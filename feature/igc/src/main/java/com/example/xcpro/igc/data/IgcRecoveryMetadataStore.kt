package com.example.xcpro.igc.data

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface IgcRecoveryMetadataStore {
    fun saveMetadata(sessionId: Long, metadata: IgcRecoveryMetadata)
    fun loadMetadata(sessionId: Long): IgcRecoveryMetadata?
    fun clearMetadata(sessionId: Long)
}

object NoopIgcRecoveryMetadataStore : IgcRecoveryMetadataStore {
    override fun saveMetadata(sessionId: Long, metadata: IgcRecoveryMetadata) = Unit

    override fun loadMetadata(sessionId: Long): IgcRecoveryMetadata? = null

    override fun clearMetadata(sessionId: Long) = Unit
}

private const val IGC_RECOVERY_METADATA_PREFS = "igc_recovery_metadata_prefs"
private const val IGC_RECOVERY_METADATA_KEY_PREFIX = "session_"

@Singleton
class SharedPrefsIgcRecoveryMetadataStore @Inject constructor(
    @ApplicationContext context: Context
) : IgcRecoveryMetadataStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(IGC_RECOVERY_METADATA_PREFS, Context.MODE_PRIVATE)

    private val gson = Gson()

    override fun saveMetadata(sessionId: Long, metadata: IgcRecoveryMetadata) {
        prefs.edit()
            .putString(key(sessionId), gson.toJson(metadata))
            .apply()
    }

    override fun loadMetadata(sessionId: Long): IgcRecoveryMetadata? {
        val rawMetadata = prefs.getString(key(sessionId), null) ?: return null
        return runCatching { gson.fromJson(rawMetadata, IgcRecoveryMetadata::class.java) }
            .getOrNull()
    }

    override fun clearMetadata(sessionId: Long) {
        prefs.edit().remove(key(sessionId)).apply()
    }

    private fun key(sessionId: Long): String = "$IGC_RECOVERY_METADATA_KEY_PREFIX$sessionId"
}
