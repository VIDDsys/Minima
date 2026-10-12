package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.export.ExportedMessage
import com.minima.chat.core.data.export.ExportedSession
import com.minima.chat.core.data.export.SessionExportBundle
import com.minima.chat.core.data.export.SessionExportCodec
import org.junit.Test

class SessionExportCodecTest {
    @Test
    fun roundTrip() {
        val bundle = SessionExportBundle(
            sessions = listOf(
                ExportedSession(
                    id = "s1",
                    title = "Hello",
                    createdAt = 1,
                    updatedAt = 2,
                    messages = listOf(
                        ExportedMessage(
                            id = "m1",
                            role = "User",
                            content = "hi",
                            createdAt = 1,
                        ),
                    ),
                ),
            ),
        )
        val json = SessionExportCodec.encode(bundle)
        assertThat(json).contains("Hello")
        val decoded = SessionExportCodec.decode(json).getOrThrow()
        assertThat(decoded.sessions).hasSize(1)
        assertThat(decoded.sessions[0].messages[0].content).isEqualTo("hi")
    }

    @Test
    fun badJsonFails() {
        assertThat(SessionExportCodec.decode("not-json").isFailure).isTrue()
    }

    @Test
    fun emptyFails() {
        assertThat(SessionExportCodec.decode("").isFailure).isTrue()
    }
}
