package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.GoalReminderEntity
import com.example.data.local.entity.HabitReminderEntity
import com.example.data.local.entity.TaskReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
  // Task Reminders
  @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY triggerDate ASC, triggerTime ASC")
  fun observeTaskReminders(taskId: String): Flow<List<TaskReminderEntity>>

  @Query("SELECT * FROM task_reminders WHERE isEnabled = 1")
  fun observeAllEnabledTaskReminders(): Flow<List<TaskReminderEntity>>

  @Query("SELECT * FROM task_reminders WHERE id = :id")
  suspend fun getTaskReminderById(id: String): TaskReminderEntity?

  @Query("SELECT * FROM task_reminders WHERE taskId = :taskId")
  suspend fun getTaskRemindersByTaskId(taskId: String): List<TaskReminderEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertTaskReminder(reminder: TaskReminderEntity)

  @Update
  suspend fun updateTaskReminder(reminder: TaskReminderEntity)

  @Query("DELETE FROM task_reminders WHERE id = :id")
  suspend fun deleteTaskReminderById(id: String): Int

  // Habit Reminders
  @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId ORDER BY triggerDate ASC, triggerTime ASC")
  fun observeHabitReminders(habitId: String): Flow<List<HabitReminderEntity>>

  @Query("SELECT * FROM habit_reminders WHERE isEnabled = 1")
  fun observeAllEnabledHabitReminders(): Flow<List<HabitReminderEntity>>

  @Query("SELECT * FROM habit_reminders WHERE id = :id")
  suspend fun getHabitReminderById(id: String): HabitReminderEntity?

  @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId")
  suspend fun getHabitRemindersByHabitId(habitId: String): List<HabitReminderEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertHabitReminder(reminder: HabitReminderEntity)

  @Update
  suspend fun updateHabitReminder(reminder: HabitReminderEntity)

  @Query("DELETE FROM habit_reminders WHERE id = :id")
  suspend fun deleteHabitReminderById(id: String): Int

  // Goal Reminders
  @Query("SELECT * FROM goal_reminders WHERE goalId = :goalId ORDER BY triggerDate ASC, triggerTime ASC")
  fun observeGoalReminders(goalId: String): Flow<List<GoalReminderEntity>>

  @Query("SELECT * FROM goal_reminders WHERE isEnabled = 1")
  fun observeAllEnabledGoalReminders(): Flow<List<GoalReminderEntity>>

  @Query("SELECT * FROM goal_reminders WHERE id = :id")
  suspend fun getGoalReminderById(id: String): GoalReminderEntity?

  @Query("SELECT * FROM goal_reminders WHERE goalId = :goalId")
  suspend fun getGoalRemindersByGoalId(goalId: String): List<GoalReminderEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertGoalReminder(reminder: GoalReminderEntity)

  @Update
  suspend fun updateGoalReminder(reminder: GoalReminderEntity)

  @Query("DELETE FROM goal_reminders WHERE id = :id")
  suspend fun deleteGoalReminderById(id: String): Int

  // Global requestCode inspection across all 3 reminder tables
  @Query(
    """
    SELECT MAX(max_code) FROM (
      SELECT MAX(requestCode) AS max_code FROM task_reminders
      UNION ALL
      SELECT MAX(requestCode) AS max_code FROM habit_reminders
      UNION ALL
      SELECT MAX(requestCode) AS max_code FROM goal_reminders
    )
    """
  )
  suspend fun getMaxReminderRequestCode(): Int?

  @Query(
    """
    SELECT EXISTS(SELECT 1 FROM task_reminders WHERE requestCode = :requestCode)
    OR EXISTS(SELECT 1 FROM habit_reminders WHERE requestCode = :requestCode)
    OR EXISTS(SELECT 1 FROM goal_reminders WHERE requestCode = :requestCode)
    """
  )
  suspend fun isRequestCodeUsedInReminders(requestCode: Int): Boolean
}
