package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.GoalDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.ReminderDeliveryDao
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.GoalHabitCrossRef
import com.example.data.local.entity.GoalStatus
import com.example.data.local.entity.GoalTaskCrossRef
import com.example.data.local.entity.ReminderOwnerType
import com.example.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow

class RoomGoalRepository(
  private val database: YawmiDatabase,
  private val goalDao: GoalDao,
  private val reminderDao: ReminderDao,
  private val deliveryDao: ReminderDeliveryDao,
  private val outboxCoordinator: ReminderOutboxCoordinator,
) : GoalRepository {
  override fun observeActiveGoals(): Flow<List<GoalEntity>> = goalDao.observeActiveGoals()

  override fun observeAllGoals(): Flow<List<GoalEntity>> = goalDao.observeAllGoals()

  override fun observeGoalById(id: String): Flow<GoalEntity?> = goalDao.observeGoalById(id)

  override suspend fun getById(id: String): GoalEntity? = goalDao.getById(id)

  override suspend fun insertGoal(goal: GoalEntity) = goalDao.insertGoal(goal)

  override suspend fun updateGoal(goal: GoalEntity) = goalDao.updateGoal(goal)

  override suspend fun updateStatus(
    id: String,
    status: GoalStatus,
    updatedAtEpochMs: Long,
  ): Int = database.withTransaction {
    val updated = goalDao.updateStatus(id, status, updatedAtEpochMs)
    if (updated > 0 && status != GoalStatus.ACTIVE) {
      val reminders = reminderDao.getGoalRemindersByGoalId(id)
      for (reminder in reminders) {
        deliveryDao.markPendingGoalDeliveriesStale(reminder.id, updatedAtEpochMs)
        if (reminder.isEnabled) {
          val nextRev = reminder.scheduleRevision + 1L
          reminderDao.updateGoalReminder(
            reminder.copy(
              isEnabled = false,
              scheduleRevision = nextRev,
              updatedAtEpochMs = updatedAtEpochMs,
            )
          )
          outboxCoordinator.stageCancelAlarmAndNotificationLocked(
            ownerType = ReminderOwnerType.GOAL,
            reminderId = reminder.id,
            requestCode = reminder.requestCode,
            targetScheduleRevision = nextRev,
            nowEpochMs = updatedAtEpochMs,
          )
        }
      }
    }
    updated
  }

  override suspend fun deleteGoalById(id: String): Int = database.withTransaction {
    val now = System.currentTimeMillis()
    val reminders = reminderDao.getGoalRemindersByGoalId(id)
    for (reminder in reminders) {
      deliveryDao.markPendingGoalDeliveriesStale(reminder.id, now)
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.GOAL,
        reminderId = reminder.id,
        requestCode = reminder.requestCode,
        targetScheduleRevision = reminder.scheduleRevision + 1L,
        nowEpochMs = now,
      )
    }
    goalDao.deleteGoalById(id)
  }

  override fun observeLinkedTasks(goalId: String): Flow<List<GoalTaskCrossRef>> =
    goalDao.observeLinkedTasks(goalId)

  override fun observeLinkedHabits(goalId: String): Flow<List<GoalHabitCrossRef>> =
    goalDao.observeLinkedHabits(goalId)

  override suspend fun linkTask(goalId: String, taskId: String) =
    goalDao.linkTask(GoalTaskCrossRef(goalId = goalId, taskId = taskId))

  override suspend fun unlinkTask(goalId: String, taskId: String): Int =
    goalDao.unlinkTask(goalId, taskId)

  override suspend fun linkHabit(goalId: String, habitId: String) =
    goalDao.linkHabit(GoalHabitCrossRef(goalId = goalId, habitId = habitId))

  override suspend fun unlinkHabit(goalId: String, habitId: String): Int =
    goalDao.unlinkHabit(goalId, habitId)
}
