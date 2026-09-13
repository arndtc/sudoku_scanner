package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        val isExactDigit: Boolean = true,
        val confidence: Float? = null
    )

    data class ScanDiagnosticsInfo(
        val passName: String = "Standard",
        val visualGridDetected: Boolean = false,
        val rawCandidates: List<com.example.diagnostics.DigitCandidateTelemetry> = emptyList(),
        val gridLeft: Float = 0f,
        val gridTop: Float = 0f,
        val cellWidth: Float = 0f,
        val cellHeight: Float = 0f
    )

    data class OcrResult(
        val board: SudokuBoard,
        val detectedCount: Int,
        val gridBounds: Rect? = null,
        val message: String,
        val diagnostics: ScanDiagnosticsInfo? = null
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

            // Step 1.5: Targeted empty cell recovery on Pass 1 (recovers missed digits like '1' and '2')
            if (bestResult.detectedCount in 4..35 && (bestResult.diagnostics?.cellWidth ?: 0f) > 0f) {
                bestResult = recoverEmptyCells(recognizer, bitmap, bestResult)
            }

            // Pass 2: If clues found are less than typical minimum (< 28), try with high-contrast enhancement
            if (bestResult.detectedCount < 28) {
                Log.d(TAG, "[OCR] Clues detected (${bestResult.detectedCount} < 28). Attempting high-contrast pass...")
                val contrastBitmap = ImageUtils.enhanceContrast(bitmap)
                var contrastResult = scanSingleBitmap(recognizer, contrastBitmap, passName = "High-Contrast")
                if (contrastResult.detectedCount in 4..35 && (contrastResult.diagnostics?.cellWidth ?: 0f) > 0f) {
                    contrastResult = recoverEmptyCells(recognizer, contrastBitmap, contrastResult)
                }
                val merged = mergeOcrResults(bestResult, contrastResult)
                if (merged.detectedCount > bestResult.detectedCount) {
                    Log.d(TAG, "[OCR] High-contrast pass merged new clues: ${bestResult.detectedCount} -> ${merged.detectedCount}")
                    bestResult = merged
                }
            }

            // Pass 3: If still low (< 14 clues), try standard 90, 180, 270 degree rotations
            if (bestResult.detectedCount < 14) {
                Log.d(TAG, "[OCR] Low clue count (${bestResult.detectedCount} < 14). Probing 90/180/270 degree rotations...")
                for (angle in listOf(90f, 270f, 180f)) {
                    val rotated = ImageUtils.rotateBitmap(bitmap, angle)
                    var rotResult = scanSingleBitmap(recognizer, rotated, passName = "Rotated-$angle")
                    if (rotResult.detectedCount in 4..35 && (rotResult.diagnostics?.cellWidth ?: 0f) > 0f) {
                        rotResult = recoverEmptyCells(recognizer, rotated, rotResult)
                    }
                    val merged = mergeOcrResults(bestResult, rotResult)
                    if (merged.detectedCount > bestResult.detectedCount) {
                        Log.d(TAG, "[OCR] Rotation $angle deg improved detection: ${bestResult.detectedCount} -> ${merged.detectedCount}")
                        bestResult = merged
                        if (bestResult.detectedCount >= 20) break
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

    fun fitSudokuGrid(
        rawCandidates: List<DigitDetection>,
        imageWidth: Int,
        imageHeight: Int,
        visualGrid: SudokuGridDetector.GridGeometry? = null,
        passName: String = "Standard"
    ): OcrResult {
        val telemetryList = rawCandidates.map {
            com.example.diagnostics.DigitCandidateTelemetry(
                digit = it.digit,
                originalChar = it.originalChar,
                centerX = it.centerX,
                centerY = it.centerY,
                boxLeft = it.boundingBox.left,
                boxTop = it.boundingBox.top,
                boxRight = it.boundingBox.right,
                boxBottom = it.boundingBox.bottom,
                confidence = it.confidence,
                isExactDigit = it.isExactDigit
            )
        }
        val diagnostics = ScanDiagnosticsInfo(
            passName = passName,
            visualGridDetected = visualGrid != null,
            rawCandidates = telemetryList,
            gridLeft = visualGrid?.left ?: 0f,
            gridTop = visualGrid?.top ?: 0f,
            cellWidth = visualGrid?.cellW ?: 0f,
            cellHeight = visualGrid?.cellH ?: 0f
        )

        if (rawCandidates.size < 4 && visualGrid == null) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "Not enough digits detected to form a Sudoku grid.",
                diagnostics = diagnostics
            )
        }

        val filteredCandidates = filterCandidatesByGeometryAndOutliers(rawCandidates)
        val candidatesToUse = if (filteredCandidates.size >= 4) filteredCandidates else rawCandidates
        Log.d(TAG, "[OCR] Candidates to fit: ${candidatesToUse.size} (raw: ${rawCandidates.size})")

        val fitResult = SudokuGridDetector.fitDigitsToGrid(
            candidates = candidatesToUse,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            visualGrid = visualGrid
        )

        val bestCells = MutableList(81) { 0 }
        for ((idx, clue) in fitResult.placedClues) {
            bestCells[idx] = clue.digit
        }

        val detectedCount = fitResult.placedClues.size
        val bestGridRect = fitResult.geometry.toRect()
        val fittedDiagnostics = diagnostics.copy(
            gridLeft = fitResult.geometry.left,
            gridTop = fitResult.geometry.top,
            cellWidth = fitResult.geometry.cellW,
            cellHeight = fitResult.geometry.cellH
        )

        if (detectedCount < 4) {
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                gridBounds = bestGridRect,
                message = "Grid alignment found fewer than 4 clues. Try framing closer to the puzzle.",
                diagnostics = fittedDiagnostics
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

        Log.d(TAG, "[OCR] Final placed clues: $detectedCount, valid=${validation.isValid}, gridRect=${bestGridRect.toShortString()}")

        return OcrResult(
            board = board,
            detectedCount = detectedCount,
            gridBounds = bestGridRect,
            message = message,
            diagnostics = fittedDiagnostics
        )
    }

    private fun scanSingleBitmap(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
        passName: String
    ): OcrResult {
        val visualGrid = SudokuGridDetector.detectVisualGridLines(bitmap)

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(inputImage))

        Log.d(TAG, "[OCR][$passName] ML Kit extracted ${visionText.textBlocks.size} text blocks.")

        val candidateDigits = extractCandidateDigits(visionText)
        Log.d(TAG, "[OCR][$passName] Raw candidate digits extracted: ${candidateDigits.size}")

        if (candidateDigits.size < 4 && visualGrid == null) {
            val telemetryList = candidateDigits.map {
                com.example.diagnostics.DigitCandidateTelemetry(
                    digit = it.digit,
                    originalChar = it.originalChar,
                    centerX = it.centerX,
                    centerY = it.centerY,
                    boxLeft = it.boundingBox.left,
                    boxTop = it.boundingBox.top,
                    boxRight = it.boundingBox.right,
                    boxBottom = it.boundingBox.bottom
                )
            }
            return OcrResult(
                board = SudokuBoard.EMPTY,
                detectedCount = 0,
                message = "No Sudoku digits found. Please ensure the puzzle is centered and in focus.",
                diagnostics = ScanDiagnosticsInfo(
                    passName = passName,
                    visualGridDetected = false,
                    rawCandidates = telemetryList
                )
            )
        }

        val rawResult = fitSudokuGrid(candidateDigits, bitmap.width, bitmap.height, visualGrid, passName = passName)
        return pruneEmptyCellFalsePositives(bitmap, rawResult)
    }

    /**
     * Prunes phantom digits placed into cells that contain no ink (e.g. blank paper / white space).
     * Guards against false positives from stray edge shadows, paper artifacts, or misrecognized text.
     */
    fun pruneEmptyCellFalsePositives(bitmap: Bitmap, ocrResult: OcrResult): OcrResult {
        val diag = ocrResult.diagnostics ?: return ocrResult
        if (diag.cellWidth <= 15f || diag.cellHeight <= 15f || ocrResult.detectedCount <= 0) {
            return ocrResult
        }

        val updatedCells = ocrResult.board.cells.toMutableList()
        var pruned = 0

        for (row in 0..8) {
            for (col in 0..8) {
                val idx = row * 9 + col
                val digit = updatedCells[idx]
                if (digit == 0) continue

                val cellL = diag.gridLeft + col * diag.cellWidth
                val cellT = diag.gridTop + row * diag.cellHeight
                val cellR = cellL + diag.cellWidth
                val cellB = cellT + diag.cellHeight

                if (cellL >= 0f && cellT >= 0f && cellR <= bitmap.width && cellB <= bitmap.height) {
                    val insetX = diag.cellWidth * 0.18f
                    val insetY = diag.cellHeight * 0.18f
                    val innerL = (cellL + insetX).toInt().coerceIn(0, bitmap.width - 1)
                    val innerT = (cellT + insetY).toInt().coerceIn(0, bitmap.height - 1)
                    val innerR = (cellR - insetX).toInt().coerceIn(innerL + 1, bitmap.width)
                    val innerB = (cellB - insetY).toInt().coerceIn(innerT + 1, bitmap.height)

                    if (!hasCellInk(bitmap, innerL, innerT, innerR, innerB)) {
                        Log.d(TAG, "[Prune] Cell R${row + 1}C${col + 1} has no ink. Pruned phantom clue $digit.")
                        updatedCells[idx] = 0
                        pruned++
                    }
                }
            }
        }

        if (pruned == 0) return ocrResult

        val isGiven = updatedCells.map { it != 0 }
        val newBoard = SudokuBoard(cells = updatedCells, isGiven = isGiven)
        val validation = SudokuValidator.findConflicts(newBoard)
        val newCount = ocrResult.detectedCount - pruned
        val newMsg = if (validation.isValid) {
            "Successfully scanned $newCount clues from photo."
        } else {
            "Scanned $newCount clues (${validation.conflictedIndices.size} conflicting). Tap highlighted cells to correct."
        }

        return ocrResult.copy(
            board = newBoard,
            detectedCount = newCount,
            message = newMsg
        )
    }

    fun extractCandidateDigits(visionText: Text): List<DigitDetection> {
        val candidates = mutableListOf<DigitDetection>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val conf = element.confidence
                    val text = element.text.trim()
                    if (text.isEmpty()) continue

                    // Check if element has exactly one clean genuine digit 1..9 (with possible border noise/punctuation)
                    val exactDigits = text.filter { it in '1'..'9' }
                    if (exactDigits.length == 1) {
                        val digitChar = exactDigits[0]
                        val digitVal = digitChar.digitToInt()

                        // If element symbols are present, locate the exact symbol bounding box
                        val matchingSymbol = element.symbols.firstOrNull { it.text.contains(digitChar) }
                        val targetBox = matchingSymbol?.boundingBox ?: box

                        if (isValidDigitGeometry(targetBox, digitVal, isExactDigit = true)) {
                            candidates.add(
                                DigitDetection(
                                    digit = digitVal,
                                    centerX = targetBox.exactCenterX(),
                                    centerY = targetBox.exactCenterY(),
                                    boundingBox = targetBox,
                                    originalChar = digitChar,
                                    isExactDigit = true,
                                    confidence = conf
                                )
                            )
                        }
                    } else if (exactDigits.length > 1) {
                        // Multi-digit token: process each digit individually with segmented horizontal bounds
                        val chars = text.toCharArray()
                        val charWidth = box.width().toFloat() / max(1, chars.size)
                        for ((i, ch) in chars.withIndex()) {
                            if (ch !in '1'..'9') continue
                            val digit = ch.digitToInt()
                            val cx = box.left + (i + 0.5f) * charWidth
                            val cy = box.exactCenterY()
                            val sBox = Rect(
                                (box.left + i * charWidth).toInt(),
                                box.top,
                                (box.left + (i + 1) * charWidth).toInt(),
                                box.bottom
                            )
                            if (isValidDigitGeometry(sBox, digit, isExactDigit = true)) {
                                candidates.add(
                                    DigitDetection(
                                        digit = digit,
                                        centerX = cx,
                                        centerY = cy,
                                        boundingBox = sBox,
                                        originalChar = ch,
                                        isExactDigit = true,
                                        confidence = conf
                                    )
                                )
                            }
                        }
                    } else if (text.length == 1) {
                        // Single non-digit character (e.g. OCR misrecognized 'S' for '5' or 'Z' for '2')
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
                                        isExactDigit = isExact,
                                        confidence = conf
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
            'B' -> Pair(8, false)
            else -> null
        }
    }

    /**
     * Inspects cells that are currently blank in the board.
     * When a grid geometry is locked, cells without detected clues are evaluated for the presence
     * of dark ink (contrasting printed characters). For any cell exhibiting ink, targeted high-contrast
     * isolated cell OCR is executed with border margin exclusion, recovering digits (such as thin '1's or '2's)
     * that full-image scene text recognition frequently misses against grid lines.
     */
    fun recoverEmptyCells(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
        currentResult: OcrResult
    ): OcrResult {
        val diag = currentResult.diagnostics ?: return currentResult
        if (diag.cellWidth <= 0f || diag.cellHeight <= 0f) return currentResult

        val gridLeft = diag.gridLeft
        val gridTop = diag.gridTop
        val cellW = diag.cellWidth
        val cellH = diag.cellHeight

        val currentBoard = currentResult.board
        val updatedCells = currentBoard.cells.toMutableList()
        val updatedCandidates = diag.rawCandidates.toMutableList()
        var recoveredCount = 0

        for (row in 0..8) {
            for (col in 0..8) {
                val cellIdx = row * 9 + col
                if (updatedCells[cellIdx] != 0) continue

                val cellL = gridLeft + col * cellW
                val cellT = gridTop + row * cellH
                val cellR = cellL + cellW
                val cellB = cellT + cellH

                // Check for valid bounds inside bitmap
                if (cellL < 0f || cellT < 0f || cellR > bitmap.width || cellB > bitmap.height) continue

                // Inset by 18% to completely avoid borders and grid lines
                val insetX = cellW * 0.18f
                val insetY = cellH * 0.18f
                val innerL = (cellL + insetX).toInt().coerceIn(0, bitmap.width - 1)
                val innerT = (cellT + insetY).toInt().coerceIn(0, bitmap.height - 1)
                val innerR = (cellR - insetX).toInt().coerceIn(innerL + 1, bitmap.width)
                val innerB = (cellB - insetY).toInt().coerceIn(innerT + 1, bitmap.height)

                if (!hasCellInk(bitmap, innerL, innerT, innerR, innerB)) {
                    continue
                }

                Log.d(TAG, "[CellRecovery] Cell R${row + 1}C${col + 1} has ink. Running targeted cell OCR...")

                // Inset slightly less (10%) for OCR so full glyph with serifs is included
                val cropL = (cellL + cellW * 0.10f).toInt().coerceIn(0, bitmap.width - 1)
                val cropT = (cellT + cellH * 0.10f).toInt().coerceIn(0, bitmap.height - 1)
                val cropR = (cellR - cellW * 0.10f).toInt().coerceIn(cropL + 1, bitmap.width)
                val cropB = (cellB - cellH * 0.10f).toInt().coerceIn(cropT + 1, bitmap.height)
                val cropW = cropR - cropL
                val cropH = cropB - cropT

                if (cropW < 12 || cropH < 12) continue

                val cellBitmap = Bitmap.createBitmap(bitmap, cropL, cropT, cropW, cropH)
                val preparedBitmap = prepareCellForOcr(cellBitmap)

                try {
                    val inputImage = InputImage.fromBitmap(preparedBitmap, 0)
                    val visionText = Tasks.await(recognizer.process(inputImage))
                    val detectedDigit = extractSingleCellDigit(visionText)

                    if (detectedDigit != null) {
                        val (digit, char, conf) = detectedDigit
                        // Validate against Sudoku row, column, and 3x3 box rules
                        if (canPlaceDigit(updatedCells, row, col, digit)) {
                            updatedCells[cellIdx] = digit
                            recoveredCount++
                            Log.d(TAG, "[CellRecovery] Successfully recovered digit $digit ('$char') at R${row + 1}C${col + 1} (conf: $conf)")

                            updatedCandidates.add(
                                com.example.diagnostics.DigitCandidateTelemetry(
                                    digit = digit,
                                    originalChar = char,
                                    centerX = (cellL + cellR) / 2f,
                                    centerY = (cellT + cellB) / 2f,
                                    boxLeft = cropL,
                                    boxTop = cropT,
                                    boxRight = cropR,
                                    boxBottom = cropB,
                                    confidence = conf,
                                    isExactDigit = true
                                )
                            )
                        } else {
                            Log.w(TAG, "[CellRecovery] Digit $digit at R${row + 1}C${col + 1} conflicts with existing clues; discarded.")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[CellRecovery] Targeted OCR error on cell R${row + 1}C${col + 1}: ${e.message}")
                }
            }
        }

        if (recoveredCount == 0) return currentResult

        val isGiven = updatedCells.map { it != 0 }
        val newBoard = SudokuBoard(cells = updatedCells, isGiven = isGiven)
        val validation = SudokuValidator.findConflicts(newBoard)
        val totalDetected = currentResult.detectedCount + recoveredCount
        val newMsg = if (validation.isValid) {
            "Successfully scanned $totalDetected clues ($recoveredCount recovered via targeted cell pass)."
        } else {
            "Scanned $totalDetected clues (${validation.conflictedIndices.size} conflicting). Tap highlighted cells to correct."
        }

        return currentResult.copy(
            board = newBoard,
            detectedCount = totalDetected,
            message = newMsg,
            diagnostics = diag.copy(rawCandidates = updatedCandidates)
        )
    }

    /**
     * Prepares a raw cell crop for optimal ML Kit isolated character detection:
     * - Upscales to a standard 140x140 resolution.
     * - Centers the character with a clean white margin.
     * - Enhances contrast by mapping the cell background to pure white and the ink stroke to black.
     */
    fun prepareCellForOcr(src: Bitmap): Bitmap {
        val targetDim = 140
        val out = Bitmap.createBitmap(targetDim, targetDim, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val canvas = Canvas(out)

        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        var minLum = 255
        var maxLum = 0
        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val range = (maxLum - minLum).coerceAtLeast(1)
        val enhancedPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            val norm = (lum - minLum).toFloat() / range
            val boosted = when {
                norm < 0.35f -> (norm / 0.35f) * 0.15f
                norm > 0.70f -> 1.0f
                else -> 0.15f + ((norm - 0.35f) / 0.35f) * 0.85f
            }
            val normLum = (boosted * 255f).roundToInt().coerceIn(0, 255)
            enhancedPixels[i] = Color.rgb(normLum, normLum, normLum)
        }

        val enhancedCrop = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        enhancedCrop.setPixels(enhancedPixels, 0, src.width, 0, 0, src.width, src.height)

        val maxInner = 95f
        val scale = min(maxInner / src.width, maxInner / src.height)
        val drawW = (src.width * scale).roundToInt()
        val drawH = (src.height * scale).roundToInt()
        val destRect = android.graphics.Rect(
            (targetDim - drawW) / 2,
            (targetDim - drawH) / 2,
            (targetDim + drawW) / 2,
            (targetDim + drawH) / 2
        )

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(enhancedCrop, null, destRect, paint)

        return out
    }

    /**
     * Determines whether the interior region of a cell contains contrasting printed ink
     * (as opposed to blank paper or uniform background).
     */
    fun hasCellInk(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val w = right - left
        val h = bottom - top
        if (w < 10 || h < 10) return false

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)

        var minLum = 255
        var maxLum = 0
        val lumValues = IntArray(w * h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            lumValues[i] = lum
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val contrast = maxLum - minLum
        if (contrast < 28) return false

        val inkThreshold = (maxLum - contrast * 0.32f).toInt()
        var inkPixels = 0
        for (lum in lumValues) {
            if (lum <= inkThreshold) {
                inkPixels++
            }
        }

        val total = w * h
        val inkRatio = inkPixels.toFloat() / total
        return inkPixels >= 12 && inkRatio in 0.005f..0.50f
    }

    data class CellDigitResult(val digit: Int, val char: Char, val confidence: Float?)

    /**
     * Extracts a digit candidate from an isolated, single-cell OCR pass.
     * In an isolated cell, vertical characters ('|', 'l', 'I', '!') are reliably digit 1,
     * while 'Z'/'z' is digit 2, 'S'/'s' is 5, etc.
     */
    fun extractSingleCellDigit(visionText: Text): CellDigitResult? {
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val rawText = element.text.trim()
                    val conf = element.confidence

                    // 1. Direct digit '1'..'9'
                    val exactDigits = rawText.filter { it in '1'..'9' }
                    if (exactDigits.length == 1) {
                        return CellDigitResult(exactDigits[0].digitToInt(), exactDigits[0], conf)
                    }

                    // 2. Single glyph parsing in isolated cell crops (stripping outer punctuation)
                    val cleaned = rawText.trim { it in " \t\r\n.,'\"-_~:`*^" }
                    if (cleaned.length == 1) {
                        val ch = cleaned[0]
                        when (ch) {
                            'l', 'I', '|', '!', 'i', 'j', '/', '\\', '(', ')', '[', ']' -> return CellDigitResult(1, '1', conf)
                            'Z', 'z' -> return CellDigitResult(2, '2', conf)
                            'S', 's' -> return CellDigitResult(5, '5', conf)
                            'G', 'b' -> return CellDigitResult(6, '6', conf)
                            'B' -> return CellDigitResult(8, '8', conf)
                            'q' -> return CellDigitResult(9, '9', conf)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Checks if placing a candidate digit into cell (row, col) respects all Sudoku constraints.
     */
    fun canPlaceDigit(cells: List<Int>, row: Int, col: Int, digit: Int): Boolean {
        if (digit !in 1..9) return false
        // Row check
        for (c in 0..8) {
            if (cells[row * 9 + c] == digit) return false
        }
        // Col check
        for (r in 0..8) {
            if (cells[r * 9 + col] == digit) return false
        }
        // 3x3 Box check
        val boxR = (row / 3) * 3
        val boxC = (col / 3) * 3
        for (r in 0..2) {
            for (c in 0..2) {
                if (cells[(boxR + r) * 9 + (boxC + c)] == digit) return false
            }
        }
        return true
    }

    /**
     * Merges non-conflicting clues from a secondary OCR pass into the base result.
     */
    fun mergeOcrResults(base: OcrResult, secondary: OcrResult): OcrResult {
        if (secondary.detectedCount == 0) return base
        if (base.detectedCount == 0) return secondary

        val baseCells = base.board.cells.toMutableList()
        val secondaryCells = secondary.board.cells
        var added = 0

        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            if (baseCells[i] == 0 && secondaryCells[i] != 0) {
                val candidateDigit = secondaryCells[i]
                if (canPlaceDigit(baseCells, r, c, candidateDigit)) {
                    baseCells[i] = candidateDigit
                    added++
                }
            }
        }

        if (added == 0) return base

        val isGiven = baseCells.map { it != 0 }
        val newBoard = SudokuBoard(cells = baseCells, isGiven = isGiven)
        val totalDetected = base.detectedCount + added
        val validation = SudokuValidator.findConflicts(newBoard)
        val msg = if (validation.isValid) {
            "Successfully scanned $totalDetected clues ($added merged from enhanced pass)."
        } else {
            "Scanned $totalDetected clues (${validation.conflictedIndices.size} conflicting). Tap highlighted cells to correct."
        }
        return base.copy(
            board = newBoard,
            detectedCount = totalDetected,
            message = msg
        )
    }
}
