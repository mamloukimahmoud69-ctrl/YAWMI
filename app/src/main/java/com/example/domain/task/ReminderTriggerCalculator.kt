package com.example.domain.task

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class DstAdjustmentPolicy {
  NONE,
  GAP_SHIFTED_FORWARD,
  OVERLAP_EARLIER_OFFSET,
}

enum class TriggerEvaluationStatus {
  ON_TIME_OR_FUTURE,
  FIRE_WITHIN_GRACE,
  EXPIRED,
  PREMATURE_CLOCK_ROLLBACK,
  INVALID,
}

data class CalculatedZonedTrigger(
  val requestedDate: LocalDate,
  val requestedTime: LocalTime,
  val zonedDateTime: ZonedDateTime,
  val triggerEpochMs: Long,
  val dstAdjustment: DstAdjustmentPolicy,
)

data class EvaluatedReminderTrigger(
  val status: TriggerEvaluationStatus,
  val triggerEpochMs: Long?,
  val effectiveDateTime: ZonedDateTime?,
  val dstAdjustment: DstAdjustmentPolicy,
  val occurrenceKey: String?,
)

enum class ScheduleTimingDecision {
  SCHEDULE_AT_TRIGGER,
  FIRE_IMMEDIATELY_WITHIN_GRACE,
  EXPIRED_OUTSIDE_GRACE,
  INVALID,
}

data class ScheduleTimingEvaluation(
  val decision: ScheduleTimingDecision,
  val resolved: CalculatedZonedTrigger?,
  val occurrenceKey: String?,
)

/**
 * Pure DST-aware reminder trigger calculator and grace-window evaluator.
 *
 * DST Policy:
 * - Spring-Forward Gap (e.g. 02:30 when 02:00 jumps to 03:00): Shifts forward by the gap duration
 *   to the first valid wall-clock time after the gap ([DstAdjustmentPolicy.GAP_SHIFTED_FORWARD]).
 * - Fall-Back Overlap (e.g. 01:30 occurring twice): Selects the earlier offset
 *   ([DstAdjustmentPolicy.OVERLAP_EARLIER_OFFSET]) so reminders are not unexpectedly delayed 1 hour.
 *
 * Grace Window Policy:
 * - Missed alarms within [DEFAULT_GRACE_WINDOW_MINUTES] (15 min) fire immediately.
 * - Alarms older than the grace window are marked [TriggerEvaluationStatus.EXPIRED] and never fire late.
 */
object ReminderTriggerCalculator {
  const val DEFAULT_GRACE_WINDOW_MINUTES: Long = 15L
  const val DEFAULT_GRACE_WINDOW_MS: Long = DEFAULT_GRACE_WINDOW_MINUTES * 60_000L
  const val EARLY_FIRE_TOLERANCE_MS: Long = 60_000L

  private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
  private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

  fun resolveTrigger(
    triggerDate: String,
    triggerTime: String,
    zoneId: ZoneId,
  ): CalculatedZonedTrigger? {
    val parsedDate = runCatching { LocalDate.parse(triggerDate.trim(), DATE_FMT) }.getOrNull() ?: return null
    val parsedTime = runCatching { LocalTime.parse(triggerTime.trim(), TIME_FMT) }.getOrNull() ?: return null
    return resolveTrigger(parsedDate, parsedTime, zoneId)
  }

  fun resolveTrigger(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId,
  ): CalculatedZonedTrigger {
    val requestedLdt = LocalDateTime.of(date, time)
    val validOffsets = zoneId.rules.getValidOffsets(requestedLdt)
    val resolvedZdt = ZonedDateTime.of(requestedLdt, zoneId).withEarlierOffsetAtOverlap()
    val policy =
      when {
        validOffsets.isEmpty() -> DstAdjustmentPolicy.GAP_SHIFTED_FORWARD
        validOffsets.size > 1 -> DstAdjustmentPolicy.OVERLAP_EARLIER_OFFSET
        else -> DstAdjustmentPolicy.NONE
      }
    return CalculatedZonedTrigger(
      requestedDate = date,
      requestedTime = time,
      zonedDateTime = resolvedZdt,
      triggerEpochMs = resolvedZdt.toInstant().toEpochMilli(),
      dstAdjustment = policy,
    )
  }

  fun buildOccurrenceKey(
    triggerDate: String,
    triggerTime: String,
    scheduleRevision: Long,
    zoneId: ZoneId,
  ): String = "${triggerDate.trim()}T${triggerTime.trim()}@${zoneId.id}#rev$scheduleRevision"

  fun evaluateForSchedule(
    triggerDate: String,
    triggerTime: String,
    scheduleRevision: Long,
    nowEpochMs: Long,
    zoneId: ZoneId,
    graceWindowMs: Long = DEFAULT_GRACE_WINDOW_MS,
  ): ScheduleTimingEvaluation {
    val resolved =
      resolveTrigger(triggerDate, triggerTime, zoneId)
        ?: return ScheduleTimingEvaluation(
          decision = ScheduleTimingDecision.INVALID,
          resolved = null,
          occurrenceKey = null,
        )
    val occurrenceKey = buildOccurrenceKey(triggerDate, triggerTime, scheduleRevision, zoneId)
    val deltaPastMs = nowEpochMs - resolved.triggerEpochMs
    val decision =
      when {
        deltaPastMs < 0L -> ScheduleTimingDecision.SCHEDULE_AT_TRIGGER
        deltaPastMs <= graceWindowMs -> ScheduleTimingDecision.FIRE_IMMEDIATELY_WITHIN_GRACE
        else -> ScheduleTimingDecision.EXPIRED_OUTSIDE_GRACE
      }
    return ScheduleTimingEvaluation(
      decision = decision,
      resolved = resolved,
      occurrenceKey = occurrenceKey,
    )
  }

  fun evaluateAtFire(
    triggerDate: String,
    triggerTime: String,
    scheduleRevision: Long,
    nowEpochMs: Long,
    zoneId: ZoneId,
    graceWindowMs: Long = DEFAULT_GRACE_WINDOW_MS,
    earlyFireToleranceMs: Long = EARLY_FIRE_TOLERANCE_MS,
  ): EvaluatedReminderTrigger {
    val resolved =
      resolveTrigger(triggerDate, triggerTime, zoneId)
        ?: return EvaluatedReminderTrigger(
          status = TriggerEvaluationStatus.INVALID,
          triggerEpochMs = null,
          effectiveDateTime = null,
          dstAdjustment = DstAdjustmentPolicy.NONE,
          occurrenceKey = null,
        )
    val occurrenceKey = buildOccurrenceKey(triggerDate, triggerTime, scheduleRevision, zoneId)
    val deltaPastMs = nowEpochMs - resolved.triggerEpochMs
    val status =
      when {
        deltaPastMs < -earlyFireToleranceMs -> TriggerEvaluationStatus.PREMATURE_CLOCK_ROLLBACK
        deltaPastMs <= 0L -> TriggerEvaluationStatus.ON_TIME_OR_FUTURE
        deltaPastMs <= graceWindowMs -> TriggerEvaluationStatus.FIRE_WITHIN_GRACE
        else -> TriggerEvaluationStatus.EXPIRED
      }
    return EvaluatedReminderTrigger(
      status = status,
      triggerEpochMs = resolved.triggerEpochMs,
      effectiveDateTime = resolved.zonedDateTime,
      dstAdjustment = resolved.dstAdjustment,
      occurrenceKey = occurrenceKey,
    )
  }
}
