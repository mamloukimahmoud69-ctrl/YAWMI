package com.example.domain.task

import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskReminderEntity
import com.example.data.local.entity.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ResolvedReminderTrigger(
  val triggerDate: String,
  val triggerTime: String,
  val offsetMinutes: Int,
)

/**
 * Outcome when synchronizing an existing [TaskReminderEntity] against an updated [TaskEntity] schedule.
 */
sealed interface ReminderScheduleSyncAction {
  data class Reschedule(
    val triggerDate: String,
    val triggerTime: String,
  ) : ReminderScheduleSyncAction

  data object PauseMissingReference : ReminderScheduleSyncAction

  data object Unchanged : ReminderScheduleSyncAction
}

/**
 * Pure domain rules for validating and recalculating task reminder triggers without ever
 * inventing a default time when a task has a date without a start time.
 */
object TaskReminderRules {
  private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
  private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

  fun isTaskActive(status: TaskStatus): Boolean =
    status == TaskStatus.PENDING || status == TaskStatus.IN_PROGRESS

  fun validateAndResolveRelativeReminder(
    task: TaskEntity,
    offsetMinutes: Int,
  ): TaskReminderResult<ResolvedReminderTrigger> {
    if (!isTaskActive(task.status)) {
      return TaskReminderResult.Failure(TaskReminderError.TaskNotActive(task.status))
    }
    if (offsetMinutes < 0) {
      return TaskReminderResult.Failure(TaskReminderError.InvalidOffsetMinutes(offsetMinutes))
    }
    val scheduledDate =
      task.scheduledDate?.let { TaskRules.parseStrictIsoDate(it) }
        ?: return TaskReminderResult.Failure(TaskReminderError.MissingScheduledDate)
    val startTime =
      task.startTime?.let { TaskRules.parseStrictTime(it) }
        ?: return TaskReminderResult.Failure(TaskReminderError.RelativeReminderRequiresStartTime)

    val triggerDateTime = LocalDateTime.of(scheduledDate, startTime).minusMinutes(offsetMinutes.toLong())
    return TaskReminderResult.Success(
      ResolvedReminderTrigger(
        triggerDate = triggerDateTime.toLocalDate().format(ISO_DATE_FORMATTER),
        triggerTime = triggerDateTime.toLocalTime().format(TIME_FORMATTER),
        offsetMinutes = offsetMinutes,
      )
    )
  }

  /**
   * Validates an explicit user-chosen reminder trigger time. When a task has a `scheduledDate`
   * without `startTime`, only an explicit user-chosen `triggerTime` (`offsetMinutes = 0`) is
   * permitted—never a synthesized default time.
   */
  fun validateAndResolveExplicitReminder(
    task: TaskEntity,
    triggerTimeRaw: String,
    triggerDateRaw: String? = null,
  ): TaskReminderResult<ResolvedReminderTrigger> {
    if (!isTaskActive(task.status)) {
      return TaskReminderResult.Failure(TaskReminderError.TaskNotActive(task.status))
    }

    val effectiveDateRaw = triggerDateRaw ?: task.scheduledDate
    if (effectiveDateRaw.isNullOrBlank()) {
      return TaskReminderResult.Failure(TaskReminderError.MissingScheduledDate)
    }
    val parsedDate =
      TaskRules.parseStrictIsoDate(effectiveDateRaw)
        ?: return TaskReminderResult.Failure(TaskReminderError.InvalidTriggerDate(effectiveDateRaw))

    val parsedTime =
      TaskRules.parseStrictTime(triggerTimeRaw)
        ?: return TaskReminderResult.Failure(TaskReminderError.InvalidTriggerTime(triggerTimeRaw))

    return TaskReminderResult.Success(
      ResolvedReminderTrigger(
        triggerDate = parsedDate.format(ISO_DATE_FORMATTER),
        triggerTime = parsedTime.format(TIME_FORMATTER),
        offsetMinutes = 0,
      )
    )
  }

