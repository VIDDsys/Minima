package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.AppSettings
import org.junit.Test

class MaxStepsClampTest {
    @Test
    fun clampsLowAndHigh() {
        assertThat(AppSettings(maxAgentSteps = 0).clampedMaxAgentSteps).isEqualTo(1)
        assertThat(AppSettings(maxAgentSteps = 99).clampedMaxAgentSteps).isEqualTo(99)
        assertThat(AppSettings(maxAgentSteps = 12).clampedMaxAgentSteps).isEqualTo(12)
        assertThat(AppSettings(maxAgentSteps = 50_000).clampedMaxAgentSteps).isEqualTo(10_000)
    }
}
