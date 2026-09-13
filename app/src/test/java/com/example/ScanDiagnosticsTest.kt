package com.example

import com.example.diagnostics.DifferenceType
import com.example.diagnostics.DigitCandidateTelemetry
import com.example.diagnostics.FeedbackHelper
import com.example.diagnostics.ScanDiagnosticRecord
import com.example.model.SudokuBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanDiagnosticsTest {

    @Test
    fun testComputeDifferences_detectsMissedNumbersCorrectly() {
        // Scanned board with 2 empty cells (row 0 col 0, row 4 col 4)
        val initialCells = MutableList(81) { 0 }
        initialCells[1] = 5
        initialCells[2] = 3
        val initialBoard = SudokuBoard(cells = initialCells)

        // User fills in missed cell (0, 0) with 7, and (4, 4) with 9
        val correctedBoard = initialBoard
            .setCell(0, 7)
            .setCell(40, 9)

        val record = ScanDiagnosticRecord(
            initialScannedBoard = initialBoard,
            imageWidth = 1000,
            imageHeight = 1000
        )

        val diffs = record.computeDifferences(correctedBoard)
        assertEquals(2, diffs.size)

        val diff1 = diffs.find { it.index == 0 }
        assertNotNull(diff1)
        assertEquals(0, diff1!!.row)
        assertEquals(0, diff1.col)
        assertEquals("R1C1", diff1.coordinateLabel)
        assertEquals(0, diff1.scannedValue)
        assertEquals(7, diff1.correctedValue)
        assertEquals(DifferenceType.MISSED_DIGIT, diff1.type)

        val diff2 = diffs.find { it.index == 40 }
        assertNotNull(diff2)
        assertEquals(4, diff2!!.row)
        assertEquals(4, diff2.col)
        assertEquals("R5C5", diff2.coordinateLabel)
        assertEquals(0, diff2.scannedValue)
        assertEquals(9, diff2.correctedValue)
        assertEquals(DifferenceType.MISSED_DIGIT, diff2.type)
    }

    @Test
    fun testComputeDifferences_detectsMisclassifiedAndFalsePositives() {
        val initialCells = MutableList(81) { 0 }
        initialCells[0] = 8 // Misclassified, should be 3
        initialCells[1] = 6 // False positive noise, user clears to 0
        val initialBoard = SudokuBoard(cells = initialCells)

        val correctedBoard = initialBoard
            .setCell(0, 3)
            .setCell(1, 0)

        val record = ScanDiagnosticRecord(initialScannedBoard = initialBoard)
        val diffs = record.computeDifferences(correctedBoard)
        assertEquals(2, diffs.size)

        val modified = diffs.find { it.index == 0 }!!
        assertEquals(DifferenceType.INCORRECT_DIGIT, modified.type)
        assertEquals(8, modified.scannedValue)
        assertEquals(3, modified.correctedValue)

        val cleared = diffs.find { it.index == 1 }!!
        assertEquals(DifferenceType.FALSE_POSITIVE, cleared.type)
        assertEquals(6, cleared.scannedValue)
        assertEquals(0, cleared.correctedValue)
    }

    @Test
    fun testGenerateMarkdownReport_containsAllRequiredDiagnostics() {
        val initialCells = MutableList(81) { 0 }
        initialCells[0] = 5
        val initialBoard = SudokuBoard(cells = initialCells)
        val correctedBoard = initialBoard.setCell(1, 9)

        val candidates = listOf(
            DigitCandidateTelemetry(
                digit = 5,
                originalChar = '5',
                centerX = 120f,
                centerY = 150f,
                boxLeft = 100,
                boxTop = 130,
                boxRight = 140,
                boxBottom = 170
            )
        )

        val record = ScanDiagnosticRecord(
            timestamp = 1700000000000L,
            imageWidth = 1080,
            imageHeight = 1920,
            initialScannedBoard = initialBoard,
            visualGridDetected = true,
            ocrPassUsed = "Adaptive Luminance Pass",
            rawCandidates = candidates
        )

        val report = record.generateMarkdownReport(
            currentBoard = correctedBoard,
            userNotes = "Slight glare on the paper"
        )

        assertTrue(report.contains("## 🧩 Sudoku OCR Scan Feedback & Diagnostics"))
        assertTrue(report.contains("Slight glare on the paper"))
        assertTrue(report.contains("1080x1920 px"))
        assertTrue(report.contains("Missed Numbers Detected:** 1"))
        assertTrue(report.contains("Adaptive Luminance Pass"))
        assertTrue(report.contains("`R1C2`"))
        assertTrue(report.contains("Scanned SDM"))
        assertTrue(report.contains("Corrected SDM"))
        assertTrue(report.contains("Raw ML Kit OCR Candidate Coordinates & Confidence Scores"))
        assertTrue(report.contains("Please attach or paste the original puzzle photo"))
    }

    @Test
    fun testConfidenceScoreAndRawCoordinatesLogging() {
        val initialCells = MutableList(81) { 0 }
        initialCells[0] = 5
        val initialBoard = SudokuBoard(cells = initialCells)
        val correctedBoard = initialBoard.setCell(1, 3) // Missed '3' at R1C2

        val candidates = listOf(
            DigitCandidateTelemetry(
                digit = 5,
                originalChar = '5',
                centerX = 50f,
                centerY = 50f,
                boxLeft = 30,
                boxTop = 30,
                boxRight = 70,
                boxBottom = 70,
                confidence = 0.96f,
                isExactDigit = true
            ),
            DigitCandidateTelemetry(
                digit = 3,
                originalChar = '3',
                centerX = 150f,
                centerY = 50f,
                boxLeft = 135,
                boxTop = 32,
                boxRight = 165,
                boxBottom = 68,
                confidence = 0.78f,
                isExactDigit = true
            )
        )

        val record = ScanDiagnosticRecord(
            imageWidth = 900,
            imageHeight = 900,
            gridLeft = 0f,
            gridTop = 0f,
            cellWidth = 100f,
            cellHeight = 100f,
            initialScannedBoard = initialBoard,
            rawCandidates = candidates
        )

        val report = record.generateMarkdownReport(
            currentBoard = correctedBoard,
            userNotes = "Checking confidence logging"
        )

        // Verify ML Kit confidence score formatting
        assertTrue(report.contains("0.96 (96%)") || report.contains("96%"))
        assertTrue(report.contains("0.78 (78%)") || report.contains("78%"))
        assertTrue(report.contains("[30, 30, 70, 70]"))
        assertTrue(report.contains("[135, 32, 165, 68]"))
        assertTrue(report.contains("Center (X, Y)"))
        assertTrue(report.contains("Bounding Box"))
        assertTrue(report.contains("Missed Numbers Deep-Dive (ML Kit Telemetry & Confidence)"))

        // Test diagnoseMissedCell
        val explanationForR1C2 = record.diagnoseMissedCell(0, 1, 3)
        assertTrue(explanationForR1C2.contains("ML Kit detected expected digit '3'"))
        assertTrue(explanationForR1C2.contains("confidence="))

        val explanationForEmptyCell = record.diagnoseMissedCell(5, 5, 8)
        assertTrue(explanationForEmptyCell.contains("No character detected by ML Kit near cell"))
    }

    @Test
    fun testGenerateGitHubIssueUrl_containsEncodedParamsAndRepo() {
        val initialBoard = SudokuBoard.EMPTY
        val correctedBoard = initialBoard.setCell(0, 4) // R1C1 missed

        val record = ScanDiagnosticRecord(
            initialScannedBoard = initialBoard,
            imageWidth = 800,
            imageHeight = 800
        )

        // Test with custom repo
        val url = record.generateGitHubIssueUrl(
            currentBoard = correctedBoard,
            repo = "myorg/mysudokurepo",
            userNotes = "Missed digit 4 at top left"
        )

        assertTrue(url.startsWith("https://github.com/myorg/mysudokurepo/issues/new?"))
        assertTrue(url.contains("title="))
        assertTrue(url.contains("body="))
        assertTrue(url.contains("R1C1%3D4") || url.contains("R1C1=4") || url.contains("Missed%201%20numbers"))

        // Test default repo points to arndtc/sudoku_scanner
        val defaultUrl = record.generateGitHubIssueUrl(
            currentBoard = correctedBoard,
            userNotes = "Default repo test"
        )
        assertTrue(defaultUrl.startsWith("https://github.com/arndtc/sudoku_scanner/issues/new?"))
        assertTrue(defaultUrl.contains("title="))
        assertTrue(defaultUrl.contains("body="))

        // Test full URL repo normalization: "https://github.com/arndtc/sudoku_scanner" -> "arndtc/sudoku_scanner"
        val normalizedUrl = record.generateGitHubIssueUrl(
            currentBoard = correctedBoard,
            repo = "https://github.com/arndtc/sudoku_scanner"
        )
        assertTrue(normalizedUrl.startsWith("https://github.com/arndtc/sudoku_scanner/issues/new?"))
        assertFalse(normalizedUrl.contains("https://github.com/https:"))

        // Test FeedbackHelper helper
        val cleaned = FeedbackHelper.cleanRepoIdentifier("https://github.com/arndtc/sudoku_scanner/")
        assertEquals("arndtc/sudoku_scanner", cleaned)
        assertEquals("arndtc/sudoku_scanner", FeedbackHelper.DEFAULT_GITHUB_REPO)
    }
}
