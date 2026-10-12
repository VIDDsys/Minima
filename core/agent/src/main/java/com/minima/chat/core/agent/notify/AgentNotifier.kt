package com.minima.chat.core.agent.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.minima.chat.core.agent.service.AgentForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        ensureChannel()
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Agent tasks",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Long-running agent tool loops"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    fun notifyCompleted(title: String, body: String, sessionId: String? = null) {
        post(NOTIF_ID, title, body, android.R.drawable.stat_sys_download_done, sessionId)
    }

    fun notifyFailed(title: String, body: String, sessionId: String? = null) {
        post(NOTIF_ID + 1, title, body, android.R.drawable.stat_notify_error, sessionId)
    }

    private fun post(id: Int, title: String, body: String, icon: Int, sessionId: String?) {
        val contentPi = sessionContentIntent(sessionId)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun sessionContentIntent(sessionId: String?): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            if (!sessionId.isNullOrBlank()) {
                action = AgentForegroundService.ACTION_OPEN_SESSION
                putExtra(AgentForegroundService.EXTRA_SESSION_ID, sessionId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        return PendingIntent.getActivity(
            context,
            sessionId?.hashCode() ?: 0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "minima_agent"
        const val NOTIF_ID = 42
    }
}
