package com.example.logic

import com.example.model.SudokuBoard

object SudokuValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val conflictedIndices: Set<Int>,
        val errorMessage: String? = null
    )

    /**
     * Checks for duplicate numbers in any row, column, or 3x3 block.
     * Returns the set of all cell indices that are part of a duplicate conflict.
     */
    fun findConflicts(board: SudokuBoard): ValidationResult {
        val conflicts = mutableSetOf<Int>()

        // Check rows
        for (r in 0 until 9) {
            val seen = mutableMapOf<Int, Int>() // digit to index
            for (c in 0 until 9) {
                val idx = r * 9 + c
                val value = board[idx]
                if (value != 0) {
                    val prevIdx = seen[value]
                    if (prevIdx != null) {
                        conflicts.add(prevIdx)
                        conflicts.add(idx)
                    } else {
                        seen[value] = idx
                    }
                }
            }
        }

        // Check columns
        for (c in 0 until 9) {
            val seen = mutableMapOf<Int, Int>()
            for (r in 0 until 9) {
                val idx = r * 9 + c
                val value = board[idx]
                if (value != 0) {
                    val prevIdx = seen[value]
                    if (prevIdx != null) {
                        conflicts.add(prevIdx)
                        conflicts.add(idx)
                    } else {
                        seen[value] = idx
                    }
                }
            }
        }

        // Check 3x3 boxes
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                val seen = mutableMapOf<Int, Int>()
                for (r in 0 until 3) {
                    for (c in 0 until 3) {
                        val row = boxRow * 3 + r
                        val col = boxCol * 3 + c
                        val idx = row * 9 + col
                        val value = board[idx]
                        if (value != 0) {
                            val prevIdx = seen[value]
                            if (prevIdx != null) {
                                conflicts.add(prevIdx)
                                conflicts.add(idx)
                            } else {
                                seen[value] = idx
                            }
                        }
                    }
                }
            }
        }

        val isValid = conflicts.isEmpty()
        val errorMsg = if (!isValid) {
            "Duplicates detected in rows, columns, or 3x3 blocks"
        } else null

        return ValidationResult(
            isValid = isValid,
            conflictedIndices = conflicts,
            errorMessage = errorMsg
        )
    }

    /**
     * Checks if placing digit in cell `index` is valid according to Sudoku rules.
     */
    fun isValidPlacement(board: SudokuBoard, index: Int, digit: Int): Boolean {
        if (digit == 0) return true
        val targetRow = index / 9
        val targetCol = index % 9

        // Row check
        for (c in 0 until 9) {
            val idx = targetRow * 9 + c
            if (idx != index && board[idx] == digit) return false
        }

        // Col check
        for (r in 0 until 9) {
            val idx = r * 9 + targetCol
            if (idx != index && board[idx] == digit) return false
        }

        // 3x3 box check
        val boxStartRow = (targetRow / 3) * 3
        val boxStartCol = (targetCol / 3) * 3
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val idx = (boxStartRow + r) * 9 + (boxStartCol + c)
                if (idx != index && board[idx] == digit) return false
            }
        }

        return true
    }
}
