package com.minima.chat.core.network.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ReachabilityResult(
    val ok: Boolean,
    val message: String,
)

/**
 * Pure classification — unit-tested without network.
 * Any HTTP response (even 401/404) means the host answered.
 * Messages are always secret-redacted (keys in URL / IO text).
 */
object ReachabilityClassify {
    fun fromBlankUrl(): ReachabilityResult =
        ReachabilityResult(false, "Set a Base URL first")

    fun fromInvalidUrl(detail: String): ReachabilityResult =
        ReachabilityResult(false, "Invalid URL: ${SecretRedact.redact(detail).take(100)}")

    fun fromHttp(code: Int, host: String): ReachabilityResult {
        val safeHost = SecretRedact.redact(host).ifBlank { "host" }
        return when {
            code in 200..499 -> ReachabilityResult(
                true,
                "Reachable · HTTP $code · $safeHost",
            )
            else -> ReachabilityResult(false, "HTTP $code from $safeHost")
        }
    }

    fun fromIo(detail: String): ReachabilityResult =
        ReachabilityResult(false, SecretRedact.redact(detail).take(120).ifBlank { "Network error" })

    fun probeUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        return "$root/models"
    }
}

/**
 * Lightweight Base URL probe: GET `{base}/models` with a short timeout.
 * Does **not** send Authorization / API key headers.
 * 401/403/404 still count as reachable (server spoke).
 */
@Singleton
class EndpointReachability @Inject constructor(
    private val httpHolder: OkHttpClientHolder,
) {
    suspend fun check(baseUrl: String): ReachabilityResult = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return@withContext ReachabilityClassify.fromBlankUrl()
        CleartextPolicy.rejectDisallowedCleartext(trimmed)?.let {
            return@withContext ReachabilityResult(false, it)
        }
        val url = ReachabilityClassify.probeUrl(ChatRequestBuilder.normalizeBaseUrl(trimmed))
        val request = try {
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()
        } catch (e: Exception) {
            return@withContext ReachabilityClassify.fromInvalidUrl(e.message ?: "parse error")
        }
        val probeClient: OkHttpClient = httpHolder.client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        try {
            probeClient.newCall(request).execute().use { resp ->
                val host = request.url.host
                ReachabilityClassify.fromHttp(resp.code, host)
            }
        } catch (e: Exception) {
            ReachabilityClassify.fromIo(e.message ?: e.javaClass.simpleName)
        }
    }
}
