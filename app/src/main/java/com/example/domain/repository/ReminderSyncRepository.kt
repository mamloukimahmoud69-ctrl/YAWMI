package com.example.domain.repository

import com.example.data.local.entity.AlarmSyncOutboxEntity
import com.example.data.local.entity.DeliveryState
import com.example.data.local.entity.GoalReminderDeliveryEntity
import com.example.data.local.entity.GoalReminderEntity
import com.example.data.local.entity.HabitReminderDeliveryEntity
import com.example.data.local.entity.HabitReminderEntity
import com.example.data.local.entity.TaskReminderDeliveryEntity
import com.example.data.local.entity.TaskReminderEntity
import kotlinx.coroutines.flow.Flow

interface ReminderSyncRepository {
  // Reminders Observation & Query
  fun observeTaskReminders(taskId: String): Flow<List<TaskReminderEntity>>

  suspend fun getTaskReminders(taskId: String): List<TaskReminderEntity>

  fun observeHabitReminders(habitId: String): Flow<List<HabitReminderEntity>>

  fun observeGoalReminders(goalId: String): Flow<List<GoalReminderEntity>>

  // Transactional Reminder Mutations + Outbox Staging
  suspend fun createTaskReminder(
    reminderId: String,
    taskId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    nowEpochMs: Long,
  ): TaskReminderEntity

  suspend fun createHabitReminder(
    reminderId: String,
    habitId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    nowEpochMs: Long,
  ): HabitReminderEntity

  suspend fun createGoalReminder(
    reminderId: String,
    goalId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    nowEpochMs: Long,
  ): GoalReminderEntity

  suspend fun updateTaskReminderSchedule(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    isEnabled: Boolean,
    nowEpochMs: Long,
  ): TaskReminderEntity

  suspend fun snoozeTaskReminder(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    nowEpochMs: Long,
  ): TaskReminderEntity

  suspend fun updateHabitReminderSchedule(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    isEnabled: Boolean,
    nowEpochMs: Long,
  ): HabitReminderEntity

  suspend fun updateGoalReminderSchedule(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    isEnabled: Boolean,
    nowEpochMs: Long,
  ): GoalReminderEntity

  suspend fun deleteTaskReminder(reminderId: String, nowEpochMs: Long): Boolean

  suspend fun deleteHabitReminder(reminderId: String, nowEpochMs: Long): Boolean

  suspend fun deleteGoalReminder(reminderId: String, nowEpochMs: Long): Boolean

  // Delivery Ledgers
  fun observeTaskDeliveries(reminderId: String): Flow<List<TaskReminderDeliveryEntity>>

  fun observeHabitDeliveries(reminderId: String): Flow<List<HabitReminderDeliveryEntity>>

  fun observeGoalDeliveries(reminderId: String): Flow<List<GoalReminderDeliveryEntity>>

  suspend fun recordPendingTaskDelivery(
    deliveryId: String,
    taskReminderId: String,
    occurrenceKey: String,
    scheduledAtEpochMs: Long,
    firedScheduleRevision: Long,
    nowEpochMs: Long,
  ): TaskReminderDeliveryEntity

  suspend fun recordPendingHabitDelivery(
    deliveryId: String,
    habitReminderId: String,
    occurrenceKey: String,
    scheduledAtEpochMs: Long,
    firedScheduleRevision: Long,
    nowEpochMs: Long,
  ): HabitReminderDeliveryEntity

  suspend fun recordPendingGoalDelivery(
    deliveryId: String,
    goalReminderId: String,
    occurrenceKey: String,
    scheduledAtEpochMs: Long,
    firedScheduleRevision: Long,
    nowEpochMs: Long,
  ): GoalReminderDeliveryEntity

  suspend fun updateTaskDeliveryState(
    deliveryId: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    nowEpochMs: Long,
  ): Int

  suspend fun updateHabitDeliveryState(
    deliveryId: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    nowEpochMs: Long,
  ): Int

  suspend fun updateGoalDeliveryState(
    deliveryId: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    nowEpochMs: Long,
  ): Int

  // Outbox & Internal Metadata
  fun observeOutboxOrdered(): Flow<List<AlarmSyncOutboxEntity>>

  suspend fun getAllOutboxOrdered(): List<AlarmSyncOutboxEntity>

  suspend fun getMetadataValue(key: String): String?
}
