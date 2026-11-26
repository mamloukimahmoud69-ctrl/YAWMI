package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.HabitDao
import com.example.data.local.dao.RecurrenceDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.ReminderDeliveryDao
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.HabitCompletionEntity
import com.example.data.local.entity.HabitEntity
import com.example.data.local.entity.HabitRecurrenceEntity
import com.example.data.local.entity.ReminderOwnerType
import com.example.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow

class RoomHabitRepository(
  private val database: YawmiDatabase,
  private val habitDao: HabitDao,
  private val recurrenceDao: RecurrenceDao,
  private val reminderDao: ReminderDao,
  private val deliveryDao: ReminderDeliveryDao,
  private val outboxCoordinator: ReminderOutboxCoordinator,
) : HabitRepository {
  override fun observeActiveHabits(): Flow<List<HabitEntity>> = habitDao.observeActiveHabits()

  override fun observeAllHabits(): Flow<List<HabitEntity>> = habitDao.observeAllHabits()

  override fun observeHabitById(id: String): Flow<HabitEntity?> = habitDao.observeHabitById(id)

  override suspend fun getById(id: String): HabitEntity? = habitDao.getById(id)

  override suspend fun insertHabit(habit: HabitEntity) = habitDao.insertHabit(habit)

  override suspend fun updateHabit(habit: HabitEntity) = habitDao.updateHabit(habit)

  override suspend fun setArchived(
    id: String,
    isArchived: Boolean,
    updatedAtEpochMs: Long,
  ): Int = database.withTransaction {
    val updated = habitDao.setArchived(id, isArchived, updatedAtEpochMs)
    if (updated > 0 && isArchived) {
      val reminders = reminderDao.getHabitRemindersByHabitId(id)
      for (reminder in reminders) {
        deliveryDao.markPendingHabitDeliveriesStale(reminder.id, updatedAtEpochMs)
        if (reminder.isEnabled) {
          val nextRev = reminder.scheduleRevision + 1L
          reminderDao.updateHabitReminder(
            reminder.copy(
              isEnabled = false,
              scheduleRevision = nextRev,
              updatedAtEpochMs = updatedAtEpochMs,
            )
          )
          outboxCoordinator.stageCancelAlarmAndNotificationLocked(
            ownerType = ReminderOwnerType.HABIT,
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

  override suspend fun deleteHabitById(id: String): Int = database.withTransaction {
    val now = System.currentTimeMillis()
    val reminders = reminderDao.getHabitRemindersByHabitId(id)
    for (reminder in reminders) {
      deliveryDao.markPendingHabitDeliveriesStale(reminder.id, now)
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.HABIT,
        reminderId = reminder.id,
        requestCode = reminder.requestCode,
        targetScheduleRevision = reminder.scheduleRevision + 1L,
        nowEpochMs = now,
      )
    }
    habitDao.deleteHabitById(id)
  }

  override fun observeCompletionsForHabit(habitId: String): Flow<List<HabitCompletionEntity>> =
    habitDao.observeCompletionsForHabit(habitId)

  override fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>> =
    habitDao.observeCompletionsForDate(date)

  override suspend fun getCompletion(habitId: String, date: String): HabitCompletionEntity? =
    habitDao.getCompletion(habitId, date)

  override suspend fun insertCompletion(completion: HabitCompletionEntity) =
    habitDao.insertCompletion(completion)

  override suspend fun updateCompletion(completion: HabitCompletionEntity) =
    habitDao.updateCompletion(completion)

  override suspend fun deleteCompletionById(id: String): Int = habitDao.deleteCompletionById(id)

  override fun observeHabitRecurrence(habitId: String): Flow<HabitRecurrenceEntity?> =
    recurrenceDao.observeHabitRecurrence(habitId)

  override suspend fun getHabitRecurrence(habitId: String): HabitRecurrenceEntity? =
    recurrenceDao.getHabitRecurrenceByHabitId(habitId)

  override suspend fun upsertHabitRecurrence(recurrence: HabitRecurrenceEntity) =
    database.withTransaction {
      val existing = recurrenceDao.getHabitRecurrenceByHabitId(recurrence.habitId)
      if (existing == null) {
        recurrenceDao.insertHabitRecurrence(recurrence)
      } else {
        recurrenceDao.updateHabitRecurrence(recurrence)
      }
    }

  override suspend fun deleteHabitRecurrence(habitId: String): Int =
    recurrenceDao.deleteHabitRecurrenceByHabitId(habitId)
}
