package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.preferences.AppLanguage
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.core.time.ObserveTodayTimeAndLifecycle
import com.example.core.time.formatLocalizedHeader
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TodayDashboardMetrics
import com.example.domain.task.TodayTaskItem
import com.example.feature.tasks.components.CategoryManagerSheet
import com.example.feature.tasks.components.CategoryTokens
import com.example.feature.tasks.components.TaskEditorSheet
import com.example.feature.tasks.components.labelRes
import com.example.feature.tasks.components.toLocalizedMessage
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalYawmiSemanticColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
  language: AppLanguage,
  taskRepository: TaskRepository,
  taskUseCases: TaskUseCases,
  categoryUseCases: CategoryUseCases,
  localClock: LocalClock,
  onOpenTaskDetail: (String) -> Unit,
  onNavigateToTasks: () -> Unit,
  onShowSnackbar: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val viewModel: TodayViewModel =
    viewModel(
      factory =
        TodayViewModelFactory(
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
        is TodayUiEvent.ActionSucceeded -> onShowSnackbar(context.getString(event.messageRes))
        is TodayUiEvent.ActionFailed -> onShowSnackbar(event.error.toLocalizedMessage(context))
      }
    }
  }

  var showEditor by remember { mutableStateOf(false) }
  var showCategoryManager by remember { mutableStateOf(false) }

  if (showEditor) {
    TaskEditorSheet(
      existingTask = null,
      categories = uiState.categories,
      taskUseCases = taskUseCases,
      localClock = localClock,
      onManageCategoriesClick = { showCategoryManager = true },
      onSaved = {
        showEditor = false
        onShowSnackbar(context.getString(R.string.today_msg_task_created))
      },
      onDismiss = { showEditor = false },
    )
  }

  if (showCategoryManager) {
    CategoryManagerSheet(
      categories = uiState.categories,
      categoryUseCases = categoryUseCases,
      onDismiss = { showCategoryManager = false },
    )
  }

  val greetingText =
    when (uiState.greetingPeriod) {
      GreetingPeriod.MORNING -> stringResource(R.string.greeting_morning)
      GreetingPeriod.AFTERNOON -> stringResource(R.string.greeting_afternoon)
      GreetingPeriod.EVENING -> stringResource(R.string.greeting_evening)
    }

  Box(modifier = modifier.fillMaxSize().testTag("screen_today")) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("today_scroll_list"),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item(key = "today_header") {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = stringResource(R.string.today_live_badge),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("today_live_badge"),
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = uiState.todayDate.formatLocalizedHeader(language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("today_date_label"),
          )
          Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("today_greeting_label"),
          )
          Text(
            text = stringResource(R.string.today_summary_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item(key = "today_progress") {
        DailyProgressCard(
          metrics = uiState.metrics,
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).testTag("today_progress_card"),
        )
      }

      item(key = "today_metrics") {
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricChipCard(
            label = stringResource(R.string.metric_total),
            value = uiState.metrics.totalCount.toString(),
            accentColor = MaterialTheme.colorScheme.onSurface,
            cardTag = "today_metric_total",
            valueTag = "today_metric_total_value",
            modifier = Modifier.weight(1f),
          )
          MetricChipCard(
            label = stringResource(R.string.metric_completed),
            value = uiState.metrics.completedCount.toString(),
            accentColor = semantic.success,
            cardTag = "today_metric_completed",
            valueTag = "today_metric_completed_value",
            modifier = Modifier.weight(1f),
          )
          MetricChipCard(
            label = stringResource(R.string.metric_remaining),
            value = uiState.metrics.remainingCount.toString(),
            accentColor = MaterialTheme.colorScheme.primary,
            cardTag = "today_metric_remaining",
            valueTag = "today_metric_remaining_value",
            modifier = Modifier.weight(1f),
          )
          MetricChipCard(
            label = stringResource(R.string.metric_overdue),
            value = uiState.metrics.overdueCount.toString(),
            accentColor = semantic.overdue,
            cardTag = "today_metric_overdue",
            valueTag = "today_metric_overdue_value",
            modifier = Modifier.weight(1f),
          )
        }
      }

      item(key = "today_priorities") {
        ImportantPrioritiesSectionCard(
          items = uiState.priorityItems,
          todayDate = uiState.todayDate,
          onTaskClick = onOpenTaskDetail,
          onToggleComplete = viewModel::toggleTaskCompletion,
          onViewAllClick = onNavigateToTasks,
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).testTag("today_priorities_card"),
        )
      }

      item(key = "today_upcoming") {
        UpcomingScheduleSectionCard(
          scheduleItems = uiState.scheduleItems,
          unscheduledItems = uiState.unscheduledItems,
          todayDate = uiState.todayDate,
          onTaskClick = onOpenTaskDetail,
          onToggleComplete = viewModel::toggleTaskCompletion,
          onViewAllClick = onNavigateToTasks,
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).testTag("today_upcoming_card"),
        )
      }
    }

    FloatingActionButton(
      onClick = { showEditor = true },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .padding(20.dp)
          .testTag("fab_quick_add"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.fab_quick_add_desc),
      )
    }
  }
}

