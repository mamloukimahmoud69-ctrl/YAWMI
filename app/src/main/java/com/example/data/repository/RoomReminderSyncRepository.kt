package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.AlarmSyncOutboxDao
import com.example.data.local.dao.AppMetadataDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.ReminderDeliveryDao
import com.example.data.local.db.PendingIntentIdentitySnapshot
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.AlarmSyncOutboxEntity
import com.example.data.local.entity.DeliveryState
import com.example.data.local.entity.GoalReminderDeliveryEntity
import com.example.data.local.entity.GoalReminderEntity
import com.example.data.local.entity.HabitReminderDeliveryEntity
import com.example.data.local.entity.HabitReminderEntity
import com.example.data.local.entity.ReminderOwnerType
import com.example.data.local.entity.ReminderScheduleState
import com.example.data.local.entity.TaskReminderDeliveryEntity
import com.example.data.local.entity.TaskReminderEntity
import com.example.domain.repository.ReminderSyncRepository
import kotlinx.coroutines.flow.Flow

internal class RoomReminderSyncRepository(
  private val database: YawmiDatabase,
  private val reminderDao: ReminderDao,
  private val deliveryDao: ReminderDeliveryDao,
  private val outboxDao: AlarmSyncOutboxDao,
  private val metadataDao: AppMetadataDao,
  private val outboxCoordinator: ReminderOutboxCoordinator,
) : ReminderSyncRepository {

  override fun observeTaskReminders(taskId: String): Flow<List<TaskReminderEntity>> =
    reminderDao.observeTaskReminders(taskId)

  override suspend fun getTaskReminders(taskId: String): List<TaskReminderEntity> =
    reminderDao.getTaskRemindersByTaskId(taskId)

  override fun observeHabitReminders(habitId: String): Flow<List<HabitReminderEntity>> =
    reminderDao.observeHabitReminders(habitId)

  override fun observeGoalReminders(goalId: String): Flow<List<GoalReminderEntity>> =
    reminderDao.observeGoalReminders(goalId)

  override suspend fun createTaskReminder(
    reminderId: String,
    taskId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    nowEpochMs: Long,
  ): TaskReminderEntity = database.withTransaction {
    val requestCode = outboxCoordinator.allocateNextRequestCodeLocked()
    val entity =
      TaskReminderEntity(
        id = reminderId,
        taskId = taskId,
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        offsetMinutes = offsetMinutes,
        requestCode = requestCode,
        scheduleRevision = 1L,
        isEnabled = true,
        scheduleState = ReminderScheduleState.PENDING_SYNC,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.insertTaskReminder(entity)
    outboxCoordinator.stageScheduleAlarmLocked(
      ownerType = ReminderOwnerType.TASK,
      reminderId = reminderId,
      requestCode = requestCode,
      targetScheduleRevision = entity.scheduleRevision,
      nowEpochMs = nowEpochMs,
    )
    entity
  }

  override suspend fun createHabitReminder(
    reminderId: String,
    habitId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    nowEpochMs: Long,
  ): HabitReminderEntity = database.withTransaction {
    val requestCode = outboxCoordinator.allocateNextRequestCodeLocked()
    val entity =
      HabitReminderEntity(
        id = reminderId,
        habitId = habitId,
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        offsetMinutes = offsetMinutes,
        requestCode = requestCode,
        scheduleRevision = 1L,
        isEnabled = true,
        scheduleState = ReminderScheduleState.PENDING_SYNC,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.insertHabitReminder(entity)
    outboxCoordinator.stageScheduleAlarmLocked(
      ownerType = ReminderOwnerType.HABIT,
      reminderId = reminderId,
      requestCode = requestCode,
      targetScheduleRevision = entity.scheduleRevision,
      nowEpochMs = nowEpochMs,
    )
    entity
  }

  override suspend fun createGoalReminder(
    reminderId: String,
    goalId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    nowEpochMs: Long,
  ): GoalReminderEntity = database.withTransaction {
    val requestCode = outboxCoordinator.allocateNextRequestCodeLocked()
    val entity =
      GoalReminderEntity(
        id = reminderId,
        goalId = goalId,
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        offsetMinutes = offsetMinutes,
        requestCode = requestCode,
        scheduleRevision = 1L,
        isEnabled = true,
        scheduleState = ReminderScheduleState.PENDING_SYNC,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.insertGoalReminder(entity)
    outboxCoordinator.stageScheduleAlarmLocked(
      ownerType = ReminderOwnerType.GOAL,
      reminderId = reminderId,
      requestCode = requestCode,
      targetScheduleRevision = entity.scheduleRevision,
      nowEpochMs = nowEpochMs,
    )
    entity
  }

  override suspend fun updateTaskReminderSchedule(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    isEnabled: Boolean,
    nowEpochMs: Long,
  ): TaskReminderEntity = database.withTransaction {
    val existing =
      requireNotNull(reminderDao.getTaskReminderById(reminderId)) {
        "TaskReminderEntity not found: $reminderId"
      }
    val nextRevision = existing.scheduleRevision + 1L
    val updated =
      existing.copy(
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        offsetMinutes = offsetMinutes,
        isEnabled = isEnabled,
        scheduleRevision = nextRevision,
        scheduleState =
          if (isEnabled) ReminderScheduleState.PENDING_SYNC else ReminderScheduleState.PAUSED,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.updateTaskReminder(updated)
    deliveryDao.markPendingTaskDeliveriesStale(reminderId, nowEpochMs)
    if (isEnabled) {
      outboxCoordinator.stageScheduleAlarmLocked(
        ownerType = ReminderOwnerType.TASK,
        reminderId = reminderId,
        requestCode = updated.requestCode,
        targetScheduleRevision = nextRevision,
        nowEpochMs = nowEpochMs,
      )
    } else {
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.TASK,
        reminderId = reminderId,
        requestCode = updated.requestCode,
        targetScheduleRevision = nextRevision,
        nowEpochMs = nowEpochMs,
      )
    }
    updated
  }

  override suspend fun snoozeTaskReminder(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    nowEpochMs: Long,
  ): TaskReminderEntity = database.withTransaction {
    val existing =
      requireNotNull(reminderDao.getTaskReminderById(reminderId)) {
        "TaskReminderEntity not found: $reminderId"
      }
    val nextRevision = existing.scheduleRevision + 1L
    val updated =
      existing.copy(
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        isEnabled = true,
        scheduleRevision = nextRevision,
        scheduleState = ReminderScheduleState.PENDING_SYNC,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.updateTaskReminder(updated)
    deliveryDao.markPendingTaskDeliveriesStale(reminderId, nowEpochMs)
    outboxCoordinator.stageCancelNotificationLocked(
      ownerType = ReminderOwnerType.TASK,
      reminderId = reminderId,
      requestCode = updated.requestCode,
      targetScheduleRevision = nextRevision,
      nowEpochMs = nowEpochMs,
    )
    outboxCoordinator.stageScheduleAlarmLocked(
      ownerType = ReminderOwnerType.TASK,
      reminderId = reminderId,
      requestCode = updated.requestCode,
      targetScheduleRevision = nextRevision,
      nowEpochMs = nowEpochMs,
    )
    updated
  }

  override suspend fun updateHabitReminderSchedule(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    isEnabled: Boolean,
    nowEpochMs: Long,
  ): HabitReminderEntity = database.withTransaction {
    val existing =
      requireNotNull(reminderDao.getHabitReminderById(reminderId)) {
        "HabitReminderEntity not found: $reminderId"
      }
    val nextRevision = existing.scheduleRevision + 1L
    val updated =
      existing.copy(
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        offsetMinutes = offsetMinutes,
        isEnabled = isEnabled,
        scheduleRevision = nextRevision,
        scheduleState =
          if (isEnabled) ReminderScheduleState.PENDING_SYNC else ReminderScheduleState.PAUSED,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.updateHabitReminder(updated)
    deliveryDao.markPendingHabitDeliveriesStale(reminderId, nowEpochMs)
    if (isEnabled) {
      outboxCoordinator.stageScheduleAlarmLocked(
        ownerType = ReminderOwnerType.HABIT,
        reminderId = reminderId,
        requestCode = updated.requestCode,
        targetScheduleRevision = nextRevision,
        nowEpochMs = nowEpochMs,
      )
    } else {
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.HABIT,
        reminderId = reminderId,
        requestCode = updated.requestCode,
        targetScheduleRevision = nextRevision,
        nowEpochMs = nowEpochMs,
      )
    }
    updated
  }

  override suspend fun updateGoalReminderSchedule(
    reminderId: String,
    triggerDate: String,
    triggerTime: String,
    offsetMinutes: Int,
    isEnabled: Boolean,
    nowEpochMs: Long,
  ): GoalReminderEntity = database.withTransaction {
    val existing =
      requireNotNull(reminderDao.getGoalReminderById(reminderId)) {
        "GoalReminderEntity not found: $reminderId"
      }
    val nextRevision = existing.scheduleRevision + 1L
    val updated =
      existing.copy(
        triggerDate = triggerDate,
        triggerTime = triggerTime,
        offsetMinutes = offsetMinutes,
        isEnabled = isEnabled,
        scheduleRevision = nextRevision,
        scheduleState =
          if (isEnabled) ReminderScheduleState.PENDING_SYNC else ReminderScheduleState.PAUSED,
        updatedAtEpochMs = nowEpochMs,
      )
    reminderDao.updateGoalReminder(updated)
    deliveryDao.markPendingGoalDeliveriesStale(reminderId, nowEpochMs)
    if (isEnabled) {
      outboxCoordinator.stageScheduleAlarmLocked(
        ownerType = ReminderOwnerType.GOAL,
        reminderId = reminderId,
        requestCode = updated.requestCode,
        targetScheduleRevision = nextRevision,
        nowEpochMs = nowEpochMs,
      )
    } else {
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.GOAL,
        reminderId = reminderId,
        requestCode = updated.requestCode,
        targetScheduleRevision = nextRevision,
        nowEpochMs = nowEpochMs,
      )
    }
    updated
  }

  override suspend fun deleteTaskReminder(reminderId: String, nowEpochMs: Long): Boolean =
    database.withTransaction {
      val existing = reminderDao.getTaskReminderById(reminderId) ?: return@withTransaction false
      deliveryDao.markPendingTaskDeliveriesStale(reminderId, nowEpochMs)
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.TASK,
        reminderId = reminderId,
        requestCode = existing.requestCode,
        targetScheduleRevision = existing.scheduleRevision + 1L,
        nowEpochMs = nowEpochMs,
      )
      reminderDao.deleteTaskReminderById(reminderId) > 0
    }

  override suspend fun deleteHabitReminder(reminderId: String, nowEpochMs: Long): Boolean =
    database.withTransaction {
      val existing = reminderDao.getHabitReminderById(reminderId) ?: return@withTransaction false
      deliveryDao.markPendingHabitDeliveriesStale(reminderId, nowEpochMs)
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.HABIT,
        reminderId = reminderId,
        requestCode = existing.requestCode,
        targetScheduleRevision = existing.scheduleRevision + 1L,
        nowEpochMs = nowEpochMs,
      )
      reminderDao.deleteHabitReminderById(reminderId) > 0
    }

  override suspend fun deleteGoalReminder(reminderId: String, nowEpochMs: Long): Boolean =
    database.withTransaction {
      val existing = reminderDao.getGoalReminderById(reminderId) ?: return@withTransaction false
      deliveryDao.markPendingGoalDeliveriesStale(reminderId, nowEpochMs)
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.GOAL,
        reminderId = reminderId,
        requestCode = existing.requestCode,
        targetScheduleRevision = existing.scheduleRevision + 1L,
        nowEpochMs = nowEpochMs,
      )
      reminderDao.deleteGoalReminderById(reminderId) > 0
    }

  override fun observeTaskDeliveries(reminderId: String): Flow<List<TaskReminderDeliveryEntity>> =
    deliveryDao.observeTaskDeliveries(reminderId)

  override fun observeHabitDeliveries(reminderId: String): Flow<List<HabitReminderDeliveryEntity>> =
    deliveryDao.observeHabitDeliveries(reminderId)

  override fun observeGoalDeliveries(reminderId: String): Flow<List<GoalReminderDeliveryEntity>> =
    deliveryDao.observeGoalDeliveries(reminderId)

  override suspend fun recordPendingTaskDelivery(
    deliveryId: String,
    taskReminderId: String,
    occurrenceKey: String,
    scheduledAtEpochMs: Long,
    firedScheduleRevision: Long,
    nowEpochMs: Long,
  ): TaskReminderDeliveryEntity {
    val entity =
      TaskReminderDeliveryEntity(
        id = deliveryId,
        taskReminderId = taskReminderId,
        occurrenceKey = occurrenceKey,
        scheduledAtEpochMs = scheduledAtEpochMs,
        firedScheduleRevision = firedScheduleRevision,
        state = DeliveryState.PENDING,
        notificationTag =
          PendingIntentIdentitySnapshot.buildNotificationTag(
            ReminderOwnerType.TASK,
            taskReminderId,
          ),
        notificationId = PendingIntentIdentitySnapshot.DEFAULT_NOTIFICATION_ID,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
      )
    deliveryDao.insertTaskDelivery(entity)
    return entity
  }

  override suspend fun recordPendingHabitDelivery(
    deliveryId: String,
    habitReminderId: String,
    occurrenceKey: String,
    scheduledAtEpochMs: Long,
    firedScheduleRevision: Long,
    nowEpochMs: Long,
  ): HabitReminderDeliveryEntity {
    val entity =
      HabitReminderDeliveryEntity(
        id = deliveryId,
        habitReminderId = habitReminderId,
        occurrenceKey = occurrenceKey,
        scheduledAtEpochMs = scheduledAtEpochMs,
        firedScheduleRevision = firedScheduleRevision,
        state = DeliveryState.PENDING,
        notificationTag =
          PendingIntentIdentitySnapshot.buildNotificationTag(
            ReminderOwnerType.HABIT,
            habitReminderId,
          ),
        notificationId = PendingIntentIdentitySnapshot.DEFAULT_NOTIFICATION_ID,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
      )
    deliveryDao.insertHabitDelivery(entity)
    return entity
  }

  override suspend fun recordPendingGoalDelivery(
    deliveryId: String,
    goalReminderId: String,
    occurrenceKey: String,
    scheduledAtEpochMs: Long,
    firedScheduleRevision: Long,
    nowEpochMs: Long,
  ): GoalReminderDeliveryEntity {
    val entity =
      GoalReminderDeliveryEntity(
        id = deliveryId,
        goalReminderId = goalReminderId,
        occurrenceKey = occurrenceKey,
        scheduledAtEpochMs = scheduledAtEpochMs,
        firedScheduleRevision = firedScheduleRevision,
        state = DeliveryState.PENDING,
        notificationTag =
          PendingIntentIdentitySnapshot.buildNotificationTag(
            ReminderOwnerType.GOAL,
            goalReminderId,
          ),
        notificationId = PendingIntentIdentitySnapshot.DEFAULT_NOTIFICATION_ID,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
      )
    deliveryDao.insertGoalDelivery(entity)
    return entity
  }

  override suspend fun updateTaskDeliveryState(
    deliveryId: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    nowEpochMs: Long,
  ): Int =
    deliveryDao.updateTaskDeliveryState(
      id = deliveryId,
      state = state,
      attemptCount = attemptCount,
      lastAttemptAtEpochMs = lastAttemptAtEpochMs,
      lastError = lastError,
      updatedAtEpochMs = nowEpochMs,
    )

  override suspend fun updateHabitDeliveryState(
    deliveryId: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    nowEpochMs: Long,
  ): Int =
    deliveryDao.updateHabitDeliveryState(
      id = deliveryId,
      state = state,
      attemptCount = attemptCount,
      lastAttemptAtEpochMs = lastAttemptAtEpochMs,
      lastError = lastError,
      updatedAtEpochMs = nowEpochMs,
    )

  override suspend fun updateGoalDeliveryState(
    deliveryId: String,
    state: DeliveryState,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    lastError: String?,
    nowEpochMs: Long,
  ): Int =
    deliveryDao.updateGoalDeliveryState(
      id = deliveryId,
      state = state,
      attemptCount = attemptCount,
      lastAttemptAtEpochMs = lastAttemptAtEpochMs,
      lastError = lastError,
      updatedAtEpochMs = nowEpochMs,
    )

  override fun observeOutboxOrdered(): Flow<List<AlarmSyncOutboxEntity>> =
    outboxDao.observeAllOrdered()

  override suspend fun getAllOutboxOrdered(): List<AlarmSyncOutboxEntity> =
    outboxDao.getAllOrdered()

  override suspend fun getMetadataValue(key: String): String? = metadataDao.getValue(key)
}
