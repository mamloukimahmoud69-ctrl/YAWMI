package com.example.domain.task

import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.UUID

object TaskRules {
  private val ISO_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)

  private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

  private val TRANSITIONS: Map<TaskStatus, Set<TaskStatus>> =
    mapOf(
      TaskStatus.PENDING to setOf(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED, TaskStatus.CANCELLED),
      TaskStatus.IN_PROGRESS to setOf(TaskStatus.PENDING, TaskStatus.COMPLETED, TaskStatus.CANCELLED),
      TaskStatus.COMPLETED to setOf(TaskStatus.PENDING),
      TaskStatus.CANCELLED to setOf(TaskStatus.PENDING),
    )

  fun allowedNextStatuses(current: TaskStatus): Set<TaskStatus> =
    TRANSITIONS[current].orEmpty()

  fun isTransitionAllowed(from: TaskStatus, to: TaskStatus): Boolean =
    TRANSITIONS[from]?.contains(to) == true

  fun canReopen(status: TaskStatus): Boolean =
    status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED

  data class NormalizedTaskFields(
    val title: String,
    val description: String?,
    val scheduledDate: String?,
    val startTime: String?,
    val endTime: String?,
    val categoryId: String?,
    val notes: String?,
  )

  fun validateInput(input: TaskInput): TaskDomainResult<NormalizedTaskFields> {
    val errors = mutableListOf<TaskValidationError>()

    val trimmedTitle = input.title.trim()
    if (trimmedTitle.isEmpty()) {
      errors += TaskValidationError.EmptyTitle
    }

    val normalizedDate =
      input.scheduledDate?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        parseStrictIsoDate(raw)?.format(ISO_DATE_FORMATTER)
          ?: run {
            errors += TaskValidationError.InvalidScheduledDate(raw)
            null
          }
      }

    val parsedStartTime =
      input.startTime?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        parseStrictTime(raw)
          ?: run {
            errors += TaskValidationError.InvalidStartTime(raw)
            null
          }
      }

    val rawEndPresent = !input.endTime.isNullOrBlank()
    val parsedEndTime =
      input.endTime?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        parseStrictTime(raw)
          ?: run {
            errors += TaskValidationError.InvalidEndTime(raw)
            null
          }
      }

    if (rawEndPresent && input.startTime.isNullOrBlank()) {
      errors += TaskValidationError.EndTimeWithoutStartTime
    } else if (parsedStartTime != null && parsedEndTime != null && parsedEndTime.isBefore(parsedStartTime)) {
      errors += TaskValidationError.EndTimeBeforeStartTime
    }

    return if (errors.isEmpty()) {
      TaskDomainResult.Success(
        NormalizedTaskFields(
          title = trimmedTitle,
          description = input.description?.trim()?.takeIf { it.isNotEmpty() },
          scheduledDate = normalizedDate,
          startTime = parsedStartTime?.format(TIME_FORMATTER),
          endTime = parsedEndTime?.format(TIME_FORMATTER),
          categoryId = input.categoryId?.trim()?.takeIf { it.isNotEmpty() },
          notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
        )
      )
    } else {
      TaskDomainResult.Failure(errors)
    }
  }

  fun buildNewTaskEntity(
    input: TaskInput,
    nowEpochMs: Long,
    idGenerator: () -> String = { UUID.randomUUID().toString() },
  ): TaskDomainResult<TaskEntity> {
    return when (val validation = validateInput(input)) {
      is TaskDomainResult.Failure -> validation
      is TaskDomainResult.Success -> {
        val fields = validation.value
        TaskDomainResult.Success(
          TaskEntity(
            id = idGenerator(),
            title = fields.title,
            description = fields.description,
            scheduledDate = fields.scheduledDate,
            startTime = fields.startTime,
            endTime = fields.endTime,
            priority = input.priority,
            status = TaskStatus.PENDING,
            categoryId = fields.categoryId,
            notes = fields.notes,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
            completedAtEpochMs = null,
          )
        )
      }
    }
  }

  fun applyEdit(
    existing: TaskEntity,
    input: TaskInput,
    nowEpochMs: Long,
  ): TaskDomainResult<TaskEntity> {
    return when (val validation = validateInput(input)) {
      is TaskDomainResult.Failure -> validation
      is TaskDomainResult.Success -> {
        val fields = validation.value
        TaskDomainResult.Success(
          existing.copy(
            title = fields.title,
            description = fields.description,
            scheduledDate = fields.scheduledDate,
            startTime = fields.startTime,
            endTime = fields.endTime,
            priority = input.priority,
            categoryId = fields.categoryId,
            notes = fields.notes,
            updatedAtEpochMs = nowEpochMs,
          )
        )
      }
    }
  }

  fun applyStatusTransition(
    existing: TaskEntity,
    targetStatus: TaskStatus,
    nowEpochMs: Long,
  ): TaskDomainResult<TaskEntity> {
    if (!isTransitionAllowed(existing.status, targetStatus)) {
      return TaskDomainResult.Failure(
        listOf(TaskValidationError.InvalidStatusTransition(from = existing.status, to = targetStatus))
      )
    }
    val nextCompletedAt =
      when (targetStatus) {
        TaskStatus.COMPLETED -> nowEpochMs
        TaskStatus.PENDING,
        TaskStatus.IN_PROGRESS,
        TaskStatus.CANCELLED -> null
      }
    return TaskDomainResult.Success(
      existing.copy(
        status = targetStatus,
        updatedAtEpochMs = nowEpochMs,
        completedAtEpochMs = nextCompletedAt,
      )
    )
  }

  fun reopenTask(
    existing: TaskEntity,
    nowEpochMs: Long,
  ): TaskDomainResult<TaskEntity> {
    if (!canReopen(existing.status)) {
      return TaskDomainResult.Failure(
        listOf(TaskValidationError.InvalidStatusTransition(from = existing.status, to = TaskStatus.PENDING))
      )
    }
    return applyStatusTransition(existing, TaskStatus.PENDING, nowEpochMs)
  }

  fun applyPostpone(
    existing: TaskEntity,
    targetDate: LocalDate,
    clearTimeWindow: Boolean,
    nowEpochMs: Long,
  ): TaskDomainResult<TaskEntity> {
    if (existing.status == TaskStatus.COMPLETED || existing.status == TaskStatus.CANCELLED) {
      return TaskDomainResult.Failure(
        listOf(TaskValidationError.InvalidStatusTransition(from = existing.status, to = existing.status))
      )
    }
    return TaskDomainResult.Success(
      existing.copy(
        scheduledDate = targetDate.format(ISO_DATE_FORMATTER),
        startTime = if (clearTimeWindow) null else existing.startTime,
        endTime = if (clearTimeWindow) null else existing.endTime,
        updatedAtEpochMs = nowEpochMs,
      )
    )
  }

  fun parseStrictIsoDate(raw: String): LocalDate? {
    return try {
      LocalDate.parse(raw.trim(), ISO_DATE_FORMATTER)
    } catch (_: DateTimeParseException) {
      null
    }
  }

  fun parseStrictTime(raw: String): LocalTime? {
    val trimmed = raw.trim()
    val normalized =
      if (trimmed.length == 4 && trimmed[1] == ':') "0$trimmed" else trimmed
    return try {
      LocalTime.parse(normalized, TIME_FORMATTER)
    } catch (_: DateTimeParseException) {
      null
    }
  }
}
