package com.example.domain.repository

import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskRecurrenceEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.task.TaskCompletionResult
import com.example.domain.task.TaskDomainResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
  fun observeAllTasks(): Flow<List<TaskEntity>>

  fun observeTasksForDate(date: String): Flow<List<TaskEntity>>

  fun observeUnscheduledTasks(): Flow<List<TaskEntity>>

  fun observeTaskById(id: String): Flow<TaskEntity?>

  suspend fun getById(id: String): TaskEntity?

  suspend fun insert(task: TaskEntity)

  suspend fun update(task: TaskEntity)

  suspend fun updateStatus(
    id: String,
    status: TaskStatus,
    updatedAtEpochMs: Long,
    completedAtEpochMs: Long?,
  ): Int

  suspend fun completeTaskWithRecurrence(
    taskId: String,
    nowEpochMs: Long,
    idGenerator: () -> String = { UUID.randomUUID().toString() },
  ): TaskDomainResult<TaskCompletionResult>

  suspend fun deleteById(id: String): Int

  fun observeTaskRecurrence(taskId: String): Flow<TaskRecurrenceEntity?>

  suspend fun getTaskRecurrence(taskId: String): TaskRecurrenceEntity?

  suspend fun upsertTaskRecurrence(recurrence: TaskRecurrenceEntity)

  suspend fun deleteTaskRecurrence(taskId: String): Int
}
