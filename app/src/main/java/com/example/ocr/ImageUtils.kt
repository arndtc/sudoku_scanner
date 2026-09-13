package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.PerspectiveQuad
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

sealed class DecodeResult {
    data class Success(val bitmap: Bitmap) : DecodeResult()
    data class Error(val message: String) : DecodeResult()
}

/**
 * Diagnostic analysis of image file signatures, headers, and color spaces.
 */
data class ImageHeaderAnalysis(
    val format: String,
    val isValidHeader: Boolean,
    val mimeTypeGuess: String?,
    val isCmyk: Boolean = false,
    val colorChannels: Int = 0,
    val details: String,
    val hexSignature: String
)

object ImageUtils {

    private const val TAG = "SudokuImagePipeline"

    fun createTempImageUri(context: Context): Uri {
        val storageDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val tempFile = File.createTempFile("sudoku_capture_", ".jpg", storageDir)
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    /**
     * Inspects raw file bytes to diagnose corrupted headers, file type signatures,
     * and unsupported color spaces such as CMYK JPEGs.
     */
    fun analyzeImageHeader(bytes: ByteArray): ImageHeaderAnalysis {
        val hexSignature = bytes.take(16).joinToString(" ") { "%02X".format(it) }

        if (bytes.size < 4) {
            return ImageHeaderAnalysis(
                format = "Truncated",
                isValidHeader = false,
                mimeTypeGuess = null,
                details = "File is too small (${bytes.size} bytes), header truncated.",
                hexSignature = hexSignature
            )
        }

        // 1. Check JPEG (starts with FF D8 FF)
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            var isCmyk = false
            var channels = 3
            var detailInfo = "Standard JPEG"

            // Parse JPEG markers to find Start of Frame (SOF) and color channels
            try {
                var offset = 2
                while (offset < bytes.size - 9) {
                    if (bytes[offset] == 0xFF.toByte()) {
                        val marker = bytes[offset + 1].toInt() and 0xFF
                        // SOF0 (0xC0), SOF1 (0xC1), SOF2 (0xC2), SOF3 (0xC3)
                        if (marker in 0xC0..0xC3) {
                            val precision = bytes[offset + 4].toInt() and 0xFF
                            val height = ((bytes[offset + 5].toInt() and 0xFF) shl 8) or (bytes[offset + 6].toInt() and 0xFF)
                            val width = ((bytes[offset + 7].toInt() and 0xFF) shl 8) or (bytes[offset + 8].toInt() and 0xFF)
                            channels = bytes[offset + 9].toInt() and 0xFF
                            if (channels == 4) {
                                isCmyk = true
                                detailInfo = "JPEG with 4 color channels (CMYK/YCCK, ${width}x${height}, precision $precision-bit)"
                            } else if (channels == 1) {
                                detailInfo = "Grayscale JPEG with 1 color channel (${width}x${height}, precision $precision-bit)"
                            } else {
                                detailInfo = "RGB/YCbCr JPEG with 3 color channels (${width}x${height}, precision $precision-bit)"
                            }
                            break
                        } else if (marker != 0x00 && marker != 0xFF && marker != 0xD9 && marker != 0xDA) {
                            // Skip segment: length is 2-byte big-endian
                            val segLen = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                            if (segLen < 2) break
                            offset += 2 + segLen
                            continue
                        }
                    }
                    offset++
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Pipeline] Error scanning JPEG SOF markers: ${e.message}")
            }

            return ImageHeaderAnalysis(
                format = "JPEG",
                isValidHeader = true,
                mimeTypeGuess = "image/jpeg",
                isCmyk = isCmyk,
                colorChannels = channels,
                details = detailInfo,
                hexSignature = hexSignature
            )
        }

        // 2. Check PNG (89 50 4E 47 0D 0A 1A 0A)
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(pngSignature)) {
            var details = "PNG image"
            if (bytes.size >= 26) {
                val colorType = bytes[25].toInt() and 0xFF
                val bitDepth = bytes[24].toInt() and 0xFF
                val colorDesc = when (colorType) {
                    0 -> "Grayscale"
                    2 -> "RGB (Truecolor)"
                    3 -> "Indexed color"
                    4 -> "Grayscale with Alpha"
                    6 -> "RGBA (Truecolor with Alpha)"
                    else -> "Unknown ($colorType)"
                }
                details = "PNG ($colorDesc, $bitDepth-bit)"
            }
            return ImageHeaderAnalysis(
                format = "PNG",
                isValidHeader = true,
                mimeTypeGuess = "image/png",
                details = details,
                hexSignature = hexSignature
            )
        }

