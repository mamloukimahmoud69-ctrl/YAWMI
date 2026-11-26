package com.example.domain.task

import com.example.data.local.entity.RecurrenceFrequency
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskRecurrenceEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Pure domain rules for task recurrence validation and next-occurrence calculation.
 *
 * Schema v1 constraint:
 * - `task_recurrences` enforces a 1:1 unique `taskId` foreign key with no `parentSeriesId` or
 *   `generatedNextTaskId`.
 * - Consequently, completing a recurring task keeps the completed `TaskEntity` and its
 *   `TaskRecurrenceEntity` intact as history, and generates a new standalone `TaskEntity` and
 *   `TaskRecurrenceEntity` for the next occurrence. Full series-wide edit/delete/exception
 *   management requires a future Schema v2 addition.
 */
object TaskRecurrenceRules {
  private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

  fun maskFromDays(days: Set<DayOfWeek>): Int =
    days.fold(0) { acc, day -> acc or dayBit(day) }

  fun daysFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(linkedSetOf()) { day -> (mask and dayBit(day)) != 0 }

  fun validateInput(
    input: TaskRecurrenceInput,
    taskScheduledDate: String?,
  ): TaskDomainResult<TaskRecurrenceInput> {
    val errors = mutableListOf<TaskValidationError>()

    val parsedScheduledDate =
      taskScheduledDate?.let { TaskRules.parseStrictIsoDate(it) }
    if (parsedScheduledDate == null) {
      errors += TaskValidationError.RecurrenceRequiresScheduledDate
    }

    if (input.interval < 1) {
      errors += TaskValidationError.InvalidRecurrenceInterval(input.interval)
    }

    if (input.frequency == RecurrenceFrequency.CUSTOM_DAYS && (input.daysOfWeekMask and 0b1111111) == 0) {
      errors += TaskValidationError.RecurrenceRequiresDaysMask
    }

    val normalizedEndDate =
      input.endDate?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val parsed = TaskRules.parseStrictIsoDate(raw)
        if (parsed == null) {
          errors += TaskValidationError.InvalidRecurrenceEndDate(raw)
          null
        } else {
          if (parsedScheduledDate != null && parsed.isBefore(parsedScheduledDate)) {
            errors += TaskValidationError.RecurrenceEndDateBeforeScheduledDate
          }
          parsed.format(ISO_DATE_FORMATTER)
        }
      }

    return if (errors.isEmpty()) {
      TaskDomainResult.Success(
        input.copy(
          daysOfWeekMask = input.daysOfWeekMask and 0b1111111,
          endDate = normalizedEndDate,
        )
      )
    } else {
      TaskDomainResult.Failure(errors)
    }
  }

  fun buildRecurrenceEntity(
    taskId: String,
    taskScheduledDate: String?,
    input: TaskRecurrenceInput,
    nowEpochMs: Long,
    idGenerator: () -> String = { UUID.randomUUID().toString() },
  ): TaskDomainResult<TaskRecurrenceEntity> {
    return when (val validation = validateInput(input, taskScheduledDate)) {
      is TaskDomainResult.Failure -> validation
      is TaskDomainResult.Success -> {
        val clean = validation.value
        TaskDomainResult.Success(
          TaskRecurrenceEntity(
            id = idGenerator(),
            taskId = taskId,
            frequency = clean.frequency,
            interval = clean.interval,
            daysOfWeekMask = clean.daysOfWeekMask,
            endDate = clean.endDate,
            updatedAtEpochMs = nowEpochMs,
          )
        )
      }
    }
  }

  /**
   * Computes the next scheduled date after [baseDate] for [recurrence], or `null` if the
   * recurrence rule is invalid or the next date exceeds [TaskRecurrenceEntity.endDate].
   */
  fun computeNextOccurrenceDate(
    baseDate: LocalDate,
    recurrence: TaskRecurrenceEntity,
  ): LocalDate? {
    val interval = recurrence.interval
    if (interval < 1) return null

    val candidate: LocalDate =
      when (recurrence.frequency) {
        RecurrenceFrequency.DAILY -> baseDate.plusDays(interval.toLong())
        RecurrenceFrequency.WEEKLY -> {
          val mask = recurrence.daysOfWeekMask and 0b1111111
          if (mask == 0) {
            baseDate.plusWeeks(interval.toLong())
          } else {
            nextMaskedWeekdayDate(baseDate = baseDate, intervalWeeks = interval, mask = mask)
          }
        }
        RecurrenceFrequency.MONTHLY -> baseDate.plusMonths(interval.toLong())
        RecurrenceFrequency.YEARLY -> baseDate.plusYears(interval.toLong())
        RecurrenceFrequency.CUSTOM_DAYS -> {
          val mask = recurrence.daysOfWeekMask and 0b1111111
          if (mask != 0) {
            nextMaskedWeekdayDate(baseDate = baseDate, intervalWeeks = interval, mask = mask)
          } else {
            baseDate.plusDays(interval.toLong())
          }
        }
      }

    val parsedEndDate = recurrence.endDate?.let { TaskRules.parseStrictIsoDate(it) }
    if (recurrence.endDate != null && parsedEndDate == null) return null
    if (parsedEndDate != null && candidate.isAfter(parsedEndDate)) return null
    return candidate
  }

  fun computeNextOccurrenceDateForTask(
    completedTask: TaskEntity,
    recurrence: TaskRecurrenceEntity,
    fallbackDate: LocalDate,
  ): LocalDate? {
    val baseDate =
      completedTask.scheduledDate?.let { TaskRules.parseStrictIsoDate(it) } ?: fallbackDate
    return computeNextOccurrenceDate(baseDate = baseDate, recurrence = recurrence)
  }

  private fun nextMaskedWeekdayDate(
    baseDate: LocalDate,
    intervalWeeks: Int,
    mask: Int,
  ): LocalDate {
    // 1. Check remaining days in the current Monday-anchored week strictly after baseDate
    val currentMonday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val currentIndex = baseDate.dayOfWeek.value - 1
    for (dayIdx in (currentIndex + 1)..6) {
      if ((mask and (1 shl dayIdx)) != 0) {
        return currentMonday.plusDays(dayIdx.toLong())
      }
    }

    // 2. Otherwise step forward by `intervalWeeks` weeks and pick the earliest selected day
    val nextWeekMonday = currentMonday.plusWeeks(intervalWeeks.toLong())
    for (dayIdx in 0..6) {
      if ((mask and (1 shl dayIdx)) != 0) {
        return nextWeekMonday.plusDays(dayIdx.toLong())
      }
    }

    return baseDate.plusWeeks(intervalWeeks.toLong())
  }
}
