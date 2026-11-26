package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "task_reminders",
  foreignKeys = [
    ForeignKey(
      entity = TaskEntity::class,
      parentColumns = ["id"],
      childColumns = ["taskId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["taskId", "isEnabled"]),
    Index(value = ["requestCode"], unique = true),
  ],
)
data class TaskReminderEntity(
  @PrimaryKey val id: String,
  val taskId: String,
  val triggerDate: String,
  val triggerTime: String,
  val offsetMinutes: Int = 0,
  val requestCode: Int,
  val scheduleRevision: Long = 1L,
  val isEnabled: Boolean = true,
  val scheduleState: ReminderScheduleState = ReminderScheduleState.PENDING_SYNC,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "habit_reminders",
  foreignKeys = [
    ForeignKey(
      entity = HabitEntity::class,
      parentColumns = ["id"],
      childColumns = ["habitId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["habitId", "isEnabled"]),
    Index(value = ["requestCode"], unique = true),
  ],
)
data class HabitReminderEntity(
  @PrimaryKey val id: String,
  val habitId: String,
  val triggerDate: String,
  val triggerTime: String,
  val offsetMinutes: Int = 0,
  val requestCode: Int,
  val scheduleRevision: Long = 1L,
  val isEnabled: Boolean = true,
  val scheduleState: ReminderScheduleState = ReminderScheduleState.PENDING_SYNC,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "goal_reminders",
  foreignKeys = [
    ForeignKey(
      entity = GoalEntity::class,
      parentColumns = ["id"],
      childColumns = ["goalId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["goalId", "isEnabled"]),
    Index(value = ["requestCode"], unique = true),
  ],
)
data class GoalReminderEntity(
  @PrimaryKey val id: String,
  val goalId: String,
  val triggerDate: String,
  val triggerTime: String,
  val offsetMinutes: Int = 0,
  val requestCode: Int,
  val scheduleRevision: Long = 1L,
  val isEnabled: Boolean = true,
  val scheduleState: ReminderScheduleState = ReminderScheduleState.PENDING_SYNC,
  val updatedAtEpochMs: Long,
)
