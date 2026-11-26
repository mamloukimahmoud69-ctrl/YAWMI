package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "notes",
  foreignKeys = [
    ForeignKey(
      entity = TaskEntity::class,
      parentColumns = ["id"],
      childColumns = ["linkedTaskId"],
      onDelete = ForeignKey.SET_NULL,
    ),
    ForeignKey(
      entity = GoalEntity::class,
      parentColumns = ["id"],
      childColumns = ["linkedGoalId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["linkedTaskId"]),
    Index(value = ["linkedGoalId"]),
    Index(value = ["isPinned", "updatedAtEpochMs"]),
  ],
)
data class NoteEntity(
  @PrimaryKey val id: String,
  val title: String,
  val content: String,
  val linkedTaskId: String? = null,
  val linkedGoalId: String? = null,
  val isPinned: Boolean = false,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)
