package com.example.domain.task

import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class TaskStatusFilter {
  ALL,
  ACTIVE,
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED,
}

enum class TaskScheduleFilter {
  ALL,
  TODAY,
  UPCOMING,
  OVERDUE,
  UNSCHEDULED,
}

enum class TaskPriorityFilter {
  ALL,
  HIGH,
  MEDIUM,
  LOW,
}

sealed interface TaskCategoryFilter {
  data object All : TaskCategoryFilter

  data object Uncategorized : TaskCategoryFilter

  data class ByCategory(val categoryId: String) : TaskCategoryFilter
}

enum class TaskSortOption {
  SCHEDULE_ASC,
  PRIORITY_DESC,
  NEWEST_FIRST,
  TITLE_ASC,
}

data class TaskQueryFilter(
  val searchQuery: String = "",
  val statusFilter: TaskStatusFilter = TaskStatusFilter.ALL,
  val scheduleFilter: TaskScheduleFilter = TaskScheduleFilter.ALL,
  val priorityFilter: TaskPriorityFilter = TaskPriorityFilter.ALL,
  val categoryFilter: TaskCategoryFilter = TaskCategoryFilter.All,
  val sortOption: TaskSortOption = TaskSortOption.SCHEDULE_ASC,
) {
  val hasActiveFilters: Boolean
    get() =
      searchQuery.isNotBlank() ||
        statusFilter != TaskStatusFilter.ALL ||
        scheduleFilter != TaskScheduleFilter.ALL ||
        priorityFilter != TaskPriorityFilter.ALL ||
        categoryFilter != TaskCategoryFilter.All
}

object TaskQueryRules {
  private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  fun isTaskOverdue(task: TaskEntity, today: LocalDate): Boolean {
    val scheduled = task.scheduledDate ?: return false
    val todayStr = today.format(ISO_DATE)
    return scheduled < todayStr &&
      (task.status == TaskStatus.PENDING || task.status == TaskStatus.IN_PROGRESS)
  }

  fun applyQuery(
    tasks: List<TaskEntity>,
    categoriesById: Map<String, CategoryEntity>,
    filter: TaskQueryFilter,
    today: LocalDate,
  ): List<TaskEntity> {
    val todayStr = today.format(ISO_DATE)
    val query = filter.searchQuery.trim()

    val filtered =
      tasks.filter { task ->
        matchesSearch(task, categoriesById[task.categoryId], query) &&
          matchesStatus(task, filter.statusFilter) &&
          matchesSchedule(task, filter.scheduleFilter, todayStr, today) &&
          matchesPriority(task, filter.priorityFilter) &&
          matchesCategory(task, filter.categoryFilter)
      }

    return filtered.sortedWith(comparatorFor(filter.sortOption))
  }

  private fun matchesSearch(
    task: TaskEntity,
    category: CategoryEntity?,
    trimmedQuery: String,
  ): Boolean {
    if (trimmedQuery.isEmpty()) return true
    return task.title.contains(trimmedQuery, ignoreCase = true) ||
      (task.description?.contains(trimmedQuery, ignoreCase = true) == true) ||
      (task.notes?.contains(trimmedQuery, ignoreCase = true) == true) ||
      (category?.name?.contains(trimmedQuery, ignoreCase = true) == true)
  }

  private fun matchesStatus(task: TaskEntity, filter: TaskStatusFilter): Boolean =
    when (filter) {
      TaskStatusFilter.ALL -> true
      TaskStatusFilter.ACTIVE ->
        task.status == TaskStatus.PENDING || task.status == TaskStatus.IN_PROGRESS
      TaskStatusFilter.PENDING -> task.status == TaskStatus.PENDING
      TaskStatusFilter.IN_PROGRESS -> task.status == TaskStatus.IN_PROGRESS
      TaskStatusFilter.COMPLETED -> task.status == TaskStatus.COMPLETED
      TaskStatusFilter.CANCELLED -> task.status == TaskStatus.CANCELLED
    }

  private fun matchesSchedule(
    task: TaskEntity,
    filter: TaskScheduleFilter,
    todayStr: String,
    today: LocalDate,
  ): Boolean =
    when (filter) {
      TaskScheduleFilter.ALL -> true
      TaskScheduleFilter.TODAY -> task.scheduledDate == todayStr
      TaskScheduleFilter.UPCOMING -> task.scheduledDate != null && task.scheduledDate > todayStr
      TaskScheduleFilter.OVERDUE -> isTaskOverdue(task, today)
      TaskScheduleFilter.UNSCHEDULED -> task.scheduledDate == null
    }

  private fun matchesPriority(task: TaskEntity, filter: TaskPriorityFilter): Boolean =
    when (filter) {
      TaskPriorityFilter.ALL -> true
      TaskPriorityFilter.HIGH -> task.priority == TaskPriority.HIGH
      TaskPriorityFilter.MEDIUM -> task.priority == TaskPriority.MEDIUM
      TaskPriorityFilter.LOW -> task.priority == TaskPriority.LOW
    }

  private fun matchesCategory(task: TaskEntity, filter: TaskCategoryFilter): Boolean =
    when (filter) {
      TaskCategoryFilter.All -> true
      TaskCategoryFilter.Uncategorized -> task.categoryId == null
      is TaskCategoryFilter.ByCategory -> task.categoryId == filter.categoryId
    }

  private fun priorityRank(priority: TaskPriority): Int =
    when (priority) {
      TaskPriority.HIGH -> 0
      TaskPriority.MEDIUM -> 1
      TaskPriority.LOW -> 2
    }

  private fun comparatorFor(sortOption: TaskSortOption): Comparator<TaskEntity> =
    when (sortOption) {
      TaskSortOption.SCHEDULE_ASC ->
        compareBy<TaskEntity>(
          { it.scheduledDate == null },
          { it.scheduledDate ?: "\uFFFF" },
          { it.startTime == null },
          { it.startTime ?: "\uFFFF" },
          { priorityRank(it.priority) },
          { -it.createdAtEpochMs },
          { it.title.lowercase() },
        )
      TaskSortOption.PRIORITY_DESC ->
        compareBy<TaskEntity>(
          { priorityRank(it.priority) },
          { it.scheduledDate == null },
          { it.scheduledDate ?: "\uFFFF" },
          { it.startTime == null },
          { it.startTime ?: "\uFFFF" },
          { -it.createdAtEpochMs },
        )
      TaskSortOption.NEWEST_FIRST ->
        compareBy<TaskEntity>(
          { -it.createdAtEpochMs },
          { it.scheduledDate == null },
          { it.scheduledDate ?: "\uFFFF" },
          { it.title.lowercase() },
        )
      TaskSortOption.TITLE_ASC ->
        compareBy<TaskEntity>(
          { it.title.lowercase() },
          { it.scheduledDate == null },
          { it.scheduledDate ?: "\uFFFF" },
          { -it.createdAtEpochMs },
        )
    }
}
