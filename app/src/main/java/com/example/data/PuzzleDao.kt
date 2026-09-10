package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.PuzzleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleDao {
    @Query("SELECT * FROM puzzles ORDER BY createdAt DESC")
    fun getAllPuzzles(): Flow<List<PuzzleEntity>>

    @Query("SELECT * FROM puzzles WHERE id = :id LIMIT 1")
    suspend fun getPuzzleById(id: Long): PuzzleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPuzzle(puzzle: PuzzleEntity): Long

    @Update
    suspend fun updatePuzzle(puzzle: PuzzleEntity)

    @Delete
    suspend fun deletePuzzle(puzzle: PuzzleEntity)

    @Query("DELETE FROM puzzles WHERE id = :id")
    suspend fun deletePuzzleById(id: Long)
}
