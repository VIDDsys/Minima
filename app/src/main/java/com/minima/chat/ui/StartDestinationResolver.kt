package com.minima.chat.ui

/**
 * Pure helpers for first-open onboarding overlay (not a Nav start destination).
 *
 * Cold start always uses [Routes.Sessions] as NavHost startDestination.
 * Onboarding is an overlay when prefs say: hydrated + no key + not completed.
 */
object StartDestinationResolver {
    /** @return true when the first-open overlay should cover Sessions. */
    fun shouldShowOnboardingOverlay(
        hydrated: Boolean,
        hasApiKey: Boolean,
        onboardingCompleted: Boolean,
        dismissedLocally: Boolean = false,
    ): Boolean {
        if (!hydrated || dismissedLocally) return false
        return !hasApiKey && !onboardingCompleted
    }

    /**
     * Legacy resolve kept for tests / callers — always Sessions once hydrated.
     * Never returns a chat route (cold start must not flash chat).
     */
    fun resolve(
        hydrated: Boolean,
        hasApiKey: Boolean,
        onboardingCompleted: Boolean,
    ): String? {
        if (!hydrated) return null
        // Nav start is always Sessions; onboarding is overlay-only.
        return Routes.Sessions
    }

    fun afterOnboardingDismissed(): String = Routes.Sessions

    /** Cold-start NavHost start destination — never chat, never onboarding route. */
    fun coldStartDestination(): String = Routes.Sessions
}
