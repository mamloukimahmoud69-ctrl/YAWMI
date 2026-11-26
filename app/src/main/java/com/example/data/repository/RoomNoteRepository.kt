package com.example.data.repository

import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.NoteEntity
import com.example.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow

class RoomNoteRepository(
  private val noteDao: NoteDao,
) : NoteRepository {
  override fun observeAllNotes(): Flow<List<NoteEntity>> = noteDao.observeAllNotes()

  override fun observeNotesForTask(taskId: String): Flow<List<NoteEntity>> =
    noteDao.observeNotesForTask(taskId)

  override fun observeNotesForGoal(goalId: String): Flow<List<NoteEntity>> =
    noteDao.observeNotesForGoal(goalId)

  override suspend fun getById(id: String): NoteEntity? = noteDao.getById(id)

  override suspend fun insert(note: NoteEntity) = noteDao.insert(note)

  override suspend fun update(note: NoteEntity) = noteDao.update(note)

  override suspend fun deleteById(id: String): Int = noteDao.deleteById(id)
}
