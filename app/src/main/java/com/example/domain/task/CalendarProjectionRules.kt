package com.example.domain.task

import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class CalendarViewMode {
  DAY,
  WEEK,
  MONTH,
}

data class CalendarDaySummary(
  val date: LocalDate,
  val isCurrentMonth: Boolean,
  val isToday: Boolean,
  val isSelected: Boolean,
  val totalCount: Int,
  val activeCount: Int,
  val completedCount: Int,
  val overdueCount: Int,
)

data class CalendarDayTaskItem(
  val task: TaskEntity,
  val category: CategoryEntity?,
  val isOverdue: Boolean,
)

data class CalendarProjection(
  val mode: CalendarViewMode,
  val selectedDate: LocalDate,
  val focusedMonth: YearMonth,
  val todayDate: LocalDate,
  val weekdayHeaders: List<DayOfWeek>,
  val monthDays: List<CalendarDaySummary>,
  val weekDays: List<CalendarDaySummary>,
  val selectedDayTasks: List<CalendarDayTaskItem>,
  val scheduledTaskCountInMonth: Int,
)

object CalendarProjectionRules {
  private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  fun compute(
    tasks: List<TaskEntity>,
    categoriesById: Map<String, CategoryEntity>,
    selectedDate: LocalDate,
    focusedMonth: YearMonth,
    todayDate: LocalDate,
    mode: CalendarViewMode,
    firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
  ): CalendarProjection {
    val tasksByDate =
      tasks
        .asSequence()
        .filter { !it.scheduledDate.isNullOrBlank() }
        .groupBy { it.scheduledDate!! }

    val weekdayHeaders = (0 until 7).map { offset -> firstDayOfWeek.plus(offset.toLong()) }

    val monthFirst = focusedMonth.atDay(1)
    val monthLast = focusedMonth.atEndOfMonth()
    val leadingDays = ((monthFirst.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    val gridStart = monthFirst.minusDays(leadingDays.toLong())
    val lastWeekEndOffset = 6 - (((monthLast.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7)
    val gridEnd = monthLast.plusDays(lastWeekEndOffset.toLong())

    fun buildSummary(date: LocalDate): CalendarDaySummary {
      val iso = date.format(ISO_DATE)
      val dayTasks = tasksByDate[iso].orEmpty()
      var active = 0
      var completed = 0
      var overdue = 0
      for (t in dayTasks) {
        when (t.status) {
          TaskStatus.PENDING,
          TaskStatus.IN_PROGRESS -> {
            active += 1
            if (TaskQueryRules.isTaskOverdue(t, todayDate)) {
              overdue += 1
            }
          }
          TaskStatus.COMPLETED -> completed += 1
          TaskStatus.CANCELLED -> Unit
        }
      }
      return CalendarDaySummary(
        date = date,
        isCurrentMonth = YearMonth.from(date) == focusedMonth,
        isToday = date == todayDate,
        isSelected = date == selectedDate,
        totalCount = dayTasks.size,
        activeCount = active,
        completedCount = completed,
        overdueCount = overdue,
      )
    }

    val monthDays = mutableListOf<CalendarDaySummary>()
    var cursor = gridStart
    while (!cursor.isAfter(gridEnd)) {
      monthDays += buildSummary(cursor)
      cursor = cursor.plusDays(1)
    }

    val selectedLeading = ((selectedDate.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    val weekStart = selectedDate.minusDays(selectedLeading.toLong())
    val weekDays = (0 until 7).map { offset -> buildSummary(weekStart.plusDays(offset.toLong())) }

    val selectedIso = selectedDate.format(ISO_DATE)
    val selectedDayTasks =
      tasksByDate[selectedIso]
        .orEmpty()
        .sortedWith(dayAgendaComparator())
        .map { task ->
          CalendarDayTaskItem(
            task = task,
            category = categoriesById[task.categoryId],
            isOverdue = TaskQueryRules.isTaskOverdue(task, todayDate),
          )
        }

    val scheduledInMonth =
      monthDays
        .asSequence()
        .filter { it.isCurrentMonth }
        .sumOf { it.totalCount }

    return CalendarProjection(
      mode = mode,
      selectedDate = selectedDate,
      focusedMonth = focusedMonth,
      todayDate = todayDate,
      weekdayHeaders = weekdayHeaders,
      monthDays = monthDays,
      weekDays = weekDays,
      selectedDayTasks = selectedDayTasks,
      scheduledTaskCountInMonth = scheduledInMonth,
    )
  }

  private fun dayAgendaComparator(): Comparator<TaskEntity> =
    compareBy<TaskEntity>(
      { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED },
      { it.startTime == null },
      { it.startTime ?: "\uFFFF" },
      {
        when (it.priority) {
          TaskPriority.HIGH -> 0
          TaskPriority.MEDIUM -> 1
          TaskPriority.LOW -> 2
        }
      },
      { -it.createdAtEpochMs },
      { it.title.lowercase() },
    )
}
