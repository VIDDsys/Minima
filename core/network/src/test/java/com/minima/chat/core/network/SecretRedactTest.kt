package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.SecretRedact
import org.junit.Test

class SecretRedactTest {
    @Test
    fun redactsEmbeddedSkInParagraph() {
        val raw = "here is my key sk-abcdefghijklmnopqrstuv please keep"
        val out = SecretRedact.redact(raw)
        assertThat(out).doesNotContain("sk-abcdefghijklmnopqrstuv")
        assertThat(SecretRedact.containsSecret(raw)).isTrue()
        assertThat(SecretRedact.containsSecret(out)).isFalse()
    }

    @Test
    fun redactsGithubAndSlack() {
        val gh = "token ghp_abcdefghijklmnopqrstuvwxyz012345"
        assertThat(SecretRedact.redact(gh)).doesNotContain("ghp_abcdefghijklmnopqrstuvwxyz012345")
        val slack = "xoxb-1234567890-abcdefghij"
        assertThat(SecretRedact.redact(slack)).doesNotContain("xoxb-1234567890-abcdefghij")
    }

    @Test
    fun redactsJwtAndAws() {
        val jwt = "auth eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signaturepartxx"
        assertThat(SecretRedact.containsSecret(jwt)).isTrue()
        assertThat(SecretRedact.redact(jwt)).doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        val aws = "key AKIAIOSFODNN7EXAMPLE"
        assertThat(SecretRedact.redact(aws)).doesNotContain("AKIAIOSFODNN7EXAMPLE")
    }

    @Test
    fun redactsPemMarker() {
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIE"
        assertThat(SecretRedact.redact(pem)).contains("***")
        assertThat(SecretRedact.containsSecret(pem)).isTrue()
    }

    @Test
    fun leavesBenignText() {
        val t = "list the Documents folder please"
        assertThat(SecretRedact.redact(t)).isEqualTo(t)
        assertThat(SecretRedact.containsSecret(t)).isFalse()
    }

    @Test
    fun redactsAccessTokenAssignment() {
        val raw = """{"access_token":"supersecrettokenvalue99"}"""
        val out = SecretRedact.redact(raw)
        assertThat(out).doesNotContain("supersecrettokenvalue99")
    }
}
