package com.minima.chat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.feature.chat.ui.ChatScreen
import com.minima.chat.feature.chat.ui.SessionsScreen
import com.minima.chat.feature.settings.ui.SettingsScreen
import com.minima.chat.feature.settings.vm.SettingsViewModel
import com.minima.chat.ui.onboarding.OnboardingScreen

object Routes {
    const val Sessions = "sessions"
    const val Chat = "chat/{sessionId}"
    const val Settings = "settings"
    fun chat(sessionId: String) = "chat/$sessionId"
}

/**
 * Fresh NavHostController that does NOT restore a previous back stack.
 * Saveable NavController was restoring chat/{id} on cold start → blank chat flash.
 */
@Composable
private fun rememberFreshNavController(): NavHostController {
    val context = LocalContext.current
    return remember {
        NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
        }
    }
}

/**
 * Cold start always lands on Sessions with ZERO chat flash.
 * Onboarding is an overlay (not a start destination).
 * Deep-link openSessionId only navigates AFTER Sessions has painted two frames.
 * Splash stays up until [onSessionsFirstFrame] (first Sessions paint).
 */
@Composable
fun MinimaNavHost(
    modifier: Modifier = Modifier,
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    forceSessionsTick: Int = 0,
    onSessionsFirstFrame: () -> Unit = {},
) {
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val hydrated by settingsVm.hydrated.collectAsStateWithLifecycle()
    var onboardingDismissedLocally by remember { mutableStateOf(false) }
    var sessionsSettled by remember { mutableStateOf(false) }
    val colors = LocalMinimaColors.current

    // Create nav immediately — never wait on DataStore. Sessions is the only start dest.
    val nav = rememberFreshNavController()

    LaunchedEffect(Unit) {
        // Two frames: ensure Sessions is the only thing painted before splash dismiss / deep-link.
        withFrameNanos { }
        withFrameNanos { }
        sessionsSettled = true
        onSessionsFirstFrame()
    }

    LaunchedEffect(forceSessionsTick) {
        if (forceSessionsTick <= 0) return@LaunchedEffect
        // Launcher warm re-entry: pop Chat/Settings so user always sees the list.
        nav.popBackStack(Routes.Sessions, inclusive = false)
    }

    LaunchedEffect(openSessionId, sessionsSettled) {
        if (!sessionsSettled) return@LaunchedEffect
        val id = openSessionId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        // Only open chat from an explicit deep link / notification — never on plain launch.
        nav.navigate(Routes.chat(id)) {
            launchSingleTop = true
            popUpTo(Routes.Sessions) { inclusive = false }
        }
        onOpenSessionConsumed()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.bgLayout),
    ) {
        NavHost(
            navController = nav,
            startDestination = Routes.Sessions,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.Sessions) {
                SessionsScreen(
                    onOpenSession = { id ->
                        nav.navigate(Routes.chat(id)) {
                            popUpTo(Routes.Sessions) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(
                route = Routes.Chat,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                ChatScreen(
                    onBack = {
                        if (!nav.popBackStack()) {
                            nav.navigate(Routes.Sessions) { launchSingleTop = true }
                        }
                    },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onBack = {
                        settingsVm.flushAllPending()
                        if (!nav.popBackStack()) {
                            nav.navigate(Routes.Sessions) { launchSingleTop = true }
                        }
                    },
                    viewModel = settingsVm,
                )
            }
        }

        // Onboarding overlay AFTER Sessions is already the root — never a competing startDest.
        val showOnboarding = hydrated &&
            !settings.hasApiKey &&
            !settings.onboardingCompleted &&
            !onboardingDismissedLocally
        if (showOnboarding) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.bgLayout)
                    .zIndex(1f),
            ) {
                OnboardingScreen(
                    onOpenSettings = {
                        onboardingDismissedLocally = true
                        settingsVm.completeOnboarding()
                        nav.navigate(Routes.Settings) {
                            launchSingleTop = true
                        }
                    },
                    onSkip = {
                        onboardingDismissedLocally = true
                        settingsVm.completeOnboarding()
                    },
                )
            }
        }
    }
}
