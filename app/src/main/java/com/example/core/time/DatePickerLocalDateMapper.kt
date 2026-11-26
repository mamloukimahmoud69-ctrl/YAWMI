package com.example.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Maps between Material 3 DatePicker UTC-midnight epoch millis and local calendar [LocalDate].
 */
object DatePickerLocalDateMapper {
  fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

  fun pickerMillisToLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

  fun initialPickerMillis(
    existingIsoDate: String?,
    localClock: LocalClock,
  ): Long {
    val parsed =
      existingIsoDate
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
    val targetDate = parsed ?: localClock.currentDate()
    return localDateToPickerMillis(targetDate)
  }
}
