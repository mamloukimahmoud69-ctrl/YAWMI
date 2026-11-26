package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks ORDER BY scheduledDate IS NULL ASC, scheduledDate ASC, startTime ASC, createdAtEpochMs DESC")
  fun observeAllTasks(): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE scheduledDate = :date ORDER BY startTime IS NULL ASC, startTime ASC, priority ASC")
  fun observeTasksForDate(date: String): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE scheduledDate IS NULL ORDER BY updatedAtEpochMs DESC")
  fun observeUnscheduledTasks(): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE id = :id")
  fun observeTaskById(id: String): Flow<TaskEntity?>

  @Query("SELECT * FROM tasks WHERE id = :id")
  suspend fun getById(id: String): TaskEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(task: TaskEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(task: TaskEntity)

  @Update
  suspend fun update(task: TaskEntity)

  @Query(
    "UPDATE tasks SET status = :status, updatedAtEpochMs = :updatedAtEpochMs, completedAtEpochMs = :completedAtEpochMs WHERE id = :id"
  )
  suspend fun updateStatus(
    id: String,
    status: TaskStatus,
    updatedAtEpochMs: Long,
    completedAtEpochMs: Long?,
  ): Int

  @Query("DELETE FROM tasks WHERE id = :id")
  suspend fun deleteById(id: String): Int
}
