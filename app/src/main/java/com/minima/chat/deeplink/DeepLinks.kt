package com.minima.chat.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.minima.chat.MainActivity

object DeepLinks {
    const val ACTION_OPEN_SESSION = "com.minima.chat.OPEN_SESSION"
    const val EXTRA_SESSION_ID = "session_id"
    const val SCHEME = "minima"
    const val HOST_CHAT = "chat"

    fun openSessionIntent(context: Context, sessionId: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SESSION
            putExtra(EXTRA_SESSION_ID, sessionId)
            data = Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST_CHAT)
                .appendPath(sessionId)
                .build()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun sessionIdFrom(intent: Intent?): String? {
        if (intent == null) return null
        intent.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val data = intent.data ?: return null
        if (data.scheme == SCHEME && data.host == HOST_CHAT) {
            return data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * MAIN/LAUNCHER (and sticky leftover task intents) must NOT open chat.
     * Only explicit OPEN_SESSION or VIEW with minima://chat/... may.
     */
    fun sessionIdForLaunch(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action
        val ok = action == ACTION_OPEN_SESSION || action == Intent.ACTION_VIEW
        if (!ok) return null
        return sessionIdFrom(intent)
    }
}
