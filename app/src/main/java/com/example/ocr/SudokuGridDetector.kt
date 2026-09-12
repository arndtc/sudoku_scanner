package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-precision Sudoku grid detector and cell localization engine.
 *
 * Combines visual line projection profiles (detecting physical 9x9 grid lines)
 * with robust digit-lattice regression (OLS fit) to guarantee digits are assigned
 * to their exact, correct cell (row 0..8, col 0..8).
 */
object SudokuGridDetector {

    private const val TAG = "SudokuGridDetector"

    data class GridGeometry(
        val left: Float,
        val top: Float,
        val cellW: Float,
        val cellH: Float
    ) {
        val right: Float get() = left + 9f * cellW
        val bottom: Float get() = top + 9f * cellH
        val width: Float get() = 9f * cellW
        val height: Float get() = 9f * cellH

        fun cellLeft(col: Int): Float = left + col * cellW
        fun cellRight(col: Int): Float = left + (col + 1) * cellW
        fun cellTop(row: Int): Float = top + row * cellH
        fun cellBottom(row: Int): Float = top + (row + 1) * cellH

        fun cellCenterX(col: Int): Float = left + (col + 0.5f) * cellW
        fun cellCenterY(row: Int): Float = top + (row + 0.5f) * cellH

        fun toRect(): Rect = Rect(
            left.roundToInt().coerceAtLeast(0),
            top.roundToInt().coerceAtLeast(0),
            right.roundToInt(),
            bottom.roundToInt()
        )

        fun toNormalizedRectF(imageW: Int, imageH: Int): RectF = RectF(
            (left / imageW).coerceIn(0f, 1f),
            (top / imageH).coerceIn(0f, 1f),
            (right / imageW).coerceIn(0f, 1f),
            (bottom / imageH).coerceIn(0f, 1f)
        )
    }

    data class PlacedClue(
        val digit: Int,
        val detection: SudokuOcrEngine.DigitDetection,
        val row: Int,
        val col: Int,
        val score: Float,
        val distFromCenter: Float
    )

    data class GridFitResult(
        val geometry: GridGeometry,
        val placedClues: Map<Int, PlacedClue>, // cellIndex (row*9 + col) -> PlacedClue
        val score: Float,
        val conflictCount: Int
    )

    /**
     * Detects physical grid lines directly on the bitmap using horizontal and vertical
     * pixel intensity projection profiles.
     * Returns the detected 9x9 grid geometry, or null if no distinct grid is visible.
     */
    fun detectVisualGridLines(bitmap: Bitmap): GridGeometry? {
        val targetDim = 600
        val scale = min(1f, targetDim.toFloat() / max(bitmap.width, bitmap.height))
        val workW = (bitmap.width * scale).roundToInt().coerceAtLeast(100)
        val workH = (bitmap.height * scale).roundToInt().coerceAtLeast(100)

        val scaled = if (scale < 0.99f) {
            Bitmap.createScaledBitmap(bitmap, workW, workH, true)
        } else {
            bitmap
        }

        val pixels = IntArray(workW * workH)
        scaled.getPixels(pixels, 0, workW, 0, 0, workW, workH)

        // Compute grayscale luminance array and global median luminance
        val lum = IntArray(workW * workH)
        var sumLum = 0L
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = (r * 77 + g * 150 + b * 29) shr 8
            lum[i] = y
            sumLum += y
        }
        val avgLum = (sumLum / lum.size).toInt()
        val darkThreshold = (avgLum * 0.85).toInt().coerceAtLeast(40)

        // Horizontal and vertical projections of darkness
        val horizDark = FloatArray(workH)
        val vertDark = FloatArray(workW)

        for (y in 0 until workH) {
            var count = 0
            val rowOffset = y * workW
            for (x in 0 until workW) {
                if (lum[rowOffset + x] < darkThreshold) count++
            }
            horizDark[y] = count.toFloat() / workW
        }

