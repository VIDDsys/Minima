package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.FriendlyHttp
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class FriendlyHttpTest {
    @Test
    fun extractsNestedErrorMessage() {
        val msg = FriendlyHttp.message(
            401,
            """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}""",
        )
        assertThat(msg).contains("401")
        assertThat(msg).contains("Incorrect API key provided")
    }

    @Test
    fun extractsStringError() {
        val msg = FriendlyHttp.message(400, """{"error":"model not found"}""")
        assertThat(msg).contains("400")
        assertThat(msg).contains("model not found")
    }

    @Test
    fun rateLimit_hasFriendlyBase() {
        val msg = FriendlyHttp.message(429, """{"error":{"message":"slow down"}}""")
        assertThat(msg).contains("429")
        assertThat(msg).contains("slow down")
    }

    @Test
    fun redactsKeyInBody() {
        val msg = FriendlyHttp.message(400, """{"error":{"message":"bad sk-abcdefghijklmnopqrstuvwxyz123456"}}""")
        assertThat(msg).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz123456")
        assertThat(msg).contains("…")
    }

    @Test
    fun extractApiMessage_nullOnGarbage() {
        assertThat(FriendlyHttp.extractApiMessage("not json")).isNull()
        assertThat(FriendlyHttp.extractApiMessage("")).isNull()
    }

    @Test
    fun fromThrowable_unknownHost() {
        assertThat(FriendlyHttp.fromThrowable(UnknownHostException("api.example.com")))
            .contains("resolve host")
    }

    @Test
    fun fromThrowable_timeout() {
        assertThat(FriendlyHttp.fromThrowable(SocketTimeoutException("timeout")))
            .contains("timed out")
    }

    @Test
    fun fromThrowable_connect() {
        assertThat(FriendlyHttp.fromThrowable(ConnectException("refused")))
            .contains("Cannot connect")
    }

    @Test
    fun fromThrowable_ssl() {
        assertThat(FriendlyHttp.fromThrowable(SSLHandshakeException("pkix")))
            .contains("TLS")
    }

    @Test
    fun fromThrowable_wrappedCause() {
        val wrapped = RuntimeException("outer", UnknownHostException("x"))
        assertThat(FriendlyHttp.fromThrowable(wrapped)).contains("resolve host")
    }
}
