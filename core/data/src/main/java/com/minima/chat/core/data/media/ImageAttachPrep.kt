package com.minima.chat.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.util.Base64
import com.minima.chat.core.network.dto.ImageAttachmentDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Picks a gallery/SAF image, downscales, JPEG-compresses, stores under
 * `filesDir/attachments/`, and returns path + base64 for the API call.
 *
 * Memory: never holds the full source file as a byte[] while decoding —
 * two-pass stream decode with [inSampleSize], then scale to [MAX_EDGE_PX].
 *
 * Robustness: copies the content:// URI into a private cache file first
 * (Photo Picker / OEM grants can expire), then decodes via ImageDecoder
 * (HEIC/WebP) with BitmapFactory fallback.
 */
object ImageAttachPrep {
    const val MAX_EDGE_PX = 1280
    const val JPEG_QUALITY = 82
    const val MAX_SOURCE_BYTES = 12L * 1024L * 1024L
    /** Reject absurd dimensions even before sample (e.g. malformed headers). */
    const val MAX_SOURCE_PIXELS = 64L * 1024L * 1024L
    const val DIR_NAME = "attachments"

    data class PreparedImage(
        /** Absolute path under app filesDir/attachments. */
        val absolutePath: String,
        val mimeType: String = "image/jpeg",
        val base64: String,
        val displayName: String = "image.jpg",
    ) {
        fun toDto(): ImageAttachmentDto = ImageAttachmentDto(mimeType, base64)

        fun relativeUnderFilesDir(context: Context): String {
            val root = context.filesDir.absolutePath.trimEnd('/')
            return if (absolutePath.startsWith(root)) {
                absolutePath.removePrefix(root).trimStart('/')
            } else {
                absolutePath
            }
        }
    }

    fun attachmentsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun resolveStoredPath(context: Context, stored: String): File? {
        // Confine to attachments/ — never follow absolute/relative escapes (v2.27).
        return ImageAttachLogic.resolveAttachmentFile(
            attachmentsDir(context),
            stored,
            filesDir = context.filesDir,
        )?.takeIf { it.canRead() }
    }

    /** Prefer storing a filesDir-relative path so history survives path quirks. */
    fun toStoredPath(context: Context, absolutePath: String): String {
        val root = context.filesDir.absolutePath.trimEnd('/')
        return if (absolutePath.startsWith(root)) {
            absolutePath.removePrefix(root).trimStart('/')
        } else {
            absolutePath
        }
    }

    /**
     * Load a previously stored attachment for history replay.
     * Skips if missing or base64 would exceed [ImageAttachmentDto.MAX_BASE64_CHARS].
     * Files under [attachmentsDir] are already JPEG-downscaled at prepare time.
     */
    fun loadForReplay(context: Context, storedPath: String?, mime: String?): ImageAttachmentDto? {
        // Unwrap packed multi-ref → first path (backward compatible single-image callers).
        val path = AttachmentCodec.decode(storedPath).firstOrNull()?.path ?: storedPath ?: return null
        val file = resolveStoredPath(context, path) ?: return null
        if (file.length() > MAX_SOURCE_BYTES) return null
        // Soft cap: stored JPEGs should be well under this; skip monsters.
        if (file.length() > ImageAttachmentDto.MAX_BASE64_CHARS) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (b64.length > ImageAttachmentDto.MAX_BASE64_CHARS) return null
        val m = mime?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        return ImageAttachmentDto(m, b64)
    }

    /**
     * Restore a [PreparedImage]-like payload for the composer after Edit last user.
     * Does not re-encode — reuses the stored JPEG bytes.
     */
    fun restorePending(context: Context, storedPath: String?, mime: String?): PreparedImage? {
        return restoreAllPending(context, storedPath, mime).firstOrNull()
    }

    /** Restore every packed attachment for edit-last-user / multi-image composer. */
    fun restoreAllPending(context: Context, storedPath: String?, mime: String?): List<PreparedImage> {
        val refs = AttachmentCodec.decode(storedPath)
        if (refs.isEmpty()) return emptyList()
        val mimes = mime?.lines()?.map { it.trim() }.orEmpty()
        val out = ArrayList<PreparedImage>(refs.size)
        refs.forEachIndexed { i, ref ->
            val file = resolveStoredPath(context, ref.path) ?: return@forEachIndexed
            val m = mimes.getOrNull(i)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val dto = loadForReplay(context, ref.path, m) ?: return@forEachIndexed
            out += PreparedImage(
                absolutePath = file.absolutePath,
                mimeType = dto.mimeType,
                base64 = dto.base64,
                displayName = ref.displayName.ifBlank { file.name },
            )
        }
        return out
    }

