package com.example

import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.task.TaskCategoryFilter
import com.example.domain.task.TaskPriorityFilter
import com.example.domain.task.TaskQueryFilter
import com.example.domain.task.TaskQueryRules
import com.example.domain.task.TaskScheduleFilter
import com.example.domain.task.TaskSortOption
import com.example.domain.task.TaskStatusFilter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Unit3B2TaskQueryRulesTest {

  private val today = LocalDate.of(2026, 11, 25)
  private val catWork =
    CategoryEntity(
      id = "cat-work",
      name = "Work",
      colorToken = "amber",
      iconName = "work",
      sortOrder = 0,
      isArchived = false,
      updatedAtEpochMs = 1L,
    )
  private val catHealth =
    CategoryEntity(
      id = "cat-health",
      name = "Health",
      colorToken = "emerald",
      iconName = "health",
      sortOrder = 1,
      isArchived = false,
      updatedAtEpochMs = 1L,
    )
  private val categories = mapOf(catWork.id to catWork, catHealth.id to catHealth)

  private fun task(
    id: String,
    title: String,
    description: String? = null,
    notes: String? = null,
    date: String? = null,
    startTime: String? = null,
    priority: TaskPriority = TaskPriority.MEDIUM,
    status: TaskStatus = TaskStatus.PENDING,
    categoryId: String? = null,
    createdAt: Long = 100L,
  ) =
    TaskEntity(
      id = id,
      title = title,
      description = description,
      scheduledDate = date,
      startTime = startTime,
      endTime = null,
      priority = priority,
      status = status,
      categoryId = categoryId,
      notes = notes,
      createdAtEpochMs = createdAt,
      updatedAtEpochMs = createdAt,
      completedAtEpochMs = if (status == TaskStatus.COMPLETED) createdAt + 10L else null,
    )

  @Test
  fun search_matchesTitleDescriptionNotesAndCategoryName() {
    val t1 = task("1", "Prepare slides", description = "Quarterly deck", categoryId = catWork.id)
    val t2 = task("2", "Evening run", notes = "5km pace", categoryId = catHealth.id)
    val t3 = task("3", "Buy milk")

    val byTitle =
      TaskQueryRules.applyQuery(
        listOf(t1, t2, t3),
        categories,
        TaskQueryFilter(searchQuery = "slides"),
        today,
      )
    assertEquals(listOf("1"), byTitle.map { it.id })

    val byNotes =
      TaskQueryRules.applyQuery(
        listOf(t1, t2, t3),
        categories,
        TaskQueryFilter(searchQuery = "PACE"),
        today,
      )
    assertEquals(listOf("2"), byNotes.map { it.id })

    val byCategory =
      TaskQueryRules.applyQuery(
        listOf(t1, t2, t3),
        categories,
        TaskQueryFilter(searchQuery = "health"),
        today,
      )
    assertEquals(listOf("2"), byCategory.map { it.id })
  }

  @Test
  fun multiCriteriaFilters_combineSearchStatusSchedulePriorityAndCategory() {
    val overdueActiveWork =
      task(
        id = "1",
        title = "Submit invoice",
        date = "2026-11-23",
        priority = TaskPriority.HIGH,
        status = TaskStatus.IN_PROGRESS,
        categoryId = catWork.id,
      )
    val overdueCompletedWork =
      task(
        id = "2",
        title = "Submit report",
        date = "2026-11-22",
        priority = TaskPriority.HIGH,
        status = TaskStatus.COMPLETED,
        categoryId = catWork.id,
      )
    val todayHighWork =
      task(
        id = "3",
        title = "Submit plan",
        date = "2026-11-25",
        priority = TaskPriority.HIGH,
        status = TaskStatus.PENDING,
        categoryId = catWork.id,
      )
    val unscheduledLow =
      task(
        id = "4",
        title = "Read article",
        date = null,
        priority = TaskPriority.LOW,
        status = TaskStatus.PENDING,
        categoryId = null,
      )

    assertTrue(TaskQueryRules.isTaskOverdue(overdueActiveWork, today))
    assertFalse(TaskQueryRules.isTaskOverdue(overdueCompletedWork, today))
    assertFalse(TaskQueryRules.isTaskOverdue(unscheduledLow, today))

    val combined =
      TaskQueryRules.applyQuery(
        listOf(overdueActiveWork, overdueCompletedWork, todayHighWork, unscheduledLow),
        categories,
        TaskQueryFilter(
          searchQuery = "submit",
          statusFilter = TaskStatusFilter.ACTIVE,
          scheduleFilter = TaskScheduleFilter.OVERDUE,
          priorityFilter = TaskPriorityFilter.HIGH,
          categoryFilter = TaskCategoryFilter.ByCategory(catWork.id),
        ),
        today,
      )
    assertEquals(listOf("1"), combined.map { it.id })

    val uncategorizedUnscheduled =
      TaskQueryRules.applyQuery(
        listOf(overdueActiveWork, overdueCompletedWork, todayHighWork, unscheduledLow),
        categories,
        TaskQueryFilter(
          scheduleFilter = TaskScheduleFilter.UNSCHEDULED,
          categoryFilter = TaskCategoryFilter.Uncategorized,
        ),
        today,
      )
    assertEquals(listOf("4"), uncategorizedUnscheduled.map { it.id })
  }

  @Test
  fun sorting_ordersScheduledChronologicallyAndPlacesUnscheduledCleanly() {
    val unscheduledHigh =
      task("u-high", "Backlog urgent", date = null, priority = TaskPriority.HIGH, createdAt = 10L)
    val unscheduledLow =
      task("u-low", "Backlog minor", date = null, priority = TaskPriority.LOW, createdAt = 50L)
    val tomorrowMorning =
      task("tmrw", "Morning meeting", date = "2026-11-26", startTime = "09:00", priority = TaskPriority.LOW, createdAt = 20L)
    val todayUntimed =
      task("today-no-time", "Inbox cleanup", date = "2026-11-25", startTime = null, priority = TaskPriority.MEDIUM, createdAt = 30L)
    val todayAfternoon =
      task("today-14", "Code review", date = "2026-11-25", startTime = "14:00", priority = TaskPriority.HIGH, createdAt = 40L)

    val all = listOf(unscheduledLow, tomorrowMorning, todayUntimed, unscheduledHigh, todayAfternoon)

    val bySchedule =
      TaskQueryRules.applyQuery(all, categories, TaskQueryFilter(sortOption = TaskSortOption.SCHEDULE_ASC), today)
    assertEquals(
      listOf("today-14", "today-no-time", "tmrw", "u-high", "u-low"),
      bySchedule.map { it.id },
    )

    val byPriority =
      TaskQueryRules.applyQuery(all, categories, TaskQueryFilter(sortOption = TaskSortOption.PRIORITY_DESC), today)
    assertEquals(
      listOf("today-14", "u-high", "today-no-time", "tmrw", "u-low"),
      byPriority.map { it.id },
    )

    val byNewest =
      TaskQueryRules.applyQuery(all, categories, TaskQueryFilter(sortOption = TaskSortOption.NEWEST_FIRST), today)
    assertEquals(
      listOf("u-low", "today-14", "today-no-time", "tmrw", "u-high"),
      byNewest.map { it.id },
    )

    val byTitle =
      TaskQueryRules.applyQuery(all, categories, TaskQueryFilter(sortOption = TaskSortOption.TITLE_ASC), today)
    assertEquals(
      listOf("u-low", "u-high", "today-14", "today-no-time", "tmrw"),
      byTitle.map { it.id },
    )
  }
}
