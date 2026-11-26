package com.example.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.time.LocalClock
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskCategoryFilter
import com.example.domain.task.TaskPriorityFilter
import com.example.domain.task.TaskQueryFilter
import com.example.domain.task.TaskScheduleFilter
import com.example.domain.task.TaskSortOption
import com.example.domain.task.TaskStatusFilter
import com.example.domain.task.TaskUseCases
import com.example.feature.tasks.components.CategoryManagerSheet
import com.example.feature.tasks.components.CategoryTokens
import com.example.feature.tasks.components.TaskEditorSheet
import com.example.feature.tasks.components.TaskListItemCard
import com.example.feature.tasks.components.toLocalizedMessage
import com.example.core.time.DatePickerLocalDateMapper
import com.example.core.time.ObserveTodayTimeAndLifecycle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalYawmiSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
  taskRepository: TaskRepository,
  taskUseCases: TaskUseCases,
  categoryUseCases: CategoryUseCases,
  localClock: LocalClock,
  onShowSnackbar: (String) -> Unit = {},
  modifier: Modifier = Modifier,
  onOpenTaskDetail: ((String) -> Unit)? = null,
) {
  val viewModel: TasksViewModel =
    viewModel(
      factory =
        TasksViewModelFactory(
          taskRepository = taskRepository,
          categoryUseCases = categoryUseCases,
          taskUseCases = taskUseCases,
          localClock = localClock,
        )
    )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val semantic = LocalYawmiSemanticColors.current

  ObserveTodayTimeAndLifecycle(
    onStarted = viewModel::onForegroundStarted,
    onResumed = viewModel::onForegroundResumed,
    onStopped = viewModel::onForegroundStopped,
    onSystemTimeChanged = viewModel::onSystemTimeOrZoneChanged,
  )

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is TasksUiEvent.DomainFailure ->
          onShowSnackbar(event.error.toLocalizedMessage(context))
      }
    }
  }

  var showEditor by remember { mutableStateOf(false) }
  var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
  var showCategoryManager by remember { mutableStateOf(false) }
  var taskPendingDelete by remember { mutableStateOf<TaskEntity?>(null) }
  var taskPendingPostponeDate by remember { mutableStateOf<TaskEntity?>(null) }
  var showSortMenu by remember { mutableStateOf(false) }
  var filtersExpanded by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  LaunchedEffect(uiState.filter) {
    listState.scrollToItem(0)
  }

  if (showEditor) {
    TaskEditorSheet(
      existingTask = editingTask,
      categories = uiState.categories,
      taskUseCases = taskUseCases,
      localClock = localClock,
      onManageCategoriesClick = { showCategoryManager = true },
      onSaved = {
        showEditor = false
        editingTask = null
      },
      onDismiss = {
        showEditor = false
        editingTask = null
      },
    )
  }

  if (showCategoryManager) {
    CategoryManagerSheet(
      categories = uiState.categories,
      categoryUseCases = categoryUseCases,
      onDismiss = { showCategoryManager = false },
    )
  }

  taskPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { taskPendingDelete = null },
      title = { Text(stringResource(R.string.task_delete_dialog_title)) },
      text = {
        Text(stringResource(R.string.task_delete_dialog_message, target.title))
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteTask(target.id)
            taskPendingDelete = null
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_delete_confirm_btn"),
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { taskPendingDelete = null },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_delete_cancel_btn"),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
      modifier = Modifier.testTag("task_delete_confirm_dialog"),
    )
  }

  taskPendingPostponeDate?.let { target ->
    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis =
          DatePickerLocalDateMapper.initialPickerMillis(
            existingIsoDate = target.scheduledDate,
            localClock = localClock,
          )
      )
    DatePickerDialog(
      onDismissRequest = { taskPendingPostponeDate = null },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val pickedDate = DatePickerLocalDateMapper.pickerMillisToLocalDate(millis)
              viewModel.postponeToDate(taskId = target.id, targetDate = pickedDate)
            }
            taskPendingPostponeDate = null
          },
          modifier = Modifier.testTag("task_postpone_date_confirm_btn"),
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { taskPendingPostponeDate = null }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  Box(modifier = modifier.fillMaxSize().testTag("screen_tasks")) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.tasks_title),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = stringResource(R.string.tasks_count_label, uiState.items.size),
                style =
                  MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier =
                  Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("tasks_count_text"),
              )
            }
          }

          TextButton(
            onClick = { showCategoryManager = true },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("tasks_manage_categories_btn"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Settings,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(stringResource(R.string.category_manage))
          }
        }

        OutlinedTextField(
          value = uiState.filter.searchQuery,
          onValueChange = viewModel::onSearchQueryChange,
          placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
          leadingIcon = {
            Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
          },
          trailingIcon = {
            if (uiState.filter.searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { viewModel.onSearchQueryChange("") },
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("tasks_search_clear_btn"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Clear,
                  contentDescription = stringResource(R.string.action_clear),
                )
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().testTag("tasks_search_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box {
            OutlinedButton(
              onClick = { showSortMenu = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("tasks_sort_btn"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(6.dp))
              Text(
                text =
                  "${stringResource(R.string.tasks_sort_label)}: ${
                    stringResource(uiState.filter.sortOption.labelRes())
                  }"
              )
            }
            DropdownMenu(
              expanded = showSortMenu,
              onDismissRequest = { showSortMenu = false },
            ) {
              SortMenuItem(
                label = stringResource(R.string.tasks_sort_schedule),
                selected = uiState.filter.sortOption == TaskSortOption.SCHEDULE_ASC,
                tag = "tasks_sort_schedule",
                onClick = {
                  showSortMenu = false
                  viewModel.onSortOptionChange(TaskSortOption.SCHEDULE_ASC)
                },
              )
              SortMenuItem(
                label = stringResource(R.string.tasks_sort_priority),
                selected = uiState.filter.sortOption == TaskSortOption.PRIORITY_DESC,
                tag = "tasks_sort_priority",
                onClick = {
                  showSortMenu = false
                  viewModel.onSortOptionChange(TaskSortOption.PRIORITY_DESC)
                },
              )
              SortMenuItem(
                label = stringResource(R.string.tasks_sort_newest),
                selected = uiState.filter.sortOption == TaskSortOption.NEWEST_FIRST,
                tag = "tasks_sort_newest",
                onClick = {
                  showSortMenu = false
                  viewModel.onSortOptionChange(TaskSortOption.NEWEST_FIRST)
                },
              )
              SortMenuItem(
                label = stringResource(R.string.tasks_sort_title),
                selected = uiState.filter.sortOption == TaskSortOption.TITLE_ASC,
                tag = "tasks_sort_title",
                onClick = {
                  showSortMenu = false
                  viewModel.onSortOptionChange(TaskSortOption.TITLE_ASC)
                },
              )
            }
          }

          FilterChip(
            selected = filtersExpanded || uiState.filter.hasActiveFilters,
            onClick = { filtersExpanded = !filtersExpanded },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            label = { Text(stringResource(R.string.tasks_filters_toggle)) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("tasks_toggle_filters_btn"),
          )

          Spacer(modifier = Modifier.weight(1f))

          if (uiState.filter.hasActiveFilters) {
            TextButton(
              onClick = viewModel::clearFilters,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("tasks_clear_filters_btn"),
            ) {
              Icon(
                imageVector = Icons.Outlined.FilterListOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(4.dp))
              Text(stringResource(R.string.tasks_filter_clear))
            }
          }
        }
      }

      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("tasks_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item(key = "filters_panel") {
          AnimatedVisibility(visible = filtersExpanded) {
            TasksFilterPanel(
              filter = uiState.filter,
              categories = uiState.categories,
              onStatusFilterChange = viewModel::onStatusFilterChange,
              onScheduleFilterChange = viewModel::onScheduleFilterChange,
              onPriorityFilterChange = viewModel::onPriorityFilterChange,
              onCategoryFilterChange = viewModel::onCategoryFilterChange,
              modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).testTag("filters_panel"),
            )
          }
        }

        if (!uiState.isLoading && uiState.allTaskCount == 0) {
          item(key = "empty_state") {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, semantic.borderSubtle),
              shape = MaterialTheme.shapes.large,
              modifier =
                Modifier.fillMaxWidth().widthIn(max = 760.dp).testTag("tasks_empty_state"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(40.dp),
                )
                Text(
                  text = stringResource(R.string.tasks_empty_title),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = stringResource(R.string.tasks_empty_desc),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                  onClick = {
                    editingTask = null
                    showEditor = true
                  },
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("tasks_empty_add_btn"),
                ) {
                  Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(stringResource(R.string.action_add))
                }
              }
            }
          }
        } else if (!uiState.isLoading && uiState.items.isEmpty()) {
          item(key = "filtered_empty_state") {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, semantic.borderSubtle),
              shape = MaterialTheme.shapes.large,
              modifier =
                Modifier.fillMaxWidth()
                  .widthIn(max = 760.dp)
                  .testTag("tasks_filtered_empty_state"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp),
                )
                Text(
                  text = stringResource(R.string.tasks_filtered_empty_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = stringResource(R.string.tasks_filtered_empty_desc),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                  onClick = viewModel::clearFilters,
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("tasks_empty_clear_filters_btn"),
                ) {
                  Text(stringResource(R.string.tasks_filter_clear))
                }
              }
            }
          }
        } else if (uiState.filter.sortOption == TaskSortOption.SCHEDULE_ASC) {
          val scheduledItems = uiState.items.filter { it.task.scheduledDate != null }
          val unscheduledItems = uiState.items.filter { it.task.scheduledDate == null }

          if (scheduledItems.isNotEmpty()) {
            item(key = "section_scheduled") {
              SectionLabel(
                text = stringResource(R.string.tasks_section_scheduled),
                modifier =
                  Modifier.fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("tasks_section_scheduled"),
              )
            }
            items(scheduledItems, key = { it.task.id }) { item ->
              TaskListItemCard(
                item = item,
                todayDate = uiState.todayDate,
                onToggleComplete = viewModel::completeOrReopenTask,
                onPostponeToDate = { t, date -> viewModel.postponeToDate(t.id, date) },
                onPostponePickDate = { t -> taskPendingPostponeDate = t },
                onEditClick = { t ->
                  editingTask = t
                  showEditor = true
                },
                onTaskClick = { t ->
                  onOpenTaskDetail?.invoke(t.id) ?: run {
                    editingTask = t
                    showEditor = true
                  }
                },
                onDeleteClick = { t -> taskPendingDelete = t },
                modifier = Modifier.widthIn(max = 760.dp),
              )
            }
          }

          if (unscheduledItems.isNotEmpty()) {
            item(key = "section_unscheduled") {
              SectionLabel(
                text = stringResource(R.string.tasks_section_unscheduled),
                modifier =
                  Modifier.fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("tasks_section_unscheduled"),
              )
            }
            items(unscheduledItems, key = { it.task.id }) { item ->
              TaskListItemCard(
                item = item,
                todayDate = uiState.todayDate,
                onToggleComplete = viewModel::completeOrReopenTask,
                onPostponeToDate = { t, date -> viewModel.postponeToDate(t.id, date) },
                onPostponePickDate = { t -> taskPendingPostponeDate = t },
                onEditClick = { t ->
                  editingTask = t
                  showEditor = true
                },
                onTaskClick = { t ->
                  onOpenTaskDetail?.invoke(t.id) ?: run {
                    editingTask = t
                    showEditor = true
                  }
                },
                onDeleteClick = { t -> taskPendingDelete = t },
                modifier = Modifier.widthIn(max = 760.dp),
              )
            }
          }
        } else {
          items(uiState.items, key = { it.task.id }) { item ->
            TaskListItemCard(
              item = item,
              todayDate = uiState.todayDate,
              onToggleComplete = viewModel::completeOrReopenTask,
              onPostponeToDate = { t, date -> viewModel.postponeToDate(t.id, date) },
              onPostponePickDate = { t -> taskPendingPostponeDate = t },
              onEditClick = { t ->
                editingTask = t
                showEditor = true
              },
              onTaskClick = { t ->
                onOpenTaskDetail?.invoke(t.id) ?: run {
                  editingTask = t
                  showEditor = true
                }
              },
              onDeleteClick = { t -> taskPendingDelete = t },
              modifier = Modifier.widthIn(max = 760.dp),
            )
          }
        }
      }
    }

    FloatingActionButton(
      onClick = {
        editingTask = null
        showEditor = true
      },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("tasks_add_fab"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.fab_quick_add_desc),
      )
    }
  }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
  )
}

