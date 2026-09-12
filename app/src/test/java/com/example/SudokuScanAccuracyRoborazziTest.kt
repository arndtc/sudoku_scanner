package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.testdata.AccuracyMetricResult
import com.example.testdata.BundledPuzzleDataset
import com.example.testdata.SudokuScanAccuracyVerifier
import com.example.ui.testcomponents.ScanAccuracySuiteReportCard
import com.example.ui.testcomponents.ScanAccuracyVerificationCard
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Android test suite leveraging Roborazzi and Robolectric Native Graphics
 * to verify OCR scanning accuracy and prevent recognition quality regressions
 * on a bundled suite of representative Sudoku test images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class SudokuScanAccuracyRoborazziTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testImagesDir = File("src/test/resources/test_images")

    @Before
    fun ensureBundledTestImagesExist() {
        if (!testImagesDir.exists()) {
            testImagesDir.mkdirs()
        }
        BundledPuzzleDataset.saveBundledImagesToDirectory(testImagesDir)
    }

    @Test
    fun verifyDailyNewspaperEasy_scanningAccuracy() {
        val puzzle = BundledPuzzleDataset.TEST_PUZZLES.first { it.id == "newspaper_easy" }
        val bitmap = BundledPuzzleDataset.getOrLoadBitmap(puzzle, testImagesDir)

        val result = SudokuScanAccuracyVerifier.verifyPuzzleScanning(puzzle, bitmap, simulateBoundaryNoise = true)

        // Strict regression assertions
        assertTrue("Visual grid lines must be detected for newspaper puzzle", result.visualGridDetected)
        assertTrue("Expected accuracy >= 95%, but got ${result.accuracyPercentage}%", result.accuracyPercentage >= 95.0f)
        assertEquals("False positives must be 0, found ${result.falsePositives}", 0, result.falsePositives)
        assertTrue("Board must follow valid Sudoku rules without duplicates", result.isSudokuRulesValid)
        assertTrue("Verification must pass overall", result.passed)

        // Roborazzi screenshot verification of the scanned output and quality scorecard
        composeTestRule.setContent {
            ScanAccuracyVerificationCard(result = result)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/scan_accuracy_newspaper_easy.png")
    }

    @Test
    fun verifyClassicMedium_scanningAccuracy() {
        val puzzle = BundledPuzzleDataset.TEST_PUZZLES.first { it.id == "classic_medium" }
        val bitmap = BundledPuzzleDataset.getOrLoadBitmap(puzzle, testImagesDir)

        val result = SudokuScanAccuracyVerifier.verifyPuzzleScanning(puzzle, bitmap, simulateBoundaryNoise = true)

        assertTrue("Visual grid lines must be detected for classic medium puzzle", result.visualGridDetected)
        assertTrue("Expected accuracy >= 95%, but got ${result.accuracyPercentage}%", result.accuracyPercentage >= 95.0f)
        assertEquals("False positives must be 0, found ${result.falsePositives}", 0, result.falsePositives)
        assertTrue("Board must follow valid Sudoku rules without duplicates", result.isSudokuRulesValid)
        assertTrue("Verification must pass overall", result.passed)

        composeTestRule.setContent {
            ScanAccuracyVerificationCard(result = result)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/scan_accuracy_classic_medium.png")
    }

    @Test
    fun verifyHardChallengeWithMargins_scanningAccuracy() {
        val puzzle = BundledPuzzleDataset.TEST_PUZZLES.first { it.id == "hard_challenge" }
        val bitmap = BundledPuzzleDataset.getOrLoadBitmap(puzzle, testImagesDir)

        val result = SudokuScanAccuracyVerifier.verifyPuzzleScanning(puzzle, bitmap, simulateBoundaryNoise = true)

        assertTrue("Visual grid lines must be detected with wide margins", result.visualGridDetected)
        assertTrue("Expected accuracy >= 95%, but got ${result.accuracyPercentage}%", result.accuracyPercentage >= 95.0f)
        assertEquals("False positives must be 0, found ${result.falsePositives}", 0, result.falsePositives)
        assertTrue("Board must follow valid Sudoku rules without duplicates", result.isSudokuRulesValid)
        assertTrue("Verification must pass overall", result.passed)

        composeTestRule.setContent {
            ScanAccuracyVerificationCard(result = result)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/scan_accuracy_hard_challenge.png")
    }

    @Test
    fun verifyModernExpertDense_scanningAccuracy() {
        val puzzle = BundledPuzzleDataset.TEST_PUZZLES.first { it.id == "expert_dense" }
        val bitmap = BundledPuzzleDataset.getOrLoadBitmap(puzzle, testImagesDir)

        val result = SudokuScanAccuracyVerifier.verifyPuzzleScanning(puzzle, bitmap, simulateBoundaryNoise = true)

        assertTrue("Visual grid lines must be detected for dense expert puzzle", result.visualGridDetected)
        assertTrue("Expected accuracy >= 95%, but got ${result.accuracyPercentage}%", result.accuracyPercentage >= 95.0f)
        assertEquals("False positives must be 0, found ${result.falsePositives}", 0, result.falsePositives)
        assertTrue("Board must follow valid Sudoku rules without duplicates", result.isSudokuRulesValid)
        assertTrue("Verification must pass overall", result.passed)

        composeTestRule.setContent {
            ScanAccuracyVerificationCard(result = result)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/scan_accuracy_expert_dense.png")
    }

    @Test
    fun verifyEntireSuite_accuracyAndRegressionReport() {
        val results = mutableListOf<AccuracyMetricResult>()

        for (puzzle in BundledPuzzleDataset.TEST_PUZZLES) {
            val bitmap = BundledPuzzleDataset.getOrLoadBitmap(puzzle, testImagesDir)
            val result = SudokuScanAccuracyVerifier.verifyPuzzleScanning(puzzle, bitmap, simulateBoundaryNoise = true)
            results.add(result)
        }

        val totalExpected = results.sumOf { it.totalExpectedClues }
        val totalMatches = results.sumOf { it.exactMatches }
        val totalFalsePositives = results.sumOf { it.falsePositives }
        val overallAccuracy = (totalMatches.toFloat() / totalExpected) * 100f

        // Suite-level threshold assertions
        assertTrue("Overall suite accuracy must exceed 98%, was $overallAccuracy%", overallAccuracy >= 98.0f)
        assertEquals("Suite-wide false positives must be 0", 0, totalFalsePositives)
        assertTrue("All individual test cases must pass", results.all { it.passed })

        // Roborazzi executive test report capture
        composeTestRule.setContent {
            ScanAccuracySuiteReportCard(results = results)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/scan_accuracy_suite_report.png")
    }
}
