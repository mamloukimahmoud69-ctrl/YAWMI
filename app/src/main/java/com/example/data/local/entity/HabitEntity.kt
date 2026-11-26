package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "habits",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.SET_NULL,
    )
  ],
  indices = [
    Index(value = ["isArchived"]),
    Index(value = ["categoryId"]),
  ],
)
data class HabitEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String? = null,
  val categoryId: String? = null,
  val targetPerPeriod: Int = 1,
  val isArchived: Boolean = false,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)
