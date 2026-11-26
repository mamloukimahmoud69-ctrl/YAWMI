package com.example.feature.tasks.detail

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.time.LocalClock
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskQueryRules
import com.example.domain.task.TaskRules
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

sealed interface TaskDetailUiState {
  data object Loading : TaskDetailUiState

  data object NotFound : TaskDetailUiState

  data class Loaded(
    val task: TaskEntity,
    val category: CategoryEntity?,
    val categories: List<CategoryEntity>,
    val isOverdue: Boolean,
    val allowedTransitions: Set<TaskStatus>,
    val canReopen: Boolean,
    val canPostpone: Boolean,
    val todayDate: LocalDate,
    val zoneId: ZoneId,
    val isMutating: Boolean,
  ) : TaskDetailUiState
}

sealed interface TaskDetailUiEvent {
  data class ActionSucceeded(@param:StringRes val messageRes: Int) : TaskDetailUiEvent

  data class PostponeSucceeded(val formattedDate: String) : TaskDetailUiEvent

  data class ActionFailed(val error: TaskValidationError) : TaskDetailUiEvent

  data object Deleted : TaskDetailUiEvent
}

private data class TaskDetailTimeContext(
  val date: LocalDate,
  val zoneId: ZoneId,
)

class TaskDetailViewModel(
  private val taskId: String,
  taskRepository: TaskRepository,
  categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
  private val midnightDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

  private val mutatingState = MutableStateFlow(false)
  private val timeContext = MutableStateFlow(readTimeContext())
  private var midnightJob: Job? = null

  private val _activeMidnightTimerCount = AtomicInteger(0)
  @VisibleForTesting
  internal val activeMidnightTimerCount: Int
    get() = _activeMidnightTimerCount.get()

  private val _events = MutableSharedFlow<TaskDetailUiEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<TaskDetailUiEvent> = _events.asSharedFlow()

  val uiState: StateFlow<TaskDetailUiState> =
    combine(
        taskRepository.observeTaskById(taskId),
        categoryUseCases.observeActiveCategories(),
        mutatingState,
        timeContext,
      ) { task, categories, isMutating, currentTime ->
        if (task == null) {
          TaskDetailUiState.NotFound
        } else {
          val today = currentTime.date
          val category = task.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
          TaskDetailUiState.Loaded(
            task = task,
            category = category,
            categories = categories,
            isOverdue = TaskQueryRules.isTaskOverdue(task, today),
            allowedTransitions = TaskRules.allowedNextStatuses(task.status),
            canReopen = TaskRules.canReopen(task.status),
            canPostpone =
              task.status == TaskStatus.PENDING || task.status == TaskStatus.IN_PROGRESS,
            todayDate = today,
            zoneId = currentTime.zoneId,
            isMutating = isMutating,
          )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TaskDetailUiState.Loading,
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

  private fun readTimeContext(): TaskDetailTimeContext =
    TaskDetailTimeContext(
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

  fun startTask() {
    executeTransition(successMessageRes = R.string.task_detail_msg_started) {
      taskUseCases.startTask(taskId)
    }
  }

  fun completeTask() {
    executeTransition(successMessageRes = R.string.task_detail_msg_completed) {
      taskUseCases.completeTask(taskId)
    }
  }

  fun cancelTask() {
    executeTransition(successMessageRes = R.string.task_detail_msg_cancelled) {
      taskUseCases.cancelTask(taskId)
    }
  }

  fun reopenTask() {
    executeTransition(successMessageRes = R.string.task_detail_msg_reopened) {
      taskUseCases.reopenTask(taskId)
    }
  }

  fun postponeToDate(targetDate: LocalDate, clearTimeWindow: Boolean = false) {
    if (mutatingState.value) return
    viewModelScope.launch {
      mutatingState.value = true
      try {
        when (
          val result =
            taskUseCases.postponeTask(
              taskId = taskId,
              targetDate = targetDate,
              clearTimeWindow = clearTimeWindow,
            )
        ) {
          is TaskDomainResult.Success -> {
            _events.tryEmit(
              TaskDetailUiEvent.PostponeSucceeded(
                targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
              )
            )
          }
          is TaskDomainResult.Failure -> {
            _events.tryEmit(TaskDetailUiEvent.ActionFailed(result.errors.first()))
          }
        }
      } finally {
        mutatingState.value = false
      }
    }
  }

  fun deleteTask() {
    if (mutatingState.value) return
    viewModelScope.launch {
      mutatingState.value = true
      try {
        val deleted = taskUseCases.deleteTask(taskId)
        if (deleted) {
          _events.tryEmit(TaskDetailUiEvent.Deleted)
        } else {
          _events.tryEmit(
            TaskDetailUiEvent.ActionFailed(TaskValidationError.TaskNotFound(taskId))
          )
        }
      } finally {
        mutatingState.value = false
      }
    }
  }

  private fun executeTransition(
    @StringRes successMessageRes: Int,
    action: suspend () -> TaskDomainResult<TaskEntity>,
  ) {
    if (mutatingState.value) return
    viewModelScope.launch {
      mutatingState.value = true
      try {
        when (val result = action()) {
          is TaskDomainResult.Success ->
            _events.tryEmit(TaskDetailUiEvent.ActionSucceeded(successMessageRes))
          is TaskDomainResult.Failure ->
            _events.tryEmit(TaskDetailUiEvent.ActionFailed(result.errors.first()))
        }
      } finally {
        mutatingState.value = false
      }
    }
  }
}

class TaskDetailViewModelFactory(
  private val taskId: String,
  private val taskRepository: TaskRepository,
  private val categoryUseCases: CategoryUseCases,
  private val taskUseCases: TaskUseCases,
  private val localClock: LocalClock,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(TaskDetailViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return TaskDetailViewModel(
      taskId = taskId,
      taskRepository = taskRepository,
      categoryUseCases = categoryUseCases,
      taskUseCases = taskUseCases,
      localClock = localClock,
    ) as T
  }
}
