package com.trust3.xcpro.weglide.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.trust3.xcpro.weglide.domain.WeGlidePostFlightUploadPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeGlidePostFlightPromptNotificationController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var isMapVisible: Boolean = false
    private var pendingPrompt: WeGlidePostFlightUploadPrompt? = null

    fun onPromptPublished(prompt: WeGlidePostFlightUploadPrompt) {
        pendingPrompt = prompt
        syncNotification()
    }

    fun onPromptResolved(localFlightId: String? = null) {
        val current = pendingPrompt
        if (current != null && (localFlightId == null || current.request.localFlightId == localFlightId)) {
            pendingPrompt = null
        }
        cancelNotification()
    }

    fun onMapVisibilityChanged(isVisible: Boolean) {
        isMapVisible = isVisible
        syncNotification()
    }

    private fun syncNotification() {
        val prompt = pendingPrompt
        if (prompt == null || isMapVisible || !canPostNotifications()) {
            cancelNotification()
            return
        }
        createNotificationChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Upload to WeGlide")
            .setContentText(buildContentText(prompt))
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildContentText(prompt)))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        buildLaunchPendingIntent()?.let(builder::setContentIntent)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun buildContentText(prompt: WeGlidePostFlightUploadPrompt): String {
        return buildString {
            append(prompt.fileName)
            append(" is ready to upload via ")
            append(prompt.aircraftName)
            prompt.profileName?.takeIf { it.isNotBlank() }?.let { profileName ->
                append(" (")
                append(profileName)
                append(')')
            }
        }
    }

    private fun buildLaunchPendingIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "WeGlide uploads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Post-flight WeGlide upload prompts"
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "weglide_post_flight_prompt"
        private const val NOTIFICATION_ID = 7001
    }
}
