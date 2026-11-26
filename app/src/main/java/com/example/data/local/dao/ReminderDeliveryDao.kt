package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DeliveryState
import com.example.data.local.entity.GoalReminderDeliveryEntity
import com.example.data.local.entity.HabitReminderDeliveryEntity
import com.example.data.local.entity.TaskReminderDeliveryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDeliveryDao {
  // Task Deliveries
  @Query("SELECT * FROM task_reminder_deliveries WHERE taskReminderId = :reminderId ORDER BY scheduledAtEpochMs DESC")
  fun observeTaskDeliveries(reminderId: String): Flow<List<TaskReminderDeliveryEntity>>

  @Query("SELECT * FROM task_reminder_deliveries WHERE taskReminderId = :reminderId AND occurrenceKey = :occurrenceKey")
  suspend fun getTaskDelivery(reminderId: String, occurrenceKey: String): TaskReminderDeliveryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertTaskDelivery(delivery: TaskReminderDeliveryEntity)

  @Query(
    """
    UPDATE task_reminder_deliveries
    SET state = :state,
        attemptCount = :attemptCount,
        lastAttemptAtEpochMs = :lastAttemptAtEpochMs,
        lastError = :lastError,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :id
    """
  )
  suspend fun updateTaskDeliveryState(
    id: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    updatedAtEpochMs: Long,
  ): Int

  @Query(
    """
    UPDATE task_reminder_deliveries
    SET state = 'STALE', updatedAtEpochMs = :updatedAtEpochMs
    WHERE taskReminderId = :reminderId AND state = 'PENDING'
    """
  )
  suspend fun markPendingTaskDeliveriesStale(reminderId: String, updatedAtEpochMs: Long): Int

  // Habit Deliveries
  @Query("SELECT * FROM habit_reminder_deliveries WHERE habitReminderId = :reminderId ORDER BY scheduledAtEpochMs DESC")
  fun observeHabitDeliveries(reminderId: String): Flow<List<HabitReminderDeliveryEntity>>

  @Query("SELECT * FROM habit_reminder_deliveries WHERE habitReminderId = :reminderId AND occurrenceKey = :occurrenceKey")
  suspend fun getHabitDelivery(reminderId: String, occurrenceKey: String): HabitReminderDeliveryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertHabitDelivery(delivery: HabitReminderDeliveryEntity)

  @Query(
    """
    UPDATE habit_reminder_deliveries
    SET state = :state,
        attemptCount = :attemptCount,
        lastAttemptAtEpochMs = :lastAttemptAtEpochMs,
        lastError = :lastError,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :id
    """
  )
  suspend fun updateHabitDeliveryState(
    id: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    updatedAtEpochMs: Long,
  ): Int

  @Query(
    """
    UPDATE habit_reminder_deliveries
    SET state = 'STALE', updatedAtEpochMs = :updatedAtEpochMs
    WHERE habitReminderId = :reminderId AND state = 'PENDING'
    """
  )
  suspend fun markPendingHabitDeliveriesStale(reminderId: String, updatedAtEpochMs: Long): Int

  // Goal Deliveries
  @Query("SELECT * FROM goal_reminder_deliveries WHERE goalReminderId = :reminderId ORDER BY scheduledAtEpochMs DESC")
  fun observeGoalDeliveries(reminderId: String): Flow<List<GoalReminderDeliveryEntity>>

  @Query("SELECT * FROM goal_reminder_deliveries WHERE goalReminderId = :reminderId AND occurrenceKey = :occurrenceKey")
  suspend fun getGoalDelivery(reminderId: String, occurrenceKey: String): GoalReminderDeliveryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertGoalDelivery(delivery: GoalReminderDeliveryEntity)

  @Query(
    """
    UPDATE goal_reminder_deliveries
    SET state = :state,
        attemptCount = :attemptCount,
        lastAttemptAtEpochMs = :lastAttemptAtEpochMs,
        lastError = :lastError,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :id
    """
  )
  suspend fun updateGoalDeliveryState(
    id: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    updatedAtEpochMs: Long,
  ): Int

  @Query(
    """
    UPDATE goal_reminder_deliveries
    SET state = 'STALE', updatedAtEpochMs = :updatedAtEpochMs
    WHERE goalReminderId = :reminderId AND state = 'PENDING'
    """
  )
  suspend fun markPendingGoalDeliveriesStale(reminderId: String, updatedAtEpochMs: Long): Int
}
