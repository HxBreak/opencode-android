package me.xiaok.opencode.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compresses images for chat attachments.
 * Decodes URI → resizes to fit [maxSide] → compresses as WebP → returns ByteArray.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Compress a content URI into a WebP byte array.
     * @param uri       Content URI from photo picker or gallery
     * @param maxSide   Maximum dimension (width or height). Default 1920px.
     * @param quality   WebP compression quality 0-100. Default 70.
     * @return Compressed byte array, or null if decoding failed.
     */
    fun compress(uri: Uri, maxSide: Int = DEFAULT_MAX_SIDE, quality: Int = DEFAULT_QUALITY): ByteArray? {
        return try {
            // Decode bounds first to determine sample size
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "compress: openInputStream returned null for uri=$uri")
                return null
            }
            inputStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.e(TAG, "compress: decodeBounds failed, outWidth=${bounds.outWidth}, outHeight=${bounds.outHeight}, outMimeType=${bounds.outMimeType}")
                return null
            }

            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            Log.d(TAG, "compress: uri=$uri, original=${bounds.outWidth}x${bounds.outHeight}, sampleSize=$sampleSize")

            // Decode with sample size
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }

            if (bitmap == null) {
                Log.e(TAG, "compress: decodeStream returned null for uri=$uri")
                return null
            }

            // Further scale if still too large
            val scaled = scaleToFit(bitmap, maxSide)
            val result = compressToWebP(scaled, quality)

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            Log.d(TAG, "compress: success, resultSize=${result.size} bytes")
            result
        } catch (e: Exception) {
            Log.e(TAG, "compress: failed for uri=$uri", e)
            null
        }
    }

    /**
     * Encode a ByteArray to a Base64 string suitable for inline image data.
     */
    fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    /**
     * Detect the MIME type from a URI, defaulting to image/webp.
     */
    fun getMimeType(uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "image/webp"
    }

    // --- internal ---

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (width <= maxSide && height <= maxSide) return 1
        var sample = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample
    }

    private fun scaleToFit(bitmap: Bitmap, maxSide: Int): Bitmap {
        if (bitmap.width <= maxSide && bitmap.height <= maxSide) return bitmap
        val scale = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height)
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun compressToWebP(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
        return stream.toByteArray()
    }

    companion object {
        private const val TAG = "ImageCompressor"
        const val DEFAULT_MAX_SIDE = 1920
        const val DEFAULT_QUALITY = 70
    }
}
