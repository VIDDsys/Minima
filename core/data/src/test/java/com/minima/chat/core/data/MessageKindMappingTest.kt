package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import org.junit.Test

class MessageKindMappingTest {
    @Test
    fun toolStatus_roundTrip() {
        fun parse(s: String?) = when (s) {
            "running" -> ToolMessageStatus.Running
            "ok" -> ToolMessageStatus.Ok
            "error" -> ToolMessageStatus.Error
            else -> null
        }
        assertThat(parse("running")).isEqualTo(ToolMessageStatus.Running)
        assertThat(parse("ok")).isEqualTo(ToolMessageStatus.Ok)
        assertThat(parse("error")).isEqualTo(ToolMessageStatus.Error)
        assertThat(parse(null)).isNull()
    }

    @Test
    fun kind_fromDb() {
        fun kind(s: String) = if (s == "tool") MessageKind.Tool else MessageKind.Text
        assertThat(kind("tool")).isEqualTo(MessageKind.Tool)
        assertThat(kind("text")).isEqualTo(MessageKind.Text)
    }

    @Test
    fun toolRole_exists() {
        assertThat(MessageRole.Tool.name).isEqualTo("Tool")
    }
}