        for (x in 0 until workW) {
            var count = 0
            for (y in 0 until workH) {
                if (lum[y * workW + x] < darkThreshold) count++
            }
            vertDark[x] = count.toFloat() / workH
        }

        fun sampleV(x: Int): Float {
            val c = x.coerceIn(0, workW - 1)
            val l = (x - 1).coerceIn(0, workW - 1)
            val r = (x + 1).coerceIn(0, workW - 1)
            return maxOf(vertDark[l], vertDark[c], vertDark[r])
        }

        fun sampleH(y: Int): Float {
            val c = y.coerceIn(0, workH - 1)
            val l = (y - 1).coerceIn(0, workH - 1)
            val r = (y + 1).coerceIn(0, workH - 1)
            return maxOf(horizDark[l], horizDark[c], horizDark[r])
        }

        // Search for 10 periodic grid lines with dense step coverage and windowed peak sampling
        var bestScoreX = 0f
        var bestX0 = 0f
        var bestStepX = 0f

        val minStepX = workW * 0.50f / 9f
        val maxStepX = workW * 0.98f / 9f

        var stepX = minStepX
        while (stepX <= maxStepX) {
            val totalSpan = 9 * stepX
            val maxX0 = workW - totalSpan
            var x0 = 0f
            while (x0 <= maxX0) {
                var s = 0f
                for (k in 0..9) {
                    val sampleX = (x0 + k * stepX).roundToInt()
                    val weight = if (k == 0 || k == 3 || k == 6 || k == 9) 1.8f else 1.0f
                    s += sampleV(sampleX) * weight
                }
                if (s > bestScoreX) {
                    bestScoreX = s
                    bestX0 = x0
                    bestStepX = stepX
                }
                x0 += 1.0f
            }
            stepX += 0.5f
        }

        // Fine refinement in X
        if (bestStepX > 0f) {
            var fineStepX = (bestStepX - 1.0f).coerceAtLeast(minStepX)
            val endStepX = (bestStepX + 1.0f).coerceAtMost(maxStepX)
            while (fineStepX <= endStepX) {
                val totalSpan = 9 * fineStepX
                val maxX0 = (workW - totalSpan).coerceAtLeast(0f)
                var fineX0 = (bestX0 - 2f).coerceAtLeast(0f)
                val endX0 = (bestX0 + 2f).coerceAtMost(maxX0)
                while (fineX0 <= endX0) {
                    var s = 0f
                    for (k in 0..9) {
                        val sampleX = (fineX0 + k * fineStepX).roundToInt()
                        val weight = if (k == 0 || k == 3 || k == 6 || k == 9) 1.8f else 1.0f
                        s += sampleV(sampleX) * weight
                    }
                    if (s > bestScoreX) {
                        bestScoreX = s
                        bestX0 = fineX0
                        bestStepX = fineStepX
                    }
                    fineX0 += 0.25f
                }
                fineStepX += 0.1f
            }
        }

        var bestScoreY = 0f
        var bestY0 = 0f
        var bestStepY = 0f

        val minStepY = workH * 0.50f / 9f
        val maxStepY = workH * 0.98f / 9f

        var stepY = minStepY
        while (stepY <= maxStepY) {
            val totalSpan = 9 * stepY
            val maxY0 = workH - totalSpan
            var y0 = 0f
            while (y0 <= maxY0) {
                var s = 0f
                for (k in 0..9) {
                    val sampleY = (y0 + k * stepY).roundToInt()
                    val weight = if (k == 0 || k == 3 || k == 6 || k == 9) 1.8f else 1.0f
                    s += sampleH(sampleY) * weight
                }
                if (s > bestScoreY) {
                    bestScoreY = s
                    bestY0 = y0
                    bestStepY = stepY
                }
                y0 += 1.0f
            }
            stepY += 0.5f
        }

