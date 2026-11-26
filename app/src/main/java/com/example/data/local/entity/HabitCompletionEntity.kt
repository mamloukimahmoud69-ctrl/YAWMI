package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "habit_completions",
  foreignKeys = [
    ForeignKey(
      entity = HabitEntity::class,
      parentColumns = ["id"],
      childColumns = ["habitId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["habitId", "completedDate"], unique = true),
  ],
)
data class HabitCompletionEntity(
  @PrimaryKey val id: String,
  val habitId: String,
  val completedDate: String,
  val count: Int = 1,
  val completedAtEpochMs: Long,
  val ruleSnapshot: String,
)