        // 3. Check WebP (RIFF .... WEBP)
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return ImageHeaderAnalysis(
                format = "WebP",
                isValidHeader = true,
                mimeTypeGuess = "image/webp",
                details = "WebP image header verified",
                hexSignature = hexSignature
            )
        }

        // 4. Check GIF (GIF87a or GIF89a)
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()
        ) {
            val ver = String(bytes.slice(0..5).toByteArray(), Charsets.US_ASCII)
            return ImageHeaderAnalysis(
                format = "GIF",
                isValidHeader = true,
                mimeTypeGuess = "image/gif",
                details = "GIF image ($ver)",
                hexSignature = hexSignature
            )
        }

        // 5. Check BMP (BM)
        if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            return ImageHeaderAnalysis(
                format = "BMP",
                isValidHeader = true,
                mimeTypeGuess = "image/bmp",
                details = "BMP Windows bitmap header",
                hexSignature = hexSignature
            )
        }

        // 6. Check HEIF / AVIF (ftyp at offset 4)
        if (bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes.slice(8..11).toByteArray(), Charsets.US_ASCII).trim()
            return ImageHeaderAnalysis(
                format = "HEIF/AVIF",
                isValidHeader = true,
                mimeTypeGuess = if (brand.contains("avif")) "image/avif" else "image/heic",
                details = "HEIF/AVIF image (brand: $brand)",
                hexSignature = hexSignature
            )
        }

        // 7. Check HTML / XML text preview (common download failure)
        val previewText = String(bytes.take(256).toByteArray(), Charsets.UTF_8).lowercase().trim()
        if (previewText.startsWith("<!doctype html") ||
            previewText.startsWith("<html") ||
            previewText.contains("<head>") ||
            previewText.contains("<body>") ||
            previewText.startsWith("<?xml")
        ) {
            return ImageHeaderAnalysis(
                format = "HTML/Webpage",
                isValidHeader = false,
                mimeTypeGuess = "text/html",
                details = "Webpage HTML document instead of raw image data",
                hexSignature = hexSignature
            )
        }

        // Unrecognized / Corrupted header
        return ImageHeaderAnalysis(
            format = "Unknown/Corrupted",
            isValidHeader = false,
            mimeTypeGuess = null,
            details = "Unrecognized file signature (corrupted header or unsupported format)",
            hexSignature = hexSignature
        )
    }

    /**
     * Decodes an image Uri safely using ImageDecoder (API 28+) or BitmapFactory fallback.
     * Performs comprehensive error logging and diagnostics for corrupted headers,
     * unsupported color spaces (e.g. CMYK), and gallery permission/stream issues.
     */
    fun decodeImage(context: Context, uri: Uri, maxDimension: Int = 1600): DecodeResult {
        Log.d(TAG, "=======================================================")
        Log.d(TAG, "[Pipeline] Starting decodeImage for Uri: $uri")
        Log.d(TAG, "[Pipeline] Scheme: ${uri.scheme}, Authority: ${uri.authority}, Path: ${uri.path}")

        // Probe ContentResolver metadata (Display Name, Size, MIME)
        var displayName: String? = null
        var declaredSize: Long? = null
        var contentMime: String? = null

        try {
            contentMime = context.contentResolver.getType(uri)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) declaredSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Pipeline] Could not query ContentResolver metadata: ${e.message}")
        }

        Log.d(TAG, "[Pipeline] Metadata: name='$displayName', declaredSize=$declaredSize bytes, mimeType='$contentMime'")

        return try {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "[Pipeline] Error opening contentResolver input stream for $uri", e)
                null
            } ?: run {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists() && file.canRead()) {
                        Log.d(TAG, "[Pipeline] Read directly from local file path: ${file.absolutePath}")
                        file.readBytes()
                    } else {
                        Log.e(TAG, "[Pipeline] Local file not readable or does not exist: ${file.absolutePath}")
                        null
                    }
                } else null
            }

            if (bytes == null) {
                Log.e(TAG, "[Pipeline] Failed to read byte data from $uri. Stream returned null.")
                return DecodeResult.Error(
                    "Unable to read image file from gallery. Ensure the file has finished downloading and gallery storage access is permitted."
                )
            }

            if (bytes.isEmpty()) {
                Log.e(TAG, "[Pipeline] Read 0 bytes from $uri. File is completely empty.")
                return DecodeResult.Error(
                    "The selected image file is 0 bytes (empty file). Please select a valid picture."
                )
            }

            Log.d(TAG, "[Pipeline] Successfully read ${bytes.size} bytes from source.")

            // Diagnose header and check for corrupted files or unsupported color spaces
            val headerAnalysis = analyzeImageHeader(bytes)
            Log.d(TAG, "[Pipeline] Header Analysis: format='${headerAnalysis.format}', valid=${headerAnalysis.isValidHeader}, details='${headerAnalysis.details}', hex=[${headerAnalysis.hexSignature}]")

            if (headerAnalysis.isCmyk) {
                Log.e(TAG, "[Pipeline] UNSUPPORTED COLOR SPACE: Image is a 4-channel CMYK JPEG. Android BitmapFactory and ImageDecoder do not natively support CMYK.")
                return DecodeResult.Error(
                    "Unsupported Color Space: This image is saved in CMYK (print color space with 4 channels). Android requires RGB images. Please convert it to standard RGB or take a photo."
                )
            }

            if (!headerAnalysis.isValidHeader) {
                Log.e(TAG, "[Pipeline] CORRUPTED FILE HEADER: Header does not match any recognized image format! Hex signature: ${headerAnalysis.hexSignature}")
                if (headerAnalysis.format == "HTML/Webpage") {
                    return DecodeResult.Error(
                        "The downloaded file is a webpage (HTML), not an image. In Chrome, tap and hold directly on the puzzle and select 'Download image'."
                    )
                }
                return DecodeResult.Error(
                    "Corrupted or invalid image header: The selected file does not have a recognized image signature (Hex: ${headerAnalysis.hexSignature})."
                )
            }

            // Attempt 1: Modern ImageDecoder (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    Log.d(TAG, "[Pipeline] Attempting decode via Android ImageDecoder (API ${Build.VERSION.SDK_INT})...")
                    val byteBuffer = ByteBuffer.wrap(bytes)
                    val source = ImageDecoder.createSource(byteBuffer)
                    val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE

                        // Inspect and log color space
                        val cs = info.colorSpace
                        Log.d(
                            TAG,
                            "[Pipeline] ImageDecoder reported: mime='${info.mimeType}', size=${info.size.width}x${info.size.height}, " +
                                    "colorSpace='${cs?.name ?: "null"}', isWideGamut=${cs?.isWideGamut ?: false}"
                        )

                        // Enforce sRGB color space conversion for consistent OCR and rendering
                        try {
                            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            Log.d(TAG, "[Pipeline] Successfully targeted sRGB color space.")
                        } catch (e: Exception) {
                            Log.w(TAG, "[Pipeline] Could not force sRGB color space: ${e.message}")
                        }

                        val maxSide = max(info.size.width, info.size.height)
                        if (maxSide > maxDimension && maxSide > 0) {
                            val scale = maxDimension.toFloat() / maxSide
                            val targetW = max(1, (info.size.width * scale).roundToInt())
                            val targetH = max(1, (info.size.height * scale).roundToInt())
                            decoder.setTargetSize(targetW, targetH)
                            Log.d(TAG, "[Pipeline] Scaled target size to ${targetW}x${targetH} (scale factor: $scale)")
                        }
                    }

                    if (decoded.width > 0 && decoded.height > 0) {
                        val csName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) decoded.colorSpace?.name else "N/A"
                        Log.d(
                            TAG,
                            "[Pipeline] ImageDecoder SUCCESS: ${decoded.width}x${decoded.height}, config=${decoded.config}, colorSpace='$csName', bytes=${decoded.byteCount}"
                        )
                        return DecodeResult.Success(decoded)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[Pipeline] ImageDecoder threw exception, falling back to BitmapFactory. Reason: ${e.message}", e)
                }
            }

            // Attempt 2: BitmapFactory fallback with comprehensive error logging
            Log.d(TAG, "[Pipeline] Attempting decode via BitmapFactory fallback...")
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            val probedMime = boundsOptions.outMimeType
            val probedColorSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) boundsOptions.outColorSpace?.name else "N/A"

            Log.d(
                TAG,
                "[Pipeline] BitmapFactory bounds probe: width=$srcWidth, height=$srcHeight, mime='$probedMime', colorSpace='$probedColorSpace'"
            )

            if (srcWidth <= 0 || srcHeight <= 0) {
                Log.e(TAG, "[Pipeline] BitmapFactory failed to parse image dimensions. Width=$srcWidth, Height=$srcHeight.")
                return DecodeResult.Error(
                    "Could not decode image dimensions. The image file header may be corrupted or use an unsupported color format."
                )
            }

            var sampleSize = 1
            val maxSide = max(srcWidth, srcHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            Log.d(TAG, "[Pipeline] Decoding bitmap with inSampleSize=$sampleSize, inPreferredConfig=ARGB_8888")
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            if (rawBitmap == null) {
                Log.e(TAG, "[Pipeline] BitmapFactory.decodeByteArray returned null. Header signature: ${headerAnalysis.hexSignature}")
                return DecodeResult.Error(
                    "BitmapFactory failed to decode image pixels. Header signature was: ${headerAnalysis.hexSignature}."
                )
            }

            val rotation = getExifOrientationFromBytes(bytes)
            Log.d(TAG, "[Pipeline] EXIF orientation rotation: $rotation degrees")

            val finalBitmap = if (rotation != 0) {
                rotateBitmap(rawBitmap, rotation.toFloat())
            } else {
                rawBitmap
            }

            val finalColorSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) finalBitmap.colorSpace?.name else "N/A"
            Log.d(
                TAG,
                "[Pipeline] BitmapFactory SUCCESS: ${finalBitmap.width}x${finalBitmap.height}, config=${finalBitmap.config}, colorSpace='$finalColorSpace', bytes=${finalBitmap.byteCount}"
            )

            DecodeResult.Success(finalBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "[Pipeline] Unexpected error in decodeImage", e)
            DecodeResult.Error("Failed to decode image: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? {
        return when (val result = decodeImage(context, uri, maxDimension)) {
            is DecodeResult.Success -> result.bitmap
            is DecodeResult.Error -> null
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
        val canvas = Canvas(result)
        val paint = Paint()

        val matrix = android.graphics.ColorMatrix()
        matrix.setSaturation(0f) // Grayscale

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

    private fun getExifOrientationFromBytes(bytes: ByteArray): Int {
        return try {
            ByteArrayInputStream(bytes).use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Prepares a realistic printed sample Sudoku puzzle photo in cache
     * for instant one-tap testing in the emulator.
     */
    fun createSamplePuzzleImage(context: Context): Uri? {
        return try {
            val storageDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
            val sampleFile = File(storageDir, "sample_sudoku_photo.jpg")

            // Try loading bundled sample photo asset or drawable
            var sampleBitmap: Bitmap? = null
            try {
                sampleBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.sample_sudoku_photo_1789095414413)
            } catch (e: Exception) {
                // Fallback to crisp programmatic newspaper puzzle
            }

            if (sampleBitmap == null) {
                sampleBitmap = renderCrispNewspaperSudokuBitmap()
            }

            FileOutputStream(sampleFile).use { out ->
                sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, sampleFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sample puzzle image", e)
            null
        }
    }

    /**
     * Programmatically renders a high-contrast printed newspaper Sudoku puzzle
     * with authentic typography, clean borders, and 30 valid clues.
     */
    private fun renderCrispNewspaperSudokuBitmap(): Bitmap {
        val size = 1000
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Off-white paper background
        canvas.drawColor(Color.rgb(248, 247, 243))

        val margin = 80f
        val gridWidth = size - 2 * margin
        val cellSize = gridWidth / 9f

        val thinPaint = Paint().apply {
            color = Color.rgb(110, 110, 110)
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val thickPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 7f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // Draw internal thin cell lines
        for (i in 0..9) {
            val pos = margin + i * cellSize
            val paint = if (i % 3 == 0) thickPaint else thinPaint
            canvas.drawLine(margin, pos, size - margin, pos, paint)
            canvas.drawLine(pos, margin, pos, size - margin, paint)
        }

        // Standard classic newspaper puzzle clues
        val sampleGrid = listOf(
            listOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
            listOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
            listOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
            listOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
            listOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
            listOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
            listOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
            listOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
            listOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
        )

        val textPaint = Paint().apply {
            color = Color.rgb(20, 20, 20)
            textSize = cellSize * 0.62f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        for (r in 0..8) {
            for (c in 0..8) {
                val digit = sampleGrid[r][c]
                if (digit != 0) {
                    val cx = margin + (c + 0.5f) * cellSize
                    val cy = margin + (r + 0.5f) * cellSize - ((textPaint.descent() + textPaint.ascent()) / 2)
                    canvas.drawText(digit.toString(), cx, cy, textPaint)
                }
            }
        }

        return bitmap
    }

    /**
     * Crops a bitmap according to normalized coordinates (0.0 to 1.0).
     */
    fun cropBitmap(src: Bitmap, normalizedRect: RectF): Bitmap {
        val clampedLeft = normalizedRect.left.coerceIn(0f, 1f)
        val clampedTop = normalizedRect.top.coerceIn(0f, 1f)
        val clampedRight = normalizedRect.right.coerceIn(clampedLeft + 0.01f, 1f)
        val clampedBottom = normalizedRect.bottom.coerceIn(clampedTop + 0.01f, 1f)

        val pxLeft = (clampedLeft * src.width).toInt().coerceIn(0, src.width - 1)
        val pxTop = (clampedTop * src.height).toInt().coerceIn(0, src.height - 1)
        val pxRight = (clampedRight * src.width).toInt().coerceIn(pxLeft + 1, src.width)
        val pxBottom = (clampedBottom * src.height).toInt().coerceIn(pxTop + 1, src.height)

        val width = (pxRight - pxLeft).coerceAtLeast(1)
        val height = (pxBottom - pxTop).coerceAtLeast(1)

        Log.d(TAG, "[Pipeline] Cropping bitmap (${src.width}x${src.height}) to rect: [$pxLeft, $pxTop, $width, $height]")
        return Bitmap.createBitmap(src, pxLeft, pxTop, width, height)
    }

    /**
     * De-warps an angled perspective quadrilateral into a crisp, rectified square Sudoku image.
     * Uses 3x3 projective homography with backward-mapping bilinear pixel interpolation
     * to eliminate perspective skew while preserving sharp digit contours for OCR.
     */
    fun warpPerspective(src: Bitmap, quad: PerspectiveQuad, targetDimension: Int? = null): Bitmap {
        val srcW = src.width
        val srcH = src.height

        // Source quad corner points in pixel coordinates
        val pTLx = quad.topLeft.x * srcW
        val pTLy = quad.topLeft.y * srcH
        val pTRx = quad.topRight.x * srcW
        val pTRy = quad.topRight.y * srcH
        val pBRx = quad.bottomRight.x * srcW
        val pBRy = quad.bottomRight.y * srcH
        val pBLx = quad.bottomLeft.x * srcW
        val pBLy = quad.bottomLeft.y * srcH

        // Calculate edge lengths in source pixels
        val topLen = Math.hypot((pTRx - pTLx).toDouble(), (pTRy - pTLy).toDouble()).toFloat()
        val botLen = Math.hypot((pBRx - pBLx).toDouble(), (pBRy - pBLy).toDouble()).toFloat()
        val leftLen = Math.hypot((pBLx - pTLx).toDouble(), (pBLy - pTLy).toDouble()).toFloat()
        val rightLen = Math.hypot((pBRx - pTRx).toDouble(), (pBRy - pTRy).toDouble()).toFloat()

        val naturalMax = max(max(topLen, botLen), max(leftLen, rightLen))
        val side = targetDimension ?: naturalMax.roundToInt().coerceIn(720, 1400)

        Log.d(TAG, "[Pipeline] Perspective warp: naturalSide=$naturalMax -> targetSide=$side px")

        // Destination square vertices: (0,0), (side,0), (side,side), (0,side)
        val dstPts = floatArrayOf(
            0f, 0f,
            side.toFloat(), 0f,
            side.toFloat(), side.toFloat(),
            0f, side.toFloat()
        )

        // Source quadrilateral vertices: TL, TR, BR, BL
        val srcPts = floatArrayOf(
            pTLx, pTLy,
            pTRx, pTRy,
            pBRx, pBRy,
            pBLx, pBLy
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(dstPts, 0, srcPts, 0, 4)

        if (!success) {
            Log.w(TAG, "[Pipeline] Homography setPolyToPoly failed (degenerate quad), falling back to axis-aligned crop")
            return cropBitmap(src, quad.toBoundingRect())
        }

        val m = FloatArray(9)
        matrix.getValues(m)
        val h00 = m[Matrix.MSCALE_X]
        val h01 = m[Matrix.MSKEW_X]
        val h02 = m[Matrix.MTRANS_X]
        val h10 = m[Matrix.MSKEW_Y]
        val h11 = m[Matrix.MSCALE_Y]
        val h12 = m[Matrix.MTRANS_Y]
        val h20 = m[Matrix.MPERSP_0]
        val h21 = m[Matrix.MPERSP_1]
        val h22 = m[Matrix.MPERSP_2]

        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
        val dstPixels = IntArray(side * side)

        for (v in 0 until side) {
            val rowOffset = v * side
            var xPrime = h01 * v + h02
            var yPrime = h11 * v + h12
            var wPrime = h21 * v + h22

            for (u in 0 until side) {
                val invW = if (wPrime != 0f) 1f / wPrime else 1f
                val srcX = (xPrime * invW).coerceIn(0f, (srcW - 1).toFloat())
                val srcY = (yPrime * invW).coerceIn(0f, (srcH - 1).toFloat())

                xPrime += h00
                yPrime += h10
                wPrime += h20

                val x0 = srcX.toInt().coerceIn(0, srcW - 1)
                val y0 = srcY.toInt().coerceIn(0, srcH - 1)
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val y1 = (y0 + 1).coerceAtMost(srcH - 1)

                val fx = srcX - x0
                val fy = srcY - y0

                val idx00 = y0 * srcW + x0
                val idx10 = y0 * srcW + x1
                val idx01 = y1 * srcW + x0
                val idx11 = y1 * srcW + x1

                val c00 = srcPixels[idx00]
                val c10 = srcPixels[idx10]
                val c01 = srcPixels[idx01]
                val c11 = srcPixels[idx11]

                // Bilinear interpolation for each color channel
                val r0 = ((c00 shr 16) and 0xFF) * (1f - fx) + ((c10 shr 16) and 0xFF) * fx
                val g0 = ((c00 shr 8) and 0xFF) * (1f - fx) + ((c10 shr 8) and 0xFF) * fx
                val b0 = (c00 and 0xFF) * (1f - fx) + (c10 and 0xFF) * fx

                val r1 = ((c01 shr 16) and 0xFF) * (1f - fx) + ((c11 shr 16) and 0xFF) * fx
                val g1 = ((c01 shr 8) and 0xFF) * (1f - fx) + ((c11 shr 8) and 0xFF) * fx
                val b1 = (c01 and 0xFF) * (1f - fx) + (c11 and 0xFF) * fx

                val r = (r0 * (1f - fy) + r1 * fy).toInt().coerceIn(0, 255)
                val g = (g0 * (1f - fy) + g1 * fy).toInt().coerceIn(0, 255)
                val b = (b0 * (1f - fy) + b1 * fy).toInt().coerceIn(0, 255)

                dstPixels[rowOffset + u] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val dewarped = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        dewarped.setPixels(dstPixels, 0, side, 0, 0, side, side)
        return dewarped
    }

    /**
     * Saves a cropped bitmap to the app cache directory and returns a FileProvider Uri.
     */
    fun saveCroppedBitmap(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val storageDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
            val croppedFile = File(storageDir, "cropped_sudoku_${System.currentTimeMillis()}.jpg")
            FileOutputStream(croppedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, croppedFile)
        } catch (e: Exception) {
            Log.e(TAG, "[Pipeline] Failed to save cropped bitmap to cache", e)
            null
        }
    }
}
