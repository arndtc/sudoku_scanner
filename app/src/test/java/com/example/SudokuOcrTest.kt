package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.SudokuGridDetector
import com.example.ocr.SudokuOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SudokuOcrTest {

    @Test
    fun testSyntheticBitmapCreation() {
        val bitmap = Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888)
        assertNotNull(bitmap)
        assertEquals(450, bitmap.width)
        assertEquals(450, bitmap.height)
    }

    @Test
    fun testExactGridFittingWithKnownClues() {
        // Create candidate detections corresponding to a standard Sudoku puzzle
        // Image size: 900x900. Grid bounds: [0, 0, 900, 900]. Cell size: 100x100.
        val knownClues = listOf(
            Triple(0, 0, 5),
            Triple(0, 4, 7),
            Triple(0, 8, 2),
            Triple(1, 1, 6),
            Triple(2, 2, 8),
            Triple(3, 3, 3),
            Triple(4, 4, 9),
            Triple(5, 5, 4),
            Triple(6, 6, 1),
            Triple(7, 7, 5),
            Triple(8, 8, 8),
            Triple(8, 0, 3),
            Triple(4, 0, 7),
            Triple(4, 8, 1)
        )

        val cellW = 100f
        val cellH = 100f
        val candidates = knownClues.map { (r, c, digit) ->
            val cx = (c + 0.5f) * cellW
            val cy = (r + 0.5f) * cellH
            SudokuOcrEngine.DigitDetection(
                digit = digit,
                centerX = cx,
                centerY = cy,
                boundingBox = Rect((cx - 15).toInt(), (cy - 20).toInt(), (cx + 15).toInt(), (cy + 20).toInt()),
                originalChar = ('0' + digit),
                isExactDigit = true
            )
        }

        val result = SudokuOcrEngine.fitSudokuGrid(candidates, 900, 900)

        assertEquals(knownClues.size, result.detectedCount)
        for ((r, c, digit) in knownClues) {
            val placed = result.board[r, c]
            assertEquals("Expected digit $digit at cell ($r, $c)", digit, placed)
        }
    }

    @Test
    fun testShiftedGridWithMarginsDoesNotShiftColumnsOrRows() {
        // Puzzle has 60px margin on all sides: Grid is [60, 60, 870, 870] in 930x930 image
        val margin = 60f
        val cellW = 90f
        val cellH = 90f

        val clues = listOf(
            Triple(0, 1, 4),
            Triple(1, 3, 5),
            Triple(2, 5, 6),
            Triple(3, 7, 7),
            Triple(4, 2, 8),
            Triple(5, 4, 9),
            Triple(6, 6, 1),
            Triple(7, 0, 2),
            Triple(8, 8, 3)
        )

        val candidates = clues.map { (r, c, digit) ->
            val cx = margin + (c + 0.5f) * cellW
            val cy = margin + (r + 0.5f) * cellH
            SudokuOcrEngine.DigitDetection(
                digit = digit,
                centerX = cx,
                centerY = cy,
                boundingBox = Rect((cx - 12).toInt(), (cy - 18).toInt(), (cx + 12).toInt(), (cy + 18).toInt()),
                originalChar = ('0' + digit),
                isExactDigit = true
            )
        }

        val result = SudokuOcrEngine.fitSudokuGrid(candidates, 930, 930)

        for ((r, c, digit) in clues) {
            val placed = result.board[r, c]
            assertEquals("Expected digit $digit at ($r, $c)", digit, placed)
        }
    }

    @Test
    fun testRejectionOfGridLineArtifactNearBorder() {
        // Cell (2, 2) has a real digit '4' at center (cx = 250, cy = 250)
        // And a thin vertical grid line artifact '1' near cell right edge (cx = 288, cy = 250)
        val cellW = 100f
        val cellH = 100f

        val clues = listOf(
            Triple(0, 0, 1),
            Triple(0, 8, 9),
            Triple(8, 0, 8),
            Triple(8, 8, 2),
            Triple(4, 4, 5),
            Triple(2, 2, 4)
        )

        val candidates = clues.map { (r, c, digit) ->
            val cx = (c + 0.5f) * cellW
            val cy = (r + 0.5f) * cellH
            SudokuOcrEngine.DigitDetection(
                digit = digit,
                centerX = cx,
                centerY = cy,
                boundingBox = Rect((cx - 15).toInt(), (cy - 20).toInt(), (cx + 15).toInt(), (cy + 20).toInt()),
                originalChar = ('0' + digit),
                isExactDigit = true
            )
        }.toMutableList()

        // Add a vertical grid line artifact in cell (2, 2) near right border (cx = 290, width = 6, height = 50)
        candidates.add(
            SudokuOcrEngine.DigitDetection(
                digit = 1,
                centerX = 290f,
                centerY = 250f,
                boundingBox = Rect(287, 225, 293, 275),
                originalChar = '|',
                isExactDigit = false
            )
        )

        val result = SudokuOcrEngine.fitSudokuGrid(candidates, 900, 900)

        // Cell (2, 2) must keep the real clue '4' and NOT be overridden by the grid line artifact
        assertEquals("Cell (2, 2) should contain the real clue 4", 4, result.board[2, 2])
    }

    @Test
    fun testVisualGridLineDetectorOnRenderedGrid() {
        val size = 900
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val margin = 45f
        val gridSpan = size - 2 * margin
        val cellSize = gridSpan / 9f

        val pixels = IntArray(size * size) { Color.WHITE }
        for (i in 0..9) {
            val p = (margin + i * cellSize).roundToInt()
            // Draw horizontal line across grid span
            for (w in -1..1) {
                val y = (p + w).coerceIn(0, size - 1)
                for (x in margin.toInt()..(size - margin).toInt()) {
                    pixels[y * size + x] = Color.BLACK
                }
            }
            // Draw vertical line across grid span
            for (w in -1..1) {
                val x = (p + w).coerceIn(0, size - 1)
                for (y in margin.toInt()..(size - margin).toInt()) {
                    pixels[y * size + x] = Color.BLACK
                }
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

        val detected = SudokuGridDetector.detectVisualGridLines(bitmap)
        assertNotNull("Visual grid lines should be detected", detected)
        if (detected != null) {
            // Check that aspect ratio is approximately 1.0
            val aspect = detected.cellW / detected.cellH
            assertTrue("Aspect ratio should be ~1.0, got $aspect", aspect in 0.90f..1.10f)
            // Check that total width is within expected range (810px)
            assertTrue("Grid width should span most of the bitmap (was ${detected.width})", detected.width > 700f)
        }
    }
}
