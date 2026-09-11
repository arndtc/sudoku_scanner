package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
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

    private const val TAG = "SudokuOcrEngine"

    data class DigitDetection(
        val digit: Int,
        val centerX: Float,
        val centerY: Float,
        val boundingBox: Rect,
        val originalChar: Char = '0',
        val isExactDigit: Boolean = true
    )

    data class OcrResult(
        val board: SudokuBoard,
        val detectedCount: Int,
        val gridBounds: Rect? = null,
        val message: String
    )

    private data class PlacedClue(
        val digit: Int,
        val detection: DigitDetection,
        val score: Float,
        val row: Int,
        val col: Int
    )

    suspend fun recognizeSudoku(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            Log.d(TAG, "[OCR] ==================================================")
            Log.d(TAG, "[OCR] Starting Sudoku recognition on bitmap: ${bitmap.width}x${bitmap.height}")

            // Pass 1: Try on the standard scaled bitmap
            var bestResult = scanSingleBitmap(recognizer, bitmap, passName = "Standard")

            // Pass 2: If few clues found (< 16), try with high-contrast enhancement
            if (bestResult.detectedCount < 16) {
                Log.d(TAG, "[OCR] Few clues detected (${bestResult.detectedCount} < 16). Attempting high-contrast pass...")
                val contrastBitmap = ImageUtils.enhanceContrast(bitmap)
                val contrastResult = scanSingleBitmap(recognizer, contrastBitmap, passName = "High-Contrast")
                if (contrastResult.detectedCount > bestResult.detectedCount) {
                    Log.d(TAG, "[OCR] High-contrast pass improved detection: ${bestResult.detectedCount} -> ${contrastResult.detectedCount}")
                    bestResult = contrastResult
                }
            }

            // Pass 3: If still low (< 12 clues), try standard 90, 180, 270 degree rotations
            if (bestResult.detectedCount < 12) {
                Log.d(TAG, "[OCR] Low clue count (${bestResult.detectedCount} < 12). Probing 90/180/270 degree rotations...")
                for (angle in listOf(90f, 270f, 180f)) {
                    val rotated = ImageUtils.rotateBitmap(bitmap, angle)
                    val rotResult = scanSingleBitmap(recognizer, rotated, passName = "Rotated-$angle")
                    if (rotResult.detectedCount > bestResult.detectedCount) {
                        Log.d(TAG, "[OCR] Rotation $angle deg improved detection: ${bestResult.detectedCount} -> ${rotResult.detectedCount}")
                        bestResult = rotResult
                        if (bestResult.detectedCount >= 17) break
                    }
                }
            }

            Log.d(TAG, "[OCR] Recognition finished. Final detected clues: ${bestResult.detectedCount}. Message: '${bestResult.message}'")
            bestResult
        } catch (e: Exception) {
            Log.e(TAG, "[OCR] Unexpected error during OCR recognition", e)
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

    /**
     * Automatically detects the bounding box of a Sudoku puzzle within an image.
     * Uses ML Kit text detection to cluster candidate digits and isolate the puzzle region
     * with comfortable padding for the grid lines.
     * Returns a normalized RectF with values in [0.0, 1.0].
     */
    suspend fun autoDetectSudokuBoundingBox(bitmap: Bitmap): RectF = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(inputImage))
            val candidateDigits = extractCandidateDigits(visionText)

            Log.d(TAG, "[AutoCrop] ML Kit found ${candidateDigits.size} candidate digits in image.")

            if (candidateDigits.size >= 4) {
                // In Sudoku, puzzle digits form a dense 2D cluster compared to isolated page numbers
                val clusterRadius = min(bitmap.width, bitmap.height) * 0.45f

                val scored = candidateDigits.map { c ->
                    val neighbors = candidateDigits.count { other ->
                        other !== c && Math.hypot(
                            (other.centerX - c.centerX).toDouble(),
                            (other.centerY - c.centerY).toDouble()
                        ) <= clusterRadius
                    }
                    c to neighbors
                }

                val maxNeighbors = scored.maxOfOrNull { it.second } ?: 0
                val puzzleCluster = if (maxNeighbors >= 3) {
                    val core = scored.maxByOrNull { it.second }!!.first
                    candidateDigits.filter { c ->
                        Math.hypot(
                            (c.centerX - core.centerX).toDouble(),
                            (c.centerY - core.centerY).toDouble()
                        ) <= clusterRadius * 1.15f
                    }
                } else {
                    candidateDigits
                }

                if (puzzleCluster.size >= 4) {
                    val minX = puzzleCluster.minOf { it.boundingBox.left }.toFloat()
                    val maxX = puzzleCluster.maxOf { it.boundingBox.right }.toFloat()
                    val minY = puzzleCluster.minOf { it.boundingBox.top }.toFloat()
                    val maxY = puzzleCluster.maxOf { it.boundingBox.bottom }.toFloat()

                    val clusterW = maxX - minX
                    val clusterH = maxY - minY
                    val baseSide = max(clusterW, clusterH)

                    // Expand 20% margin for borders and grid lines
                    val paddedSide = baseSide * 1.25f

                    val cx = (minX + maxX) / 2f
                    val cy = (minY + maxY) / 2f

                    var left = cx - paddedSide / 2f
                    var right = cx + paddedSide / 2f
                    var top = cy - paddedSide / 2f
                    var bottom = cy + paddedSide / 2f

                    if (left < 0f) {
                        right = min(bitmap.width.toFloat(), right - left)
                        left = 0f
                    }
                    if (right > bitmap.width.toFloat()) {
                        val overflow = right - bitmap.width.toFloat()
                        left = max(0f, left - overflow)
                        right = bitmap.width.toFloat()
                    }
                    if (top < 0f) {
                        bottom = min(bitmap.height.toFloat(), bottom - top)
                        top = 0f
                    }
                    if (bottom > bitmap.height.toFloat()) {
                        val overflow = bottom - bitmap.height.toFloat()
                        top = max(0f, top - overflow)
                        bottom = bitmap.height.toFloat()
                    }

                    val normLeft = (left / bitmap.width).coerceIn(0f, 0.85f)
                    val normTop = (top / bitmap.height).coerceIn(0f, 0.85f)
                    val normRight = (right / bitmap.width).coerceIn(normLeft + 0.15f, 1f)
                    val normBottom = (bottom / bitmap.height).coerceIn(normTop + 0.15f, 1f)

                    Log.d(TAG, "[AutoCrop] Clustered ${puzzleCluster.size} digits into crop box: [$normLeft, $normTop, $normRight, $normBottom]")
                    return@withContext RectF(normLeft, normTop, normRight, normBottom)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[AutoCrop] Error auto-detecting bounds: ${e.message}")
        } finally {
            recognizer.close()
        }

        // Fallback: Centered 85% square
        val minDim = min(bitmap.width, bitmap.height).toFloat()
        val side = minDim * 0.85f
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val left = ((cx - side / 2f) / bitmap.width).coerceIn(0f, 1f)
        val top = ((cy - side / 2f) / bitmap.height).coerceIn(0f, 1f)
        val right = ((cx + side / 2f) / bitmap.width).coerceIn(left + 0.1f, 1f)
        val bottom = ((cy + side / 2f) / bitmap.height).coerceIn(top + 0.1f, 1f)

        Log.d(TAG, "[AutoCrop] Using fallback centered square: [$left, $top, $right, $bottom]")
        RectF(left, top, right, bottom)
    }

    private fun scanSingleBitmap(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
        passName: String
    ): OcrResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(inputImage))

        Log.d(TAG, "[OCR][$passName] ML Kit extracted ${visionText.textBlocks.size} text blocks.")

        val candidateDigits = extractCandidateDigits(visionText)
        Log.d(TAG, "[OCR][$passName] Raw candidate digits extracted: ${candidateDigits.size}")

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
                            val parsed = parseSingleCharToDigit(ch) ?: continue
                            val (digit, isExact) = parsed
                            val sBox = symbol.boundingBox ?: box

                            if (isValidDigitGeometry(sBox, digit, isExact)) {
                                candidates.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = sBox.exactCenterX(),
                                        centerY = sBox.exactCenterY(),
                                        boundingBox = sBox,
                                        originalChar = ch,
                                        isExactDigit = isExact
                                    )
                                )
                            }
                        }
                    } else if (text.length == 1) {
                        val parsed = parseSingleCharToDigit(text[0])
                        if (parsed != null) {
                            val (digit, isExact) = parsed
                            if (isValidDigitGeometry(box, digit, isExact)) {
                                candidates.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = box.exactCenterX(),
                                        centerY = box.exactCenterY(),
                                        boundingBox = box,
                                        originalChar = text[0],
                                        isExactDigit = isExact
                                    )
                                )
                            }
                        }
                    } else {
                        // Multi-char word: only check single isolated digits with clean bounds
                        val chars = text.toCharArray()
                        val charWidth = box.width().toFloat() / max(1, chars.size)
                        for ((i, ch) in chars.withIndex()) {
                            val parsed = parseSingleCharToDigit(ch) ?: continue
                            val (digit, isExact) = parsed
                            val cx = box.left + (i + 0.5f) * charWidth
                            val cy = box.exactCenterY()
                            val sBox = Rect(
                                (box.left + i * charWidth).toInt(),
                                box.top,
                                (box.left + (i + 1) * charWidth).toInt(),
                                box.bottom
                            )
                            if (isValidDigitGeometry(sBox, digit, isExact)) {
                                candidates.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = cx,
                                        centerY = cy,
                                        boundingBox = sBox,
                                        originalChar = ch,
                                        isExactDigit = isExact
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

    /**
     * Rejects extreme line artifacts while keeping all genuine digits.
     */
    private fun isValidDigitGeometry(box: Rect, digit: Int, isExactDigit: Boolean): Boolean {
        val w = box.width().toFloat()
        val h = box.height().toFloat()

        if (w < 2.5f || h < 4.5f) return false
        val aspectRatio = h / max(1f, w)

        if (isExactDigit) {
            // For recognized digits '1'..'9', only reject extreme vertical/horizontal lines
            if (aspectRatio > 6.0f || aspectRatio < 0.12f) return false
            return true
        } else {
            // Letters like 'l', 'I', 'S', etc.
            if (aspectRatio > 3.8f || aspectRatio < 0.28f || w < 4f) return false
            return true
        }
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

        val filteredCandidates = filterCandidatesByGeometryAndOutliers(rawCandidates)
        val candidatesToUse = if (filteredCandidates.size >= 4) filteredCandidates else rawCandidates
        Log.d(TAG, "[OCR] Candidates to fit: ${candidatesToUse.size} (raw: ${rawCandidates.size})")

        val heights = candidatesToUse.map { it.boundingBox.height().toFloat() }.sorted()
        val medianH = if (heights.isNotEmpty()) heights[heights.size / 2] else 20f

        var bestScore = -1f
        var bestCells = MutableList(81) { 0 }
        var bestGridRect: Rect? = null

        fun evaluateHypothesis(
            gridLeft: Float,
            gridTop: Float,
            gridRight: Float,
            gridBottom: Float,
            hypName: String
        ) {
            val gridW = gridRight - gridLeft
            val gridH = gridBottom - gridTop
            if (gridW <= 20f || gridH <= 20f) return
            val cellW = gridW / 9f
            val cellH = gridH / 9f

            val placedMap = mutableMapOf<Int, PlacedClue>()
            var conflictsCount = 0

            for (d in candidatesToUse) {
                val col = ((d.centerX - gridLeft) / cellW).toInt()
                val row = ((d.centerY - gridTop) / cellH).toInt()

                if (col in 0..8 && row in 0..8) {
                    val expectedCx = gridLeft + (col + 0.5f) * cellW
                    val expectedCy = gridTop + (row + 0.5f) * cellH
                    val dx = abs(d.centerX - expectedCx) / cellW
                    val dy = abs(d.centerY - expectedCy) / cellH
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                    // Tolerant cell proximity: Sudoku digits can be slightly off-center
                    if (dx > 0.46f || dy > 0.46f || dist > 0.62f) {
                        continue
                    }

                    val cellIndex = row * 9 + col
                    val centerScore = (1.0f - dist).coerceIn(0f, 1f) * 40f
                    val heightDev = abs(d.boundingBox.height() - medianH) / max(1f, medianH)
                    val sizeScore = (1.0f - heightDev).coerceIn(0f, 1f) * 30f
                    val charScore = if (d.isExactDigit) 30f else 10f
                    val candidateScore = centerScore + sizeScore + charScore

                    val candidateClue = PlacedClue(
                        digit = d.digit,
                        detection = d,
                        score = candidateScore,
                        row = row,
                        col = col
                    )

                    val existingConflicts = placedMap.values.filter { existing ->
                        existing.digit == d.digit && (
                            existing.row == row ||
                            existing.col == col ||
                            (existing.row / 3 == row / 3 && existing.col / 3 == col / 3)
                        )
                    }

                    if (existingConflicts.isEmpty()) {
                        val currentCell = placedMap[cellIndex]
                        if (currentCell == null || candidateScore > currentCell.score) {
                            placedMap[cellIndex] = candidateClue
                        }
                    } else {
                        conflictsCount++
                        val conflicting = existingConflicts.first()
                        if (candidateScore > conflicting.score + 10f) {
                            placedMap.remove(conflicting.row * 9 + conflicting.col)
                            placedMap[cellIndex] = candidateClue
                        }
                    }
                }
            }

            val score = placedMap.size * 15f - conflictsCount * 12f
            if (score > bestScore) {
                bestScore = score
                val resultCells = MutableList(81) { 0 }
                for ((idx, clue) in placedMap) {
                    resultCells[idx] = clue.digit
                }
                bestCells = resultCells
                bestGridRect = Rect(
                    max(0, gridLeft.roundToInt()),
                    max(0, gridTop.roundToInt()),
                    min(imageWidth, gridRight.roundToInt()),
                    min(imageHeight, gridBottom.roundToInt())
                )
            }
        }

        // --- Hypothesis Group 1: Image Boundary Grids ---
        for (margin in listOf(0.0f, 0.015f, 0.03f, 0.05f, 0.08f, 0.12f)) {
            val gLeft = imageWidth * margin
            val gTop = imageHeight * margin
            val gRight = imageWidth * (1f - margin)
            val gBottom = imageHeight * (1f - margin)
            evaluateHypothesis(gLeft, gTop, gRight, gBottom, "ImageMargin-$margin")
        }

        // --- Hypothesis Group 2: Digit Spanning Combinations ---
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (d in candidatesToUse) {
            minX = min(minX, d.centerX)
            minY = min(minY, d.centerY)
            maxX = max(maxX, d.centerX)
            maxY = max(maxY, d.centerY)
        }

        val spanX = maxX - minX
        val spanY = maxY - minY

        if (spanX > 15f && spanY > 15f) {
            for (kx in 4..8) {
                for (ky in 4..8) {
                    val cellW = spanX / kx
                    val cellH = spanY / ky
                    val aspect = cellW / cellH
                    if (aspect !in 0.65f..1.55f) continue

                    for (ox in 0..(8 - kx)) {
                        for (oy in 0..(8 - ky)) {
                            val gLeft = minX - (ox + 0.5f) * cellW
                            val gTop = minY - (oy + 0.5f) * cellH
                            val gRight = gLeft + 9f * cellW
                            val gBottom = gTop + 9f * cellH
                            evaluateHypothesis(gLeft, gTop, gRight, gBottom, "Span-$kx-$ky-$ox-$oy")
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
                message = "Grid alignment found fewer than 4 clues. Try framing closer to the puzzle."
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

        Log.d(TAG, "[OCR] Final placed clues: $detectedCount, valid=${validation.isValid}, gridRect=${bestGridRect?.toShortString()}")

        return OcrResult(
            board = board,
            detectedCount = detectedCount,
            gridBounds = bestGridRect,
            message = message
        )
    }

    /**
     * Filters out page numbers, book headers, and isolated background numbers,
     * as well as candidates whose font dimensions drastically deviate from the puzzle's median.
     */
    private fun filterCandidatesByGeometryAndOutliers(candidates: List<DigitDetection>): List<DigitDetection> {
        if (candidates.size <= 6) return candidates

        // 1. Median dimension filtering across the board
        val heights = candidates.map { it.boundingBox.height().toFloat() }.sorted()
        val medianH = heights[heights.size / 2]
        val widths = candidates.map { it.boundingBox.width().toFloat() }.sorted()
        val medianW = widths[widths.size / 2]

        val sizeFiltered = candidates.filter { d ->
            val h = d.boundingBox.height().toFloat()
            val w = d.boundingBox.width().toFloat()
            // Generous bounds so thin '1' digits and bold numbers are preserved
            val validHeight = h in (0.30f * medianH)..(2.5f * medianH)
            val validWidth = w in (0.08f * medianW)..(3.2f * medianW)
            validHeight && validWidth
        }

        if (sizeFiltered.size <= 6) return candidates

        // 2. Sort by Y and identify median vertical gap between adjacent digits (headers/footers)
        val sortedY = sizeFiltered.sortedBy { it.centerY }
        val gapsY = mutableListOf<Float>()
        for (i in 0 until sortedY.size - 1) {
            val gap = sortedY[i + 1].centerY - sortedY[i].centerY
            if (gap > 2f) gapsY.add(gap)
        }

        if (gapsY.isEmpty()) return sizeFiltered
        gapsY.sort()
        val medianGapY = gapsY[gapsY.size / 2]

        // If an extreme digit has a gap > 3.2 * medianGapY to its neighbor, it's a header/footer
        val threshold = max(medianGapY * 3.2f, 75f)

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

    /**
     * Parses a single character to a valid Sudoku digit.
     * Crucially excludes vertical pipe '|' and exclamation '!' which frequently cause
     * vertical grid borders to be misrecognized as an extra '1'.
     */
    private fun parseSingleCharToDigit(ch: Char): Pair<Int, Boolean>? {
        if (ch in '1'..'9') return Pair(ch.digitToInt(), true)
        return when (ch) {
            'l', 'I' -> Pair(1, false)
            'Z', 'z' -> Pair(2, false)
            'S', 's' -> Pair(5, false)
            'G', 'b' -> Pair(6, false)
            'B' -> Pair(8, false)
            'q' -> Pair(9, false)
            else -> null
        }
    }
}
