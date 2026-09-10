package com.example.logic

import com.example.model.SudokuBoard

data class SamplePuzzle(
    val title: String,
    val difficulty: String,
    val sdm: String,
    val description: String
)

object SamplePuzzles {
    val samples = listOf(
        SamplePuzzle(
            title = "Daily Newspaper Easy",
            difficulty = "Easy",
            sdm = "000000010400000000020000000000050407008000300001090000300400200050100000000806000",
            description = "Gentle puzzle with 22 initial clues. Great for verifying OCR & export."
        ),
        SamplePuzzle(
            title = "Classic Medium",
            difficulty = "Medium",
            sdm = "003020600900305001001806400008102900700000008006708200002609500800203009005010300",
            description = "Standard newspaper layout with 32 clues, crisp symmetry."
        ),
        SamplePuzzle(
            title = "Hard Challenge",
            difficulty = "Hard",
            sdm = "800000000003600000070090200050007000000045700000100030001000068008500010090000400",
            description = "24 clues with advanced logic deductions required."
        )
    )

    fun getSampleBoard(index: Int = 0): SudokuBoard {
        val sample = samples.getOrElse(index) { samples.first() }
        return SudokuBoard.fromString(sample.sdm, markAsGiven = true)
    }
}
