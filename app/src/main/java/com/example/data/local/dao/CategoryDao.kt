package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
  @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder ASC, name ASC")
  fun observeActiveCategories(): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
  fun observeAllCategories(): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories WHERE id = :id")
  suspend fun getById(id: String): CategoryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(category: CategoryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(category: CategoryEntity)

  @Update
  suspend fun update(category: CategoryEntity)

  @Query("DELETE FROM categories WHERE id = :id")
  suspend fun deleteById(id: String): Int
}