@Composable
private fun DailyProgressCard(
  metrics: TodayDashboardMetrics,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val activeColor = MaterialTheme.colorScheme.primary
  val percentInt = (metrics.completionRatio * 100).toInt()

  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = stringResource(R.string.today_progress_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            if (metrics.totalCount == 0) {
              stringResource(R.string.today_progress_empty)
            } else {
              stringResource(R.string.today_progress_summary, metrics.completedCount, metrics.totalCount)
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("today_progress_summary"),
        )
      }

      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeWidth = 8.dp.toPx()
          drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
          if (metrics.completionRatio > 0f) {
            drawArc(
              color = activeColor,
              startAngle = -90f,
              sweepAngle = 360f * metrics.completionRatio.coerceIn(0f, 1f),
              useCenter = false,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
          }
        }
        Text(
          text = "$percentInt%",
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("today_progress_percent"),
        )
      }
    }
  }
}

@Composable
private fun MetricChipCard(
  label: String,
  value: String,
  accentColor: Color,
  cardTag: String,
  valueTag: String,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  Card(
    modifier = modifier.testTag(cardTag),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = value,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = accentColor,
        modifier = Modifier.testTag(valueTag),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ImportantPrioritiesSectionCard(
  items: List<TodayTaskItem>,
  todayDate: LocalDate,
  onTaskClick: (String) -> Unit,
  onToggleComplete: (TaskEntity) -> Unit,
  onViewAllClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SectionCardHeader(
        title = stringResource(R.string.section_priorities),
        icon = Icons.Outlined.Flag,
        viewAllTag = "today_priorities_view_all_btn",
        onViewAllClick = onViewAllClick,
      )

      if (items.isEmpty()) {
        Text(
          text = stringResource(R.string.section_priorities_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("today_priorities_empty"),
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items.forEachIndexed { index, item ->
            TodayTaskRow(
              item = item,
              todayDate = todayDate,
              rowTag = "today_priority_item_${item.task.id}",
              completeButtonTag = "today_priority_complete_btn_${item.task.id}",
              onClick = { onTaskClick(item.task.id) },
              onToggleComplete = { onToggleComplete(item.task) },
            )
            if (index < items.lastIndex) {
              HorizontalDivider(color = semantic.borderSubtle)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UpcomingScheduleSectionCard(
  scheduleItems: List<TodayTaskItem>,
  unscheduledItems: List<TodayTaskItem>,
  todayDate: LocalDate,
  onTaskClick: (String) -> Unit,
  onToggleComplete: (TaskEntity) -> Unit,
  onViewAllClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, semantic.borderSubtle),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SectionCardHeader(
        title = stringResource(R.string.section_upcoming),
        icon = Icons.Outlined.EventAvailable,
        viewAllTag = "today_upcoming_view_all_btn",
        onViewAllClick = onViewAllClick,
      )

      if (scheduleItems.isEmpty() && unscheduledItems.isEmpty()) {
        Text(
          text = stringResource(R.string.section_upcoming_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("today_upcoming_empty"),
        )
      } else {
        if (scheduleItems.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            scheduleItems.forEachIndexed { index, item ->
              TodayTaskRow(
                item = item,
                todayDate = todayDate,
                rowTag = "today_schedule_item_${item.task.id}",
                completeButtonTag = "today_schedule_complete_btn_${item.task.id}",
                onClick = { onTaskClick(item.task.id) },
                onToggleComplete = { onToggleComplete(item.task) },
              )
              if (index < scheduleItems.lastIndex) {
                HorizontalDivider(color = semantic.borderSubtle)
              }
            }
          }
        }

        if (unscheduledItems.isNotEmpty()) {
          if (scheduleItems.isNotEmpty()) {
            HorizontalDivider(color = semantic.borderSubtle)
          }
          Column(
            modifier = Modifier.fillMaxWidth().testTag("today_unscheduled_section"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.today_section_unscheduled),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            unscheduledItems.forEachIndexed { index, item ->
              TodayTaskRow(
                item = item,
                todayDate = todayDate,
                rowTag = "today_unscheduled_item_${item.task.id}",
                completeButtonTag = "today_unscheduled_complete_btn_${item.task.id}",
                onClick = { onTaskClick(item.task.id) },
                onToggleComplete = { onToggleComplete(item.task) },
              )
              if (index < unscheduledItems.lastIndex) {
                HorizontalDivider(color = semantic.borderSubtle)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionCardHeader(
  title: String,
  icon: ImageVector,
  viewAllTag: String,
  onViewAllClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    TextButton(
      onClick = onViewAllClick,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(viewAllTag),
    ) {
      Text(stringResource(R.string.today_action_view_all))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayTaskRow(
  item: TodayTaskItem,
  todayDate: LocalDate,
  rowTag: String,
  completeButtonTag: String,
  onClick: () -> Unit,
  onToggleComplete: () -> Unit,
) {
  val task = item.task
  val semantic = LocalYawmiSemanticColors.current
  val todayStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
  val tomorrowStr =
    remember(todayDate) { todayDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .padding(vertical = 6.dp, horizontal = 4.dp)
        .testTag(rowTag),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IconButton(
      onClick = onToggleComplete,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(completeButtonTag),
    ) {
      val (icon, tint) =
        if (task.status == TaskStatus.IN_PROGRESS) {
          Icons.Outlined.PlayCircle to MaterialTheme.colorScheme.primary
        } else {
          Icons.Outlined.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
        }
      Icon(
        imageVector = icon,
        contentDescription = stringResource(R.string.task_action_complete),
        tint = tint,
      )
    }

    Column(
      modifier = Modifier.weight(1f).padding(top = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = task.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val statusColor =
          if (task.status == TaskStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.secondary
        TodayMiniBadge(
          text = stringResource(task.status.labelRes()),
          contentColor = statusColor,
          containerColor = statusColor.copy(alpha = 0.14f),
        )

        if (item.isOverdue) {
          TodayMiniBadge(
            text = stringResource(R.string.task_badge_overdue),
            contentColor = semantic.overdue,
            containerColor = semantic.overdue.copy(alpha = 0.16f),
          )
        }

        val dateLabel =
          when (task.scheduledDate) {
            null -> stringResource(R.string.task_schedule_unscheduled)
            todayStr -> stringResource(R.string.task_schedule_today)
            tomorrowStr -> stringResource(R.string.task_schedule_tomorrow)
            else -> task.scheduledDate
          }
        val dateColor =
          when {
            item.isOverdue -> semantic.overdue
            task.scheduledDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
          }
        TodayMiniBadge(
          text = dateLabel,
          contentColor = dateColor,
          containerColor =
            if (item.isOverdue) semantic.overdue.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant,
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.CalendarToday,
              contentDescription = null,
              tint = dateColor,
              modifier = Modifier.size(12.dp),
            )
          },
          useMonoFont =
            task.scheduledDate != null &&
              task.scheduledDate != todayStr &&
              task.scheduledDate != tomorrowStr,
        )

        if (task.startTime != null) {
          val timeText =
            if (task.endTime != null) "${task.startTime} – ${task.endTime}" else task.startTime
          TodayMiniBadge(
            text = timeText,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
              )
            },
            useMonoFont = true,
          )
        }

        item.category?.let { category ->
          val catColor = CategoryTokens.resolveColor(category.colorToken)
          TodayMiniBadge(
            text = category.name,
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = catColor.copy(alpha = 0.14f),
            leadingIcon = {
              Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(catColor))
            },
          )
        }
      }
    }
  }
}

@Composable
private fun TodayMiniBadge(
  text: String,
  contentColor: Color,
  containerColor: Color,
  useMonoFont: Boolean = false,
  leadingIcon: (@Composable () -> Unit)? = null,
) {
  Surface(
    color = containerColor,
    shape = MaterialTheme.shapes.small,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      leadingIcon?.invoke()
      Text(
        text = text,
        style =
          if (useMonoFont) {
            MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
          } else {
            MaterialTheme.typography.labelSmall
          },
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}
