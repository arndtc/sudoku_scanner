package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "puzzles")
data class PuzzleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sdmString: String,
    val cluesMask: String,
    val clueCount: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toBoard(): SudokuBoard {
        val board = SudokuBoard.fromString(sdmString, markAsGiven = false)
        val isGiven = List(81) { idx ->
            if (idx < cluesMask.length) cluesMask[idx] == '1' else board[idx] != 0
        }
        return board.copy(isGiven = isGiven)
    }

    companion object {
        fun fromBoard(title: String, board: SudokuBoard): PuzzleEntity {
            val sdm = board.toSdmString(emptyChar = '0')
            val mask = board.isGiven.map { if (it) '1' else '0' }.joinToString("")
            return PuzzleEntity(
                title = title,
                sdmString = sdm,
                cluesMask = mask,
                clueCount = board.clueCount
            )
        }
    }
}
