package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import kotlin.math.max

object ImageUtils {

    fun createTempImageUri(context: Context): Uri {
        val storageDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val tempFile = File.createTempFile("sudoku_capture_", ".jpg", storageDir)
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? {
        return try {
            // First decode bounds only
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            val maxSide = max(srcWidth, srcHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // Decode actual bitmap with sample size
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Handle EXIF orientation rotation
            val rotation = getExifOrientation(context, uri)
            if (rotation != 0) {
                rotateBitmap(rawBitmap, rotation.toFloat())
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Enhances contrast and converts to grayscale for improved OCR digit detection
     * on paper books, newsprint, and shaded backgrounds.
     */
    fun enhanceContrast(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()

        val matrix = android.graphics.ColorMatrix()
        matrix.setSaturation(0f) // Grayscale

        // High contrast adjustment: scale up darks/lights and offset
        val contrastMatrix = android.graphics.ColorMatrix(
            floatArrayOf(
                1.7f, 0f, 0f, 0f, -50f,
                0f, 1.7f, 0f, 0f, -50f,
                0f, 0f, 1.7f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrastMatrix)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
