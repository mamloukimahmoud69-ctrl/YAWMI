package com.example.feature.dashboard

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import com.example.domain.task.TodayDashboardMetrics
import com.example.domain.task.TodayDashboardRules
import com.example.domain.task.TodayTaskItem
import java.time.LocalDate
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

data class TodayUiState(
  val isLoading: Boolean = true,
  val todayDate: LocalDate = LocalDate.MIN,
  val greetingPeriod: GreetingPeriod = GreetingPeriod.MORNING,
  val metrics: TodayDashboardMetrics =
    TodayDashboardMetrics(
      totalCount = 0,
      completedCount = 0,
      remainingCount = 0,
      overdueCount = 0,
      completionRatio = 0f,
    ),
  val priorityItems: List<TodayTaskItem> = emptyList(),
  val scheduleItems: List<TodayTaskItem> = emptyList(),
  val unscheduledItems: List<TodayTaskItem> = emptyList(),
  val categories: List<CategoryEntity> = emptyList(),
)

sealed interface TodayUiEvent {
  data class ActionSucceeded(@param:StringRes val messageRes: Int) : TodayUiEvent

  data class ActionFailed(val error: TaskValidationError) : TodayUiEvent
}

private data class TodayTimeContext(
  val date: LocalDate,
  val zoneId: ZoneId,
  val greetingPeriod: GreetingPeriod,
)

class TodayViewModel(
  taskRepository: TaskRepository,
  categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
  private val midnightDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

  private val _events = MutableSharedFlow<TodayUiEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<TodayUiEvent> = _events.asSharedFlow()

  private val timeContext = MutableStateFlow(readTimeContext())
  private var midnightJob: Job? = null

  private val _activeMidnightTimerCount = AtomicInteger(0)
  @VisibleForTesting
  internal val activeMidnightTimerCount: Int
    get() = _activeMidnightTimerCount.get()

  private val _dashboardComputeCount = AtomicInteger(0)
  @VisibleForTesting
  internal val dashboardComputeCount: Int
    get() = _dashboardComputeCount.get()

  val uiState: StateFlow<TodayUiState> =
    combine(
        taskRepository.observeAllTasks(),
        categoryUseCases.observeActiveCategories(),
        timeContext,
      ) { tasks, categories, currentTime ->
        _dashboardComputeCount.incrementAndGet()
        val snapshot =
          TodayDashboardRules.compute(
            tasks = tasks,
            categories = categories,
            today = currentTime.date,
            zoneId = currentTime.zoneId,
          )
        TodayUiState(
          isLoading = false,
          todayDate = snapshot.todayDate,
          greetingPeriod = currentTime.greetingPeriod,
          metrics = snapshot.metrics,
          priorityItems = snapshot.priorityItems,
          scheduleItems = snapshot.scheduleItems,
          unscheduledItems = snapshot.unscheduledItems,
          categories = categories,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue =
          TodayUiState(
            isLoading = true,
            todayDate = timeContext.value.date,
            greetingPeriod = timeContext.value.greetingPeriod,
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
      timeContext.value = latest
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
      timeContext.value = latest
    }
    rescheduleMidnightTimer()
  }

  private fun readTimeContext(): TodayTimeContext =
    TodayTimeContext(
      date = localClock.currentDate(),
      zoneId = localClock.zoneId(),
      greetingPeriod = localClock.greetingPeriod(),
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
              timeContext.value = latest
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

  fun toggleTaskCompletion(task: TaskEntity) {
    viewModelScope.launch {
      val isTerminal = task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED
      val result =
        if (isTerminal) taskUseCases.reopenTask(task.id)
        else taskUseCases.completeTask(task.id)

      when (result) {
        is TaskDomainResult.Success -> {
          val msg =
            if (isTerminal) R.string.today_msg_task_reopened else R.string.today_msg_task_completed
          _events.tryEmit(TodayUiEvent.ActionSucceeded(msg))
        }
        is TaskDomainResult.Failure -> {
          _events.tryEmit(TodayUiEvent.ActionFailed(result.errors.first()))
        }
      }
    }
  }
}

class TodayViewModelFactory(
  private val taskRepository: TaskRepository,
  private val categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return TodayViewModel(
      taskRepository = taskRepository,
      categoryUseCases = categoryUseCases,
      taskUseCases = taskUseCases,
      localClock = localClock,
    ) as T
  }
}