        // Fine refinement in Y
        if (bestStepY > 0f) {
            var fineStepY = (bestStepY - 1.0f).coerceAtLeast(minStepY)
            val endStepY = (bestStepY + 1.0f).coerceAtMost(maxStepY)
            while (fineStepY <= endStepY) {
                val totalSpan = 9 * fineStepY
                val maxY0 = (workH - totalSpan).coerceAtLeast(0f)
                var fineY0 = (bestY0 - 2f).coerceAtLeast(0f)
                val endY0 = (bestY0 + 2f).coerceAtMost(maxY0)
                while (fineY0 <= endY0) {
                    var s = 0f
                    for (k in 0..9) {
                        val sampleY = (fineY0 + k * fineStepY).roundToInt()
                        val weight = if (k == 0 || k == 3 || k == 6 || k == 9) 1.8f else 1.0f
                        s += sampleH(sampleY) * weight
                    }
                    if (s > bestScoreY) {
                        bestScoreY = s
                        bestY0 = fineY0
                        bestStepY = fineStepY
                    }
                    fineY0 += 0.25f
                }
                fineStepY += 0.1f
            }
        }

        if (bestStepX <= 0f || bestStepY <= 0f) return null

        val aspect = (bestStepX * 9f) / (bestStepY * 9f)
        if (aspect !in 0.82f..1.22f) return null

        // Scale back to original bitmap dimensions
        val origLeft = bestX0 / scale
        val origTop = bestY0 / scale
        val origCellW = bestStepX / scale
        val origCellH = bestStepY / scale

