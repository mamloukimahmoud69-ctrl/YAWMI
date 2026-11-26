package com.example.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.preferences.AppLanguage
import com.example.core.time.DatePickerLocalDateMapper
import com.example.core.time.LocalClock
import com.example.core.time.ObserveTodayTimeAndLifecycle
import com.example.core.time.formatLocalizedHeader
import com.example.data.local.entity.TaskEntity
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.CalendarDaySummary
import com.example.domain.task.CalendarDayTaskItem
import com.example.domain.task.CalendarProjection
import com.example.domain.task.CalendarViewMode
import com.example.domain.task.TaskUseCases
import com.example.feature.tasks.TaskListItemUi
import com.example.feature.tasks.components.CategoryManagerSheet
import com.example.feature.tasks.components.TaskEditorSheet
import com.example.feature.tasks.components.TaskListItemCard
import com.example.feature.tasks.components.toLocalizedMessage
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalYawmiSemanticColors
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
  language: AppLanguage,
  taskRepository: TaskRepository,
  taskUseCases: TaskUseCases,
  categoryUseCases: CategoryUseCases,
  localClock: LocalClock,
  onOpenTaskDetail: (String) -> Unit,
  onShowSnackbar: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val firstDayOfWeek =
    remember(language) {
      if (language == AppLanguage.ARABIC) DayOfWeek.SATURDAY else DayOfWeek.SUNDAY
    }

  val viewModel: CalendarViewModel =
    viewModel(
      factory =
        CalendarViewModelFactory(
          taskRepository = taskRepository,
          categoryUseCases = categoryUseCases,
          taskUseCases = taskUseCases,
          localClock = localClock,
          firstDayOfWeek = firstDayOfWeek,
        )
    )

  LaunchedEffect(firstDayOfWeek) {
    viewModel.setFirstDayOfWeek(firstDayOfWeek)
  }

  ObserveTodayTimeAndLifecycle(
    onStarted = viewModel::onForegroundStarted,
    onResumed = viewModel::onForegroundResumed,
    onStopped = viewModel::onForegroundStopped,
    onSystemTimeChanged = viewModel::onSystemTimeOrZoneChanged,
  )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val semantic = LocalYawmiSemanticColors.current

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is CalendarUiEvent.DomainFailure ->
          onShowSnackbar(event.error.toLocalizedMessage(context))
      }
    }
  }

  var showEditor by remember { mutableStateOf(false) }
  var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
  var showCategoryManager by remember { mutableStateOf(false) }
  var taskPendingDelete by remember { mutableStateOf<TaskEntity?>(null) }
  var taskPendingPostponeDate by remember { mutableStateOf<TaskEntity?>(null) }

  val projection = uiState.projection
  val locale = remember(language) { Locale.forLanguageTag(language.tag) }

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
      initialScheduledDate =
        if (editingTask == null) projection.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        else null,
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
      text = { Text(stringResource(R.string.task_delete_dialog_message, target.title)) },
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
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("calendar_delete_confirm_btn"),
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { taskPendingDelete = null },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("calendar_delete_cancel_btn"),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
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
          modifier = Modifier.testTag("calendar_postpone_date_confirm_btn"),
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

  BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("screen_calendar")) {
    val isExpanded = maxWidth >= 840.dp

    if (isExpanded) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CalendarTopControls(
            projection = projection,
            locale = locale,
            onSelectMode = viewModel::setViewMode,
            onPrevious = viewModel::navigatePrevious,
            onNext = viewModel::navigateNext,
            onJumpToToday = viewModel::jumpToToday,
          )
          CalendarViewSurface(
            projection = projection,
            locale = locale,
            onSelectDate = viewModel::selectDate,
          )
        }

        LazyColumn(
          modifier = Modifier.weight(1.1f).fillMaxHeight().testTag("calendar_agenda_list"),
          contentPadding = PaddingValues(bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item(key = "agenda_header") {
            CalendarAgendaHeader(
              projection = projection,
              language = language,
              onAddForSelectedDate = {
                editingTask = null
                showEditor = true
              },
            )
          }
          if (projection.selectedDayTasks.isEmpty()) {
            item(key = "agenda_empty") {
              CalendarAgendaEmptyCard(
                selectedDateFormatted = projection.selectedDate.formatLocalizedHeader(language),
                onAddClick = {
                  editingTask = null
                  showEditor = true
                },
              )
            }
          } else {
            items(projection.selectedDayTasks, key = { it.task.id }) { item ->
              CalendarAgendaTaskCard(
                item = item,
                todayDate = projection.todayDate,
                onToggleComplete = viewModel::completeOrReopenTask,
                onPostponeToDate = { t, date -> viewModel.postponeToDate(t.id, date) },
                onPostponePickDate = { t -> taskPendingPostponeDate = t },
                onEditClick = { t ->
                  editingTask = t
                  showEditor = true
                },
                onOpenTaskDetail = onOpenTaskDetail,
                onDeleteClick = { t -> taskPendingDelete = t },
              )
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("calendar_scroll_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item(key = "calendar_controls") {
          CalendarTopControls(
            projection = projection,
            locale = locale,
            onSelectMode = viewModel::setViewMode,
            onPrevious = viewModel::navigatePrevious,
            onNext = viewModel::navigateNext,
            onJumpToToday = viewModel::jumpToToday,
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
          )
        }

        item(key = "calendar_surface") {
          CalendarViewSurface(
            projection = projection,
            locale = locale,
            onSelectDate = viewModel::selectDate,
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
          )
        }

        item(key = "agenda_header") {
          CalendarAgendaHeader(
            projection = projection,
            language = language,
            onAddForSelectedDate = {
              editingTask = null
              showEditor = true
            },
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
          )
        }

        if (projection.selectedDayTasks.isEmpty()) {
          item(key = "agenda_empty") {
            CalendarAgendaEmptyCard(
              selectedDateFormatted = projection.selectedDate.formatLocalizedHeader(language),
              onAddClick = {
                editingTask = null
                showEditor = true
              },
              modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            )
          }
        } else {
          items(projection.selectedDayTasks, key = { it.task.id }) { item ->
            CalendarAgendaTaskCard(
              item = item,
              todayDate = projection.todayDate,
              onToggleComplete = viewModel::completeOrReopenTask,
              onPostponeToDate = { t, date -> viewModel.postponeToDate(t.id, date) },
              onPostponePickDate = { t -> taskPendingPostponeDate = t },
              onEditClick = { t ->
                editingTask = t
                showEditor = true
              },
              onOpenTaskDetail = onOpenTaskDetail,
              onDeleteClick = { t -> taskPendingDelete = t },
              modifier = Modifier.widthIn(max = 720.dp),
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
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .padding(20.dp)
          .testTag("calendar_add_fab"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.fab_quick_add_desc),
      )
    }
  }
}

@Composable
private fun CalendarTopControls(
  projection: CalendarProjection,
  locale: Locale,
  onSelectMode: (CalendarViewMode) -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onJumpToToday: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val monthTitle =
    remember(projection.focusedMonth, locale) {
      projection.focusedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
  val isNotOnToday =
    projection.selectedDate != projection.todayDate ||
      projection.focusedMonth != YearMonth.from(projection.todayDate)

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = monthTitle,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag("calendar_period_title"),
        )
        Text(
          text =
            stringResource(
              R.string.calendar_month_task_count,
              projection.scheduledTaskCountInMonth,
            ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        if (isNotOnToday) {
          TextButton(
            onClick = onJumpToToday,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_today_btn"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Today,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(stringResource(R.string.calendar_action_today))
          }
        }
        IconButton(
          onClick = onPrevious,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_prev_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.calendar_prev_period_desc),
          )
        }
        IconButton(
          onClick = onNext,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_next_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = stringResource(R.string.calendar_next_period_desc),
          )
        }
      }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      val modes =
        listOf(
          Triple(CalendarViewMode.DAY, R.string.calendar_mode_day, "calendar_mode_day"),
          Triple(CalendarViewMode.WEEK, R.string.calendar_mode_week, "calendar_mode_week"),
          Triple(CalendarViewMode.MONTH, R.string.calendar_mode_month, "calendar_mode_month"),
        )
      modes.forEachIndexed { index, (mode, labelRes, tag) ->
        SegmentedButton(
          selected = projection.mode == mode,
          onClick = { onSelectMode(mode) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
          modifier = Modifier.minimumInteractiveComponentSize().testTag(tag),
        ) {
          Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun CalendarViewSurface(
  projection: CalendarProjection,
  locale: Locale,
  onSelectDate: (java.time.LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  when (projection.mode) {
    CalendarViewMode.MONTH ->
      CalendarMonthGridCard(
        projection = projection,
        locale = locale,
        onSelectDate = onSelectDate,
        modifier = modifier,
      )
    CalendarViewMode.WEEK ->
      CalendarWeekStripCard(
        projection = projection,
        locale = locale,
        onSelectDate = onSelectDate,
        modifier = modifier,
      )
    CalendarViewMode.DAY ->
      CalendarDayBannerCard(
        projection = projection,
        locale = locale,
        modifier = modifier,
      )
  }
}

@Composable
private fun CalendarMonthGridCard(
  projection: CalendarProjection,
  locale: Locale,
  onSelectDate: (java.time.LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  val weeks = remember(projection.monthDays) { projection.monthDays.chunked(7) }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth().testTag("calendar_month_grid"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth()) {
        projection.weekdayHeaders.forEach { dayOfWeek ->
          Text(
            text = dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(bottom = 6.dp),
          )
        }
      }

      weeks.forEach { week ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          week.forEach { day ->
            CalendarDayCell(
              day = day,
              tag = "calendar_day_${day.date}",
              onClick = { onSelectDate(day.date) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CalendarWeekStripCard(
  projection: CalendarProjection,
  locale: Locale,
  onSelectDate: (java.time.LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth().testTag("calendar_week_strip"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      projection.weekDays.forEach { day ->
        Column(
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(MaterialTheme.shapes.medium)
              .background(
                when {
                  day.isSelected -> MaterialTheme.colorScheme.primaryContainer
                  day.isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
                  else -> MaterialTheme.colorScheme.surface
                }
              )
              .clickable { onSelectDate(day.date) }
              .padding(vertical = 8.dp, horizontal = 2.dp)
              .testTag("calendar_week_day_${day.date}"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelSmall,
            color =
              if (day.isSelected) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
          Text(
            text = day.date.dayOfMonth.toString(),
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Medium,
              ),
            color =
              when {
                day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                day.isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
              },
          )
          DayIndicatorDots(day = day)
        }
      }
    }
  }
}

@Composable
private fun CalendarDayBannerCard(
  projection: CalendarProjection,
  locale: Locale,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  val selectedSummary =
    projection.weekDays.firstOrNull { it.date == projection.selectedDate }
      ?: CalendarDaySummary(
        date = projection.selectedDate,
        isCurrentMonth = true,
        isToday = projection.selectedDate == projection.todayDate,
        isSelected = true,
        totalCount = projection.selectedDayTasks.size,
        activeCount = 0,
        completedCount = 0,
        overdueCount = projection.selectedDayTasks.count { it.isOverdue },
      )
  val weekdayName =
    remember(projection.selectedDate, locale) {
      projection.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth().testTag("calendar_day_summary"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = weekdayName,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = projection.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
          style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryStatPill(
          text = stringResource(R.string.calendar_day_summary_active, selectedSummary.activeCount),
          contentColor = MaterialTheme.colorScheme.primary,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        SummaryStatPill(
          text =
            stringResource(R.string.calendar_day_summary_completed, selectedSummary.completedCount),
          contentColor = semantic.success,
          containerColor = semantic.success.copy(alpha = 0.14f),
        )
        if (selectedSummary.overdueCount > 0) {
          SummaryStatPill(
            text =
              stringResource(R.string.calendar_day_summary_overdue, selectedSummary.overdueCount),
            contentColor = semantic.overdue,
            containerColor = semantic.overdue.copy(alpha = 0.14f),
          )
        }
      }
    }
  }
}

@Composable
private fun CalendarDayCell(
  day: CalendarDaySummary,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  val containerColor =
    when {
      day.isSelected -> MaterialTheme.colorScheme.primaryContainer
      day.isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
      else -> MaterialTheme.colorScheme.surface
    }
  val textColor =
    when {
      day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
      day.isToday -> MaterialTheme.colorScheme.primary
      else -> MaterialTheme.colorScheme.onSurface
    }
  val selectedDesc = if (day.isSelected) "selected" else ""

  Surface(
    color = containerColor,
    shape = MaterialTheme.shapes.medium,
    border =
      when {
        day.isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        day.isToday -> BorderStroke(1.dp, semantic.borderSubtle)
        else -> null
      },
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .height(52.dp)
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .alpha(if (day.isCurrentMonth) 1f else 0.42f)
        .semantics { stateDescription = selectedDesc }
        .testTag(tag),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(vertical = 5.dp, horizontal = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = day.date.dayOfMonth.toString(),
        style =
          MaterialTheme.typography.labelLarge.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Medium,
          ),
        color = textColor,
      )
      DayIndicatorDots(day = day)
    }
  }
}

@Composable
private fun DayIndicatorDots(day: CalendarDaySummary) {
  val semantic = LocalYawmiSemanticColors.current
  Row(
    modifier = Modifier.height(8.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (day.overdueCount > 0) {
      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(semantic.overdue))
    }
    if (day.activeCount > day.overdueCount) {
      Box(
        modifier =
          Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
      )
    }
    if (day.completedCount > 0) {
      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(semantic.success))
    }
  }
}

@Composable
private fun SummaryStatPill(
  text: String,
  contentColor: androidx.compose.ui.graphics.Color,
  containerColor: androidx.compose.ui.graphics.Color,
) {
  Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun CalendarAgendaHeader(
  projection: CalendarProjection,
  language: AppLanguage,
  onAddForSelectedDate: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().testTag("calendar_agenda_header"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Icon(
        imageVector = Icons.Outlined.CalendarMonth,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Column {
        Text(
          text = stringResource(R.string.calendar_agenda_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = projection.selectedDate.formatLocalizedHeader(language),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("calendar_selected_date_label"),
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
      ) {
        Text(
          text = projection.selectedDayTasks.size.toString(),
          style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("calendar_agenda_count"),
        )
      }
      TextButton(
        onClick = onAddForSelectedDate,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_agenda_add_btn"),
      ) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(stringResource(R.string.action_add))
      }
    }
  }
}

@Composable
private fun CalendarAgendaEmptyCard(
  selectedDateFormatted: String,
  onAddClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth().testTag("calendar_agenda_empty"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.EventAvailable,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
      Text(
        text = stringResource(R.string.calendar_agenda_empty_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.calendar_agenda_empty_desc, selectedDateFormatted),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Button(
        onClick = onAddClick,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_empty_add_btn"),
      ) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
        Spacer(modifier = Modifier.size(6.dp))
        Text(stringResource(R.string.action_add))
      }
    }
  }
}

@Composable
private fun CalendarAgendaTaskCard(
  item: CalendarDayTaskItem,
  todayDate: java.time.LocalDate,
  onToggleComplete: (TaskEntity) -> Unit,
  onPostponeToDate: (TaskEntity, java.time.LocalDate) -> Unit,
  onPostponePickDate: (TaskEntity) -> Unit,
  onEditClick: (TaskEntity) -> Unit,
  onOpenTaskDetail: (String) -> Unit,
  onDeleteClick: (TaskEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  TaskListItemCard(
    item =
      TaskListItemUi(
        task = item.task,
        category = item.category,
        isOverdue = item.isOverdue,
      ),
    todayDate = todayDate,
    onToggleComplete = onToggleComplete,
    onPostponeToDate = onPostponeToDate,
    onPostponePickDate = onPostponePickDate,
    onEditClick = onEditClick,
    onTaskClick = { t -> onOpenTaskDetail(t.id) },
    onDeleteClick = onDeleteClick,
    modifier = modifier,
  )
}
