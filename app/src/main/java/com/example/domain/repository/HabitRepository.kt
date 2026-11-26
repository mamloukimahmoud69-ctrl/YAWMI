package com.example.domain.repository

import com.example.data.local.entity.HabitCompletionEntity
import com.example.data.local.entity.HabitEntity
import com.example.data.local.entity.HabitRecurrenceEntity
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
  fun observeActiveHabits(): Flow<List<HabitEntity>>

  fun observeAllHabits(): Flow<List<HabitEntity>>

  fun observeHabitById(id: String): Flow<HabitEntity?>

  suspend fun getById(id: String): HabitEntity?

  suspend fun insertHabit(habit: HabitEntity)

  suspend fun updateHabit(habit: HabitEntity)

  suspend fun setArchived(id: String, isArchived: Boolean, updatedAtEpochMs: Long): Int

  suspend fun deleteHabitById(id: String): Int

  fun observeCompletionsForHabit(habitId: String): Flow<List<HabitCompletionEntity>>

  fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

  suspend fun getCompletion(habitId: String, date: String): HabitCompletionEntity?

  suspend fun insertCompletion(completion: HabitCompletionEntity)

  suspend fun updateCompletion(completion: HabitCompletionEntity)

  suspend fun deleteCompletionById(id: String): Int

  fun observeHabitRecurrence(habitId: String): Flow<HabitRecurrenceEntity?>

  suspend fun getHabitRecurrence(habitId: String): HabitRecurrenceEntity?

  suspend fun upsertHabitRecurrence(recurrence: HabitRecurrenceEntity)

  suspend fun deleteHabitRecurrence(habitId: String): Int
}
