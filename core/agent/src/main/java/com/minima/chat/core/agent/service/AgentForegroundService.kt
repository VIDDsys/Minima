package com.minima.chat.core.agent.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.minima.chat.core.agent.di.AgentRunEntryPoint
import com.minima.chat.core.agent.notify.AgentNotifier
import dagger.hilt.android.EntryPointAccessors

/**
 * Keeps long agent runs alive while the user backgrounds Minima.
 * Started/stopped by ChatViewModel; Stop action cancels the **same** run via
 * [com.minima.chat.core.agent.run.AgentRunController] (and a package broadcast).
 * Content tap opens the related session when EXTRA_SESSION_ID is set.
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Primary: same handler ChatViewModel binds for in-chat Stop.
                runCatching {
                    EntryPointAccessors.fromApplication(
                        applicationContext,
                        AgentRunEntryPoint::class.java,
                    ).agentRunController().requestStop()
                }
                // Secondary: broadcast for any registered receivers.
                sendBroadcast(Intent(ACTION_STOP).setPackage(packageName))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Minima agent"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "Working…"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val notification = buildNotification(title, text, sessionId)
                startFg(notification)
                return START_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Minima agent"
                val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Working…"
                val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
                val notification = buildNotification(title, text, sessionId)
                startFg(notification)
                return START_STICKY
            }
        }
    }

    private fun startFg(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(title: String, text: String, sessionId: String?): Notification {
        val stopIntent = Intent(this, AgentForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPi = contentPendingIntent(sessionId)
        val builder = NotificationCompat.Builder(this, AgentNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopPi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (contentPi != null) {
            builder.setContentIntent(contentPi)
        }
        return builder.build()
    }

    private fun contentPendingIntent(sessionId: String?): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        if (!sessionId.isNullOrBlank()) {
            launch.action = ACTION_OPEN_SESSION
            launch.putExtra(EXTRA_SESSION_ID, sessionId)
            launch.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_STOP = "com.minima.chat.agent.STOP"
        const val ACTION_UPDATE = "com.minima.chat.agent.UPDATE"
        const val ACTION_OPEN_SESSION = "com.minima.chat.OPEN_SESSION"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SESSION_ID = "session_id"
        const val NOTIF_ID = 1001

        fun start(context: Context, title: String, text: String, sessionId: String? = null) {
            val i = Intent(context, AgentForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                if (!sessionId.isNullOrBlank()) putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun update(context: Context, title: String, text: String, sessionId: String? = null) {
            val i = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                if (!sessionId.isNullOrBlank()) putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
