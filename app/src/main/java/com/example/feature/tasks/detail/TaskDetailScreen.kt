package com.example.feature.tasks.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.time.DatePickerLocalDateMapper
import com.example.core.time.LocalClock
import com.example.core.time.ObserveTodayTimeAndLifecycle
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskUseCases
import com.example.feature.tasks.components.CategoryManagerSheet
import com.example.feature.tasks.components.CategoryTokens
import com.example.feature.tasks.components.TaskEditorSheet
import com.example.feature.tasks.components.labelRes
import com.example.feature.tasks.components.toLocalizedMessage
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalYawmiSemanticColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
  taskId: String,
  taskRepository: TaskRepository,
  taskUseCases: TaskUseCases,
  categoryUseCases: CategoryUseCases,
  localClock: LocalClock,
  onNavigateBack: () -> Unit,
  onShowSnackbar: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val viewModel: TaskDetailViewModel =
    viewModel(
      key = "task_detail_$taskId",
      factory =
        TaskDetailViewModelFactory(
          taskId = taskId,
          taskRepository = taskRepository,
          categoryUseCases = categoryUseCases,
          taskUseCases = taskUseCases,
          localClock = localClock,
        ),
    )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val semantic = LocalYawmiSemanticColors.current
  val localSnackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  ObserveTodayTimeAndLifecycle(
    onStarted = viewModel::onForegroundStarted,
    onResumed = viewModel::onForegroundResumed,
    onStopped = viewModel::onForegroundStopped,
    onSystemTimeChanged = viewModel::onSystemTimeOrZoneChanged,
  )

  val emitFeedback: (String) -> Unit = { message ->
    if (onShowSnackbar != null) {
      onShowSnackbar(message)
    } else {
      scope.launch {
        localSnackbarHostState.currentSnackbarData?.dismiss()
        localSnackbarHostState.showSnackbar(message)
      }
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is TaskDetailUiEvent.ActionSucceeded -> emitFeedback(context.getString(event.messageRes))
        is TaskDetailUiEvent.PostponeSucceeded ->
          emitFeedback(context.getString(R.string.task_detail_msg_postponed, event.formattedDate))
        is TaskDetailUiEvent.ActionFailed -> emitFeedback(event.error.toLocalizedMessage(context))
        TaskDetailUiEvent.Deleted -> {
          emitFeedback(context.getString(R.string.task_detail_msg_deleted))
          onNavigateBack()
        }
      }
    }
  }

  var showEditor by remember { mutableStateOf(false) }
  var showCategoryManager by remember { mutableStateOf(false) }
  var showCancelDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showPostponeMenu by remember { mutableStateOf(false) }
  var showPostponeDatePicker by remember { mutableStateOf(false) }

  val loadedState = uiState as? TaskDetailUiState.Loaded

  if (showEditor && loadedState != null) {
    TaskEditorSheet(
      existingTask = loadedState.task,
      categories = loadedState.categories,
      taskUseCases = taskUseCases,
      localClock = localClock,
      onManageCategoriesClick = { showCategoryManager = true },
      onSaved = {
        showEditor = false
        emitFeedback(context.getString(R.string.task_detail_msg_updated))
      },
      onDismiss = { showEditor = false },
    )
  }

  if (showCategoryManager && loadedState != null) {
    CategoryManagerSheet(
      categories = loadedState.categories,
      categoryUseCases = categoryUseCases,
      onDismiss = { showCategoryManager = false },
    )
  }

  if (showCancelDialog && loadedState != null) {
    AlertDialog(
      onDismissRequest = { showCancelDialog = false },
      title = { Text(stringResource(R.string.task_cancel_dialog_title)) },
      text = {
        Text(stringResource(R.string.task_cancel_dialog_message, loadedState.task.title))
      },
      confirmButton = {
        Button(
          onClick = {
            showCancelDialog = false
            viewModel.cancelTask()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("task_detail_cancel_confirm_btn"),
        ) {
          Text(stringResource(R.string.task_cancel_confirm_action))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showCancelDialog = false },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("task_detail_cancel_dismiss_btn"),
        ) {
          Text(stringResource(R.string.action_close))
        }
      },
      modifier = Modifier.testTag("task_detail_cancel_dialog"),
    )
  }

  if (showDeleteDialog && loadedState != null) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(R.string.task_delete_dialog_title)) },
      text = {
        Text(stringResource(R.string.task_delete_dialog_message, loadedState.task.title))
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteDialog = false
            viewModel.deleteTask()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("task_detail_delete_confirm_btn"),
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteDialog = false },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("task_detail_delete_cancel_btn"),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
      modifier = Modifier.testTag("task_detail_delete_dialog"),
    )
  }

  if (showPostponeDatePicker && loadedState != null) {
    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis =
          DatePickerLocalDateMapper.initialPickerMillis(
            existingIsoDate = loadedState.task.scheduledDate,
            localClock = localClock,
          )
      )
    DatePickerDialog(
      onDismissRequest = { showPostponeDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val pickedDate = DatePickerLocalDateMapper.pickerMillisToLocalDate(millis)
              viewModel.postponeToDate(pickedDate)
            }
            showPostponeDatePicker = false
          },
          modifier = Modifier.testTag("task_detail_postpone_date_confirm_btn"),
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showPostponeDatePicker = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("screen_task_detail"),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = {
      if (onShowSnackbar == null) {
        SnackbarHost(hostState = localSnackbarHostState)
      }
    },
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.task_detail_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("task_detail_back_btn"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        actions = {
          if (loadedState != null) {
            IconButton(
              onClick = { showEditor = true },
              enabled = !loadedState.isMutating,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("task_detail_edit_btn"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.action_edit),
              )
            }
            IconButton(
              onClick = { showDeleteDialog = true },
              enabled = !loadedState.isMutating,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("task_detail_delete_btn"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      when (val state = uiState) {
        TaskDetailUiState.Loading -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(modifier = Modifier.testTag("task_detail_loading"))
          }
        }

        TaskDetailUiState.NotFound -> {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, semantic.borderSubtle),
            shape = MaterialTheme.shapes.large,
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(16.dp)
                .testTag("task_detail_not_found"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
              )
              Text(
                text = stringResource(R.string.task_detail_not_found_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.task_detail_not_found_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedButton(
                onClick = onNavigateBack,
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("task_detail_not_found_back_btn"),
              ) {
                Text(stringResource(R.string.action_back))
              }
            }
          }
        }

        is TaskDetailUiState.Loaded -> {
          val task = state.task
          val isCompleted = task.status == TaskStatus.COMPLETED
          val isCancelled = task.status == TaskStatus.CANCELLED
          val isTerminal = isCompleted || isCancelled

          Column(
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp)
                .testTag("task_detail_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // 1. Header & Status Card
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border =
                BorderStroke(
                  1.dp,
                  if (state.isOverdue) semantic.overdue.copy(alpha = 0.7f)
                  else semantic.borderSubtle,
                ),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.fillMaxWidth().testTag("task_detail_header_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  val statusColor =
                    when (task.status) {
                      TaskStatus.COMPLETED -> semantic.success
                      TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                      TaskStatus.PENDING -> MaterialTheme.colorScheme.secondary
                      TaskStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                  DetailBadge(
                    text = stringResource(task.status.labelRes()),
                    contentColor = statusColor,
                    containerColor = statusColor.copy(alpha = 0.14f),
                    modifier = Modifier.testTag("task_detail_status_badge"),
                  )

                  if (state.isOverdue) {
                    DetailBadge(
                      text = stringResource(R.string.task_badge_overdue),
                      contentColor = semantic.overdue,
                      containerColor = semantic.overdue.copy(alpha = 0.16f),
                      modifier = Modifier.testTag("task_detail_overdue_badge"),
                    )
                  }

                  val priorityColor =
                    when (task.priority) {
                      TaskPriority.HIGH -> semantic.overdue
                      TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                      TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                  DetailBadge(
                    text = stringResource(task.priority.labelRes()),
                    contentColor = priorityColor,
                    containerColor = priorityColor.copy(alpha = 0.14f),
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        tint = priorityColor,
                        modifier = Modifier.size(14.dp),
                      )
                    },
                    modifier = Modifier.testTag("task_detail_priority_badge"),
                  )

                  if (state.category != null) {
                    val catColor = CategoryTokens.resolveColor(state.category.colorToken)
                    DetailBadge(
                      text = state.category.name,
                      contentColor = MaterialTheme.colorScheme.onSurface,
                      containerColor = catColor.copy(alpha = 0.14f),
                      leadingIcon = {
                        Box(
                          modifier = Modifier.size(8.dp).clip(CircleShape).background(catColor)
                        )
                      },
                      modifier = Modifier.testTag("task_detail_category_badge"),
                    )
                  } else {
                    DetailBadge(
                      text = stringResource(R.string.category_none),
                      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      containerColor = MaterialTheme.colorScheme.surfaceVariant,
                      modifier = Modifier.testTag("task_detail_category_none_badge"),
                    )
                  }
                }

                Text(
                  text = task.title,
                  style = MaterialTheme.typography.headlineSmall,
                  color =
                    if (isTerminal) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                  textDecoration = if (isTerminal) TextDecoration.LineThrough else null,
                  modifier = Modifier.testTag("task_detail_title"),
                )

                if (isTerminal) {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("task_detail_terminal_hint"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint =
                          if (isCompleted) semantic.success
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                      )
                      Text(
                        text =
                          stringResource(
                            if (isCompleted) R.string.task_detail_terminal_hint_completed
                            else R.string.task_detail_terminal_hint_cancelled
                          ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }

            // 2. Schedule & Time Window Card
            TaskScheduleCard(
              task = task,
              todayDate = state.todayDate,
              isOverdue = state.isOverdue,
            )

            // 3. Description & Notes Card
            TaskDetailsContentCard(task = task)

            // 4. Timeline / Timestamps Card
            TaskTimestampsCard(task = task, zoneId = state.zoneId)

            // 5. Lifecycle Actions Card
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, semantic.borderSubtle),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.fillMaxWidth().testTag("task_detail_actions_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = stringResource(R.string.task_detail_section_actions),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )

                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  if (TaskStatus.IN_PROGRESS in state.allowedTransitions) {
                    OutlinedButton(
                      onClick = viewModel::startTask,
                      enabled = !state.isMutating,
                      modifier =
                        Modifier.minimumInteractiveComponentSize().testTag("task_detail_start_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.size(6.dp))
                      Text(stringResource(R.string.task_action_start))
                    }
                  }

                  if (TaskStatus.COMPLETED in state.allowedTransitions) {
                    Button(
                      onClick = viewModel::completeTask,
                      enabled = !state.isMutating,
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("task_detail_complete_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.size(6.dp))
                      Text(stringResource(R.string.task_action_complete))
                    }
                  }

                  if (state.canPostpone) {
                    val tomorrowDate = remember(state.todayDate) { state.todayDate.plusDays(1) }
                    val nextWeekDate = remember(state.todayDate) { state.todayDate.plusDays(7) }
                    Box {
                      OutlinedButton(
                        onClick = { showPostponeMenu = true },
                        enabled = !state.isMutating,
                        modifier =
                          Modifier.minimumInteractiveComponentSize()
                            .testTag("task_detail_postpone_btn"),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Update,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.task_action_postpone))
                      }
                      DropdownMenu(
                        expanded = showPostponeMenu,
                        onDismissRequest = { showPostponeMenu = false },
                      ) {
                        DropdownMenuItem(
                          text = { Text(stringResource(R.string.task_postpone_tomorrow)) },
                          onClick = {
                            showPostponeMenu = false
                            viewModel.postponeToDate(tomorrowDate)
                          },
                          modifier = Modifier.testTag("task_detail_postpone_tomorrow"),
                        )
                        DropdownMenuItem(
                          text = { Text(stringResource(R.string.task_postpone_next_week)) },
                          onClick = {
                            showPostponeMenu = false
                            viewModel.postponeToDate(nextWeekDate)
                          },
                          modifier = Modifier.testTag("task_detail_postpone_next_week"),
                        )
                        DropdownMenuItem(
                          text = { Text(stringResource(R.string.task_postpone_pick_date)) },
                          onClick = {
                            showPostponeMenu = false
                            showPostponeDatePicker = true
                          },
                          modifier = Modifier.testTag("task_detail_postpone_pick_date"),
                        )
                      }
                    }
                  }

                  if (TaskStatus.CANCELLED in state.allowedTransitions) {
                    OutlinedButton(
                      onClick = { showCancelDialog = true },
                      enabled = !state.isMutating,
                      colors =
                        ButtonDefaults.outlinedButtonColors(
                          contentColor = MaterialTheme.colorScheme.error
                        ),
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("task_detail_cancel_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.size(6.dp))
                      Text(stringResource(R.string.task_action_cancel_task))
                    }
                  }

                  if (state.canReopen) {
                    Button(
                      onClick = viewModel::reopenTask,
                      enabled = !state.isMutating,
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("task_detail_reopen_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.size(6.dp))
                      Text(stringResource(R.string.task_action_reopen))
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(72.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun TaskScheduleCard(
  task: TaskEntity,
  todayDate: LocalDate,
  isOverdue: Boolean,
) {
  val semantic = LocalYawmiSemanticColors.current
  val todayStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
  val tomorrowStr =
    remember(todayDate) { todayDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) }

  val dateDisplay =
    when (task.scheduledDate) {
      null -> stringResource(R.string.task_schedule_unscheduled)
      todayStr -> "${stringResource(R.string.task_schedule_today)} (${task.scheduledDate})"
      tomorrowStr -> "${stringResource(R.string.task_schedule_tomorrow)} (${task.scheduledDate})"
      else -> task.scheduledDate
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().testTag("task_detail_schedule_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.task_detail_section_schedule),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      DetailInfoRow(
        icon = Icons.Outlined.CalendarToday,
        label = stringResource(R.string.task_detail_label_date),
        value = dateDisplay,
        valueColor = if (isOverdue) semantic.overdue else MaterialTheme.colorScheme.onSurface,
        useMonoFont = task.scheduledDate != null,
        valueTag = "task_detail_date_value",
      )

      if (task.startTime != null || task.endTime != null) {
        val timeDisplay =
          when {
            task.startTime != null && task.endTime != null -> "${task.startTime} – ${task.endTime}"
            task.startTime != null -> task.startTime
            else -> task.endTime.orEmpty()
          }
        DetailInfoRow(
          icon = Icons.Outlined.Schedule,
          label = stringResource(R.string.task_detail_label_time),
          value = timeDisplay,
          useMonoFont = true,
          valueTag = "task_detail_time_value",
        )
      } else {
        DetailInfoRow(
          icon = Icons.Outlined.Schedule,
          label = stringResource(R.string.task_detail_label_time),
          value = stringResource(R.string.task_detail_label_time_none),
          valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
          valueTag = "task_detail_time_value",
        )
      }
    }
  }
}

@Composable
private fun TaskDetailsContentCard(task: TaskEntity) {
  val semantic = LocalYawmiSemanticColors.current

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().testTag("task_detail_details_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.task_detail_section_details),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (task.description == null && task.notes == null) {
        Text(
          text = stringResource(R.string.task_detail_empty_details),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("task_detail_empty_details"),
        )
      } else {
        task.description?.let { desc ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Outlined.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Text(
              text = desc,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("task_detail_description_value"),
            )
          }
        }

        if (task.description != null && task.notes != null) {
          HorizontalDivider(color = semantic.borderSubtle)
        }

        task.notes?.let { notes ->
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.task_detail_section_notes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = notes,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("task_detail_notes_value"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TaskTimestampsCard(task: TaskEntity, zoneId: ZoneId) {
  val semantic = LocalYawmiSemanticColors.current
  val configuration = LocalConfiguration.current
  val locale =
    remember(configuration) {
      if (!configuration.locales.isEmpty) configuration.locales[0] else Locale.getDefault()
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().testTag("task_detail_timestamps_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.task_detail_section_timeline),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      DetailInfoRow(
        icon = Icons.Outlined.History,
        label = stringResource(R.string.task_detail_label_created_at),
        value = formatEpochTimestamp(task.createdAtEpochMs, locale, zoneId),
        useMonoFont = true,
        valueTag = "task_detail_created_at",
      )

      DetailInfoRow(
        icon = Icons.Outlined.Update,
        label = stringResource(R.string.task_detail_label_updated_at),
        value = formatEpochTimestamp(task.updatedAtEpochMs, locale, zoneId),
        useMonoFont = true,
        valueTag = "task_detail_updated_at",
      )

      task.completedAtEpochMs?.let { completedMs ->
        DetailInfoRow(
          icon = Icons.Outlined.EventAvailable,
          label = stringResource(R.string.task_detail_label_completed_at),
          value = formatEpochTimestamp(completedMs, locale, zoneId),
          valueColor = semantic.success,
          useMonoFont = true,
          valueTag = "task_detail_completed_at",
        )
      }
    }
  }
}

@Composable
private fun DetailInfoRow(
  icon: ImageVector,
  label: String,
  value: String,
  valueTag: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
  useMonoFont: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.size(12.dp))
    Text(
      text = value,
      style =
        if (useMonoFont) {
          MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
        } else {
          MaterialTheme.typography.bodyMedium
        },
      color = valueColor,
      modifier = Modifier.testTag(valueTag),
    )
  }
}

@Composable
private fun DetailBadge(
  text: String,
  contentColor: Color,
  containerColor: Color,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
) {
  Surface(
    color = containerColor,
    shape = MaterialTheme.shapes.small,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      leadingIcon?.invoke()
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
      )
    }
  }
}

private fun formatEpochTimestamp(
  epochMs: Long,
  locale: Locale,
  zoneId: ZoneId,
): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd · HH:mm", locale)
  return Instant.ofEpochMilli(epochMs).atZone(zoneId).format(formatter)
}
