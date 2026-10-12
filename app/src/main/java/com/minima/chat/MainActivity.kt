package com.minima.chat

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minima.chat.core.data.model.AppearanceSetting
import com.minima.chat.core.ui.theme.AppearanceMode
import com.minima.chat.core.ui.theme.MinimaTheme
import com.minima.chat.deeplink.DeepLinks
import com.minima.chat.ui.MinimaNavHost
import com.minima.chat.ui.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingSessionId by mutableStateOf<String?>(null)
    /** Bumped on MAIN/LAUNCHER so NavHost pops Chat → Sessions (no sticky chat). */
    private var forceSessionsTick by mutableIntStateOf(0)
    /** Keep SplashScreen until Sessions has painted — hides task-snapshot of last Chat. */
    private var keepSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplash }
        // Drop view hierarchy restore — Compose nav must not revive a chat back-stack frame.
        super.onCreate(null)
        // Prevent Android task-snapshot of last Chat screen flashing on cold start (~1s).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        applyLaunchIntent(intent, isColdStart = true)
        enableEdgeToEdge()
        setContent {
            val themeVm: ThemeViewModel = hiltViewModel()
            val appearance by themeVm.appearance.collectAsStateWithLifecycle()
            MinimaTheme(
                appearance = when (appearance) {
                    AppearanceSetting.System -> AppearanceMode.System
                    AppearanceSetting.Light -> AppearanceMode.Light
                    AppearanceSetting.Dark -> AppearanceMode.Dark
                },
            ) {
                MinimaNavHost(
                    modifier = Modifier.fillMaxSize(),
                    openSessionId = pendingSessionId,
                    onOpenSessionConsumed = { pendingSessionId = null },
                    forceSessionsTick = forceSessionsTick,
                    onSessionsFirstFrame = { keepSplash = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent, isColdStart = false)
    }

    private fun applyLaunchIntent(intent: Intent?, isColdStart: Boolean) {
        if (intent == null) return
        if (isLauncherMain(intent)) {
            // Always land on Sessions for icon / cold MAIN — never reopen last Chat.
            pendingSessionId = null
            if (!isColdStart) {
                forceSessionsTick += 1
            }
            return
        }
        pendingSessionId = DeepLinks.sessionIdForLaunch(intent)
    }

    companion object {
        fun isLauncherMain(intent: Intent): Boolean {
            val action = intent.action
            if (action != Intent.ACTION_MAIN) return false
            val cats = intent.categories ?: return true
            return cats.contains(Intent.CATEGORY_LAUNCHER) || cats.isEmpty()
        }
    }
}
