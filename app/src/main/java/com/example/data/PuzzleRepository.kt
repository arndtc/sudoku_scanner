package com.example.data

import com.example.model.PuzzleEntity
import kotlinx.coroutines.flow.Flow

class PuzzleRepository(private val puzzleDao: PuzzleDao) {
    val allPuzzles: Flow<List<PuzzleEntity>> = puzzleDao.getAllPuzzles()

    suspend fun getPuzzleById(id: Long): PuzzleEntity? = puzzleDao.getPuzzleById(id)

    suspend fun insertPuzzle(puzzle: PuzzleEntity): Long = puzzleDao.insertPuzzle(puzzle)

    suspend fun updatePuzzle(puzzle: PuzzleEntity) = puzzleDao.updatePuzzle(puzzle)

    suspend fun deletePuzzle(puzzle: PuzzleEntity) = puzzleDao.deletePuzzle(puzzle)

    suspend fun deletePuzzleById(id: Long) = puzzleDao.deletePuzzleById(id)
}
