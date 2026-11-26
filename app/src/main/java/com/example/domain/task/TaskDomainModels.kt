package com.example.domain.task

import com.example.data.local.entity.RecurrenceFrequency
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskRecurrenceEntity
import com.example.data.local.entity.TaskReminderEntity
import com.example.data.local.entity.TaskStatus

data class TaskInput(
  val title: String,
  val description: String? = null,
  val scheduledDate: String? = null,
  val startTime: String? = null,
  val endTime: String? = null,
  val priority: TaskPriority = TaskPriority.MEDIUM,
  val categoryId: String? = null,
  val notes: String? = null,
)

data class TaskRecurrenceInput(
  val frequency: RecurrenceFrequency,
  val interval: Int = 1,
  val daysOfWeekMask: Int = 0,
  val endDate: String? = null,
)

/**
 * Outcome of completing a task.
 *
 * Schema v1 note: `tasks` and `task_recurrences` do not include `parentSeriesId` or
 * `generatedNextTaskId`, so completing a recurring task preserves the completed task and its
 * recurrence row intact and spawns an independent next-occurrence task row without series-wide
 * retroactive edit/delete linking.
 */
data class TaskCompletionResult(
  val completedTask: TaskEntity,
  val generatedNextTask: TaskEntity? = null,
  val generatedRecurrence: TaskRecurrenceEntity? = null,
  val generatedReminders: List<TaskReminderEntity> = emptyList(),
)

sealed interface TaskValidationError {
  data object EmptyTitle : TaskValidationError

  data class InvalidScheduledDate(val raw: String) : TaskValidationError

  data class InvalidStartTime(val raw: String) : TaskValidationError

  data class InvalidEndTime(val raw: String) : TaskValidationError

  data object EndTimeWithoutStartTime : TaskValidationError

  data object EndTimeBeforeStartTime : TaskValidationError

  data class CategoryNotFound(val categoryId: String) : TaskValidationError

  data class TaskNotFound(val taskId: String) : TaskValidationError

  data class InvalidStatusTransition(
    val from: TaskStatus,
    val to: TaskStatus,
  ) : TaskValidationError

  data class InvalidRecurrenceInterval(val interval: Int) : TaskValidationError

  data class InvalidRecurrenceEndDate(val raw: String) : TaskValidationError

  data object RecurrenceEndDateBeforeScheduledDate : TaskValidationError

  data object RecurrenceRequiresScheduledDate : TaskValidationError

  data object RecurrenceRequiresDaysMask : TaskValidationError
}

sealed interface TaskDomainResult<out T> {
  data class Success<T>(val value: T) : TaskDomainResult<T>

  data class Failure(val errors: List<TaskValidationError>) : TaskDomainResult<Nothing> {
    init {
      require(errors.isNotEmpty()) { "Failure must contain at least one TaskValidationError" }
    }
  }
}

sealed interface TaskReminderError {
  data class TaskNotFound(val taskId: String) : TaskReminderError

  data class ReminderNotFound(val reminderId: String) : TaskReminderError

  data class TaskNotActive(val status: TaskStatus) : TaskReminderError

  data object MissingScheduledDate : TaskReminderError

  data object RelativeReminderRequiresStartTime : TaskReminderError

  data class InvalidOffsetMinutes(val offsetMinutes: Int) : TaskReminderError

  data class InvalidTriggerDate(val raw: String) : TaskReminderError

  data class InvalidTriggerTime(val raw: String) : TaskReminderError

  data class InvalidSnoozeMinutes(val snoozeMinutes: Int) : TaskReminderError
}

sealed interface TaskReminderResult<out T> {
  data class Success<T>(val value: T) : TaskReminderResult<T>

  data class Failure(val error: TaskReminderError) : TaskReminderResult<Nothing>
}
