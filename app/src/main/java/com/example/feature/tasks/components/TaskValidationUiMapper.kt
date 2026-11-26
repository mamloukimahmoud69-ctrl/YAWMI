package com.example.feature.tasks.components

import android.content.Context
import com.example.R
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryValidationError
import com.example.domain.task.TaskValidationError

fun TaskValidationError.toLocalizedMessage(context: Context): String =
  when (this) {
    TaskValidationError.EmptyTitle -> context.getString(R.string.task_error_empty_title)
    is TaskValidationError.InvalidScheduledDate -> context.getString(R.string.task_error_invalid_date)
    is TaskValidationError.InvalidStartTime -> context.getString(R.string.task_error_invalid_start_time)
    is TaskValidationError.InvalidEndTime -> context.getString(R.string.task_error_invalid_end_time)
    TaskValidationError.EndTimeWithoutStartTime -> context.getString(R.string.task_error_end_without_start)
    TaskValidationError.EndTimeBeforeStartTime -> context.getString(R.string.task_error_end_before_start)
    is TaskValidationError.CategoryNotFound -> context.getString(R.string.task_error_category_not_found)
    is TaskValidationError.TaskNotFound -> context.getString(R.string.task_error_not_found)
    is TaskValidationError.InvalidStatusTransition -> context.getString(R.string.task_error_invalid_transition)
    is TaskValidationError.InvalidRecurrenceInterval -> context.getString(R.string.task_error_recurrence_interval)
    is TaskValidationError.InvalidRecurrenceEndDate -> context.getString(R.string.task_error_recurrence_end_date)
    TaskValidationError.RecurrenceEndDateBeforeScheduledDate ->
      context.getString(R.string.task_error_recurrence_end_before_start)
    TaskValidationError.RecurrenceRequiresScheduledDate ->
      context.getString(R.string.task_error_recurrence_requires_date)
    TaskValidationError.RecurrenceRequiresDaysMask ->
      context.getString(R.string.task_error_recurrence_requires_days)
  }

fun CategoryValidationError.toLocalizedMessage(context: Context): String =
  when (this) {
    CategoryValidationError.EmptyName -> context.getString(R.string.category_error_empty_name)
    is CategoryValidationError.CategoryNotFound -> context.getString(R.string.task_error_category_not_found)
  }

fun TaskPriority.labelRes(): Int =
  when (this) {
    TaskPriority.HIGH -> R.string.priority_high
    TaskPriority.MEDIUM -> R.string.priority_medium
    TaskPriority.LOW -> R.string.priority_low
  }

fun TaskStatus.labelRes(): Int =
  when (this) {
    TaskStatus.PENDING -> R.string.task_status_pending
    TaskStatus.IN_PROGRESS -> R.string.task_status_in_progress
    TaskStatus.COMPLETED -> R.string.task_status_completed
    TaskStatus.CANCELLED -> R.string.task_status_cancelled
  }
