package com.example.diagnostics

import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import com.example.model.SudokuBoard
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents raw OCR candidate character telemetry from ML Kit before grid fitting,
 * including exact pixel bounding boxes and ML Kit model confidence scores.
 */
data class DigitCandidateTelemetry(
    val digit: Int,
    val originalChar: Char,
    val centerX: Float,
    val centerY: Float,
    val boxLeft: Int,
    val boxTop: Int,
    val boxRight: Int,
    val boxBottom: Int,
    val confidence: Float? = null,
    val isExactDigit: Boolean = true
) {
    val width: Int get() = (boxRight - boxLeft).coerceAtLeast(0)
    val height: Int get() = (boxBottom - boxTop).coerceAtLeast(0)
    val formattedConfidence: String
        get() = if (confidence != null) "%.2f (%.0f%%)".format(confidence, confidence * 100f) else "N/A"
}

/**
 * Type of difference between initial OCR scan and user's corrected board.
 */
enum class DifferenceType {
    MISSED_DIGIT,      // Scanned 0, corrected to 1..9 (Most common user pain point)
    INCORRECT_DIGIT,   // Scanned A, corrected to B (Recognition misclassification)
    FALSE_POSITIVE     // Scanned A, cleared to 0 (Noise artifact)
}

/**
 * A difference at a specific grid position between the initial scan and corrected puzzle.
 */
data class CellDifference(
    val index: Int,
    val row: Int,
    val col: Int,
    val scannedValue: Int,
    val correctedValue: Int,
    val type: DifferenceType
) {
    val coordinateLabel: String get() = "R${row + 1}C${col + 1}"
    val cellDescription: String get() = "Row ${row + 1}, Col ${col + 1} (Cell #$index)"
}

/**
 * Comprehensive diagnostic telemetry record for a single Sudoku scan session.
 */
