package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.HabitCompletionEntity
import com.example.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
  @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY createdAtEpochMs ASC")
  fun observeActiveHabits(): Flow<List<HabitEntity>>

  @Query("SELECT * FROM habits ORDER BY isArchived ASC, createdAtEpochMs ASC")
  fun observeAllHabits(): Flow<List<HabitEntity>>

  @Query("SELECT * FROM habits WHERE id = :id")
  fun observeHabitById(id: String): Flow<HabitEntity?>

  @Query("SELECT * FROM habits WHERE id = :id")
  suspend fun getById(id: String): HabitEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertHabit(habit: HabitEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertHabit(habit: HabitEntity)

  @Update
  suspend fun updateHabit(habit: HabitEntity)

  @Query("UPDATE habits SET isArchived = :isArchived, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
  suspend fun setArchived(id: String, isArchived: Boolean, updatedAtEpochMs: Long): Int

  @Query("DELETE FROM habits WHERE id = :id")
  suspend fun deleteHabitById(id: String): Int

  @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY completedDate DESC")
  fun observeCompletionsForHabit(habitId: String): Flow<List<HabitCompletionEntity>>

  @Query("SELECT * FROM habit_completions WHERE completedDate = :date")
  fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

  @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND completedDate = :date")
  suspend fun getCompletion(habitId: String, date: String): HabitCompletionEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCompletion(completion: HabitCompletionEntity)

  @Update
  suspend fun updateCompletion(completion: HabitCompletionEntity)

  @Query("DELETE FROM habit_completions WHERE id = :id")
  suspend fun deleteCompletionById(id: String): Int
}
