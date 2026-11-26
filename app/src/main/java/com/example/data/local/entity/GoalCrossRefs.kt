package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "goal_task_cross_refs",
  primaryKeys = ["goalId", "taskId"],
  foreignKeys = [
    ForeignKey(
      entity = GoalEntity::class,
      parentColumns = ["id"],
      childColumns = ["goalId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = TaskEntity::class,
      parentColumns = ["id"],
      childColumns = ["taskId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["goalId"]),
    Index(value = ["taskId"]),
  ],
)
data class GoalTaskCrossRef(
  val goalId: String,
  val taskId: String,
)

@Entity(
  tableName = "goal_habit_cross_refs",
  primaryKeys = ["goalId", "habitId"],
  foreignKeys = [
    ForeignKey(
      entity = GoalEntity::class,
      parentColumns = ["id"],
      childColumns = ["goalId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = HabitEntity::class,
      parentColumns = ["id"],
      childColumns = ["habitId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["goalId"]),
    Index(value = ["habitId"]),
  ],
)
data class GoalHabitCrossRef(
  val goalId: String,
  val habitId: String,
)
