package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.time.LocalClock
import com.example.core.time.SystemLocalClock
import com.example.data.local.dao.GoalDao
import com.example.data.local.dao.RecurrenceDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.ReminderDeliveryDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.GoalTaskCrossRef
import com.example.data.local.entity.ReminderOwnerType
import com.example.data.local.entity.ReminderScheduleState
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskRecurrenceEntity
import com.example.data.local.entity.TaskReminderEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.repository.TaskRepository
import com.example.domain.task.ReminderScheduleSyncAction
import com.example.domain.task.TaskCompletionResult
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskRecurrenceRules
import com.example.domain.task.TaskReminderRules
import com.example.domain.task.TaskRules
import com.example.domain.task.TaskValidationError
import kotlinx.coroutines.flow.Flow

class RoomTaskRepository(
  private val database: YawmiDatabase,
  private val taskDao: TaskDao,
  private val recurrenceDao: RecurrenceDao,
  private val reminderDao: ReminderDao,
  private val deliveryDao: ReminderDeliveryDao,
  private val outboxCoordinator: ReminderOutboxCoordinator,
  private val localClock: LocalClock = SystemLocalClock(),
  private val goalDao: GoalDao = database.goalDao(),
) : TaskRepository {
  override fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeAllTasks()

  override fun observeTasksForDate(date: String): Flow<List<TaskEntity>> =
    taskDao.observeTasksForDate(date)

  override fun observeUnscheduledTasks(): Flow<List<TaskEntity>> =
    taskDao.observeUnscheduledTasks()

  override fun observeTaskById(id: String): Flow<TaskEntity?> = taskDao.observeTaskById(id)

  override suspend fun getById(id: String): TaskEntity? = taskDao.getById(id)

  override suspend fun insert(task: TaskEntity) = taskDao.insert(task)

  override suspend fun update(task: TaskEntity) = database.withTransaction {
    val existing = taskDao.getById(task.id) ?: return@withTransaction
    taskDao.update(task)

    val now = task.updatedAtEpochMs
    val reminders = reminderDao.getTaskRemindersByTaskId(task.id)
    if (reminders.isEmpty()) return@withTransaction

    if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
      cancelTaskRemindersLocked(reminders = reminders, nowEpochMs = now)
      return@withTransaction
    }

    for (reminder in reminders) {
      when (
        val action =
          TaskReminderRules.resolveSyncActionOnTaskScheduleChange(
            previousTask = existing,
            updatedTask = task,
            reminder = reminder,
          )
      ) {
        is ReminderScheduleSyncAction.Reschedule -> {
          val nextRev = reminder.scheduleRevision + 1L
          reminderDao.updateTaskReminder(
            reminder.copy(
              triggerDate = action.triggerDate,
              triggerTime = action.triggerTime,
              scheduleRevision = nextRev,
              scheduleState = ReminderScheduleState.PENDING_SYNC,
              updatedAtEpochMs = now,
            )
          )
          deliveryDao.markPendingTaskDeliveriesStale(reminder.id, now)
          outboxCoordinator.stageScheduleAlarmLocked(
            ownerType = ReminderOwnerType.TASK,
            reminderId = reminder.id,
            requestCode = reminder.requestCode,
            targetScheduleRevision = nextRev,
            nowEpochMs = now,
          )
        }
        ReminderScheduleSyncAction.PauseMissingReference -> {
          val nextRev = reminder.scheduleRevision + 1L
          reminderDao.updateTaskReminder(
            reminder.copy(
              isEnabled = false,
              scheduleRevision = nextRev,
              scheduleState = ReminderScheduleState.PAUSED,
              updatedAtEpochMs = now,
            )
          )
          deliveryDao.markPendingTaskDeliveriesStale(reminder.id, now)
          outboxCoordinator.stageCancelAlarmAndNotificationLocked(
            ownerType = ReminderOwnerType.TASK,
            reminderId = reminder.id,
            requestCode = reminder.requestCode,
            targetScheduleRevision = nextRev,
            nowEpochMs = now,
          )
        }
        ReminderScheduleSyncAction.Unchanged -> Unit
      }
    }
  }

  override suspend fun updateStatus(
    id: String,
    status: TaskStatus,
    updatedAtEpochMs: Long,
    completedAtEpochMs: Long?,
  ): Int = database.withTransaction {
    val updated = taskDao.updateStatus(id, status, updatedAtEpochMs, completedAtEpochMs)
    if (updated > 0 && (status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED)) {
      val reminders = reminderDao.getTaskRemindersByTaskId(id)
      cancelTaskRemindersLocked(reminders = reminders, nowEpochMs = updatedAtEpochMs)
    }
    updated
  }

  override suspend fun completeTaskWithRecurrence(
    taskId: String,
    nowEpochMs: Long,
    idGenerator: () -> String,
  ): TaskDomainResult<TaskCompletionResult> = database.withTransaction {
    val existing =
      taskDao.getById(taskId)
        ?: return@withTransaction TaskDomainResult.Failure(
          listOf(TaskValidationError.TaskNotFound(taskId))
        )

    val transitioned =
      TaskRules.applyStatusTransition(
        existing = existing,
        targetStatus = TaskStatus.COMPLETED,
        nowEpochMs = nowEpochMs,
      )
    val completedTask =
      when (transitioned) {
        is TaskDomainResult.Failure -> return@withTransaction transitioned
        is TaskDomainResult.Success -> transitioned.value
      }

    // Snapshot existing reminders before completion disables them so enabled reminders can carry
    // forward to the spawned recurring occurrence.
    val existingReminders = reminderDao.getTaskRemindersByTaskId(taskId)
    val enabledRemindersBeforeCompletion = existingReminders.filter { it.isEnabled }

    taskDao.updateStatus(
      id = completedTask.id,
      status = completedTask.status,
      updatedAtEpochMs = completedTask.updatedAtEpochMs,
      completedAtEpochMs = completedTask.completedAtEpochMs,
    )
    cancelTaskRemindersLocked(reminders = existingReminders, nowEpochMs = nowEpochMs)

    // Keep the completed task's TaskRecurrenceEntity intact as historical record; if a next
    // occurrence falls within endDate, spawn a new PENDING TaskEntity + new TaskRecurrenceEntity
    // + new enabled reminders + goal links.
    val recurrence = recurrenceDao.getTaskRecurrenceByTaskId(taskId)
    val nextDate =
      recurrence?.let {
        TaskRecurrenceRules.computeNextOccurrenceDateForTask(
          completedTask = completedTask,
          recurrence = it,
          fallbackDate = localClock.currentDate(),
        )
      }

    if (recurrence == null || nextDate == null) {
      return@withTransaction TaskDomainResult.Success(
        TaskCompletionResult(completedTask = completedTask)
      )
    }

    val nextTask =
      TaskEntity(
        id = idGenerator(),
        title = completedTask.title,
        description = completedTask.description,
        scheduledDate = TaskReminderRules.formatDate(nextDate),
        startTime = completedTask.startTime,
        endTime = completedTask.endTime,
        priority = completedTask.priority,
        status = TaskStatus.PENDING,
        categoryId = completedTask.categoryId,
        notes = completedTask.notes,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        completedAtEpochMs = null,
      )
    taskDao.insert(nextTask)

    val nextRecurrence =
      TaskRecurrenceEntity(
        id = idGenerator(),
        taskId = nextTask.id,
        frequency = recurrence.frequency,
        interval = recurrence.interval,
        daysOfWeekMask = recurrence.daysOfWeekMask,
        endDate = recurrence.endDate,
        updatedAtEpochMs = nowEpochMs,
      )
    recurrenceDao.insertTaskRecurrence(nextRecurrence)

    for (goalLink in goalDao.getGoalLinksForTask(taskId)) {
      goalDao.linkTask(GoalTaskCrossRef(goalId = goalLink.goalId, taskId = nextTask.id))
    }

    val spawnedReminders = mutableListOf<TaskReminderEntity>()
    for (previousReminder in enabledRemindersBeforeCompletion) {
      val resolved =
        TaskReminderRules.resolveNextOccurrenceReminderTrigger(
          nextTask = nextTask,
          previousReminder = previousReminder,
        ) ?: continue
      val requestCode = outboxCoordinator.allocateNextRequestCodeLocked()
      val nextReminder =
        TaskReminderEntity(
          id = idGenerator(),
          taskId = nextTask.id,
          triggerDate = resolved.triggerDate,
          triggerTime = resolved.triggerTime,
          offsetMinutes = resolved.offsetMinutes,
          requestCode = requestCode,
          scheduleRevision = 1L,
          isEnabled = true,
          scheduleState = ReminderScheduleState.PENDING_SYNC,
          updatedAtEpochMs = nowEpochMs,
        )
      reminderDao.insertTaskReminder(nextReminder)
      outboxCoordinator.stageScheduleAlarmLocked(
        ownerType = ReminderOwnerType.TASK,
        reminderId = nextReminder.id,
        requestCode = requestCode,
        targetScheduleRevision = nextReminder.scheduleRevision,
        nowEpochMs = nowEpochMs,
      )
      spawnedReminders += nextReminder
    }

    TaskDomainResult.Success(
      TaskCompletionResult(
        completedTask = completedTask,
        generatedNextTask = nextTask,
        generatedRecurrence = nextRecurrence,
        generatedReminders = spawnedReminders,
      )
    )
  }

  override suspend fun deleteById(id: String): Int = database.withTransaction {
    val now = localClock.currentEpochMs()
    val reminders = reminderDao.getTaskRemindersByTaskId(id)
    for (reminder in reminders) {
      deliveryDao.markPendingTaskDeliveriesStale(reminder.id, now)
      outboxCoordinator.stageCancelAlarmAndNotificationLocked(
        ownerType = ReminderOwnerType.TASK,
        reminderId = reminder.id,
        requestCode = reminder.requestCode,
        targetScheduleRevision = reminder.scheduleRevision + 1L,
        nowEpochMs = now,
      )
    }
    taskDao.deleteById(id)
  }

  override fun observeTaskRecurrence(taskId: String): Flow<TaskRecurrenceEntity?> =
    recurrenceDao.observeTaskRecurrence(taskId)

  override suspend fun getTaskRecurrence(taskId: String): TaskRecurrenceEntity? =
    recurrenceDao.getTaskRecurrenceByTaskId(taskId)

  override suspend fun upsertTaskRecurrence(recurrence: TaskRecurrenceEntity) =
    database.withTransaction {
      val existing = recurrenceDao.getTaskRecurrenceByTaskId(recurrence.taskId)
      if (existing == null) {
        recurrenceDao.insertTaskRecurrence(recurrence)
      } else {
        recurrenceDao.updateTaskRecurrence(recurrence)
      }
    }

  override suspend fun deleteTaskRecurrence(taskId: String): Int =
    recurrenceDao.deleteTaskRecurrenceByTaskId(taskId)

  private suspend fun cancelTaskRemindersLocked(
    reminders: List<TaskReminderEntity>,
    nowEpochMs: Long,
  ) {
    for (reminder in reminders) {
      deliveryDao.markPendingTaskDeliveriesStale(reminder.id, nowEpochMs)
      if (reminder.isEnabled || reminder.scheduleState != ReminderScheduleState.CANCELLED) {
        val nextRev = reminder.scheduleRevision + 1L
        reminderDao.updateTaskReminder(
          reminder.copy(
            isEnabled = false,
            scheduleRevision = nextRev,
            scheduleState = ReminderScheduleState.CANCELLED,
            updatedAtEpochMs = nowEpochMs,
          )
        )
        outboxCoordinator.stageCancelAlarmAndNotificationLocked(
          ownerType = ReminderOwnerType.TASK,
          reminderId = reminder.id,
          requestCode = reminder.requestCode,
          targetScheduleRevision = nextRev,
          nowEpochMs = nowEpochMs,
        )
      }
    }
  }
}
