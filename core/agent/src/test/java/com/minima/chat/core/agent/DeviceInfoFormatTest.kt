package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure format contract for device_info tool output lines. */
class DeviceInfoFormatTest {
    @Test
    fun expectedKeysPresentInSample() {
        val sample = """
            app=com.minima.chat
            version=2.5.0
            manufacturer=Google
            model=Pixel
            sdk=34
            release=14
            locale=zh-CN
            timezone=Asia/Shanghai
        """.trimIndent()
        for (key in listOf("app=", "version=", "manufacturer=", "model=", "sdk=", "locale=", "timezone=")) {
            assertThat(sample).contains(key)
        }
        assertThat(sample.lines()).hasSize(8)
    }
}
