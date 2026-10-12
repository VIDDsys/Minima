package com.minima.chat.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartDestinationResolverTest {

    @Test
    fun notHydrated_resolveNull_butColdStartIsSessions() {
        assertThat(
            StartDestinationResolver.resolve(
                hydrated = false,
                hasApiKey = false,
                onboardingCompleted = false,
            ),
        ).isNull()
        assertThat(StartDestinationResolver.coldStartDestination()).isEqualTo(Routes.Sessions)
    }

    @Test
    fun hydrated_alwaysSessions_neverChat() {
        assertThat(
            StartDestinationResolver.resolve(
                hydrated = true,
                hasApiKey = false,
                onboardingCompleted = false,
            ),
        ).isEqualTo(Routes.Sessions)
        assertThat(
            StartDestinationResolver.resolve(
                hydrated = true,
                hasApiKey = true,
                onboardingCompleted = false,
            ),
        ).isEqualTo(Routes.Sessions)
        assertThat(StartDestinationResolver.coldStartDestination()).doesNotContain("chat")
    }

    @Test
    fun onboardingOverlay_onlyWhenNoKeyAndNotCompleted() {
        assertThat(
            StartDestinationResolver.shouldShowOnboardingOverlay(
                hydrated = true,
                hasApiKey = false,
                onboardingCompleted = false,
            ),
        ).isTrue()
        assertThat(
            StartDestinationResolver.shouldShowOnboardingOverlay(
                hydrated = true,
                hasApiKey = true,
                onboardingCompleted = false,
            ),
        ).isFalse()
        assertThat(
            StartDestinationResolver.shouldShowOnboardingOverlay(
                hydrated = true,
                hasApiKey = false,
                onboardingCompleted = true,
            ),
        ).isFalse()
        assertThat(
            StartDestinationResolver.shouldShowOnboardingOverlay(
                hydrated = false,
                hasApiKey = false,
                onboardingCompleted = false,
            ),
        ).isFalse()
    }

    @Test
    fun afterDismiss_isSessions() {
        assertThat(StartDestinationResolver.afterOnboardingDismissed()).isEqualTo(Routes.Sessions)
    }

    @Test
    fun skipPath_cannotStickOnOnboardingRoute() {
        val afterSkip = StartDestinationResolver.resolve(
            hydrated = true,
            hasApiKey = false,
            onboardingCompleted = true,
        )
        assertThat(afterSkip).isEqualTo(Routes.Sessions)
        assertThat(afterSkip).isNotEqualTo("onboarding")
    }
}
