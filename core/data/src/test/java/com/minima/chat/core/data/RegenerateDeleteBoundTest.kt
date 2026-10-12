package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.util.RegeneratePlan
import org.junit.Test

/**
 * Documents that regenerate should use deleteFrom(plan.afterCreatedAt) —
 * inclusive of the user turn — then re-append via runAgent.
 */
class RegenerateDeleteBoundTest {
    @Test
    fun planTimestampIsUserCreatedAt_inclusiveDeleteBound() {
        val user = Message(
            id = "u1",
            sessionId = "s",
            role = MessageRole.User,
            content = "hello",
            createdAt = 1000L,
            kind = MessageKind.Text,
        )
        val asst = Message(
            id = "a1",
            sessionId = "s",
            role = MessageRole.Assistant,
            content = "hi",
            createdAt = 1001L,
            kind = MessageKind.Text,
        )
        val plan = RegeneratePlan.from(listOf(user, asst))!!
        assertThat(plan.afterCreatedAt).isEqualTo(1000L)
        assertThat(plan.userText).isEqualTo("hello")
        // Inclusive: createdAt >= 1000 removes user+assistant; exclusive > 999 would also remove user
        // but races if another message shares the same ms. Prefer deleteFrom(afterCreatedAt).
        assertThat(plan.afterCreatedAt).isEqualTo(user.createdAt)
    }
}
