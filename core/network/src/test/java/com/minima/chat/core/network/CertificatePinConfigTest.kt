package com.minima.chat.core.network

import com.minima.chat.core.network.api.CertificatePinConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificatePinConfigTest {
    @Test
    fun blankMeansOff() {
        assertTrue(CertificatePinConfig.parse(null).isEmpty())
        assertTrue(CertificatePinConfig.parse("").isEmpty())
        assertTrue(CertificatePinConfig.parse("   \n# comment").isEmpty())
        assertFalse(CertificatePinConfig.isEnabled(""))
        assertNull(CertificatePinConfig.buildPinner(""))
    }

    @Test
    fun parsesHostPipeSha() {
        val raw = """
            # comment
            api.openai.com|sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            other.example.com|BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
        """.trimIndent()
        val pins = CertificatePinConfig.parse(raw)
        assertEquals(2, pins.size)
        assertEquals("api.openai.com", pins[0].host)
        assertTrue(pins[0].pin.startsWith("sha256/"))
        assertTrue(pins[1].pin.startsWith("sha256/"))
        assertTrue(CertificatePinConfig.isEnabled(raw))
    }

    @Test
    fun skipsJunkLines() {
        assertTrue(CertificatePinConfig.parse("not-a-pin").isEmpty())
        assertTrue(CertificatePinConfig.parse("host|ab").isEmpty())
    }
}
