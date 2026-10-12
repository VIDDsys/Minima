package com.minima.chat.core.data

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.util.RegeneratePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegeneratePlanTest {
    private fun msg(
        id: String,
        role: MessageRole,
        content: String,
        at: Long,
        kind: MessageKind = MessageKind.Text,
    ) = Message(
        id = id,
        sessionId = "s",
        role = role,
        content = content,
        createdAt = at,
        kind = kind,
    )

    @Test
    fun findsLastUserAssistantPair() {
        val messages = listOf(
            msg("u1", MessageRole.User, "hi", 1),
            msg("a1", MessageRole.Assistant, "hello", 2),
            msg("u2", MessageRole.User, "again", 3),
            msg("a2", MessageRole.Assistant, "world", 4),
        )
        val plan = RegeneratePlan.from(messages)!!
        assertEquals("again", plan.userText)
        assertEquals(3L, plan.afterCreatedAt)
        assertEquals("u2", plan.userMessageId)
    }

    @Test
    fun skipsToolCardsWhenFindingUser() {
        val messages = listOf(
            msg("u1", MessageRole.User, "list files", 1),
            msg("t1", MessageRole.Tool, "tool", 2, MessageKind.Tool),
            msg("a1", MessageRole.Assistant, "done", 3),
        )
        val plan = RegeneratePlan.from(messages)!!
        assertEquals("list files", plan.userText)
        assertEquals(1L, plan.afterCreatedAt)
    }

    @Test
    fun emptyOrAssistantOnlyReturnsNull() {
        assertNull(RegeneratePlan.from(emptyList()))
        assertNull(
            RegeneratePlan.from(
                listOf(msg("a1", MessageRole.Assistant, "orphan", 1)),
            ),
        )
    }

    @Test
    fun findsUserWhenToolsFollowAssistant_dbOrder() {
        val messages = listOf(
            msg("u1", MessageRole.User, "use tools", 1),
            msg("a1", MessageRole.Assistant, "done", 2),
            msg("t1", MessageRole.Tool, "tool", 3, MessageKind.Tool),
            msg("t2", MessageRole.Tool, "tool", 4, MessageKind.Tool),
        )
        val plan = RegeneratePlan.from(messages)!!
        assertEquals("use tools", plan.userText)
        assertEquals(1L, plan.afterCreatedAt)
        assertEquals("u1", plan.userMessageId)
    }

    @Test
    fun regenerateAfterStoppedNote_targetsThatTurn() {
        val messages = listOf(
            msg("u0", MessageRole.User, "earlier", 1),
            msg("a0", MessageRole.Assistant, "ok", 2),
            msg("u1", MessageRole.User, "retry me", 3),
            msg("a1", MessageRole.Assistant, "partial\n\n_(Stopped)_", 4),
            msg("t1", MessageRole.Tool, "tool", 5, MessageKind.Tool),
        )
        val plan = RegeneratePlan.from(messages)!!
        assertEquals("retry me", plan.userText)
        assertEquals(3L, plan.afterCreatedAt)
    }

    @Test
    fun carriesVisionAttachmentOnPlan() {
        val user = Message(
            id = "u1",
            sessionId = "s",
            role = MessageRole.User,
            content = "(image)",
            createdAt = 1,
            kind = MessageKind.Text,
            attachmentPath = "attachments/x.jpg",
            attachmentMime = "image/jpeg",
        )
        val asst = msg("a1", MessageRole.Assistant, "a cat", 2)
        val plan = RegeneratePlan.from(listOf(user, asst))!!
        assertEquals("(image)", plan.userText)
        assertEquals("attachments/x.jpg", plan.attachmentPath)
        assertEquals("image/jpeg", plan.attachmentMime)
    }

    @Test
    fun findsImageOnlyUserWithBlankContent() {
        val user = Message(
            id = "u1",
            sessionId = "s",
            role = MessageRole.User,
            content = "",
            createdAt = 1,
            kind = MessageKind.Text,
            attachmentPath = "attachments/y.jpg",
        )
        val asst = msg("a1", MessageRole.Assistant, "ok", 2)
        val plan = RegeneratePlan.from(listOf(user, asst))!!
        assertEquals("u1", plan.userMessageId)
        assertEquals("attachments/y.jpg", plan.attachmentPath)
    }
}
