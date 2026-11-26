package com.example.domain.repository

import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.GoalHabitCrossRef
import com.example.data.local.entity.GoalStatus
import com.example.data.local.entity.GoalTaskCrossRef
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
  fun observeActiveGoals(): Flow<List<GoalEntity>>

  fun observeAllGoals(): Flow<List<GoalEntity>>

  fun observeGoalById(id: String): Flow<GoalEntity?>

  suspend fun getById(id: String): GoalEntity?

  suspend fun insertGoal(goal: GoalEntity)

  suspend fun updateGoal(goal: GoalEntity)

  suspend fun updateStatus(id: String, status: GoalStatus, updatedAtEpochMs: Long): Int

  suspend fun deleteGoalById(id: String): Int

  fun observeLinkedTasks(goalId: String): Flow<List<GoalTaskCrossRef>>

  fun observeLinkedHabits(goalId: String): Flow<List<GoalHabitCrossRef>>

  suspend fun linkTask(goalId: String, taskId: String)

  suspend fun unlinkTask(goalId: String, taskId: String): Int

  suspend fun linkHabit(goalId: String, habitId: String)

  suspend fun unlinkHabit(goalId: String, habitId: String): Int
}
