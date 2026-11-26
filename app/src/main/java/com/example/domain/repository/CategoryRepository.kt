package com.example.domain.repository

import com.example.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
  fun observeActiveCategories(): Flow<List<CategoryEntity>>

  fun observeAllCategories(): Flow<List<CategoryEntity>>

  suspend fun getById(id: String): CategoryEntity?

  suspend fun upsert(category: CategoryEntity)

  suspend fun deleteById(id: String): Int
}