@Composable
private fun SortMenuItem(
  label: String,
  selected: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  DropdownMenuItem(
    text = {
      Text(
        text = label,
        color =
          if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      )
    },
    onClick = onClick,
    modifier = Modifier.testTag(tag),
  )
}

@Composable
private fun TasksFilterPanel(
  filter: TaskQueryFilter,
  categories: List<CategoryEntity>,
  onStatusFilterChange: (TaskStatusFilter) -> Unit,
  onScheduleFilterChange: (TaskScheduleFilter) -> Unit,
  onPriorityFilterChange: (TaskPriorityFilter) -> Unit,
  onCategoryFilterChange: (TaskCategoryFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Status filters
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .testTag("tasks_filter_status_row"),
    ) {
      val statusOptions =
        listOf(
          Triple(TaskStatusFilter.ALL, R.string.tasks_filter_status_all, "tasks_filter_status_all"),
          Triple(
            TaskStatusFilter.ACTIVE,
            R.string.tasks_filter_status_active,
            "tasks_filter_status_active",
          ),
          Triple(
            TaskStatusFilter.PENDING,
            R.string.tasks_filter_status_pending,
            "tasks_filter_status_pending",
          ),
          Triple(
            TaskStatusFilter.IN_PROGRESS,
            R.string.tasks_filter_status_in_progress,
            "tasks_filter_status_in_progress",
          ),
          Triple(
            TaskStatusFilter.COMPLETED,
            R.string.tasks_filter_status_completed,
            "tasks_filter_status_completed",
          ),
          Triple(
            TaskStatusFilter.CANCELLED,
            R.string.tasks_filter_status_cancelled,
            "tasks_filter_status_cancelled",
          ),
        )
      statusOptions.forEach { (option, labelRes, tag) ->
        YawmiFilterChip(
          selected = filter.statusFilter == option,
          label = stringResource(labelRes),
          tag = tag,
          onClick = { onStatusFilterChange(option) },
        )
      }
    }

    // Schedule filters
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .testTag("tasks_filter_schedule_row"),
    ) {
      val scheduleOptions =
        listOf(
          Triple(
            TaskScheduleFilter.ALL,
            R.string.tasks_filter_schedule_all,
            "tasks_filter_schedule_all",
          ),
          Triple(
            TaskScheduleFilter.TODAY,
            R.string.tasks_filter_schedule_today,
            "tasks_filter_schedule_today",
          ),
          Triple(
            TaskScheduleFilter.UPCOMING,
            R.string.tasks_filter_schedule_upcoming,
            "tasks_filter_schedule_upcoming",
          ),
          Triple(
            TaskScheduleFilter.OVERDUE,
            R.string.tasks_filter_schedule_overdue,
            "tasks_filter_schedule_overdue",
          ),
          Triple(
            TaskScheduleFilter.UNSCHEDULED,
            R.string.tasks_filter_schedule_unscheduled,
            "tasks_filter_schedule_unscheduled",
          ),
        )
      scheduleOptions.forEach { (option, labelRes, tag) ->
        YawmiFilterChip(
          selected = filter.scheduleFilter == option,
          label = stringResource(labelRes),
          tag = tag,
          onClick = { onScheduleFilterChange(option) },
        )
      }
    }

    // Priority & Category filters
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .testTag("tasks_filter_priority_category_row"),
    ) {
      val priorityOptions =
        listOf(
          Triple(
            TaskPriorityFilter.ALL,
            R.string.tasks_filter_priority_all,
            "tasks_filter_priority_all",
          ),
          Triple(TaskPriorityFilter.HIGH, R.string.priority_high, "tasks_filter_priority_high"),
          Triple(
            TaskPriorityFilter.MEDIUM,
            R.string.priority_medium,
            "tasks_filter_priority_medium",
          ),
          Triple(TaskPriorityFilter.LOW, R.string.priority_low, "tasks_filter_priority_low"),
        )
      priorityOptions.forEach { (option, labelRes, tag) ->
        YawmiFilterChip(
          selected = filter.priorityFilter == option,
          label = stringResource(labelRes),
          tag = tag,
          onClick = { onPriorityFilterChange(option) },
        )
      }

      YawmiFilterChip(
        selected = filter.categoryFilter == TaskCategoryFilter.All,
        label = stringResource(R.string.tasks_filter_category_all),
        tag = "tasks_filter_category_all",
        onClick = { onCategoryFilterChange(TaskCategoryFilter.All) },
      )
      YawmiFilterChip(
        selected = filter.categoryFilter == TaskCategoryFilter.Uncategorized,
        label = stringResource(R.string.tasks_filter_category_none),
        tag = "tasks_filter_category_none",
        onClick = { onCategoryFilterChange(TaskCategoryFilter.Uncategorized) },
      )
      categories.forEach { cat ->
        val dotColor = CategoryTokens.resolveColor(cat.colorToken)
        FilterChip(
          selected = filter.categoryFilter == TaskCategoryFilter.ByCategory(cat.id),
          onClick = { onCategoryFilterChange(TaskCategoryFilter.ByCategory(cat.id)) },
          leadingIcon = {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
          },
          label = { Text(cat.name) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("tasks_filter_category_${cat.id}"),
        )
      }
    }
  }
}

@Composable
private fun YawmiFilterChip(
  selected: Boolean,
  label: String,
  tag: String,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ),
    modifier = Modifier.minimumInteractiveComponentSize().testTag(tag),
  )
}

private fun TaskSortOption.labelRes(): Int =
  when (this) {
    TaskSortOption.SCHEDULE_ASC -> R.string.tasks_sort_schedule
    TaskSortOption.PRIORITY_DESC -> R.string.tasks_sort_priority
    TaskSortOption.NEWEST_FIRST -> R.string.tasks_sort_newest
    TaskSortOption.TITLE_ASC -> R.string.tasks_sort_title
  }
