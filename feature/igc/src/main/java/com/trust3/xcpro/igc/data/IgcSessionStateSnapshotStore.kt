package com.trust3.xcpro.igc.data

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.igc.domain.IgcSessionStateMachine
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface IgcSessionStateSnapshotStore {
    fun saveSnapshot(snapshot: IgcSessionStateMachine.Snapshot)
    fun loadSnapshot(): IgcSessionStateMachine.Snapshot?
    fun clearSnapshot()
}

private const val IGC_SESSION_PREFS = "igc_session_prefs"
private const val IGC_SESSION_STATE_SNAPSHOT_KEY = "session_state_snapshot"

@Singleton
class SharedPrefsIgcSessionStateSnapshotStore @Inject constructor(
    @ApplicationContext context: Context
) : IgcSessionStateSnapshotStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(IGC_SESSION_PREFS, Context.MODE_PRIVATE)

    private val gson = Gson()

    override fun saveSnapshot(snapshot: IgcSessionStateMachine.Snapshot) {
        val json = gson.toJson(snapshot)
        prefs.edit().putString(IGC_SESSION_STATE_SNAPSHOT_KEY, json).apply()
    }

    override fun loadSnapshot(): IgcSessionStateMachine.Snapshot? {
        val rawSnapshot = prefs.getString(IGC_SESSION_STATE_SNAPSHOT_KEY, null) ?: return null
        return runCatching { gson.fromJson(rawSnapshot, IgcSessionStateMachine.Snapshot::class.java) }
            .getOrNull()
    }

    override fun clearSnapshot() {
        prefs.edit().remove(IGC_SESSION_STATE_SNAPSHOT_KEY).apply()
    }
}
