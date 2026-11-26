package com.example.domain.task

import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TodayDashboardMetrics(
  val totalCount: Int,
  val completedCount: Int,
  val remainingCount: Int,
  val overdueCount: Int,
  val completionRatio: Float,
)

data class TodayTaskItem(
  val task: TaskEntity,
  val category: CategoryEntity?,
  val isOverdue: Boolean,
)

data class TodayDashboardSnapshot(
  val todayDate: LocalDate,
  val metrics: TodayDashboardMetrics,
  val priorityItems: List<TodayTaskItem>,
  val scheduleItems: List<TodayTaskItem>,
  val unscheduledItems: List<TodayTaskItem>,
)

/**
 * Pure domain rules for Today's dashboard summary metrics and live sections.
 *
 * Metric calculation & mutual-exclusion invariant:
 * - Overdue: active (`PENDING` or `IN_PROGRESS`) tasks with `scheduledDate < today`.
 * - Completed: `COMPLETED` tasks whose `scheduledDate == today` OR whose `completedAtEpochMs`
 *   falls within `today` in the device's local [ZoneId].
 * - Remaining: active (`PENDING` or `IN_PROGRESS`) tasks with `scheduledDate == today`.
 * - Excluded from Today's 4 progress counters: `CANCELLED` tasks, future (`scheduledDate > today`)
 *   active tasks, past-day completed tasks, and active unscheduled tasks (`scheduledDate == null`).
 * - Every task is counted in at most one bucket, so `totalCount == completedCount + remainingCount + overdueCount`
 *   and `completionRatio = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()`.
 */
object TodayDashboardRules {
  private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  fun compute(
    tasks: List<TaskEntity>,
    categories: List<CategoryEntity>,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): TodayDashboardSnapshot {
    val todayStr = today.format(ISO_DATE)
    val categoriesById = categories.associateBy { it.id }

    var completedCount = 0
    var remainingCount = 0
    var overdueCount = 0

    for (task in tasks) {
      when {
        TaskQueryRules.isTaskOverdue(task, today) -> overdueCount += 1
        task.status == TaskStatus.COMPLETED &&
          (task.scheduledDate == todayStr ||
            isEpochOnLocalDate(task.completedAtEpochMs, today, zoneId)) -> completedCount += 1
        task.scheduledDate == todayStr &&
          (task.status == TaskStatus.PENDING || task.status == TaskStatus.IN_PROGRESS) ->
          remainingCount += 1
      }
    }

    val totalCount = completedCount + remainingCount + overdueCount
    val completionRatio =
      if (totalCount == 0) 0f
      else (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

    val priorityTasks =
      TaskQueryRules.applyQuery(
        tasks = tasks,
        categoriesById = categoriesById,
        filter =
          TaskQueryFilter(
            statusFilter = TaskStatusFilter.ACTIVE,
            priorityFilter = TaskPriorityFilter.HIGH,
            sortOption = TaskSortOption.SCHEDULE_ASC,
          ),
        today = today,
      )

    val activeScheduledTasks =
      TaskQueryRules.applyQuery(
        tasks = tasks.filter { it.scheduledDate != null },
        categoriesById = categoriesById,
        filter =
          TaskQueryFilter(
            statusFilter = TaskStatusFilter.ACTIVE,
            sortOption = TaskSortOption.SCHEDULE_ASC,
          ),
        today = today,
      )

    val activeUnscheduledTasks =
      TaskQueryRules.applyQuery(
        tasks = tasks,
        categoriesById = categoriesById,
        filter =
          TaskQueryFilter(
            statusFilter = TaskStatusFilter.ACTIVE,
            scheduleFilter = TaskScheduleFilter.UNSCHEDULED,
            sortOption = TaskSortOption.PRIORITY_DESC,
          ),
        today = today,
      )

    fun TaskEntity.toTodayItem(): TodayTaskItem =
      TodayTaskItem(
        task = this,
        category = categoriesById[categoryId],
        isOverdue = TaskQueryRules.isTaskOverdue(this, today),
      )

    return TodayDashboardSnapshot(
      todayDate = today,
      metrics =
        TodayDashboardMetrics(
          totalCount = totalCount,
          completedCount = completedCount,
          remainingCount = remainingCount,
          overdueCount = overdueCount,
          completionRatio = completionRatio,
        ),
      priorityItems = priorityTasks.map { it.toTodayItem() },
      scheduleItems = activeScheduledTasks.map { it.toTodayItem() },
      unscheduledItems = activeUnscheduledTasks.map { it.toTodayItem() },
    )
  }

  private fun isEpochOnLocalDate(
    epochMs: Long?,
    date: LocalDate,
    zoneId: ZoneId,
  ): Boolean {
    if (epochMs == null) return false
    return Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate() == date
  }
}
