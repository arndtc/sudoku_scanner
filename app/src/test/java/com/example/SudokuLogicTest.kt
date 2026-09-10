package com.example

import com.example.logic.SamplePuzzles
import com.example.logic.SudokuExporter
import com.example.logic.SudokuSolver
import com.example.logic.SudokuValidator
import com.example.model.SudokuBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuLogicTest {

    @Test
    fun testSudokuBoardCreationAndSdmString() {
        val sdm = "003020600900305001001806400008102900700000008006708200002609500800203009005010300"
        val board = SudokuBoard.fromString(sdm)

        assertEquals(81, board.cells.size)
        assertEquals(32, board.clueCount)
        assertEquals(3, board[0, 2])
        assertEquals(sdm, board.toSdmString())
    }

    @Test
    fun testSudokuValidatorDetectsConflicts() {
        // Valid board: no conflicts
        val sample = SamplePuzzles.getSampleBoard(0)
        val validResult = SudokuValidator.findConflicts(sample)
        assertTrue(validResult.isValid)
        assertTrue(validResult.conflictedIndices.isEmpty())

        // Introduce conflict: put another 1 in same row
        val conflictedRow = sample.setCell(0, 1).setCell(1, 1)
        val rowConflictResult = SudokuValidator.findConflicts(conflictedRow)
        assertFalse(rowConflictResult.isValid)
        assertTrue(rowConflictResult.conflictedIndices.contains(0))
        assertTrue(rowConflictResult.conflictedIndices.contains(1))
    }

    @Test
    fun testSudokuSolver() {
        val sample = SamplePuzzles.getSampleBoard(1)
        val result = SudokuSolver.analyzeAndSolve(sample)

        assertEquals(SudokuSolver.Solvability.SOLVABLE_UNIQUE, result.solvability)
        assertNotNull(result.solutionBoard)
        assertTrue(result.solutionBoard!!.isFull)
        assertEquals(81, result.solutionBoard!!.clueCount)

        // Solution must have 0 conflicts
        val solConflicts = SudokuValidator.findConflicts(result.solutionBoard!!)
        assertTrue(solConflicts.isValid)
    }

    @Test
    fun testSudokuExporterOpenSudokuXml() {
        val sample = SamplePuzzles.getSampleBoard(0)
        val xml = SudokuExporter.toOpenSudokuXml(sample, "My Test Puzzle")

        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(xml.contains("<opensudoku version=\"2\">"))
        assertTrue(xml.contains("<folder name=\"My Test Puzzle\">"))
        assertTrue(xml.contains("data=\"${sample.toSdmString('0')}\""))
        assertTrue(xml.contains("</opensudoku>"))
    }

    @Test
    fun testSudokuExporterSdmText() {
        val sample = SamplePuzzles.getSampleBoard(0)
        val text = SudokuExporter.toSdmText(sample)

        assertEquals(sample.toSdmString('0') + "\n", text)
        assertEquals(82, text.length) // 81 chars + \n
    }

    @Test
    fun testSudokuValidatorColumnAndBoxConflicts() {
        val emptyBoard = SudokuBoard.EMPTY

        // Column conflict: row 0 col 2 = 7, row 4 col 2 = 7
        val colConflictBoard = emptyBoard.setCell(2, 7).setCell(38, 7) // 4*9 + 2 = 38
        val colResult = SudokuValidator.findConflicts(colConflictBoard)
        assertFalse(colResult.isValid)
        assertTrue(colResult.conflictedIndices.contains(2))
        assertTrue(colResult.conflictedIndices.contains(38))

        // Box conflict: row 0 col 0 = 4, row 1 col 1 = 4 (both in top-left 3x3 box)
        val boxConflictBoard = emptyBoard.setCell(0, 4).setCell(10, 4) // 1*9 + 1 = 10
        val boxResult = SudokuValidator.findConflicts(boxConflictBoard)
        assertFalse(boxResult.isValid)
        assertTrue(boxResult.conflictedIndices.contains(0))
        assertTrue(boxResult.conflictedIndices.contains(10))
    }

    @Test
    fun testSudokuValidatorMultipleDuplicatesInSameUnit() {
        // 3 of the same digit in the same row
        val emptyBoard = SudokuBoard.EMPTY
        val threeDuplicates = emptyBoard
            .setCell(0, 9)
            .setCell(1, 9)
            .setCell(2, 9)

        val result = SudokuValidator.findConflicts(threeDuplicates)
        assertFalse(result.isValid)
        assertEquals(3, result.conflictedIndices.size)
        assertTrue(result.conflictedIndices.contains(0))
        assertTrue(result.conflictedIndices.contains(1))
        assertTrue(result.conflictedIndices.contains(2))
    }

    @Test
    fun testSudokuSolverUnsolvableBoard() {
        // Row 0 has digits 1..8, leaving cell (0,8) needing 9.
        // But cell (1,8) in the same 3x3 box and column is 9, making cell (0,8) impossible.
        var board = SudokuBoard.EMPTY
        for (col in 0..7) {
            board = board.setCell(col, col + 1) // 1 to 8
        }
        board = board.setCell(17, 9) // cell (1,8) = 1*9 + 8 = 17

        val result = SudokuSolver.analyzeAndSolve(board)
        assertEquals(SudokuSolver.Solvability.UNSOLVABLE, result.solvability)
    }

    @Test
    fun testSudokuSolverDuplicateConflictsReturnsInvalidDuplicates() {
        val sample = SamplePuzzles.getSampleBoard(0)
        val conflicted = sample.setCell(0, 5).setCell(1, 5)

        val result = SudokuSolver.analyzeAndSolve(conflicted)
        assertEquals(SudokuSolver.Solvability.INVALID_DUPLICATES, result.solvability)
    }

    @Test
    fun testSudokuSolverMultipleSolutions() {
        // A sparse board with valid clues will have multiple solutions
        var sparse = SudokuBoard.EMPTY
        sparse = sparse.setCell(0, 1).setCell(10, 2).setCell(20, 3)

        val result = SudokuSolver.analyzeAndSolve(sparse)
        assertEquals(SudokuSolver.Solvability.SOLVABLE_MULTIPLE, result.solvability)
        assertNotNull(result.solutionBoard)
    }

    @Test
    fun testSudokuExporterSpecialCharactersEscaping() {
        val sample = SamplePuzzles.getSampleBoard(0)
        val trickyTitle = "Tom & Jerry's <\"Special\"> Sudoku"
        val xml = SudokuExporter.toOpenSudokuXml(sample, trickyTitle)

        assertTrue(xml.contains("Tom &amp; Jerry&apos;s &lt;&quot;Special&quot;&gt; Sudoku"))
        assertFalse(xml.contains("<\"Special\">"))
    }

    @Test
    fun testSudokuBoardImmutability() {
        val original = SudokuBoard.EMPTY
        val modified = original.setCell(0, 5, asGiven = true)

        assertEquals(0, original[0])
        assertEquals(0, original.clueCount)
        assertEquals(5, modified[0])
        assertEquals(1, modified.clueCount)
        assertTrue(modified.isGiven[0])
        assertFalse(original.isGiven[0])
    }

    @Test
    fun testPuzzleEntityConversion() {
        val sample = SamplePuzzles.getSampleBoard(0)
        val entity = com.example.model.PuzzleEntity.fromBoard("Entity Test", sample)

        val restoredBoard = entity.toBoard()
        assertEquals(sample.toSdmString('0'), restoredBoard.toSdmString('0'))
        assertEquals(sample.clueCount, restoredBoard.clueCount)
        assertEquals("Entity Test", entity.title)
    }
}
