package com.example.domain.task

import com.example.core.time.LocalClock
import com.example.data.local.entity.TaskReminderEntity
import com.example.domain.repository.ReminderSyncRepository
import com.example.domain.repository.TaskRepository
import java.util.UUID

class TaskReminderUseCases(
  private val taskRepository: TaskRepository,
  private val reminderSyncRepository: ReminderSyncRepository,
  private val localClock: LocalClock,
  private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
  suspend fun createRelativeReminder(
    taskId: String,
    offsetMinutes: Int,
  ): TaskReminderResult<TaskReminderEntity> {
    val task =
      taskRepository.getById(taskId)
        ?: return TaskReminderResult.Failure(TaskReminderError.TaskNotFound(taskId))

    val resolved =
      when (val result = TaskReminderRules.validateAndResolveRelativeReminder(task, offsetMinutes)) {
        is TaskReminderResult.Failure -> return result
        is TaskReminderResult.Success -> result.value
      }

    val entity =
      reminderSyncRepository.createTaskReminder(
        reminderId = idGenerator(),
        taskId = taskId,
        triggerDate = resolved.triggerDate,
        triggerTime = resolved.triggerTime,
        offsetMinutes = resolved.offsetMinutes,
        nowEpochMs = localClock.currentEpochMs(),
      )
    return TaskReminderResult.Success(entity)
  }

  suspend fun createExplicitReminder(
    taskId: String,
    triggerTime: String,
    triggerDate: String? = null,
  ): TaskReminderResult<TaskReminderEntity> {
    val task =
      taskRepository.getById(taskId)
        ?: return TaskReminderResult.Failure(TaskReminderError.TaskNotFound(taskId))

    val resolved =
      when (
        val result =
          TaskReminderRules.validateAndResolveExplicitReminder(
            task = task,
            triggerTimeRaw = triggerTime,
            triggerDateRaw = triggerDate,
          )
      ) {
        is TaskReminderResult.Failure -> return result
        is TaskReminderResult.Success -> result.value
      }

    val entity =
      reminderSyncRepository.createTaskReminder(
        reminderId = idGenerator(),
        taskId = taskId,
        triggerDate = resolved.triggerDate,
        triggerTime = resolved.triggerTime,
        offsetMinutes = 0,
        nowEpochMs = localClock.currentEpochMs(),
      )
    return TaskReminderResult.Success(entity)
  }

  suspend fun setReminderEnabled(
    taskId: String,
    reminderId: String,
    enabled: Boolean,
  ): TaskReminderResult<TaskReminderEntity> {
    val task =
      taskRepository.getById(taskId)
        ?: return TaskReminderResult.Failure(TaskReminderError.TaskNotFound(taskId))
    val reminder =
      reminderSyncRepository.getTaskReminders(taskId).firstOrNull { it.id == reminderId }
        ?: return TaskReminderResult.Failure(TaskReminderError.ReminderNotFound(reminderId))

    if (!enabled) {
      val paused =
        reminderSyncRepository.updateTaskReminderSchedule(
          reminderId = reminder.id,
          triggerDate = reminder.triggerDate,
          triggerTime = reminder.triggerTime,
          offsetMinutes = reminder.offsetMinutes,
          isEnabled = false,
          nowEpochMs = localClock.currentEpochMs(),
        )
      return TaskReminderResult.Success(paused)
    }

    val resolved =
      if (reminder.offsetMinutes != 0 || task.startTime != null) {
        if (reminder.offsetMinutes != 0) {
          TaskReminderRules.validateAndResolveRelativeReminder(task, reminder.offsetMinutes)
        } else {
          // If explicit trigger time exists on an untimed task, preserve its triggerTime against
          // the task's scheduledDate; if the task now has a startTime, keep user's explicit
          // reminder time if valid or validate date/time.
          TaskReminderRules.validateAndResolveExplicitReminder(
            task = task,
            triggerTimeRaw = reminder.triggerTime,
            triggerDateRaw = task.scheduledDate ?: reminder.triggerDate,
          )
        }
      } else {
        TaskReminderRules.validateAndResolveExplicitReminder(
          task = task,
          triggerTimeRaw = reminder.triggerTime,
          triggerDateRaw = task.scheduledDate,
        )
      }

    val trigger =
      when (resolved) {
        is TaskReminderResult.Failure -> return resolved
        is TaskReminderResult.Success -> resolved.value
      }

    val updated =
      reminderSyncRepository.updateTaskReminderSchedule(
        reminderId = reminder.id,
        triggerDate = trigger.triggerDate,
        triggerTime = trigger.triggerTime,
        offsetMinutes = reminder.offsetMinutes,
        isEnabled = true,
        nowEpochMs = localClock.currentEpochMs(),
      )
    return TaskReminderResult.Success(updated)
  }

  suspend fun updateReminderOffset(
    taskId: String,
    reminderId: String,
    offsetMinutes: Int,
  ): TaskReminderResult<TaskReminderEntity> {
    val task =
      taskRepository.getById(taskId)
        ?: return TaskReminderResult.Failure(TaskReminderError.TaskNotFound(taskId))
    val reminder =
      reminderSyncRepository.getTaskReminders(taskId).firstOrNull { it.id == reminderId }
        ?: return TaskReminderResult.Failure(TaskReminderError.ReminderNotFound(reminderId))

    val resolved =
      when (val result = TaskReminderRules.validateAndResolveRelativeReminder(task, offsetMinutes)) {
        is TaskReminderResult.Failure -> return result
        is TaskReminderResult.Success -> result.value
      }

    val updated =
      reminderSyncRepository.updateTaskReminderSchedule(
        reminderId = reminder.id,
        triggerDate = resolved.triggerDate,
        triggerTime = resolved.triggerTime,
        offsetMinutes = resolved.offsetMinutes,
        isEnabled = true,
        nowEpochMs = localClock.currentEpochMs(),
      )
    return TaskReminderResult.Success(updated)
  }

  suspend fun updateExplicitReminderTime(
    taskId: String,
    reminderId: String,
    triggerTime: String,
    triggerDate: String? = null,
  ): TaskReminderResult<TaskReminderEntity> {
    val task =
      taskRepository.getById(taskId)
        ?: return TaskReminderResult.Failure(TaskReminderError.TaskNotFound(taskId))
    val reminder =
      reminderSyncRepository.getTaskReminders(taskId).firstOrNull { it.id == reminderId }
        ?: return TaskReminderResult.Failure(TaskReminderError.ReminderNotFound(reminderId))

    val resolved =
      when (
        val result =
          TaskReminderRules.validateAndResolveExplicitReminder(
            task = task,
            triggerTimeRaw = triggerTime,
            triggerDateRaw = triggerDate,
          )
      ) {
        is TaskReminderResult.Failure -> return result
        is TaskReminderResult.Success -> result.value
      }

    val updated =
      reminderSyncRepository.updateTaskReminderSchedule(
        reminderId = reminder.id,
        triggerDate = resolved.triggerDate,
        triggerTime = resolved.triggerTime,
        offsetMinutes = 0,
        isEnabled = true,
        nowEpochMs = localClock.currentEpochMs(),
      )
    return TaskReminderResult.Success(updated)
  }

  /**
   * Snoozes ONLY the reminder without modifying [TaskEntity.scheduledDate] or
   * [TaskEntity.startTime].
   */
  suspend fun snoozeReminder(
    taskId: String,
    reminderId: String,
    snoozeMinutes: Int,
  ): TaskReminderResult<TaskReminderEntity> {
    if (snoozeMinutes < 1) {
      return TaskReminderResult.Failure(TaskReminderError.InvalidSnoozeMinutes(snoozeMinutes))
    }
    val task =
      taskRepository.getById(taskId)
        ?: return TaskReminderResult.Failure(TaskReminderError.TaskNotFound(taskId))
    if (!TaskReminderRules.isTaskActive(task.status)) {
      return TaskReminderResult.Failure(TaskReminderError.TaskNotActive(task.status))
    }
    reminderSyncRepository.getTaskReminders(taskId).firstOrNull { it.id == reminderId }
      ?: return TaskReminderResult.Failure(TaskReminderError.ReminderNotFound(reminderId))

    val now = localClock.currentEpochMs()
    val (snoozeDate, snoozeTime) =
      TaskReminderRules.computeSnoozeDateTime(
        fromEpochMs = now,
        snoozeMinutes = snoozeMinutes,
        zoneId = localClock.zoneId(),
      )
    val updated =
      reminderSyncRepository.snoozeTaskReminder(
        reminderId = reminderId,
        triggerDate = snoozeDate,
        triggerTime = snoozeTime,
        nowEpochMs = now,
      )
    return TaskReminderResult.Success(updated)
  }

  suspend fun deleteReminder(reminderId: String): Boolean =
    reminderSyncRepository.deleteTaskReminder(reminderId, localClock.currentEpochMs())
}