    /** Load all packed attachments for LLM replay. */
    fun loadAllForReplay(context: Context, storedPath: String?, mime: String?): List<ImageAttachmentDto> {
        val refs = AttachmentCodec.decode(storedPath)
        if (refs.isEmpty()) return emptyList()
        val mimes = mime?.lines()?.map { it.trim() }.orEmpty()
        return refs.mapIndexedNotNull { i, ref ->
            val m = mimes.getOrNull(i)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            loadForReplay(context, ref.path, m)
        }
    }

    /** Best-effort DISPLAY_NAME from the content provider (not MediaStore digit IDs). */
    fun resolveDisplayName(context: Context, uri: Uri): String {
        val cr = context.contentResolver
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val n = c.getString(idx)?.trim().orEmpty()
                    if (n.isNotBlank() && !n.all { it.isDigit() || it == '.' }) return n
                    if (n.isNotBlank() && n.contains('.')) return n
                }
            }
        }
        val seg = uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
        if (seg.isNotBlank() && seg.contains('.') && !seg.removeSuffix(".jpg").removeSuffix(".png").all { it.isDigit() || it == '-' }) {
            return seg
        }
        // Fall back to a friendly sequential name rather than opaque UUID digits
        return "image.jpg"
    }

    fun prepareFromUri(context: Context, uri: Uri): Result<PreparedImage> = runCatching {
        val cr = context.contentResolver
        val mimeRaw = cr.getType(uri)
        // Photo Picker / some OEMs report null or octet-stream — still try decode.
        val mime = mimeRaw?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        if (mimeRaw != null &&
            !mimeRaw.startsWith("image/") &&
            mimeRaw != "application/octet-stream" &&
            mimeRaw != "*/*"
        ) {
            error("请选择图片文件（当前类型: $mimeRaw）")
        }

        // Cheap size gate when the provider reports length (avoid reading 100MB into RAM).
        cr.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val len = afd.length
            if (len > 0L && len > MAX_SOURCE_BYTES) {
                error("图片过大（最大 12MB）")
            }
        }

        // Copy into private cache first — Photo Picker / some OEMs revoke URI grants
        // as soon as the picker Activity finishes, which caused "Cannot read image".
        val cacheFile = copyUriToCache(context, uri)

        val decoded = decodeBitmap(cacheFile)
            ?: error("无法读取图片，请换一张 JPEG/PNG/WebP 再试")
        if (decoded.width <= 0 || decoded.height <= 0) {
            decoded.recycle()
            error("不支持的图片格式")
        }

        val pixels = decoded.width.toLong() * decoded.height.toLong()
        if (pixels > MAX_SOURCE_PIXELS) {
            decoded.recycle()
            error("图片尺寸过大")
        }

        val scaled = scaleToMaxEdge(decoded, MAX_EDGE_PX)
        if (scaled !== decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        scaled.recycle()
        if (!ok) error("图片压缩失败")
        val jpeg = out.toByteArray()
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        if (b64.length > ImageAttachmentDto.MAX_BASE64_CHARS) {
            error("压缩后仍过大，请换一张更小的图")
        }

        runCatching { cacheFile.delete() }

        val id = UUID.randomUUID().toString()
        val dest = File(attachmentsDir(context), "$id.jpg")
        dest.writeBytes(jpeg)
        var name = resolveDisplayName(context, uri)
        // Keep a sensible extension for chip display
        if (!name.contains('.')) name = "$name.jpg"
        PreparedImage(
            absolutePath = dest.absolutePath,
            mimeType = "image/jpeg",
            base64 = b64,
            displayName = name,
        )
    }

    /** Stream the content URI into app cache before decode (survives grant expiry). */
    private fun copyUriToCache(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "attach_in").also { it.mkdirs() }
        val dest = File(dir, "src-${UUID.randomUUID()}.bin")
        val cr = context.contentResolver
        val input = cr.openInputStream(uri)
            ?: error("无法打开图片（权限或文件不可用）")
        input.use { src ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_SOURCE_BYTES) {
                        runCatching { dest.delete() }
                        error("图片过大（最大 12MB）")
                    }
                    out.write(buf, 0, n)
                }
            }
        }
        if (!dest.isFile || dest.length() == 0L) {
            runCatching { dest.delete() }
            error("无法读取图片")
        }
        return dest
    }

    private fun decodeBitmap(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val viaDecoder = runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val sample = ImageAttachLogic.computeInSampleSize(
                        info.size.width,
                        info.size.height,
                        MAX_EDGE_PX,
                    )
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                }
            }.getOrNull()
            if (viaDecoder != null) return viaDecoder
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = ImageAttachLogic.computeInSampleSize(
            bounds.outWidth,
            bounds.outHeight,
            MAX_EDGE_PX,
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val edge = maxOf(w, h)
        if (edge <= maxEdge) return src
        val scale = maxEdge.toFloat() / edge.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
