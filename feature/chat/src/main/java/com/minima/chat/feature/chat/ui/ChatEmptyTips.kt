package com.minima.chat.feature.chat.ui

/** Quiet starter chips for an empty chat — fill the composer only. */
object ChatEmptyTips {
    fun suggestions(agentEnabled: Boolean): List<String> =
        if (agentEnabled) {
            listOf(
                "你能用哪些工具？",
                "列出 Documents 里的文件",
                "在笔记里搜索：",
                "用 summarize-doc 技能总结：",
            )
        } else {
            listOf(
                "用一句话打个招呼",
                "帮我起草一条短信",
                "简单解释一下：",
            )
        }
}
