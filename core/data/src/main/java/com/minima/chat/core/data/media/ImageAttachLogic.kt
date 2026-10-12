package com.minima.chat.core.data.media

import java.io.File

/**
 * JVM-safe sizing helpers (no Android Bitmap).
 */
object ImageAttachLogic {
    fun computeInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge || h / 2 >= maxEdge) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val edge = maxOf(width, height)
        if (edge <= maxEdge) return width to height
        val scale = maxEdge.toFloat() / edge.toFloat()
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /**
     * Upper bound on ARGB_8888 bytes after [computeInSampleSize] + optional
     * [scaledSize] to [maxEdge]. Used to assert OOM-safe prepare path.
     */
    fun estimatedArgbBytesAfterDownscale(width: Int, height: Int, maxEdge: Int): Long {
        val sample = computeInSampleSize(width, height, maxEdge)
        val sw = (width / sample).coerceAtLeast(1)
        val sh = (height / sample).coerceAtLeast(1)
        val (fw, fh) = scaledSize(sw, sh, maxEdge)
        return fw.toLong() * fh.toLong() * 4L
    }

    /** Display label for composer chip / bubble. */
    fun chipLabel(displayName: String): String {
        val n = displayName.trim().ifBlank { "image.jpg" }
        return if (n.length <= 28) n else n.take(25) + "…"
    }

    /**
     * Resolve a stored attachment path to a readable file that **must** sit under
     * [attachmentsRoot] after canonicalization. Blocks `..`, NUL, and absolute
     * paths outside the attachments directory (exfil / path-escape harden).
     *
     * @param filesDir optional app filesDir so relative keys like `attachments/x.jpg` resolve.
     */
    fun resolveAttachmentFile(
        attachmentsRoot: File,
        stored: String,
        filesDir: File? = null,
    ): File? {
        val s = stored.trim()
        if (s.isEmpty() || s.contains('\u0000')) return null
        if (s.contains("..")) return null
        runCatching { attachmentsRoot.mkdirs() }
        val rootCanon = runCatching { attachmentsRoot.canonicalFile }.getOrNull() ?: return null
        val prefix = rootCanon.absolutePath.trimEnd('/') + "/"
        val candidates = ArrayList<File>(3)
        if (s.startsWith("/")) {
            candidates += File(s)
        } else {
            // Bare filename under attachments/
            candidates += File(rootCanon, s.substringAfterLast('/'))
            // Relative as stored (e.g. attachments/uuid.jpg) against attachments root's parent via filesDir
            candidates += File(rootCanon, s)
            if (filesDir != null) {
                candidates += File(filesDir, s)
            }
        }
        for (c in candidates) {
            val canon = runCatching { c.canonicalFile }.getOrNull() ?: continue
            if (!canon.isFile) continue
            val path = canon.absolutePath
            if (path == rootCanon.absolutePath) continue
            if (!path.startsWith(prefix)) continue
            return canon
        }
        return null
    }
}