data class ScanDiagnosticRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val cropRect: RectF? = null,
    val initialScannedBoard: SudokuBoard = SudokuBoard.EMPTY,
    val visualGridDetected: Boolean = false,
    val gridBounds: Rect? = null,
    val ocrPassUsed: String = "Standard",
    val rawCandidates: List<DigitCandidateTelemetry> = emptyList(),
    val placedCluesCount: Int = 0,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val osVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val imageUri: Uri? = null,
    val imageFilePath: String? = null,
    val gridLeft: Float = 0f,
    val gridTop: Float = 0f,
    val cellWidth: Float = 0f,
    val cellHeight: Float = 0f
) {
    /**
     * Finds candidates within or near a specific grid cell to inspect why it was missed.
     */
    fun findCandidatesNearCell(row: Int, col: Int, maxDistanceFactor: Float = 1.0f): List<Pair<DigitCandidateTelemetry, Float>> {
        val cellW = if (cellWidth > 0f) cellWidth else if (imageWidth > 0) imageWidth / 9f else 0f
        val cellH = if (cellHeight > 0f) cellHeight else if (imageHeight > 0) imageHeight / 9f else 0f
        if (cellW <= 0f || cellH <= 0f) return emptyList()

        val cellCenterX = gridLeft + (col + 0.5f) * cellW
        val cellCenterY = gridTop + (row + 0.5f) * cellH
        val maxDist = kotlin.math.max(cellW, cellH) * maxDistanceFactor

        return rawCandidates.mapNotNull { c ->
            val dx = c.centerX - cellCenterX
            val dy = c.centerY - cellCenterY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= maxDist) c to dist else null
        }.sortedBy { it.second }
    }

    /**
     * Produces a diagnostic explanation for why a specific cell was missed by ML Kit or grid fitting.
     */
    fun diagnoseMissedCell(row: Int, col: Int, expectedDigit: Int): String {
        val near = findCandidatesNearCell(row, col)
        val cellW = if (cellWidth > 0f) cellWidth else if (imageWidth > 0) imageWidth / 9f else 0f
        val cellH = if (cellHeight > 0f) cellHeight else if (imageHeight > 0) imageHeight / 9f else 0f
        val cellBoxLeft = (gridLeft + col * cellW).toInt()
        val cellBoxTop = (gridTop + row * cellH).toInt()
        val cellBoxRight = (cellBoxLeft + cellW).toInt()
        val cellBoxBottom = (cellBoxTop + cellH).toInt()

        if (near.isEmpty()) {
            return "No character detected by ML Kit near cell [L:$cellBoxLeft, T:$cellBoxTop, R:$cellBoxRight, B:$cellBoxBottom]. Optical pass returned 0 text elements for this cell."
        }

        val matching = near.firstOrNull { it.first.digit == expectedDigit }
        if (matching != null) {
            val (c, dist) = matching
            return "ML Kit detected expected digit '$expectedDigit' at (${c.centerX.toInt()}, ${c.centerY.toInt()}) with confidence=${c.formattedConfidence} (dist=${dist.toInt()}px from cell center), but it was discarded during grid geometric alignment or conflict filtering."
        }

        val closest = near.first()
        val (c, dist) = closest
        return "ML Kit detected candidate '${c.originalChar}' (digit ${c.digit}) instead of expected '$expectedDigit' at (${c.centerX.toInt()}, ${c.centerY.toInt()}) with confidence=${c.formattedConfidence}, box=[${c.boxLeft}, ${c.boxTop}, ${c.boxRight}, ${c.boxBottom}], dist=${dist.toInt()}px from cell center."
    }

    /**
     * Compares the original scanned board against the user's corrected board to detect missed clues.
     */
    fun computeDifferences(currentBoard: SudokuBoard): List<CellDifference> {
        val diffs = mutableListOf<CellDifference>()
        for (i in 0 until 81) {
            val scanned = initialScannedBoard[i]
            val corrected = currentBoard[i]
            if (scanned != corrected) {
                val row = i / 9
                val col = i % 9
                val type = when {
                    scanned == 0 && corrected in 1..9 -> DifferenceType.MISSED_DIGIT
                    scanned in 1..9 && corrected in 1..9 -> DifferenceType.INCORRECT_DIGIT
                    scanned in 1..9 && corrected == 0 -> DifferenceType.FALSE_POSITIVE
                    else -> DifferenceType.MISSED_DIGIT
                }
                diffs.add(
                    CellDifference(
                        index = i,
                        row = row,
                        col = col,
                        scannedValue = scanned,
                        correctedValue = corrected,
                        type = type
                    )
                )
            }
        }
        return diffs
    }

    /**
     * Builds a structured markdown report suitable for GitHub Issues or diagnostic sharing.
     * @param isForUrl When true, formats tables compactly to fit browser URL length limits without hitting HTTP 414 errors.
     */
    fun generateMarkdownReport(
        currentBoard: SudokuBoard,
        userNotes: String? = null,
        repoTarget: String = "arndtc/sudoku_scanner",
        isForUrl: Boolean = false
    ): String {
        val diffs = computeDifferences(currentBoard)
        val missed = diffs.filter { it.type == DifferenceType.MISSED_DIGIT }
        val incorrect = diffs.filter { it.type == DifferenceType.INCORRECT_DIGIT }
        val falsePositives = diffs.filter { it.type == DifferenceType.FALSE_POSITIVE }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))

        return buildString {
            appendLine("## 🧩 Sudoku OCR Scan Feedback & Diagnostics")
            appendLine()
            appendLine("**Repository:** `https://github.com/$repoTarget`")
            appendLine("**Date:** $dateStr")
            appendLine("**Device:** $deviceModel ($osVersion)")
            appendLine("**Image Size:** ${imageWidth}x${imageHeight} px")
            if (cropRect != null) {
                appendLine("**Crop Applied:** [${"%.2f".format(cropRect.left)}, ${"%.2f".format(cropRect.top)}, ${"%.2f".format(cropRect.right)}, ${"%.2f".format(cropRect.bottom)}]")
            }
            appendLine()

            if (!userNotes.isNullOrBlank()) {
                appendLine("### 📝 User Notes")
                appendLine("> $userNotes")
                appendLine()
            }

            appendLine("### 📊 Clue Recognition Summary")
            appendLine("- **Initially Scanned Clues:** ${initialScannedBoard.clueCount}")
            appendLine("- **Corrected Total Clues:** ${currentBoard.clueCount}")
            appendLine("- **Missed Numbers Detected:** ${missed.size}")
            if (incorrect.isNotEmpty()) {
                appendLine("- **Misclassified Numbers:** ${incorrect.size}")
            }
            if (falsePositives.isNotEmpty()) {
                appendLine("- **False Positives (Removed Noise):** ${falsePositives.size}")
            }
            appendLine()

            if (diffs.isNotEmpty()) {
                appendLine("### 🔍 Cell Differences (Scanned vs Corrected)")
                appendLine("| Coordinate | Position | Scanned | Corrected | Issue Type |")
                appendLine("| :---: | :--- | :---: | :---: | :--- |")
                val diffsToShow = if (isForUrl && diffs.size > 20) diffs.take(20) else diffs
                for (d in diffsToShow) {
                    val scannedStr = if (d.scannedValue == 0) "[Empty]" else d.scannedValue.toString()
                    val correctedStr = if (d.correctedValue == 0) "[Cleared]" else d.correctedValue.toString()
                    val typeLabel = when (d.type) {
                        DifferenceType.MISSED_DIGIT -> "**Missed Digit**"
                        DifferenceType.INCORRECT_DIGIT -> "Incorrect Value"
                        DifferenceType.FALSE_POSITIVE -> "False Positive"
                    }
                    appendLine("| `${d.coordinateLabel}` | Row ${d.row + 1}, Col ${d.col + 1} | $scannedStr | **$correctedStr** | $typeLabel |")
                }
                if (isForUrl && diffs.size > 20) {
                    appendLine("| ... | *(+${diffs.size - 20} more)* | ... | ... | ... |")
                }
                appendLine()

                if (missed.isNotEmpty() || incorrect.isNotEmpty()) {
                    appendLine("### 🔬 Missed Numbers Deep-Dive (ML Kit Telemetry & Confidence)")
                    appendLine("Cross-referenced with raw ML Kit model output to identify why numbers were missed:")
                    appendLine()
                    val deepDiveLimit = if (isForUrl) 6 else 12
                    for (d in (missed + incorrect).take(deepDiveLimit)) {
                        val cellW = if (cellWidth > 0f) cellWidth else if (imageWidth > 0) imageWidth / 9f else 0f
                        val cellH = if (cellHeight > 0f) cellHeight else if (imageHeight > 0) imageHeight / 9f else 0f
                        val cellL = (gridLeft + d.col * cellW).toInt()
                        val cellT = (gridTop + d.row * cellH).toInt()
                        val cellR = (cellL + cellW).toInt()
                        val cellB = (cellT + cellH).toInt()
                        val diag = diagnoseMissedCell(d.row, d.col, d.correctedValue)

                        appendLine("- **`${d.coordinateLabel}`** (${d.cellDescription}): Expected **${d.correctedValue}**, Scanned **${if (d.scannedValue == 0) "[Empty]" else d.scannedValue.toString()}**")
                        appendLine("  - *Cell Bounds:* `[L:$cellL, T:$cellT, R:$cellR, B:$cellB]`")
                        appendLine("  - *ML Kit Diagnosis:* $diag")
                        val near = findCandidatesNearCell(d.row, d.col)
                        if (near.isNotEmpty()) {
                            appendLine("  - *Nearby Detections:*")
                            for ((cand, dist) in near.take(if (isForUrl) 2 else 3)) {
                                appendLine("    - Char `'${cand.originalChar}'` (${cand.digit}) | Conf: **${cand.formattedConfidence}** | Box: `[${cand.boxLeft}, ${cand.boxTop}, ${cand.boxRight}, ${cand.boxBottom}]` | Dist: ${dist.toInt()}px")
                            }
                        }
                    }
                    if (missed.size + incorrect.size > deepDiveLimit) {
                        appendLine("- *(... and ${missed.size + incorrect.size - deepDiveLimit} more cells)*")
                    }
                    appendLine()
                }
            } else {
                appendLine("### ℹ️ Cell Differences")
                appendLine("*No manual corrections made yet on the grid.*")
                appendLine()
            }

            appendLine("### 🔢 Sudoku Data (SDM Format)")
            appendLine("- **Scanned SDM:**")
            appendLine("```")
            appendLine(initialScannedBoard.toSdmString())
            appendLine("```")
            appendLine("- **Corrected SDM:**")
            appendLine("```")
            appendLine(currentBoard.toSdmString())
            appendLine("```")
            appendLine()

            appendLine("### ⚙️ OCR & Grid Detection Pipeline")
            appendLine("- **Visual Grid Detected:** ${if (visualGridDetected) "Yes (Luminance Profile Locked)" else "No (Fitted from Digit Clusters)"}")
            if (gridBounds != null) {
                appendLine("- **Grid Bounds:** [left=${gridBounds.left}, top=${gridBounds.top}, right=${gridBounds.right}, bottom=${gridBounds.bottom}]")
            }
            if (cellWidth > 0f && cellHeight > 0f) {
                appendLine("- **Cell Dimensions:** %.1fx%.1f px (grid origin: [%.1f, %.1f])".format(cellWidth, cellHeight, gridLeft, gridTop))
            }
            appendLine("- **OCR Pass Used:** $ocrPassUsed")
            appendLine("- **Raw Text Candidates Found:** ${rawCandidates.size}")
            appendLine()

            if (rawCandidates.isNotEmpty()) {
                val candidatesWithConf = rawCandidates.mapNotNull { it.confidence }
                val avgConf = if (candidatesWithConf.isNotEmpty()) candidatesWithConf.average().toFloat() else null
                val highConfCount = candidatesWithConf.count { it >= 0.80f }
                val medConfCount = candidatesWithConf.count { it in 0.50f..<0.80f }
                val lowConfCount = candidatesWithConf.count { it < 0.50f }

                val maxCandidatesToShow = if (isForUrl) 12 else 60
                appendLine("<details open>")
                appendLine("<summary>📋 Raw ML Kit OCR Candidate Coordinates & Confidence Scores (${rawCandidates.size} detections)</summary>")
                appendLine()
                appendLine("- **Total Candidates:** ${rawCandidates.size}")
                if (avgConf != null) {
                    appendLine("- **Average Confidence:** ${"%.2f (%.0f%%)".format(avgConf, avgConf * 100f)}")
                    appendLine("- **Confidence Breakdown:** $highConfCount high (≥80%), $medConfCount medium (50-79%), $lowConfCount low (<50%)")
                }
                appendLine()
                appendLine("| # | Char | Digit | ML Kit Conf | Center (X, Y) | Bounding Box [L, T, R, B] | Size | Exact? |")
                appendLine("| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
                for ((idx, c) in rawCandidates.take(maxCandidatesToShow).withIndex()) {
                    appendLine("| ${idx + 1} | `${c.originalChar}` | ${c.digit} | **${c.formattedConfidence}** | (${c.centerX.toInt()}, ${c.centerY.toInt()}) | [${c.boxLeft}, ${c.boxTop}, ${c.boxRight}, ${c.boxBottom}] | ${c.width}x${c.height} | ${if (c.isExactDigit) "Yes" else "No"} |")
                }
                if (rawCandidates.size > maxCandidatesToShow) {
                    appendLine()
                    appendLine("*(... and ${rawCandidates.size - maxCandidatesToShow} more candidates - full table copied to clipboard)*")
                }
                appendLine("</details>")
                appendLine()
            }

            if (isForUrl) {
                appendLine("📋 *Note: The complete uncompressed diagnostic report is also copied to your device clipboard.*")
                appendLine()
            }
            appendLine("---")
            appendLine("📸 **Please attach or paste the original puzzle photo** below to help add it to the regression test suite!")
        }
    }

    /**
     * Generates a GitHub Issue creation URL with title and body pre-populated for github.com/arndtc/sudoku_scanner.
     */
    fun generateGitHubIssueUrl(
        currentBoard: SudokuBoard,
        repo: String = "arndtc/sudoku_scanner",
        userNotes: String? = null
    ): String {
        val cleanRepo = repo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .removeSuffix("/")
            .ifBlank { "arndtc/sudoku_scanner" }

        val diffs = computeDifferences(currentBoard)
        val missed = diffs.filter { it.type == DifferenceType.MISSED_DIGIT }
        val title = if (missed.isNotEmpty()) {
            val cluesList = missed.take(4).joinToString(", ") { "${it.coordinateLabel}=${it.correctedValue}" }
            val moreSuffix = if (missed.size > 4) " +${missed.size - 4} more" else ""
            "[OCR Feedback] Missed ${missed.size} numbers in Sudoku scan ($cluesList$moreSuffix)"
        } else if (diffs.isNotEmpty()) {
            "[OCR Feedback] ${diffs.size} corrections in Sudoku scan"
        } else {
            "[OCR Feedback] Recognition quality report (${initialScannedBoard.clueCount} clues)"
        }

        // Generate compact body optimized for URL query string parameters
        val urlBody = generateMarkdownReport(currentBoard, userNotes, cleanRepo, isForUrl = true)
        val safeBody = if (urlBody.length > 3200) {
            urlBody.substring(0, 3200) + "\n\n*(Telemetry truncated for browser URL limit - full report is copied to clipboard)*"
        } else {
            urlBody
        }

        val encodedTitle = urlEncode(title)
        val encodedBody = urlEncode(safeBody)

        return "https://github.com/$cleanRepo/issues/new?title=$encodedTitle&body=$encodedBody"
    }

    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }
}
