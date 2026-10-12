package com.minima.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Static review of network_security_config.xml — must stay aligned with CleartextPolicy.
 */
class NetworkSecurityConfigTest {
    private fun readNsc(): String {
        val candidates = listOf(
            File("app/src/main/res/xml/network_security_config.xml"),
            File("/workspace/minima-chat/app/src/main/res/xml/network_security_config.xml"),
            File("../app/src/main/res/xml/network_security_config.xml"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun cleartextOffByDefault_onlyLoopbackDomains() {
        val xml = readNsc()
        assertThat(xml).contains("cleartextTrafficPermitted=\"false\"")
        assertThat(xml).contains("<domain includeSubdomains=\"false\">localhost</domain>")
        assertThat(xml).contains("<domain includeSubdomains=\"false\">127.0.0.1</domain>")
        assertThat(xml).contains("<domain includeSubdomains=\"false\">10.0.2.2</domain>")
        assertThat(xml).contains("<domain includeSubdomains=\"false\">::1</domain>")
        // Must not open cleartext to the world
        assertThat(xml).doesNotContain("cleartextTrafficPermitted=\"true\" />")
        // domain-config is the only place true appears
        val trueCount = Regex("""cleartextTrafficPermitted="true"""").findAll(xml).count()
        assertThat(trueCount).isEqualTo(1)
    }

    @Test
    fun manifestPinsNscAndDisablesUsesCleartext() {
        val candidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("/workspace/minima-chat/app/src/main/AndroidManifest.xml"),
        )
        val man = candidates.first { it.exists() }.readText()
        assertThat(man).contains("android:networkSecurityConfig=\"@xml/network_security_config\"")
        assertThat(man).contains("android:usesCleartextTraffic=\"false\"")
    }
}
