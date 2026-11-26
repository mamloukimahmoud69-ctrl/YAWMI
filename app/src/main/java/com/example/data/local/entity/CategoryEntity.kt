package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "categories",
  indices = [Index(value = ["sortOrder"])],
)
data class CategoryEntity(
  @PrimaryKey val id: String,
  val name: String,
  val colorToken: String,
  val iconName: String,
  val sortOrder: Int = 0,
  val isArchived: Boolean = false,
  val updatedAtEpochMs: Long,
)
