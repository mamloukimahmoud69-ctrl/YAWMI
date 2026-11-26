package com.example.core.time

import com.example.core.preferences.AppLanguage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class GreetingPeriod {
  MORNING,
  AFTERNOON,
  EVENING,
}

interface LocalClock {
  fun currentDate(): LocalDate

  fun currentTime(): LocalTime

  fun currentEpochMs(): Long

  fun greetingPeriod(): GreetingPeriod

  fun zoneId(): ZoneId = ZoneId.systemDefault()

  fun millisUntilNextMidnight(): Long {
    val zone = zoneId()
    val now = ZonedDateTime.of(currentDate(), currentTime(), zone)
    val nextMidnight = currentDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
  }
}

class SystemLocalClock : LocalClock {
  override fun currentDate(): LocalDate =
    Instant.ofEpochMilli(currentEpochMs()).atZone(zoneId()).toLocalDate()

  override fun currentTime(): LocalTime =
    Instant.ofEpochMilli(currentEpochMs()).atZone(zoneId()).toLocalTime()

  override fun currentEpochMs(): Long = System.currentTimeMillis()

  override fun greetingPeriod(): GreetingPeriod {
    val hour = currentTime().hour
    return when {
      hour < 12 -> GreetingPeriod.MORNING
      hour < 17 -> GreetingPeriod.AFTERNOON
      else -> GreetingPeriod.EVENING
    }
  }

  override fun millisUntilNextMidnight(): Long {
    val zone = zoneId()
    val now = Instant.ofEpochMilli(currentEpochMs()).atZone(zone)
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
  }
}

fun LocalDate.formatLocalizedHeader(language: AppLanguage): String {
  val locale = Locale.forLanguageTag(language.tag)
  val pattern =
    if (language == AppLanguage.ARABIC) "EEEE، d MMMM yyyy" else "EEEE, MMM d, yyyy"
  return format(DateTimeFormatter.ofPattern(pattern, locale))
}