        Log.d(TAG, "[GridLine] Detected visual grid: left=$origLeft, top=$origTop, cellW=$origCellW, cellH=$origCellH, aspect=$aspect")
        return GridGeometry(origLeft, origTop, origCellW, origCellH)
    }

    /**
     * Fits candidate digits onto a 9x9 grid using comprehensive geometric evaluation
     * and Ordinary Least Squares (OLS) lattice refinement.
     */
    fun fitDigitsToGrid(
        candidates: List<SudokuOcrEngine.DigitDetection>,
        imageWidth: Int,
        imageHeight: Int,
        visualGrid: GridGeometry? = null
    ): GridFitResult {
        if (candidates.isEmpty()) {
            val defaultGeom = visualGrid ?: GridGeometry(0f, 0f, imageWidth / 9f, imageHeight / 9f)
            return GridFitResult(defaultGeom, emptyMap(), 0f, 0)
        }

        val heights = candidates.map { it.boundingBox.height().toFloat() }.sorted()
        val medianH = heights[heights.size / 2]

        val candidateGrids = mutableListOf<GridGeometry>()

        // 1. Add Visual Grid hypothesis if available
        if (visualGrid != null) {
            candidateGrids.add(visualGrid)
            // Add subtle offsets around visual grid (±1%, ±2%)
            for (dx in listOf(-0.02f, -0.01f, 0.01f, 0.02f)) {
                for (dy in listOf(-0.02f, -0.01f, 0.01f, 0.02f)) {
                    candidateGrids.add(
                        GridGeometry(
                            left = visualGrid.left + dx * visualGrid.cellW,
                            top = visualGrid.top + dy * visualGrid.cellH,
                            cellW = visualGrid.cellW * (1f + dx * 0.5f),
                            cellH = visualGrid.cellH * (1f + dy * 0.5f)
                        )
                    )
                }
            }
        }

        // 2. Add Standard Image Margins (for cropped puzzle photos)
        for (margin in listOf(0.0f, 0.01f, 0.02f, 0.03f, 0.05f, 0.07f, 0.10f, 0.12f, 0.15f, 0.18f)) {
            val gLeft = imageWidth * margin
            val gTop = imageHeight * margin
            val gW = imageWidth * (1f - 2f * margin)
            val gH = imageHeight * (1f - 2f * margin)
            candidateGrids.add(GridGeometry(gLeft, gTop, gW / 9f, gH / 9f))
        }

        // 3. Add Digit Spatial Cluster Hypotheses
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (c in candidates) {
            minX = min(minX, c.centerX)
            minY = min(minY, c.centerY)
            maxX = max(maxX, c.centerX)
            maxY = max(maxY, c.centerY)
        }

        val spanX = maxX - minX
        val spanY = maxY - minY

        if (spanX > 20f && spanY > 20f) {
            for (kx in 4..8) {
                for (ky in 4..8) {
                    val cellW = spanX / kx
                    val cellH = spanY / ky
                    val aspect = cellW / cellH
                    if (aspect !in 0.70f..1.40f) continue

                    for (ox in 0..(8 - kx)) {
                        for (oy in 0..(8 - ky)) {
                            val gLeft = minX - (ox + 0.5f) * cellW
                            val gTop = minY - (oy + 0.5f) * cellH
                            candidateGrids.add(GridGeometry(gLeft, gTop, cellW, cellH))
                        }
                    }
                }
            }
        }

        // Evaluate all candidate grids
        var bestFit: GridFitResult? = null
        var bestScore = -10000f

        for (geom in candidateGrids) {
            val fit = evaluateGeometry(geom, candidates, medianH)
            if (fit.score > bestScore) {
                bestScore = fit.score
                bestFit = fit
            }
        }

        val initialBest = bestFit ?: GridFitResult(
            GridGeometry(0f, 0f, imageWidth / 9f, imageHeight / 9f),
            emptyMap(),
            0f,
            0
        )

        // 4. Refinement step via Ordinary Least Squares (OLS) lattice regression
        // Once candidate assignments are known, OLS directly finds the exact continuous
        // (gLeft, gTop, cellW, cellH) that minimizes mean-squared-error to candidate centers!
        val refined = refineGeometryWithOls(initialBest, candidates, medianH)
        return if (refined.score >= initialBest.score) refined else initialBest
    }

    private fun evaluateGeometry(
        geom: GridGeometry,
        candidates: List<SudokuOcrEngine.DigitDetection>,
        medianH: Float
    ): GridFitResult {
        val cellW = geom.cellW
        val cellH = geom.cellH
        if (cellW <= 5f || cellH <= 5f) {
            return GridFitResult(geom, emptyMap(), -10000f, 0)
        }

        val placedMap = mutableMapOf<Int, PlacedClue>()
        var conflictCount = 0
        var totalClueScore = 0f

        for (d in candidates) {
            // Find which cell (col, row) contains this candidate center
            val u = (d.centerX - geom.left) / cellW
            val v = (d.centerY - geom.top) / cellH

            val col = Math.floor(u.toDouble()).toInt()
            val row = Math.floor(v.toDouble()).toInt()

            if (col in 0..8 && row in 0..8) {
                val expectedCx = geom.cellCenterX(col)
                val expectedCy = geom.cellCenterY(row)
                val dx = abs(d.centerX - expectedCx) / cellW
                val dy = abs(d.centerY - expectedCy) / cellH
                val dist = sqrt(dx * dx + dy * dy)

                // Safe cell interior check: reject candidates outside 0.32 cell-width from center
                // Real Sudoku digits are centered; this guarantees grid lines and adjacent artifacts are rejected.
                if (dx > 0.32f || dy > 0.32f || dist > 0.42f) {
                    continue
                }

                // Grid line artifact filter & inexact char validation
                if (!d.isExactDigit) {
                    // Inexact candidates (OCR substitutions) must be well-centered and must not be punctuation/line slivers
                    if (dx > 0.20f || dy > 0.20f || d.originalChar !in listOf('l', 'I', 'Z', 'z', 'S', 's', 'G', 'b', 'B', 'q')) {
                        continue
                    }
                }

                // Check for vertical sliver artifact (e.g. grid border fragment recognized as '1' or 'l')
                if (d.digit == 1) {
                    val w = d.boundingBox.width().toFloat()
                    val h = d.boundingBox.height().toFloat()
                    if (w < cellW * 0.15f && dx > 0.22f) {
                        continue
                    }
                }

                // Score: centeredness (quadratic drop-off), height consistency, exact digit bonus
                val centerScore = (1.0f - (dist / 0.50f).pow(2)).coerceIn(0f, 1f) * 45f
                val heightDev = (abs(d.boundingBox.height() - medianH) / max(1f, medianH)).coerceIn(0f, 1f)
                val sizeScore = (1.0f - heightDev) * 20f
                val charScore = if (d.isExactDigit) 35f else 15f
                val clueScore = centerScore + sizeScore + charScore

                val clue = PlacedClue(
                    digit = d.digit,
                    detection = d,
                    row = row,
                    col = col,
                    score = clueScore,
                    distFromCenter = dist
                )

                val cellIndex = row * 9 + col
                val existing = placedMap[cellIndex]

                if (existing == null) {
                    placedMap[cellIndex] = clue
                    totalClueScore += clueScore
                } else if (clueScore > existing.score) {
                    totalClueScore += (clueScore - existing.score)
                    placedMap[cellIndex] = clue
                }
            }
        }

        // Count conflicts according to standard Sudoku rules
        for (clue in placedMap.values) {
            val conflicts = placedMap.values.count { other ->
                other !== clue && other.digit == clue.digit && (
                    other.row == clue.row ||
                    other.col == clue.col ||
                    (other.row / 3 == clue.row / 3 && other.col / 3 == clue.col / 3)
                )
            }
            if (conflicts > 0) conflictCount++
        }

        // Aspect ratio penalty (Sudoku grids are strictly square 1.0)
        val aspect = cellW / cellH
        val aspectDev = abs(aspect - 1.0f)
        val aspectPenalty = aspectDev * 120f

        // Final hypothesis score:
        // Heavily rewards more clues, rewards clues near cell center, heavily penalizes conflicts!
        val finalScore = (placedMap.size * 90f) + totalClueScore - (conflictCount * 350f) - aspectPenalty

        return GridFitResult(
            geometry = geom,
            placedClues = placedMap,
            score = finalScore,
            conflictCount = conflictCount
        )
    }

    /**
     * Refines grid geometry using Ordinary Least Squares (OLS) linear regression
     * on the placed clues' coordinates.
     */
    private fun refineGeometryWithOls(
        fit: GridFitResult,
        candidates: List<SudokuOcrEngine.DigitDetection>,
        medianH: Float
    ): GridFitResult {
        if (fit.placedClues.size < 4) return fit

        val clues = fit.placedClues.values.toList()

        // Fit X: cx = gLeft + (col + 0.5) * cellW
        val n = clues.size.toFloat()
        var sumU = 0f
        var sumX = 0f
        var sumU2 = 0f
        var sumUX = 0f

        for (c in clues) {
            val u = c.col + 0.5f
            val x = c.detection.centerX
            sumU += u
            sumX += x
            sumU2 += u * u
            sumUX += u * x
        }

        val denomX = n * sumU2 - sumU * sumU
        val refinedCellW = if (abs(denomX) > 1e-4) {
            ((n * sumUX - sumU * sumX) / denomX).coerceAtLeast(10f)
        } else {
            fit.geometry.cellW
        }
        val refinedLeft = (sumX - refinedCellW * sumU) / n

        // Fit Y: cy = gTop + (row + 0.5) * cellH
        var sumV = 0f
        var sumY = 0f
        var sumV2 = 0f
        var sumVY = 0f

        for (c in clues) {
            val v = c.row + 0.5f
            val y = c.detection.centerY
            sumV += v
            sumY += y
            sumV2 += v * v
            sumVY += v * y
        }

        val denomY = n * sumV2 - sumV * sumV
        val refinedCellH = if (abs(denomY) > 1e-4) {
            ((n * sumVY - sumV * sumY) / denomY).coerceAtLeast(10f)
        } else {
            fit.geometry.cellH
        }
        val refinedTop = (sumY - refinedCellH * sumV) / n

        val refinedGeom = GridGeometry(refinedLeft, refinedTop, refinedCellW, refinedCellH)
        return evaluateGeometry(refinedGeom, candidates, medianH)
    }
}
