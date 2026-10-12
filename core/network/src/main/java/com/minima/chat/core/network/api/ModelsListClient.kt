package com.minima.chat.core.network.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ModelsListResult(
    val ok: Boolean,
    val ids: List<String> = emptyList(),
    val message: String,
)

/**
 * Fetch OpenAI-compatible model list: GET `{base}/models`.
 * Sends Bearer when [apiKey] is non-blank. All error paths go through [SecretRedact].
 */
@Singleton
class ModelsListClient @Inject constructor(
    private val httpHolder: OkHttpClientHolder,
) {
    suspend fun fetch(baseUrl: String, apiKey: String?): ModelsListResult = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext ModelsListResult(false, message = "Set a Base URL first")
        }
        CleartextPolicy.rejectDisallowedCleartext(trimmed)?.let {
            return@withContext ModelsListResult(false, message = it)
        }
        val url = ReachabilityClassify.probeUrl(ChatRequestBuilder.normalizeBaseUrl(trimmed))
        val request = try {
            val b = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
            val key = apiKey?.trim().orEmpty()
            if (key.isNotEmpty()) {
                b.header("Authorization", "Bearer $key")
            }
            b.build()
        } catch (e: Exception) {
            return@withContext ModelsListResult(
                false,
                message = "Invalid URL: ${SecretRedact.redact(e.message).take(100)}",
            )
        }
        val client: OkHttpClient = httpHolder.client.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val body = try {
                    resp.body?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                if (!resp.isSuccessful) {
                    val snippet = SecretRedact.redact(body).take(80)
                    val detail = if (snippet.isBlank()) "" else " · $snippet"
                    return@withContext ModelsListResult(
                        false,
                        message = SecretRedact.redact("HTTP ${resp.code}$detail").take(160)
                            .ifBlank { "HTTP ${resp.code}" },
                    )
                }
                val parsed = ModelsListParser.parse(body)
                if (parsed.error != null && parsed.ids.isEmpty()) {
                    return@withContext ModelsListResult(
                        false,
                        message = SecretRedact.redact(parsed.error).take(160)
                            .ifBlank { "Could not parse models" },
                    )
                }
                ModelsListResult(
                    ok = true,
                    ids = parsed.ids,
                    message = "${parsed.ids.size} model(s)",
                )
            }
        } catch (e: Exception) {
            ModelsListResult(
                false,
                message = SecretRedact.redact(e.message ?: e.javaClass.simpleName)
                    .take(120)
                    .ifBlank { "Network error" },
            )
        }
    }
}
