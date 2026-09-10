package com.example.model

/**
 * Represents a 9x9 Sudoku puzzle board.
 * Cells are indexed from 0 to 80 (row * 9 + col).
 * A cell value of 0 indicates an empty cell, while 1..9 represent digits.
 */
data class SudokuBoard(
    val cells: List<Int> = List(81) { 0 },
    val isGiven: List<Boolean> = List(81) { false }
) {
    init {
        require(cells.size == 81) { "Sudoku board must have exactly 81 cells." }
        require(isGiven.size == 81) { "Sudoku board must have 81 given flags." }
    }

    operator fun get(row: Int, col: Int): Int = cells[row * 9 + col]
    operator fun get(index: Int): Int = cells[index]

    fun setCell(index: Int, value: Int, asGiven: Boolean? = null): SudokuBoard {
        require(index in 0..80)
        require(value in 0..9)
        val newCells = cells.toMutableList().apply { this[index] = value }
        val newGivens = isGiven.toMutableList().apply {
            if (asGiven != null) {
                this[index] = asGiven && value != 0
            } else if (value == 0) {
                this[index] = false
            }
        }
        return copy(cells = newCells, isGiven = newGivens)
    }

    fun toSdmString(emptyChar: Char = '0'): String {
        return cells.joinToString("") { value ->
            if (value == 0) emptyChar.toString() else value.toString()
        }
    }

    val clueCount: Int
        get() = cells.count { it != 0 }

    val givenCount: Int
        get() = cells.indices.count { isGiven[it] && cells[it] != 0 }

    val isFull: Boolean
        get() = cells.none { it == 0 }

    companion object {
        val EMPTY = SudokuBoard()

        fun fromString(str: String, markAsGiven: Boolean = true): SudokuBoard {
            val sanitized = str.filter { it in '0'..'9' || it == '.' }
            val cells = MutableList(81) { 0 }
            val isGiven = MutableList(81) { false }
            for (i in 0 until minOf(81, sanitized.length)) {
                val c = sanitized[i]
                val digit = if (c in '1'..'9') c.digitToInt() else 0
                cells[i] = digit
                isGiven[i] = markAsGiven && (digit != 0)
            }
            return SudokuBoard(cells = cells, isGiven = isGiven)
        }
    }
}
