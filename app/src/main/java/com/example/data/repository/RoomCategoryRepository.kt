package com.example.data.repository

import com.example.data.local.dao.CategoryDao
import com.example.data.local.entity.CategoryEntity
import com.example.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class RoomCategoryRepository(
  private val categoryDao: CategoryDao,
) : CategoryRepository {
  override fun observeActiveCategories(): Flow<List<CategoryEntity>> =
    categoryDao.observeActiveCategories()

  override fun observeAllCategories(): Flow<List<CategoryEntity>> =
    categoryDao.observeAllCategories()

  override suspend fun getById(id: String): CategoryEntity? = categoryDao.getById(id)

  override suspend fun upsert(category: CategoryEntity) = categoryDao.upsert(category)

  override suspend fun deleteById(id: String): Int = categoryDao.deleteById(id)
}
