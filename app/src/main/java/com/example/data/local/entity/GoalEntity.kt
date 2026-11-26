package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "goals",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.SET_NULL,
    )
  ],
  indices = [
    Index(value = ["categoryId"]),
    Index(value = ["status"]),
  ],
)
data class GoalEntity(
  @PrimaryKey val id: String,
  val name: String,
  val description: String? = null,
  val categoryId: String? = null,
  val startDate: String,
  val targetDate: String? = null,
  val targetValue: Double? = null,
  val currentProgress: Double = 0.0,
  val allowOverTarget: Boolean = false,
  val status: GoalStatus = GoalStatus.ACTIVE,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)
