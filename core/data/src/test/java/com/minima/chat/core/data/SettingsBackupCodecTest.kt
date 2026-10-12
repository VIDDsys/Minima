package com.minima.chat.core.data

import com.minima.chat.core.data.export.SettingsBackupCodec
import com.minima.chat.core.data.model.AppSettings
import com.minima.chat.core.data.model.AppearanceSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {
    @Test
    fun exportWithoutKeyOmitsSecret() {
        val s = AppSettings(
            baseUrl = "https://example.com/v1",
            modelId = "demo",
            apiKey = "sk-secret-should-not-appear",
            appearance = AppearanceSetting.Dark,
            certPins = "",
        )
        val json = SettingsBackupCodec.encode(SettingsBackupCodec.fromSettings(s, includeApiKey = false))
        assertFalse(json.contains("sk-secret"))
        assertTrue(json.contains("includeApiKey"))
        assertFalse(SettingsBackupCodec.decode(json).getOrThrow().includeApiKey)
        val decoded = SettingsBackupCodec.decode(json).getOrThrow()
        assertNull(decoded.apiKey)
        assertEquals("https://example.com/v1", decoded.baseUrl)
    }

    @Test
    fun exportWithKeyIncludesSecretAndFlag() {
        val s = AppSettings(apiKey = "sk-export-me-please-123456")
        val bundle = SettingsBackupCodec.fromSettings(s, includeApiKey = true)
        assertTrue(bundle.includeApiKey)
        assertEquals("sk-export-me-please-123456", bundle.apiKey)
        val round = SettingsBackupCodec.decode(SettingsBackupCodec.encode(bundle)).getOrThrow()
        assertEquals("sk-export-me-please-123456", round.apiKey)
    }

    @Test
    fun toPartialRespectsIncludeFlag() {
        val withKey = SettingsBackupCodec.fromSettings(
            AppSettings(baseUrl = "https://a", apiKey = "sk-abc"),
            includeApiKey = true,
        )
        val partial = SettingsBackupCodec.toPartialSettings(withKey)
        assertEquals("sk-abc", partial.apiKey)
        val without = SettingsBackupCodec.toPartialSettings(
            SettingsBackupCodec.fromSettings(AppSettings(apiKey = "sk-abc"), includeApiKey = false),
        )
        assertEquals("", without.apiKey)
    }

    @Test
    fun samplingRoundTrips() {
        val s = AppSettings(temperature = 0.7, maxTokens = 2048, topP = 0.9)
        val bundle = SettingsBackupCodec.fromSettings(s, includeApiKey = false)
        assertEquals(0.7, bundle.temperature!!, 1e-9)
        assertEquals(2048, bundle.maxTokens)
        assertEquals(0.9, bundle.topP!!, 1e-9)
        val partial = SettingsBackupCodec.toPartialSettings(bundle)
        assertEquals(0.7, partial.temperature!!, 1e-9)
        assertEquals(2048, partial.maxTokens)
        assertEquals(0.9, partial.topP!!, 1e-9)
    }

    @Test
    fun samplingOmittedWhenNull() {
        val json = SettingsBackupCodec.encode(
            SettingsBackupCodec.fromSettings(AppSettings(), includeApiKey = false),
        )
        val decoded = SettingsBackupCodec.decode(json).getOrThrow()
        assertNull(decoded.temperature)
        assertNull(decoded.maxTokens)
        assertNull(decoded.topP)
    }

    @Test
    fun agentTimingRoundTrips() {
        val s = AppSettings(agentWallClockSec = 240, agentToolTimeoutSec = 90)
        val bundle = SettingsBackupCodec.fromSettings(s, includeApiKey = false)
        assertEquals(240, bundle.agentWallClockSec)
        assertEquals(90, bundle.agentToolTimeoutSec)
        val partial = SettingsBackupCodec.toPartialSettings(bundle)
        assertEquals(240, partial.agentWallClockSec)
        assertEquals(90, partial.agentToolTimeoutSec)
    }

    @Test
    fun agentTiming_toolCappedToWallOnImport() {
        val bundle = SettingsBackupCodec.fromSettings(
            AppSettings(agentWallClockSec = 40, agentToolTimeoutSec = 200),
            includeApiKey = false,
        )
        // fromSettings uses clamped getters → tool already ≤ wall
        assertEquals(40, bundle.agentWallClockSec)
        assertEquals(40, bundle.agentToolTimeoutSec)
    }
}
