package com.example.domain.category

import com.example.core.time.LocalClock
import com.example.data.local.entity.CategoryEntity
import com.example.domain.repository.CategoryRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class CategoryInput(
  val name: String,
  val colorToken: String = "amber",
  val iconName: String = "folder",
  val sortOrder: Int = 0,
)

sealed interface CategoryValidationError {
  data object EmptyName : CategoryValidationError

  data class CategoryNotFound(val categoryId: String) : CategoryValidationError
}

sealed interface CategoryDomainResult<out T> {
  data class Success<T>(val value: T) : CategoryDomainResult<T>

  data class Failure(val error: CategoryValidationError) : CategoryDomainResult<Nothing>
}

class CategoryUseCases(
  private val categoryRepository: CategoryRepository,
  private val localClock: LocalClock,
  private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
  fun observeActiveCategories(): Flow<List<CategoryEntity>> =
    categoryRepository.observeActiveCategories()

  suspend fun createCategory(input: CategoryInput): CategoryDomainResult<CategoryEntity> {
    val trimmedName = input.name.trim()
    if (trimmedName.isEmpty()) {
      return CategoryDomainResult.Failure(CategoryValidationError.EmptyName)
    }
    val entity =
      CategoryEntity(
        id = idGenerator(),
        name = trimmedName,
        colorToken = input.colorToken.trim().ifEmpty { "amber" },
        iconName = input.iconName.trim().ifEmpty { "folder" },
        sortOrder = input.sortOrder,
        isArchived = false,
        updatedAtEpochMs = localClock.currentEpochMs(),
      )
    categoryRepository.upsert(entity)
    return CategoryDomainResult.Success(entity)
  }

  suspend fun updateCategory(
    categoryId: String,
    input: CategoryInput,
  ): CategoryDomainResult<CategoryEntity> {
    val existing =
      categoryRepository.getById(categoryId)
        ?: return CategoryDomainResult.Failure(CategoryValidationError.CategoryNotFound(categoryId))
    val trimmedName = input.name.trim()
    if (trimmedName.isEmpty()) {
      return CategoryDomainResult.Failure(CategoryValidationError.EmptyName)
    }
    val updated =
      existing.copy(
        name = trimmedName,
        colorToken = input.colorToken.trim().ifEmpty { existing.colorToken },
        iconName = input.iconName.trim().ifEmpty { existing.iconName },
        sortOrder = input.sortOrder,
        updatedAtEpochMs = localClock.currentEpochMs(),
      )
    categoryRepository.upsert(updated)
    return CategoryDomainResult.Success(updated)
  }

  suspend fun deleteCategory(categoryId: String): Boolean =
    categoryRepository.deleteById(categoryId) > 0
}
