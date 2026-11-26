package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "tasks",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.SET_NULL,
    )
  ],
  indices = [
    Index(value = ["scheduledDate", "status"]),
    Index(value = ["categoryId"]),
    Index(value = ["priority"]),
  ],
)
data class TaskEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String? = null,
  val scheduledDate: String? = null,
  val startTime: String? = null,
  val endTime: String? = null,
  val priority: TaskPriority = TaskPriority.MEDIUM,
  val status: TaskStatus = TaskStatus.PENDING,
  val categoryId: String? = null,
  val notes: String? = null,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
  val completedAtEpochMs: Long? = null,
)
