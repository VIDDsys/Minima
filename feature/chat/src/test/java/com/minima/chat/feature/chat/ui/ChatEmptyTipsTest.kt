package com.minima.chat.feature.chat.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatEmptyTipsTest {
    @Test
    fun agentTipsMentionToolsAndDocs() {
        val tips = ChatEmptyTips.suggestions(agentEnabled = true)
        assertThat(tips).hasSize(4)
        assertThat(tips.joinToString()).contains("工具")
        assertThat(tips.joinToString()).contains("Documents")
        assertThat(tips.joinToString()).contains("搜索")
        assertThat(tips.joinToString()).contains("summarize-doc")
    }

    @Test
    fun nonAgentTipsAreSimple() {
        val tips = ChatEmptyTips.suggestions(agentEnabled = false)
        assertThat(tips).hasSize(3)
        assertThat(tips.joinToString().lowercase()).doesNotContain("documents")
        assertThat(tips.first()).contains("招呼")
    }
}
