package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertTrue

class LocalToolAllowlistTest {
    @Test
    fun `on-device models are offered public_storage`() {
        assertTrue("public_storage" in LOCAL_TOOL_ALLOWLIST)
        assertTrue("execute_shell_command" in LOCAL_TOOL_ALLOWLIST)
    }
}
