package com.minima.chat.core.network.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parse OpenAI-compatible `GET /v1/models` JSON into model ids.
 * Tolerant of extra fields; ignores entries without a non-blank string `id`.
 */
object ModelsListParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class ParseResult(
        val ids: List<String>,
        val error: String? = null,
    )

    fun parse(raw: String?): ParseResult {
        if (raw.isNullOrBlank()) {
            return ParseResult(emptyList(), "Empty models response")
        }
        return try {
            val root = json.parseToJsonElement(raw)
            val data: JsonArray = when (root) {
                is JsonObject -> {
                    when (val dataEl = root["data"]) {
                        is JsonArray -> dataEl
                        null -> {
                            // Some proxies wrap once: { "result": { "data": [...] } }
                            val nested = root["result"]?.jsonObject?.get("data")
                            if (nested is JsonArray) nested
                            else return ParseResult(emptyList(), "No data array in models response")
                        }
                        else -> return ParseResult(emptyList(), "data is not an array")
                    }
                }
                is JsonArray -> root
                else -> return ParseResult(emptyList(), "Unexpected models JSON root")
            }
            val ids = LinkedHashSet<String>()
            for (el in data) {
                val id = when (el) {
                    is JsonObject -> {
                        val prim = el["id"] as? JsonPrimitive
                        if (prim != null && prim.isString) prim.contentOrNull?.trim() else null
                    }
                    is JsonPrimitive -> if (el.isString) el.contentOrNull?.trim() else null
                    else -> null
                }
                if (!id.isNullOrBlank()) ids.add(id)
            }
            if (ids.isEmpty()) {
                ParseResult(emptyList(), "No model ids in response")
            } else {
                ParseResult(ids.toList())
            }
        } catch (e: Exception) {
            ParseResult(
                emptyList(),
                SecretRedact.redact(e.message ?: "Invalid models JSON").take(120)
                    .ifBlank { "Invalid models JSON" },
            )
        }
    }
}
