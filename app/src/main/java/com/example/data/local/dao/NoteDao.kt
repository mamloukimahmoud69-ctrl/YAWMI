package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
  @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAtEpochMs DESC")
  fun observeAllNotes(): Flow<List<NoteEntity>>

  @Query("SELECT * FROM notes WHERE linkedTaskId = :taskId ORDER BY updatedAtEpochMs DESC")
  fun observeNotesForTask(taskId: String): Flow<List<NoteEntity>>

  @Query("SELECT * FROM notes WHERE linkedGoalId = :goalId ORDER BY updatedAtEpochMs DESC")
  fun observeNotesForGoal(goalId: String): Flow<List<NoteEntity>>

  @Query("SELECT * FROM notes WHERE id = :id")
  suspend fun getById(id: String): NoteEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(note: NoteEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(note: NoteEntity)

  @Update
  suspend fun update(note: NoteEntity)

  @Query("DELETE FROM notes WHERE id = :id")
  suspend fun deleteById(id: String): Int
}
