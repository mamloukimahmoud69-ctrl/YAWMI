package com.example.feature.tasks

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
import com.example.domain.task.TaskCategoryFilter
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskPriorityFilter
import com.example.domain.task.TaskQueryFilter
import com.example.domain.task.TaskQueryRules
import com.example.domain.task.TaskScheduleFilter
import com.example.domain.task.TaskSortOption
import com.example.domain.task.TaskStatusFilter
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TaskListItemUi(
  val task: TaskEntity,
  val category: CategoryEntity?,
  val isOverdue: Boolean,
)

data class TasksUiState(
  val isLoading: Boolean = true,
  val allTaskCount: Int = 0,
  val items: List<TaskListItemUi> = emptyList(),
  val categories: List<CategoryEntity> = emptyList(),
  val filter: TaskQueryFilter = TaskQueryFilter(),
  val todayDate: LocalDate = LocalDate.MIN,
)

sealed interface TasksUiEvent {
  data class DomainFailure(val error: TaskValidationError) : TasksUiEvent
}

private data class TasksTimeContext(
  val date: LocalDate,
  val zoneId: ZoneId,
)

class TasksViewModel(
  taskRepository: TaskRepository,
  categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
  private val midnightDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

  private val filterState = MutableStateFlow(TaskQueryFilter())
  private val timeContext = MutableStateFlow(readTimeContext())
  private var midnightJob: Job? = null

  private val _activeMidnightTimerCount = AtomicInteger(0)
  @VisibleForTesting
  internal val activeMidnightTimerCount: Int
    get() = _activeMidnightTimerCount.get()

  private val _events = MutableSharedFlow<TasksUiEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<TasksUiEvent> = _events.asSharedFlow()

  val uiState: StateFlow<TasksUiState> =
    combine(
        taskRepository.observeAllTasks(),
        categoryUseCases.observeActiveCategories(),
        filterState,
        timeContext,
      ) { tasks, categories, filter, currentTime ->
        val today = currentTime.date
        val categoriesById = categories.associateBy { it.id }
        val queriedTasks =
          TaskQueryRules.applyQuery(
            tasks = tasks,
            categoriesById = categoriesById,
            filter = filter,
            today = today,
          )
        TasksUiState(
          isLoading = false,
          allTaskCount = tasks.size,
          items =
            queriedTasks.map { task ->
              TaskListItemUi(
                task = task,
                category = categoriesById[task.categoryId],
                isOverdue = TaskQueryRules.isTaskOverdue(task, today),
              )
            },
          categories = categories,
          filter = filter,
          todayDate = today,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TasksUiState(isLoading = true, todayDate = timeContext.value.date),
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

  private fun readTimeContext(): TasksTimeContext =
    TasksTimeContext(
      date = localClock.currentDate(),
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

  fun onSearchQueryChange(query: String) {
    filterState.update { it.copy(searchQuery = query) }
  }

  fun onStatusFilterChange(statusFilter: TaskStatusFilter) {
    filterState.update { it.copy(statusFilter = statusFilter) }
  }

  fun onScheduleFilterChange(scheduleFilter: TaskScheduleFilter) {
    filterState.update { it.copy(scheduleFilter = scheduleFilter) }
  }

  fun onPriorityFilterChange(priorityFilter: TaskPriorityFilter) {
    filterState.update { it.copy(priorityFilter = priorityFilter) }
  }

  fun onCategoryFilterChange(categoryFilter: TaskCategoryFilter) {
    filterState.update { it.copy(categoryFilter = categoryFilter) }
  }

  fun onSortOptionChange(sortOption: TaskSortOption) {
    filterState.update { it.copy(sortOption = sortOption) }
  }

  fun clearFilters() {
    filterState.update { current ->
      TaskQueryFilter(sortOption = current.sortOption)
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
        _events.tryEmit(TasksUiEvent.DomainFailure(result.errors.first()))
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
        _events.tryEmit(TasksUiEvent.DomainFailure(result.errors.first()))
      }
    }
  }

  fun deleteTask(taskId: String) {
    viewModelScope.launch {
      taskUseCases.deleteTask(taskId)
    }
  }
}

class TasksViewModelFactory(
  private val taskRepository: TaskRepository,
  private val categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(TasksViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return TasksViewModel(
      taskRepository = taskRepository,
      categoryUseCases = categoryUseCases,
      taskUseCases = taskUseCases,
      localClock = localClock,
    ) as T
  }
}
