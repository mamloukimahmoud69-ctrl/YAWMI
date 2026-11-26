package com.example.feature.calendar

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.time.LocalClock
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.CalendarProjection
import com.example.domain.task.CalendarProjectionRules
import com.example.domain.task.CalendarViewMode
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CalendarUiState(
  val isLoading: Boolean = true,
  val projection: CalendarProjection,
  val categories: List<CategoryEntity> = emptyList(),
)

sealed interface CalendarUiEvent {
  data class DomainFailure(val error: TaskValidationError) : CalendarUiEvent
}

private data class CalendarTimeContext(
  val todayDate: LocalDate,
  val zoneId: ZoneId,
)

private data class CalendarNavState(
  val mode: CalendarViewMode,
  val selectedDate: LocalDate,
  val focusedMonth: YearMonth,
  val firstDayOfWeek: DayOfWeek,
)

class CalendarViewModel(
  taskRepository: TaskRepository,
  categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
  initialFirstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
  private val midnightDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

  private val timeContext = MutableStateFlow(readTimeContext())
  private val navState =
    MutableStateFlow(
      CalendarNavState(
        mode = CalendarViewMode.MONTH,
        selectedDate = timeContext.value.todayDate,
        focusedMonth = YearMonth.from(timeContext.value.todayDate),
        firstDayOfWeek = initialFirstDayOfWeek,
      )
    )
  private var hasUserPinnedDate: Boolean = false
  private var midnightJob: Job? = null

  private val _activeMidnightTimerCount = AtomicInteger(0)
  @VisibleForTesting
  internal val activeMidnightTimerCount: Int
    get() = _activeMidnightTimerCount.get()

  private val _events = MutableSharedFlow<CalendarUiEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<CalendarUiEvent> = _events.asSharedFlow()

  val uiState: StateFlow<CalendarUiState> =
    combine(
        taskRepository.observeAllTasks(),
        categoryUseCases.observeActiveCategories(),
        navState,
        timeContext,
      ) { tasks, categories, nav, currentTime ->
        val categoriesById = categories.associateBy { it.id }
        val projection =
          CalendarProjectionRules.compute(
            tasks = tasks,
            categoriesById = categoriesById,
            selectedDate = nav.selectedDate,
            focusedMonth = nav.focusedMonth,
            todayDate = currentTime.todayDate,
            mode = nav.mode,
            firstDayOfWeek = nav.firstDayOfWeek,
          )
        CalendarUiState(
          isLoading = false,
          projection = projection,
          categories = categories,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue =
          CalendarUiState(
            isLoading = true,
            projection =
              CalendarProjectionRules.compute(
                tasks = emptyList(),
                categoriesById = emptyMap(),
                selectedDate = navState.value.selectedDate,
                focusedMonth = navState.value.focusedMonth,
                todayDate = timeContext.value.todayDate,
                mode = navState.value.mode,
                firstDayOfWeek = navState.value.firstDayOfWeek,
              ),
          ),
      )

  init {
    ensureMidnightTimerScheduled()
  }

  fun onForegroundStarted() {
    ensureMidnightTimerScheduled()
  }

  fun onForegroundResumed() {
    val latest = readTimeContext()
    if (latest != timeContext.value) {
      applyNewTimeContext(latest)
      rescheduleMidnightTimer()
    } else {
      ensureMidnightTimerScheduled()
    }
  }

  fun onForegroundStopped() {
    cancelMidnightTimer()
  }

  fun onSystemTimeOrZoneChanged() {
    val latest = readTimeContext()
    if (latest != timeContext.value) {
      applyNewTimeContext(latest)
    }
    rescheduleMidnightTimer()
  }

  private fun applyNewTimeContext(latest: CalendarTimeContext) {
    timeContext.value = latest
    if (!hasUserPinnedDate) {
      navState.value =
        navState.value.copy(
          selectedDate = latest.todayDate,
          focusedMonth = YearMonth.from(latest.todayDate),
        )
    }
  }

  private fun readTimeContext(): CalendarTimeContext =
    CalendarTimeContext(
      todayDate = localClock.currentDate(),
      zoneId = localClock.zoneId(),
    )

  private fun ensureMidnightTimerScheduled() {
    if (midnightJob?.isActive == true) return
    rescheduleMidnightTimer()
  }

  private fun rescheduleMidnightTimer() {
    midnightJob?.cancel()
    midnightJob =
      viewModelScope.launch(midnightDispatcher) {
        _activeMidnightTimerCount.incrementAndGet()
        try {
          while (isActive) {
            val delayMs = localClock.millisUntilNextMidnight()
            delay(delayMs)
            val latest = readTimeContext()
            if (latest != timeContext.value) {
              applyNewTimeContext(latest)
            }
          }
        } finally {
          _activeMidnightTimerCount.decrementAndGet()
        }
      }
  }

  private fun cancelMidnightTimer() {
    midnightJob?.cancel()
    midnightJob = null
  }

  override fun onCleared() {
    cancelMidnightTimer()
    super.onCleared()
  }

  fun setFirstDayOfWeek(firstDayOfWeek: DayOfWeek) {
    if (navState.value.firstDayOfWeek != firstDayOfWeek) {
      navState.value = navState.value.copy(firstDayOfWeek = firstDayOfWeek)
    }
  }

  fun setViewMode(mode: CalendarViewMode) {
    val current = navState.value
    navState.value =
      current.copy(
        mode = mode,
        focusedMonth = YearMonth.from(current.selectedDate),
      )
  }

  fun selectDate(date: LocalDate) {
    hasUserPinnedDate = date != timeContext.value.todayDate
    navState.value =
      navState.value.copy(
        selectedDate = date,
        focusedMonth = YearMonth.from(date),
      )
  }

  fun jumpToToday() {
    val today = localClock.currentDate()
    hasUserPinnedDate = false
    navState.value =
      navState.value.copy(
        selectedDate = today,
        focusedMonth = YearMonth.from(today),
      )
  }

  fun navigatePrevious() {
    shiftPeriod(-1)
  }

  fun navigateNext() {
    shiftPeriod(1)
  }

  private fun shiftPeriod(direction: Long) {
    val current = navState.value
    hasUserPinnedDate = true
    when (current.mode) {
      CalendarViewMode.MONTH -> {
        val targetMonth = current.focusedMonth.plusMonths(direction)
        val clampedDay = current.selectedDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth())
        navState.value =
          current.copy(
            focusedMonth = targetMonth,
            selectedDate = targetMonth.atDay(clampedDay),
          )
      }
      CalendarViewMode.WEEK -> {
        val targetDate = current.selectedDate.plusWeeks(direction)
        navState.value =
          current.copy(
            selectedDate = targetDate,
            focusedMonth = YearMonth.from(targetDate),
          )
      }
      CalendarViewMode.DAY -> {
        val targetDate = current.selectedDate.plusDays(direction)
        navState.value =
          current.copy(
            selectedDate = targetDate,
            focusedMonth = YearMonth.from(targetDate),
          )
      }
    }
  }

  fun completeOrReopenTask(task: TaskEntity) {
    viewModelScope.launch {
      val result =
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
          taskUseCases.reopenTask(task.id)
        } else {
          taskUseCases.completeTask(task.id)
        }
      if (result is TaskDomainResult.Failure) {
        _events.tryEmit(CalendarUiEvent.DomainFailure(result.errors.first()))
      }
    }
  }

  fun postponeToDate(
    taskId: String,
    targetDate: LocalDate,
    clearTimeWindow: Boolean = false,
  ) {
    viewModelScope.launch {
      val result =
        taskUseCases.postponeTask(
          taskId = taskId,
          targetDate = targetDate,
          clearTimeWindow = clearTimeWindow,
        )
      if (result is TaskDomainResult.Failure) {
        _events.tryEmit(CalendarUiEvent.DomainFailure(result.errors.first()))
      }
    }
  }

  fun deleteTask(taskId: String) {
    viewModelScope.launch {
      taskUseCases.deleteTask(taskId)
    }
  }
}

class CalendarViewModelFactory(
  private val taskRepository: TaskRepository,
  private val categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
  private val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return CalendarViewModel(
      taskRepository = taskRepository,
      categoryUseCases = categoryUseCases,
      taskUseCases = taskUseCases,
      localClock = localClock,
      initialFirstDayOfWeek = firstDayOfWeek,
    ) as T
  }
}
