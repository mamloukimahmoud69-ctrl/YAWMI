package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "task_recurrences",
  foreignKeys = [
    ForeignKey(
      entity = TaskEntity::class,
      parentColumns = ["id"],
      childColumns = ["taskId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["taskId"], unique = true),
  ],
)
data class TaskRecurrenceEntity(
  @PrimaryKey val id: String,
  val taskId: String,
  val frequency: RecurrenceFrequency,
  val interval: Int = 1,
  val daysOfWeekMask: Int = 0,
  val endDate: String? = null,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "habit_recurrences",
  foreignKeys = [
    ForeignKey(
      entity = HabitEntity::class,
      parentColumns = ["id"],
      childColumns = ["habitId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["habitId"], unique = true),
  ],
)
data class HabitRecurrenceEntity(
  @PrimaryKey val id: String,
  val habitId: String,
  val frequency: RecurrenceFrequency,
  val interval: Int = 1,
  val daysOfWeekMask: Int = 0,
  val endDate: String? = null,
  val updatedAtEpochMs: Long,
)
