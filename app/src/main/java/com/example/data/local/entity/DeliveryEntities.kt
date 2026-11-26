package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "task_reminder_deliveries",
  foreignKeys = [
    ForeignKey(
      entity = TaskReminderEntity::class,
      parentColumns = ["id"],
      childColumns = ["taskReminderId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["taskReminderId", "occurrenceKey"], unique = true),
    Index(value = ["state"]),
  ],
)
data class TaskReminderDeliveryEntity(
  @PrimaryKey val id: String,
  val taskReminderId: String,
  val occurrenceKey: String,
  val scheduledAtEpochMs: Long,
  val firedScheduleRevision: Long,
  val state: DeliveryState,
  val notificationTag: String,
  val notificationId: Int = 0,
  val attemptCount: Int = 0,
  val lastAttemptAtEpochMs: Long? = null,
  val lastError: String? = null,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "habit_reminder_deliveries",
  foreignKeys = [
    ForeignKey(
      entity = HabitReminderEntity::class,
      parentColumns = ["id"],
      childColumns = ["habitReminderId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["habitReminderId", "occurrenceKey"], unique = true),
    Index(value = ["state"]),
  ],
)
data class HabitReminderDeliveryEntity(
  @PrimaryKey val id: String,
  val habitReminderId: String,
  val occurrenceKey: String,
  val scheduledAtEpochMs: Long,
  val firedScheduleRevision: Long,
  val state: DeliveryState,
  val notificationTag: String,
  val notificationId: Int = 0,
  val attemptCount: Int = 0,
  val lastAttemptAtEpochMs: Long? = null,
  val lastError: String? = null,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "goal_reminder_deliveries",
  foreignKeys = [
    ForeignKey(
      entity = GoalReminderEntity::class,
      parentColumns = ["id"],
      childColumns = ["goalReminderId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["goalReminderId", "occurrenceKey"], unique = true),
    Index(value = ["state"]),
  ],
)
data class GoalReminderDeliveryEntity(
  @PrimaryKey val id: String,
  val goalReminderId: String,
  val occurrenceKey: String,
  val scheduledAtEpochMs: Long,
  val firedScheduleRevision: Long,
  val state: DeliveryState,
  val notificationTag: String,
  val notificationId: Int = 0,
  val attemptCount: Int = 0,
  val lastAttemptAtEpochMs: Long? = null,
  val lastError: String? = null,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)
