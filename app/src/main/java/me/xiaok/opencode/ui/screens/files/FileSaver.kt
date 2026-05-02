package me.xiaok.opencode.ui.screens.files

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.IOException

object FileSaver {

    private const val TAG = "FileSaver"

    /**
     * Decode [FileContent] into raw bytes.
     * - Text content → UTF-8 bytes
     * - Base64 content → decoded bytes
     */
    fun decodeBytes(fileContent: me.xiaok.opencode.domain.model.FileContent): ByteArray {
        return if (fileContent.encoding == "base64") {
            Base64.decode(fileContent.content, Base64.DEFAULT)
        } else {
            fileContent.content.toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Write bytes to a content URI (obtained from MediaStore or SAF).
     * Uses "wt" mode (write truncate) to overwrite existing content.
     */
    fun writeToUri(
        contentResolver: ContentResolver,
        uri: Uri,
        bytes: ByteArray,
    ) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: throw IOException("Failed to open output stream for URI: $uri")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to URI: $uri", e)
            throw e
        }
    }

    /**
     * Extract file name from a file path (last segment after '/').
     */
    fun extractFileName(path: String): String {
        return path.substringAfterLast('/')
    }

    /**
     * Guess file extension from MIME type.
     */
    fun guessExtension(mimeType: String?): String? {
        if (mimeType == null) return null
        return when (mimeType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpeg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/svg+xml" -> "svg"
            "image/bmp" -> "bmp"
            "application/pdf" -> "pdf"
            "application/zip" -> "zip"
            "application/json" -> "json"
            else -> mimeType.substringAfterLast('/').takeIf { '/' in mimeType }
        }
    }
}
