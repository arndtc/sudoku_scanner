package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.logic.SudokuValidator
import com.example.model.SudokuBoard
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
            // Pass 1: Try on the standard scaled bitmap
            var bestResult = scanSingleBitmap(recognizer, bitmap)

            // Pass 2: If few clues found (< 16), try with high-contrast enhancement
            if (bestResult.detectedCount < 16) {
                val contrastBitmap = ImageUtils.enhanceContrast(bitmap)
                val contrastResult = scanSingleBitmap(recognizer, contrastBitmap)
                if (contrastResult.detectedCount > bestResult.detectedCount) {
                    bestResult = contrastResult
                }
            }

            // Pass 3: If still low (< 12 clues), try standard 90, 180, 270 degree rotations
            // in case camera EXIF was missing or rotated
            if (bestResult.detectedCount < 12) {
                for (angle in listOf(90f, 270f, 180f)) {
                    val rotated = ImageUtils.rotateBitmap(bitmap, angle)
                    val rotResult = scanSingleBitmap(recognizer, rotated)
                    if (rotResult.detectedCount > bestResult.detectedCount) {
                        bestResult = rotResult
                        if (bestResult.detectedCount >= 17) break
                    }
                }
            }

            bestResult
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = when {
                e is MlKitException && (e.errorCode == MlKitException.UNAVAILABLE || e.message?.contains("download", ignoreCase = true) == true) ->
                    "Google ML Kit is downloading the offline text recognition model. Please connect to Wi-Fi/data and tap Rescan in a moment."
                e.message?.contains("download", ignoreCase = true) == true ->
                    "Google ML Kit is downloading the text recognition model. Please connect to internet and tap Rescan."
                else ->
                    "Scanning error: ${e.localizedMessage ?: "Unknown error"}. You can enter digits manually or rescan."
            }
            OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = msg
            )
        } finally {
            recognizer.close()
        }
    }

    private fun scanSingleBitmap(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): OcrResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(inputImage))

        val candidateDigits = extractCandidateDigits(visionText)

        if (candidateDigits.size < 4) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "No Sudoku digits found. Please ensure the puzzle is centered and in focus."
            )
        }

        return fitSudokuGrid(candidateDigits, bitmap.width, bitmap.height)
    }

    private fun extractCandidateDigits(visionText: Text): List<DigitDetection> {
        val candidates = mutableListOf<DigitDetection>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val text = element.text.trim()

                    // Try using individual ML Kit symbols if available
                    if (element.symbols.isNotEmpty()) {
                        for (symbol in element.symbols) {
                            val ch = symbol.text.trim().firstOrNull() ?: continue
                            val digit = parseSingleCharToDigit(ch)
                            if (digit != null && digit in 1..9) {
                                val sBox = symbol.boundingBox ?: box
                                candidates.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = sBox.exactCenterX(),
                                        centerY = sBox.exactCenterY(),
                                        boundingBox = sBox
                                    )
                                )
                            }
                        }
                    } else if (text.length == 1) {
                        val digit = parseSingleCharToDigit(text[0])
                        if (digit != null && digit in 1..9) {
                            candidates.add(
                                DigitDetection(
                                    digit = digit,
                                    centerX = box.exactCenterX(),
                                    centerY = box.exactCenterY(),
                                    boundingBox = box
                                )
                            )
                        }
                    } else {
                        // Multi-char word: find any digits inside
                        val chars = text.toCharArray()
                        val charWidth = box.width().toFloat() / max(1, chars.size)
                        for ((i, ch) in chars.withIndex()) {
                            val digit = parseSingleCharToDigit(ch)
                            if (digit != null && digit in 1..9) {
                                val cx = box.left + (i + 0.5f) * charWidth
                                val cy = box.exactCenterY()
                                val sBox = Rect(
                                    (box.left + i * charWidth).toInt(),
                                    box.top,
                                    (box.left + (i + 1) * charWidth).toInt(),
                                    box.bottom
                                )
                                candidates.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = cx,
                                        centerY = cy,
                                        boundingBox = sBox
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return candidates
    }

    private fun fitSudokuGrid(
        rawCandidates: List<DigitDetection>,
        imageWidth: Int,
        imageHeight: Int
    ): OcrResult {
        if (rawCandidates.size < 4) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "Not enough digits detected to form a Sudoku grid."
            )
        }

        // 1. Remove isolated vertical outliers (e.g. puzzle number / page headers / footers)
        val filteredCandidates = filterOutliers(rawCandidates)

        if (filteredCandidates.size < 4) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "Could not isolate Sudoku grid from surrounding text."
            )
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (d in filteredCandidates) {
            minX = min(minX, d.centerX)
            minY = min(minY, d.centerY)
            maxX = max(maxX, d.centerX)
            maxY = max(maxY, d.centerY)
        }

        val spanX = maxX - minX
        val spanY = maxY - minY

        if (spanX <= 10f || spanY <= 10f) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "Detected digits are too clustered."
            )
        }

        // Test grid alignment configurations across possible outer row/col spans
        // In a 9x9 grid, outermost clues are typically at cols/rows 0..8 (span 8) or 1..7 (span 6 or 7)
        var bestScore = -1
        var bestCells = MutableList(81) { 0 }
        var bestGridRect: Rect? = null

        val testSteps = listOf(8, 7, 6)
        val testOffsets = listOf(0, 1)

        for (kx in testSteps) {
            for (ky in testSteps) {
                val cellW = spanX / kx
                val cellH = spanY / ky

                // Sudoku cells are roughly square
                val aspect = cellW / cellH
                if (aspect !in 0.70f..1.42f) continue

                for (ox in testOffsets) {
                    for (oy in testOffsets) {
                        val gridLeft = minX - (ox + 0.5f) * cellW
                        val gridTop = minY - (oy + 0.5f) * cellH
                        val gridRight = gridLeft + 9f * cellW
                        val gridBottom = gridTop + 9f * cellH

                        val candidateCells = MutableList(81) { 0 }
                        var placedCount = 0
                        var conflictPenalties = 0

                        for (d in filteredCandidates) {
                            val col = ((d.centerX - gridLeft) / cellW).toInt()
                            val row = ((d.centerY - gridTop) / cellH).toInt()

                            if (col in 0..8 && row in 0..8) {
                                // Check proximity to cell center
                                val expectedCx = gridLeft + (col + 0.5f) * cellW
                                val expectedCy = gridTop + (row + 0.5f) * cellH
                                val dx = abs(d.centerX - expectedCx) / cellW
                                val dy = abs(d.centerY - expectedCy) / cellH

                                if (dx < 0.42f && dy < 0.42f) {
                                    val idx = row * 9 + col
                                    if (candidateCells[idx] == 0) {
                                        candidateCells[idx] = d.digit
                                        placedCount++
                                    } else if (candidateCells[idx] != d.digit) {
                                        conflictPenalties++
                                    }
                                }
                            }
                        }

                        val score = placedCount * 10 - conflictPenalties * 15
                        if (score > bestScore) {
                            bestScore = score
                            bestCells = candidateCells
                            bestGridRect = Rect(
                                max(0, gridLeft.roundToInt()),
                                max(0, gridTop.roundToInt()),
                                min(imageWidth, gridRight.roundToInt()),
                                min(imageHeight, gridBottom.roundToInt())
                            )
                        }
                    }
                }
            }
        }

        val detectedCount = bestCells.count { it != 0 }
        if (detectedCount < 4) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                gridBounds = bestGridRect,
                message = "Grid alignment found fewer than 4 clues. Try snapping closer to the puzzle."
            )
        }

        // Build board and validate conflicts
        val isGiven = bestCells.map { it != 0 }
        val board = SudokuBoard(cells = bestCells, isGiven = isGiven)
        val validation = SudokuValidator.findConflicts(board)

        val message = if (validation.isValid) {
            "Successfully scanned $detectedCount clues from photo."
        } else {
            "Scanned $detectedCount clues (${validation.conflictedIndices.size} conflicting). Tap highlighted cells to correct."
        }

        return OcrResult(
            board = board,
            detectedCount = detectedCount,
            gridBounds = bestGridRect,
            message = message
        )
    }

    /**
     * Filters out page numbers, book headers, and isolated background numbers.
     */
    private fun filterOutliers(candidates: List<DigitDetection>): List<DigitDetection> {
        if (candidates.size <= 6) return candidates

        // Sort by Y and identify median vertical gap between adjacent digits
        val sortedY = candidates.sortedBy { it.centerY }
        val gapsY = mutableListOf<Float>()
        for (i in 0 until sortedY.size - 1) {
            val gap = sortedY[i + 1].centerY - sortedY[i].centerY
            if (gap > 2f) gapsY.add(gap)
        }

        if (gapsY.isEmpty()) return candidates
        gapsY.sort()
        val medianGapY = gapsY[gapsY.size / 2]

        // If an extreme digit has a gap > 3.5 * medianGapY to its neighbor, it's a header/footer
        val threshold = max(medianGapY * 3.5f, 80f)

        var startIdx = 0
        while (startIdx < sortedY.size - 1 && (sortedY[startIdx + 1].centerY - sortedY[startIdx].centerY) > threshold) {
            startIdx++
        }

        var endIdx = sortedY.size - 1
        while (endIdx > startIdx && (sortedY[endIdx].centerY - sortedY[endIdx - 1].centerY) > threshold) {
            endIdx--
        }

        return sortedY.subList(startIdx, endIdx + 1)
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
