package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.HabitRecurrenceEntity
import com.example.data.local.entity.TaskRecurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceDao {
  @Query("SELECT * FROM task_recurrences WHERE taskId = :taskId")
  fun observeTaskRecurrence(taskId: String): Flow<TaskRecurrenceEntity?>

  @Query("SELECT * FROM task_recurrences WHERE taskId = :taskId")
  suspend fun getTaskRecurrenceByTaskId(taskId: String): TaskRecurrenceEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertTaskRecurrence(recurrence: TaskRecurrenceEntity)

  @Update
  suspend fun updateTaskRecurrence(recurrence: TaskRecurrenceEntity)

  @Query("DELETE FROM task_recurrences WHERE taskId = :taskId")
  suspend fun deleteTaskRecurrenceByTaskId(taskId: String): Int

  @Query("SELECT * FROM habit_recurrences WHERE habitId = :habitId")
  fun observeHabitRecurrence(habitId: String): Flow<HabitRecurrenceEntity?>

  @Query("SELECT * FROM habit_recurrences WHERE habitId = :habitId")
  suspend fun getHabitRecurrenceByHabitId(habitId: String): HabitRecurrenceEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertHabitRecurrence(recurrence: HabitRecurrenceEntity)

  @Update
  suspend fun updateHabitRecurrence(recurrence: HabitRecurrenceEntity)

  @Query("DELETE FROM habit_recurrences WHERE habitId = :habitId")
  suspend fun deleteHabitRecurrenceByHabitId(habitId: String): Int
}
