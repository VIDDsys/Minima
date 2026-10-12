package com.minima.chat.core.data

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.data.util.EditLastUserPlan
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNull
import org.junit.Test

class EditLastUserPlanTest {
    private fun msg(
        id: String,
        role: MessageRole,
        content: String,
        at: Long,
        kind: MessageKind = MessageKind.Text,
        toolStatus: ToolMessageStatus? = null,
    ) = Message(
        id = id,
        sessionId = "s",
        role = role,
        content = content,
        createdAt = at,
        kind = kind,
        toolStatus = toolStatus,
        toolName = if (kind == MessageKind.Tool) "t" else null,
    )

    @Test
    fun findsLastUserWhenClean() {
        val plan = EditLastUserPlan.from(
            listOf(
                msg("u1", MessageRole.User, "first", 1),
                msg("a1", MessageRole.Assistant, "ok", 2),
                msg("u2", MessageRole.User, "second", 3),
                msg("a2", MessageRole.Assistant, "done", 4),
            ),
            isStreaming = false,
        )!!
        assertThat(plan.userText).isEqualTo("second")
        assertThat(plan.userMessageId).isEqualTo("u2")
        assertThat(plan.fromCreatedAt).isEqualTo(3L)
    }

    @Test
    fun nullWhenStreaming() {
        assertNull(
            EditLastUserPlan.from(
                listOf(msg("u1", MessageRole.User, "x", 1)),
                isStreaming = true,
            ),
        )
    }

    @Test
    fun nullWhenToolStillRunning() {
        assertNull(
            EditLastUserPlan.from(
                listOf(
                    msg("u1", MessageRole.User, "go", 1),
                    msg("t1", MessageRole.Tool, "tool", 2, MessageKind.Tool, ToolMessageStatus.Running),
                ),
                isStreaming = false,
            ),
        )
    }

    @Test
    fun allowsCompletedToolThenAssistant() {
        val plan = EditLastUserPlan.from(
            listOf(
                msg("u1", MessageRole.User, "go", 1),
                msg("t1", MessageRole.Tool, "tool", 2, MessageKind.Tool, ToolMessageStatus.Ok),
                msg("a1", MessageRole.Assistant, "done", 3),
            ),
            isStreaming = false,
        )!!
        assertThat(plan.userText).isEqualTo("go")
    }

    @Test
    fun restoresVisionAttachmentAndStripsPlaceholder() {
        val plan = EditLastUserPlan.from(
            listOf(
                Message(
                    id = "u1",
                    sessionId = "s",
                    role = MessageRole.User,
                    content = "(image)",
                    createdAt = 1,
                    kind = MessageKind.Text,
                    attachmentPath = "attachments/z.jpg",
                    attachmentMime = "image/jpeg",
                ),
                msg("a1", MessageRole.Assistant, "ok", 2),
            ),
            isStreaming = false,
        )!!
        assertThat(plan.userText).isEmpty()
        assertThat(plan.attachmentPath).isEqualTo("attachments/z.jpg")
        assertThat(plan.attachmentMime).isEqualTo("image/jpeg")
    }
}
