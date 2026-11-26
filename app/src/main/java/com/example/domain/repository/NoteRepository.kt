package com.example.domain.repository

import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
  fun observeAllNotes(): Flow<List<NoteEntity>>

  fun observeNotesForTask(taskId: String): Flow<List<NoteEntity>>

  fun observeNotesForGoal(goalId: String): Flow<List<NoteEntity>>

  suspend fun getById(id: String): NoteEntity?

  suspend fun insert(note: NoteEntity)

  suspend fun update(note: NoteEntity)

  suspend fun deleteById(id: String): Int
}
