package com.example

import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.task.TodayDashboardRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Unit3B4TodayDashboardRulesTest {

  private val today = LocalDate.of(2026, 11, 25)

  private fun task(
    id: String,
    title: String = "Task $id",
    scheduledDate: String? = "2026-11-25",
    startTime: String? = null,
    priority: TaskPriority = TaskPriority.MEDIUM,
    status: TaskStatus = TaskStatus.PENDING,
    categoryId: String? = null,
    completedAtEpochMs: Long? = null,
    createdAtEpochMs: Long = 1_000L,
  ): TaskEntity =
    TaskEntity(
      id = id,
      title = title,
      description = null,
      scheduledDate = scheduledDate,
      startTime = startTime,
      endTime = null,
      priority = priority,
      status = status,
      categoryId = categoryId,
      notes = null,
      createdAtEpochMs = createdAtEpochMs,
      updatedAtEpochMs = createdAtEpochMs,
      completedAtEpochMs = completedAtEpochMs,
    )

  @Test
  fun compute_emptyTasks_returnsZeroMetricsAndEmptySections() {
    val snapshot =
      TodayDashboardRules.compute(
        tasks = emptyList(),
        categories = emptyList(),
        today = today,
      )

    assertEquals(0, snapshot.metrics.totalCount)
    assertEquals(0, snapshot.metrics.completedCount)
    assertEquals(0, snapshot.metrics.remainingCount)
    assertEquals(0, snapshot.metrics.overdueCount)
    assertEquals(0f, snapshot.metrics.completionRatio, 0.0001f)
    assertTrue(snapshot.priorityItems.isEmpty())
    assertTrue(snapshot.scheduleItems.isEmpty())
    assertTrue(snapshot.unscheduledItems.isEmpty())
  }

  @Test
  fun compute_preventsDoubleCounting_andSeparatesOverdueRemainingCompletedUpcomingUnscheduled() {
    val work =
      CategoryEntity(
        id = "cat-work",
        name = "Work",
        colorToken = "amber",
        iconName = "work",
        updatedAtEpochMs = 100L,
      )
    val todayMidnightPlus1h =
      ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val yesterdayCompletedMs =
      ZonedDateTime.of(2026, 11, 24, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    val tasks =
      listOf(
        // Today completed -> Completed (1)
        task(id = "t-today-done", scheduledDate = "2026-11-25", status = TaskStatus.COMPLETED, completedAtEpochMs = todayMidnightPlus1h),
        // Today pending high -> Remaining (1) + Important Priority
        task(id = "t-today-pending", scheduledDate = "2026-11-25", startTime = "09:00", priority = TaskPriority.HIGH, status = TaskStatus.PENDING, categoryId = work.id),
        // Today in-progress -> Remaining (2)
        task(id = "t-today-progress", scheduledDate = "2026-11-25", startTime = "14:00", status = TaskStatus.IN_PROGRESS),
        // Today cancelled -> Excluded from metrics & active lists
        task(id = "t-today-cancelled", scheduledDate = "2026-11-25", priority = TaskPriority.HIGH, status = TaskStatus.CANCELLED),
        // Overdue pending high -> Overdue (1), NOT Remaining, + Important Priority
        task(id = "t-overdue-pending", scheduledDate = "2026-11-23", priority = TaskPriority.HIGH, status = TaskStatus.PENDING),
        // Overdue completed yesterday -> Past completed, excluded from today metrics
        task(id = "t-past-done", scheduledDate = "2026-11-24", status = TaskStatus.COMPLETED, completedAtEpochMs = yesterdayCompletedMs),
        // Future upcoming -> Excluded from today metrics, included in scheduleItems
        task(id = "t-upcoming", scheduledDate = "2026-11-27", startTime = "08:30", priority = TaskPriority.LOW, status = TaskStatus.PENDING),
        // Unscheduled active high -> Excluded from today metrics, included in unscheduledItems (null date preserved) & priorityItems
        task(id = "t-unscheduled", scheduledDate = null, priority = TaskPriority.HIGH, status = TaskStatus.PENDING),
      )

    val snapshot =
      TodayDashboardRules.compute(
        tasks = tasks,
        categories = listOf(work),
        today = today,
        zoneId = ZoneId.of("UTC"),
      )

    // Total = completed (1) + remaining (2) + overdue (1) = 4; completion = 1/4 = 0.25
    assertEquals(1, snapshot.metrics.completedCount)
    assertEquals(2, snapshot.metrics.remainingCount)
    assertEquals(1, snapshot.metrics.overdueCount)
    assertEquals(4, snapshot.metrics.totalCount)
    assertEquals(0.25f, snapshot.metrics.completionRatio, 0.0001f)

    // Priority list: only active HIGH tasks (overdue -> today -> unscheduled), excludes cancelled
    assertEquals(
      listOf("t-overdue-pending", "t-today-pending", "t-unscheduled"),
      snapshot.priorityItems.map { it.task.id },
    )
    assertEquals(work, snapshot.priorityItems[1].category)
    assertTrue(snapshot.priorityItems[0].isOverdue)
    assertFalse(snapshot.priorityItems[1].isOverdue)

    // Schedule list: active scheduled tasks chronologically (overdue -> today 09:00 -> today 14:00 -> upcoming)
    assertEquals(
      listOf("t-overdue-pending", "t-today-pending", "t-today-progress", "t-upcoming"),
      snapshot.scheduleItems.map { it.task.id },
    )

    // Unscheduled list keeps scheduledDate == null (never invents a date)
    assertEquals(listOf("t-unscheduled"), snapshot.unscheduledItems.map { it.task.id })
    assertNull(snapshot.unscheduledItems.single().task.scheduledDate)
  }

  @Test
  fun compute_respectsLocalTimezoneMidnightBoundaryForCompletedTasks() {
    // Instant is 2026-11-24T22:30:00Z:
    // - In Asia/Riyadh (UTC+3): 2026-11-25 01:30 -> completed ON 2026-11-25!
    // - In America/Los_Angeles (UTC-8): 2026-11-24 14:30 -> completed ON 2026-11-24!
    val instantMs = Instant.parse("2026-11-24T22:30:00Z").toEpochMilli()
    val completedOverdueTask =
      task(
        id = "t-midnight",
        scheduledDate = "2026-11-24",
        status = TaskStatus.COMPLETED,
        completedAtEpochMs = instantMs,
      )

    val riyadhSnapshot =
      TodayDashboardRules.compute(
        tasks = listOf(completedOverdueTask),
        categories = emptyList(),
        today = LocalDate.of(2026, 11, 25),
        zoneId = ZoneId.of("Asia/Riyadh"),
      )
    assertEquals(1, riyadhSnapshot.metrics.completedCount)
    assertEquals(1, riyadhSnapshot.metrics.totalCount)
    assertEquals(1.0f, riyadhSnapshot.metrics.completionRatio, 0.0001f)

    val laSnapshot =
      TodayDashboardRules.compute(
        tasks = listOf(completedOverdueTask),
        categories = emptyList(),
        today = LocalDate.of(2026, 11, 25),
        zoneId = ZoneId.of("America/Los_Angeles"),
      )
    assertEquals(0, laSnapshot.metrics.completedCount)
    assertEquals(0, laSnapshot.metrics.totalCount)
    assertEquals(0f, laSnapshot.metrics.completionRatio, 0.0001f)
  }
}
