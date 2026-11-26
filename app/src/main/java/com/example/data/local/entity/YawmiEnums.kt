package com.example.data.local.entity

enum class TaskPriority {
  HIGH,
  MEDIUM,
  LOW,
}

enum class TaskStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED,
}

enum class GoalStatus {
  ACTIVE,
  PAUSED,
  COMPLETED,
  ARCHIVED,
}

enum class RecurrenceFrequency {
  DAILY,
  WEEKLY,
  MONTHLY,
  YEARLY,
  CUSTOM_DAYS,
}

enum class ReminderScheduleState {
  PENDING_SYNC,
  SCHEDULED_EXACT,
  SCHEDULED_INEXACT,
  PAUSED,
  CONSUMED,
  CANCELLED,
  FAILED_PERMANENT,
}

enum class ReminderOwnerType {
  TASK,
  HABIT,
  GOAL,
}

enum class OutboxSystemTarget {
  ALARM_MANAGER,
  NOTIFICATION_MANAGER,
}

enum class OutboxAction {
  SCHEDULE_ALARM,
  CANCEL_ALARM,
  CANCEL_NOTIFICATION,
}

enum class OutboxStatus {
  PENDING,
  CLAIMED,
  SUCCEEDED,
  SUPERSEDED,
  FAILED_PERMANENT,
}

enum class DeliveryState {
  PENDING,
  POSTED_TO_SYSTEM,
  SUPPRESSED_PERMISSION,
  SUPPRESSED_CHANNEL,
  STALE,
  EXPIRED,
  FAILED_RETRYABLE,
  FAILED_PERMANENT,
}
