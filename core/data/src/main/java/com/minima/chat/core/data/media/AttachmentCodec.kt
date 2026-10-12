package com.minima.chat.core.data.media

/**
 * Packs one or more (displayName, storedPath) pairs into [Message.attachmentPath]
 * without a schema migration. Legacy bare paths still decode.
 *
 * Format per line: `displayName|||relativeOrAbsolutePath`
 */
object AttachmentCodec {
    private const val SEP = "|||"
    const val MAX_PENDING_IMAGES = 8

    data class Ref(val displayName: String, val path: String)

    fun encode(items: List<Ref>): String? {
        if (items.isEmpty()) return null
        return items.joinToString("\n") { ref ->
            val name = ref.displayName
                .replace('\n', ' ')
                .replace(SEP, "|")
                .ifBlank { "image.jpg" }
            "$name$SEP${ref.path}"
        }
    }

    fun decode(stored: String?): List<Ref> {
        if (stored.isNullOrBlank()) return emptyList()
        return stored.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            val idx = line.indexOf(SEP)
            if (idx < 0) {
                // Legacy single path
                val name = line.substringAfterLast('/').ifBlank { "image.jpg" }
                Ref(name, line)
            } else {
                val name = line.substring(0, idx).ifBlank { "image.jpg" }
                val path = line.substring(idx + SEP.length).trim()
                if (path.isEmpty()) null else Ref(name, path)
            }
        }.toList()
    }

    fun paths(stored: String?): List<String> = decode(stored).map { it.path }

    fun labels(stored: String?): List<String> =
        decode(stored).map { ImageAttachLogic.chipLabel(it.displayName) }

    /** Single-line chip summary for bubbles (comma-joined, already truncated per name). */
    fun bubbleLabel(stored: String?): String? {
        val labels = labels(stored)
        if (labels.isEmpty()) return null
        return labels.joinToString(", ")
    }
}
