package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.model.SudokuBoard
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object SudokuOcrEngine {

    data class DigitDetection(
        val digit: Int,
        val centerX: Float,
        val centerY: Float,
        val boundingBox: Rect
    )

    data class OcrResult(
        val board: SudokuBoard,
        val detectedCount: Int,
        val gridBounds: Rect? = null,
        val message: String
    )

    suspend fun recognizeSudoku(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(inputImage))

            val candidateDigits = mutableListOf<DigitDetection>()

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val text = element.text.trim()
                        val box = element.boundingBox ?: continue

                        // Try parsing direct digit or individual characters in element
                        if (text.length == 1) {
                            val digit = parseSingleCharToDigit(text[0])
                            if (digit != null && digit in 1..9) {
                                candidateDigits.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = box.exactCenterX(),
                                        centerY = box.exactCenterY(),
                                        boundingBox = box
                                    )
                                )
                            }
                        } else {
                            // If multi-char element (e.g. adjacent numbers scanned together),
                            // estimate individual sub-boxes
                            val chars = text.toCharArray()
                            val charWidth = box.width().toFloat() / chars.size
                            for ((i, ch) in chars.withIndex()) {
                                val digit = parseSingleCharToDigit(ch)
                                if (digit != null && digit in 1..9) {
                                    val cx = box.left + (i + 0.5f) * charWidth
                                    val cy = box.exactCenterY()
                                    candidateDigits.add(
                                        DigitDetection(
                                            digit = digit,
                                            centerX = cx,
                                            centerY = cy,
                                            boundingBox = Rect(
                                                (box.left + i * charWidth).toInt(),
                                                box.top,
                                                (box.left + (i + 1) * charWidth).toInt(),
                                                box.bottom
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (candidateDigits.isEmpty()) {
                return@withContext OcrResult(
                    board = SudokuBoard.EMPTY,
                    detectedCount = 0,
                    message = "No digits detected. Please ensure the puzzle is well-lit and in frame."
                )
            }

            // Estimate grid boundary
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (d in candidateDigits) {
                minX = min(minX, d.centerX)
                minY = min(minY, d.centerY)
                maxX = max(maxX, d.centerX)
                maxY = max(maxY, d.centerY)
            }

            val spanX = maxX - minX
            val spanY = maxY - minY

            // In a 9x9 puzzle, span between outermost detected digits is ~ 7 to 8 cell units
            val approxCellSize = max(spanX, spanY) / 8f
            val padding = approxCellSize * 0.5f

            val gridLeft = max(0f, minX - padding)
            val gridTop = max(0f, minY - padding)
            val gridRight = min(bitmap.width.toFloat(), maxX + padding)
            val gridBottom = min(bitmap.height.toFloat(), maxY + padding)

            val gridWidth = gridRight - gridLeft
            val gridHeight = gridBottom - gridTop

            val cellWidth = gridWidth / 9f
            val cellHeight = gridHeight / 9f

            val cells = MutableList(81) { 0 }
            val isGiven = MutableList(81) { false }

            for (d in candidateDigits) {
                val col = ((d.centerX - gridLeft) / cellWidth).toInt().coerceIn(0, 8)
                val row = ((d.centerY - gridTop) / cellHeight).toInt().coerceIn(0, 8)
                val idx = row * 9 + col

                // If cell is empty or we place digit
                if (cells[idx] == 0) {
                    cells[idx] = d.digit
                    isGiven[idx] = true
                }
            }

            val detectedCount = cells.count { it != 0 }
            val board = SudokuBoard(cells = cells, isGiven = isGiven)

            OcrResult(
                board = board,
                detectedCount = detectedCount,
                gridBounds = Rect(gridLeft.toInt(), gridTop.toInt(), gridRight.toInt(), gridBottom.toInt()),
                message = "Detected $detectedCount clues from photo."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "Scanning failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        } finally {
            recognizer.close()
        }
    }

    private fun parseSingleCharToDigit(ch: Char): Int? {
        if (ch in '1'..'9') return ch.digitToInt()
        return when (ch) {
            'l', 'I', '|', '!' -> 1
            'Z', 'z' -> 2
            'S', 's' -> 5
            'G', 'b' -> 6
            'B' -> 8
            'q' -> 9
            else -> null
        }
    }
}
