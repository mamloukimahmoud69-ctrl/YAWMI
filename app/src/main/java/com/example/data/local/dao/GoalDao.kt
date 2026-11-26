package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.GoalHabitCrossRef
import com.example.data.local.entity.GoalStatus
import com.example.data.local.entity.GoalTaskCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
  @Query("SELECT * FROM goals WHERE status != 'ARCHIVED' ORDER BY createdAtEpochMs DESC")
  fun observeActiveGoals(): Flow<List<GoalEntity>>

  @Query("SELECT * FROM goals ORDER BY createdAtEpochMs DESC")
  fun observeAllGoals(): Flow<List<GoalEntity>>

  @Query("SELECT * FROM goals WHERE id = :id")
  fun observeGoalById(id: String): Flow<GoalEntity?>

  @Query("SELECT * FROM goals WHERE id = :id")
  suspend fun getById(id: String): GoalEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertGoal(goal: GoalEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertGoal(goal: GoalEntity)

  @Update
  suspend fun updateGoal(goal: GoalEntity)

  @Query("UPDATE goals SET status = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
  suspend fun updateStatus(id: String, status: GoalStatus, updatedAtEpochMs: Long): Int

  @Query("DELETE FROM goals WHERE id = :id")
  suspend fun deleteGoalById(id: String): Int

  @Query("SELECT * FROM goal_task_cross_refs WHERE goalId = :goalId")
  fun observeLinkedTasks(goalId: String): Flow<List<GoalTaskCrossRef>>

  @Query("SELECT * FROM goal_task_cross_refs WHERE taskId = :taskId")
  suspend fun getGoalLinksForTask(taskId: String): List<GoalTaskCrossRef>

  @Query("SELECT * FROM goal_habit_cross_refs WHERE goalId = :goalId")
  fun observeLinkedHabits(goalId: String): Flow<List<GoalHabitCrossRef>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun linkTask(ref: GoalTaskCrossRef)

  @Query("DELETE FROM goal_task_cross_refs WHERE goalId = :goalId AND taskId = :taskId")
  suspend fun unlinkTask(goalId: String, taskId: String): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun linkHabit(ref: GoalHabitCrossRef)

  @Query("DELETE FROM goal_habit_cross_refs WHERE goalId = :goalId AND habitId = :habitId")
  suspend fun unlinkHabit(goalId: String, habitId: String): Int
}
