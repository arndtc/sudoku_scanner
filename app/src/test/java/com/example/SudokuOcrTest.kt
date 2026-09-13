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

    @Test
    fun testHasCellInkDetectsPrintedDigitVsBlank() {
        val cellW = 80
        val cellH = 80
        val bitmap = Bitmap.createBitmap(cellW, cellH, Bitmap.Config.ARGB_8888)

        // Blank cell: pure white background
        bitmap.eraseColor(Color.WHITE)
        val isBlankInk = SudokuOcrEngine.hasCellInk(bitmap, 10, 10, 70, 70)
        assertEquals("Blank cell must not have ink detected", false, isBlankInk)

        // Draw digit '1' (vertical stroke) in center
        for (y in 25..55) {
            for (x in 38..42) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
        val hasInkDigit1 = SudokuOcrEngine.hasCellInk(bitmap, 10, 10, 70, 70)
        assertEquals("Cell with digit 1 must have ink detected", true, hasInkDigit1)

        // Clear and draw digit '2'
        bitmap.eraseColor(Color.WHITE)
        for (x in 25..55) bitmap.setPixel(x, 25, Color.BLACK)
        for (y in 25..40) bitmap.setPixel(55, y, Color.BLACK)
        for (x in 25..55) bitmap.setPixel(x, 40, Color.BLACK)
        for (y in 40..55) bitmap.setPixel(25, y, Color.BLACK)
        for (x in 25..55) bitmap.setPixel(x, 55, Color.BLACK)
        val hasInkDigit2 = SudokuOcrEngine.hasCellInk(bitmap, 10, 10, 70, 70)
        assertEquals("Cell with digit 2 must have ink detected", true, hasInkDigit2)
    }

    @Test
    fun testCanPlaceDigitValidatesSudokuConstraints() {
        val cells = MutableList(81) { 0 }
        // Set up row 0 with 5, col 0 with 8, box 0 with 3
        cells[0 * 9 + 4] = 5 // Row 0 Col 4 is 5
        cells[2 * 9 + 0] = 8 // Row 2 Col 0 is 8
        cells[1 * 9 + 1] = 3 // Row 1 Col 1 is 3 (Box 0)

        // Placing 5 at (0, 1) should fail (row 0 conflict)
        assertEquals(false, SudokuOcrEngine.canPlaceDigit(cells, 0, 1, 5))

        // Placing 8 at (5, 0) should fail (col 0 conflict)
        assertEquals(false, SudokuOcrEngine.canPlaceDigit(cells, 5, 0, 8))

        // Placing 3 at (0, 2) should fail (box 0 conflict)
        assertEquals(false, SudokuOcrEngine.canPlaceDigit(cells, 0, 2, 3))

        // Placing 1 at (0, 1) should succeed
        assertEquals(true, SudokuOcrEngine.canPlaceDigit(cells, 0, 1, 1))
    }

    @Test
    fun testPrepareCellForOcrGeneratesPaddedHighContrastBitmap() {
        val cell = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        cell.eraseColor(Color.LTGRAY)
        for (y in 15..45) {
            for (x in 28..32) {
                cell.setPixel(x, y, Color.DKGRAY)
            }
        }

        val prepared = SudokuOcrEngine.prepareCellForOcr(cell)
        assertEquals(140, prepared.width)
        assertEquals(140, prepared.height)
        // Verify padding: corners should be pure white
        assertEquals(Color.WHITE, prepared.getPixel(5, 5))
        assertEquals(Color.WHITE, prepared.getPixel(135, 135))
    }

    @Test
    fun testMergeOcrResultsRecoversIssue1MissedDigits() {
        // Issue #1:
        // Scanned SDM (23 clues):
        // 900154000803000000400000900000000070008305400070800500009000002000000307000290005
        // Corrected SDM (26 clues):
        // 900154000803000000400000900001002070008305400070800500009000002000000307000291005
        val scannedSdm = "900154000803000000400000900000000070008305400070800500009000002000000307000290005"
        val correctedSdm = "900154000803000000400000900001002070008305400070800500009000002000000307000291005"

        val baseCells = scannedSdm.map { it.digitToInt() }
        val baseBoard = com.example.model.SudokuBoard(cells = baseCells, isGiven = baseCells.map { it != 0 })
        val baseResult = SudokuOcrEngine.OcrResult(
            board = baseBoard,
            detectedCount = 23,
            message = "Pass 1"
        )

        // Secondary pass (from targeted cell recovery or enhanced pass) finds the 3 missing digits:
        // R4C3 = 1 (row 3, col 2 => idx 3*9 + 2 = 29)
        // R4C6 = 2 (row 3, col 5 => idx 3*9 + 5 = 32)
        // R9C6 = 1 (row 8, col 5 => idx 8*9 + 5 = 77)
        val secondaryCells = MutableList(81) { 0 }
        secondaryCells[3 * 9 + 2] = 1
        secondaryCells[3 * 9 + 5] = 2
        secondaryCells[8 * 9 + 5] = 1
        val secondaryBoard = com.example.model.SudokuBoard(cells = secondaryCells, isGiven = secondaryCells.map { it != 0 })
        val secondaryResult = SudokuOcrEngine.OcrResult(
            board = secondaryBoard,
            detectedCount = 3,
            message = "Recovered pass"
        )

        val merged = SudokuOcrEngine.mergeOcrResults(baseResult, secondaryResult)

        assertEquals("Merged result should contain 26 clues", 26, merged.detectedCount)
        assertEquals("R4C3 must be 1", 1, merged.board[3, 2])
        assertEquals("R4C6 must be 2", 2, merged.board[3, 5])
        assertEquals("R9C6 must be 1", 1, merged.board[8, 5])
        assertEquals("Resulting board string must match Corrected SDM", correctedSdm, merged.board.toSdmString())
    }

    @Test
    fun testMergeOcrResultsRecoversIssue2MissedDigits() {
        // Issue #2:
        // Scanned SDM (25 clues):
        // 900154000803000000400000900000002070008305400070800500609000002000000307000290005
        // Corrected SDM (26 clues):
        // 900154000803000000400000900001002070008305400070800500009000002000000307000291005
        // Missed digits:
        // R4C3 = 1 (row 3, col 2 => idx 3*9 + 2 = 29)
        // R9C6 = 1 (row 8, col 5 => idx 8*9 + 5 = 77)
        // False positive:
        // R7C1 = 6 (row 6, col 0 => idx 6*9 + 0 = 54)
        val scannedSdm = "900154000803000000400000900000002070008305400070800500609000002000000307000290005"
        val correctedSdm = "900154000803000000400000900001002070008305400070800500009000002000000307000291005"

        val baseCells = scannedSdm.map { it.digitToInt() }
        val baseBoard = com.example.model.SudokuBoard(cells = baseCells, isGiven = baseCells.map { it != 0 })
        val baseResult = SudokuOcrEngine.OcrResult(
            board = baseBoard,
            detectedCount = 25,
            message = "Pass 1"
        )

        val secondaryCells = MutableList(81) { 0 }
        secondaryCells[3 * 9 + 2] = 1
        secondaryCells[8 * 9 + 5] = 1
        val secondaryBoard = com.example.model.SudokuBoard(cells = secondaryCells, isGiven = secondaryCells.map { it != 0 })
        val secondaryResult = SudokuOcrEngine.OcrResult(
            board = secondaryBoard,
            detectedCount = 2,
            message = "Recovered pass"
        )

        val merged = SudokuOcrEngine.mergeOcrResults(baseResult, secondaryResult)

        assertEquals("R4C3 must be 1", 1, merged.board[3, 2])
        assertEquals("R9C6 must be 1", 1, merged.board[8, 5])
        assertEquals("Merged clue count should be 27 before user clears false positive", 27, merged.detectedCount)

        // Simulating user clearing false positive R7C1 (cell 54)
        val cleanedBoard = merged.board.setCell(6 * 9 + 0, 0)
        assertEquals("Cleaned board should match Corrected SDM exactly", correctedSdm, cleanedBoard.toSdmString())
        assertEquals(26, cleanedBoard.clueCount)
    }

    @Test
    fun testPruneEmptyCellFalsePositivesRemovesPhantomClues() {
        val cellW = 80f
        val cellH = 80f
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        // Draw digit '4' at R1C1 (row 0, col 0)
        for (y in 25..55) {
            for (x in 35..45) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
        // Cell R7C1 (row 6, col 0) is left completely blank white (pure paper, no ink)

        val cells = MutableList(81) { 0 }
        cells[0 * 9 + 0] = 4 // real clue with ink
        cells[6 * 9 + 0] = 6 // phantom false positive with NO ink

        val board = com.example.model.SudokuBoard(cells = cells, isGiven = cells.map { it != 0 })
        val diag = SudokuOcrEngine.ScanDiagnosticsInfo(
            passName = "Standard",
            visualGridDetected = true,
            gridLeft = 0f,
            gridTop = 0f,
            cellWidth = cellW,
            cellHeight = cellH
        )
        val ocrResult = SudokuOcrEngine.OcrResult(
            board = board,
            detectedCount = 2,
            message = "Test",
            diagnostics = diag
        )

        val pruned = SudokuOcrEngine.pruneEmptyCellFalsePositives(bitmap, ocrResult)

        assertEquals("Real clue R1C1 with ink must be retained", 4, pruned.board[0, 0])
        assertEquals("Phantom clue R7C1 without ink must be pruned", 0, pruned.board[6, 0])
        assertEquals("Detected count should decrease to 1", 1, pruned.detectedCount)
    }
}

