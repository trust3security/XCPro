package com.trust3.xcpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trust3.xcpro.R
import com.trust3.xcpro.vario.VarioServiceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VarioForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "vario_foreground_channel"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "VarioForegroundService"
        private const val ACTION_ENSURE_RUNNING = "com.trust3.xcpro.service.action.ENSURE_RUNNING"

        fun start(context: Context) {
            val intent = Intent(context, VarioForegroundService::class.java).apply {
                action = ACTION_ENSURE_RUNNING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Start only if location permission is granted. Returns true when start was requested.
         */
        fun startIfPermitted(context: Context): Boolean {
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "Skipping vario service start; location permission missing")
                return false
            }
            start(context)
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VarioForegroundService::class.java))
        }
    }

    @Inject lateinit var manager: VarioServiceManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_ENSURE_RUNNING
        if (action == ACTION_ENSURE_RUNNING) {
            serviceScope.launch {
                val sensorsStarted = runCatching { manager.start(serviceScope) }
                    .onFailure { error ->
                        if (error is CancellationException) {
                            throw error
                        }
                        Log.e(TAG, "Failed to start sensor pipeline from foreground service", error)
                    }
                    .getOrDefault(false)
                if (!sensorsStarted) {
                    Log.w(
                        TAG,
                        "Foreground service active but waiting for GPS permission before publishing data"
                    )
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        manager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Variometer running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Variometer",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