  /**
   * Determines how an existing enabled [reminder] reacts when [previousTask] is edited or postponed
   * into [updatedTask].
   */
  fun resolveSyncActionOnTaskScheduleChange(
    previousTask: TaskEntity,
    updatedTask: TaskEntity,
    reminder: TaskReminderEntity,
  ): ReminderScheduleSyncAction {
    if (!reminder.isEnabled) return ReminderScheduleSyncAction.Unchanged

    val updatedDate = updatedTask.scheduledDate?.let { TaskRules.parseStrictIsoDate(it) }
    if (updatedDate == null) {
      return ReminderScheduleSyncAction.PauseMissingReference
    }

    val updatedTime = updatedTask.startTime?.let { TaskRules.parseStrictTime(it) }
    val scheduleChanged =
      previousTask.scheduledDate != updatedTask.scheduledDate ||
        previousTask.startTime != updatedTask.startTime

    if (!scheduleChanged) {
      return ReminderScheduleSyncAction.Unchanged
    }

    // Task now has date without startTime:
    // - Relative reminders (offsetMinutes != 0) lose their reference -> pause cleanly.
    // - An explicit reminder whose task previously had a startTime and had its time window cleared
    //   also pauses cleanly so no stale anchored time remains active.
    // - An explicit reminder on an already-untimed task follows the new scheduledDate while keeping
    //   its user-chosen triggerTime.
    if (updatedTime == null) {
      if (reminder.offsetMinutes != 0 || previousTask.startTime != null) {
        return ReminderScheduleSyncAction.PauseMissingReference
      }
      return ReminderScheduleSyncAction.Reschedule(
        triggerDate = updatedDate.format(ISO_DATE_FORMATTER),
        triggerTime = reminder.triggerTime,
      )
    }

    val triggerDateTime =
      LocalDateTime.of(updatedDate, updatedTime).minusMinutes(reminder.offsetMinutes.toLong())
    return ReminderScheduleSyncAction.Reschedule(
      triggerDate = triggerDateTime.toLocalDate().format(ISO_DATE_FORMATTER),
      triggerTime = triggerDateTime.toLocalTime().format(TIME_FORMATTER),
    )
  }

  /**
   * Resolves trigger date & time when generating the next occurrence of a recurring task.
   * Returns `null` if the reminder cannot be anchored without inventing a time.
   */
  fun resolveNextOccurrenceReminderTrigger(
    nextTask: TaskEntity,
    previousReminder: TaskReminderEntity,
  ): ResolvedReminderTrigger? {
    val nextDate = nextTask.scheduledDate?.let { TaskRules.parseStrictIsoDate(it) } ?: return null
    val nextStartTime = nextTask.startTime?.let { TaskRules.parseStrictTime(it) }

    return if (nextStartTime != null) {
      val dt = LocalDateTime.of(nextDate, nextStartTime).minusMinutes(previousReminder.offsetMinutes.toLong())
      ResolvedReminderTrigger(
        triggerDate = dt.toLocalDate().format(ISO_DATE_FORMATTER),
        triggerTime = dt.toLocalTime().format(TIME_FORMATTER),
        offsetMinutes = previousReminder.offsetMinutes,
      )
    } else if (previousReminder.offsetMinutes == 0) {
      val explicitTime = TaskRules.parseStrictTime(previousReminder.triggerTime) ?: return null
      ResolvedReminderTrigger(
        triggerDate = nextDate.format(ISO_DATE_FORMATTER),
        triggerTime = explicitTime.format(TIME_FORMATTER),
        offsetMinutes = 0,
      )
    } else {
      null
    }
  }

  fun computeSnoozeDateTime(
    fromEpochMs: Long,
    snoozeMinutes: Int,
    zoneId: java.time.ZoneId,
  ): Pair<String, String> {
    val targetZoned =
      java.time.Instant.ofEpochMilli(fromEpochMs)
        .atZone(zoneId)
        .plusMinutes(snoozeMinutes.toLong())
    return targetZoned.toLocalDate().format(ISO_DATE_FORMATTER) to
      targetZoned.toLocalTime().format(TIME_FORMATTER)
  }

  fun formatDate(date: LocalDate): String = date.format(ISO_DATE_FORMATTER)

  fun formatTime(time: LocalTime): String = time.format(TIME_FORMATTER)
}
