package com.example.logic

import com.example.model.SudokuBoard

object SudokuSolver {

    enum class Solvability {
        SOLVABLE_UNIQUE,
        SOLVABLE_MULTIPLE,
        UNSOLVABLE,
        INVALID_DUPLICATES
    }

    data class SolveResult(
        val solvability: Solvability,
        val solutionBoard: SudokuBoard? = null,
        val solutionsCount: Int = 0
    )

    /**
     * Attempts to solve the given board and determines its uniqueness.
     * Searches for up to 2 solutions to distinguish between unique and multiple.
     */
    fun analyzeAndSolve(board: SudokuBoard): SolveResult {
        // Fast pre-check: are there duplicate conflicts on current board?
        val validation = SudokuValidator.findConflicts(board)
        if (!validation.isValid) {
            return SolveResult(Solvability.INVALID_DUPLICATES)
        }

        val grid = IntArray(81) { board[it] }
        val solutions = mutableListOf<IntArray>()

        fun solveRecursive(cellIndex: Int): Boolean {
            if (cellIndex == 81) {
                solutions.add(grid.clone())
                return solutions.size >= 2 // stop after 2 solutions
            }

            if (grid[cellIndex] != 0) {
                return solveRecursive(cellIndex + 1)
            }

            val row = cellIndex / 9
            val col = cellIndex % 9
            val boxStartRow = (row / 3) * 3
            val boxStartCol = (col / 3) * 3

            for (num in 1..9) {
                var canPlace = true

                // Check row and column
                for (i in 0 until 9) {
                    if (grid[row * 9 + i] == num || grid[i * 9 + col] == num) {
                        canPlace = false
                        break
                    }
                }

                if (canPlace) {
                    // Check box
                    for (r in 0 until 3) {
                        for (c in 0 until 3) {
                            if (grid[(boxStartRow + r) * 9 + (boxStartCol + c)] == num) {
                                canPlace = false
                                break
                            }
                        }
                        if (!canPlace) break
                    }
                }

                if (canPlace) {
                    grid[cellIndex] = num
                    if (solveRecursive(cellIndex + 1)) return true
                    grid[cellIndex] = 0
                }
            }

            return false
        }

        solveRecursive(0)

        return when (solutions.size) {
            0 -> SolveResult(Solvability.UNSOLVABLE, solutionsCount = 0)
            1 -> SolveResult(
                solvability = Solvability.SOLVABLE_UNIQUE,
                solutionBoard = SudokuBoard(
                    cells = solutions[0].toList(),
                    isGiven = board.isGiven
                ),
                solutionsCount = 1
            )
            else -> SolveResult(
                solvability = Solvability.SOLVABLE_MULTIPLE,
                solutionBoard = SudokuBoard(
                    cells = solutions[0].toList(),
                    isGiven = board.isGiven
                ),
                solutionsCount = solutions.size
            )
        }
    }
}
