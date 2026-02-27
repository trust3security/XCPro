package com.example.xcpro

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class XCProApplication : Application(), Configuration.Provider {

    private companion object {
        private const val TAG = "XCProApplication"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder()
            // Defensive fallback: WorkManager may query config before Hilt field injection.
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            } else {
                Log.w(TAG, "HiltWorkerFactory unavailable during startup; using default WorkerFactory")
            }
            return builder.build()
        }
}
