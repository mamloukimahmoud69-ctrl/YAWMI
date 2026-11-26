package com.example.feature.tasks.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.feature.tasks.TaskListItemUi
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalYawmiSemanticColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskListItemCard(
  item: TaskListItemUi,
  todayDate: LocalDate,
  onToggleComplete: (TaskEntity) -> Unit,
  onPostponeToDate: (TaskEntity, LocalDate) -> Unit,
  onPostponePickDate: (TaskEntity) -> Unit,
  onEditClick: (TaskEntity) -> Unit,
  onDeleteClick: (TaskEntity) -> Unit,
  modifier: Modifier = Modifier,
  onTaskClick: (TaskEntity) -> Unit = onEditClick,
) {
  val task = item.task
  val semantic = LocalYawmiSemanticColors.current
  val isCompleted = task.status == TaskStatus.COMPLETED
  val isCancelled = task.status == TaskStatus.CANCELLED
  val isTerminal = isCompleted || isCancelled
  var showPostponeMenu by remember(task.id) { mutableStateOf(false) }

  val todayStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
  val tomorrowDate = remember(todayDate) { todayDate.plusDays(1) }
  val tomorrowStr = remember(tomorrowDate) { tomorrowDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
  val nextWeekDate = remember(todayDate) { todayDate.plusDays(7) }

  val borderColor =
    if (item.isOverdue) {
      semantic.overdue.copy(alpha = 0.65f)
    } else {
      semantic.borderSubtle
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, borderColor),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth().testTag("task_item_${task.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(
          onClick = { onToggleComplete(task) },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("task_complete_btn_${task.id}"),
        ) {
          val (statusIcon, statusTint, actionDescRes) =
            when (task.status) {
              TaskStatus.COMPLETED ->
                Triple(Icons.Filled.CheckCircle, semantic.success, R.string.task_action_reopen)
              TaskStatus.IN_PROGRESS ->
                Triple(
                  Icons.Outlined.PlayCircle,
                  MaterialTheme.colorScheme.primary,
                  R.string.task_action_complete,
                )
              TaskStatus.CANCELLED ->
                Triple(
                  Icons.Outlined.Cancel,
                  MaterialTheme.colorScheme.onSurfaceVariant,
                  R.string.task_action_reopen,
                )
              TaskStatus.PENDING ->
                Triple(
                  Icons.Outlined.RadioButtonUnchecked,
                  MaterialTheme.colorScheme.onSurfaceVariant,
                  R.string.task_action_complete,
                )
            }
          Icon(
            imageVector = statusIcon,
            contentDescription = stringResource(actionDescRes),
            tint = statusTint,
          )
        }

        Column(
          modifier =
            Modifier.weight(1f)
              .clip(MaterialTheme.shapes.small)
              .clickable { onTaskClick(task) }
              .padding(vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            color =
              if (isTerminal) MaterialTheme.colorScheme.onSurfaceVariant
              else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isTerminal) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("task_title_${task.id}"),
          )

          task.description?.let { desc ->
            Text(
              text = desc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!isTerminal) {
            Box {
              IconButton(
                onClick = { showPostponeMenu = true },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("task_postpone_btn_${task.id}"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Update,
                  contentDescription = stringResource(R.string.task_action_postpone),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              DropdownMenu(
                expanded = showPostponeMenu,
                onDismissRequest = { showPostponeMenu = false },
              ) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.task_postpone_tomorrow)) },
                  onClick = {
                    showPostponeMenu = false
                    onPostponeToDate(task, tomorrowDate)
                  },
                  modifier = Modifier.testTag("task_postpone_tomorrow_${task.id}"),
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.task_postpone_next_week)) },
                  onClick = {
                    showPostponeMenu = false
                    onPostponeToDate(task, nextWeekDate)
                  },
                  modifier = Modifier.testTag("task_postpone_next_week_${task.id}"),
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.task_postpone_pick_date)) },
                  onClick = {
                    showPostponeMenu = false
                    onPostponePickDate(task)
                  },
                  modifier = Modifier.testTag("task_postpone_pick_${task.id}"),
                )
              }
            }
          }

          IconButton(
            onClick = { onEditClick(task) },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("task_edit_btn_${task.id}"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = stringResource(R.string.action_edit),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          IconButton(
            onClick = { onDeleteClick(task) },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("task_delete_btn_${task.id}"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Delete,
              contentDescription = stringResource(R.string.action_delete),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val statusColor =
          when (task.status) {
            TaskStatus.COMPLETED -> semantic.success
            TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
            TaskStatus.PENDING -> MaterialTheme.colorScheme.secondary
            TaskStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
          }
        MetadataBadge(
          text = stringResource(task.status.labelRes()),
          contentColor = statusColor,
          containerColor = statusColor.copy(alpha = 0.14f),
          modifier = Modifier.testTag("task_status_badge_${task.id}"),
        )

        val priorityColor =
          when (task.priority) {
            TaskPriority.HIGH -> semantic.overdue
            TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
            TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
          }
        MetadataBadge(
          text = stringResource(task.priority.labelRes()),
          contentColor = priorityColor,
          containerColor = priorityColor.copy(alpha = 0.14f),
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Flag,
              contentDescription = null,
              tint = priorityColor,
              modifier = Modifier.size(13.dp),
            )
          },
          modifier = Modifier.testTag("task_priority_badge_${task.id}"),
        )

        val dateText =
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
        MetadataBadge(
          text = dateText,
          contentColor = dateColor,
          containerColor =
            if (item.isOverdue) semantic.overdue.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant,
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.CalendarToday,
              contentDescription = null,
              tint = dateColor,
              modifier = Modifier.size(13.dp),
            )
          },
          useMonoFont = task.scheduledDate != null && task.scheduledDate != todayStr && task.scheduledDate != tomorrowStr,
          modifier =
            Modifier.testTag(
              if (task.scheduledDate == null) "task_unscheduled_badge_${task.id}"
              else "task_date_badge_${task.id}"
            ),
        )

        if (item.isOverdue) {
          MetadataBadge(
            text = stringResource(R.string.task_badge_overdue),
            contentColor = semantic.overdue,
            containerColor = semantic.overdue.copy(alpha = 0.16f),
            modifier = Modifier.testTag("task_overdue_badge_${task.id}"),
          )
        }

        if (task.startTime != null) {
          val timeText =
            if (task.endTime != null) "${task.startTime} – ${task.endTime}" else task.startTime
          MetadataBadge(
            text = timeText,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
              )
            },
            useMonoFont = true,
            modifier = Modifier.testTag("task_time_badge_${task.id}"),
          )
        }

        item.category?.let { category ->
          val catColor = CategoryTokens.resolveColor(category.colorToken)
          MetadataBadge(
            text = category.name,
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = catColor.copy(alpha = 0.14f),
            leadingIcon = {
              Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(catColor))
            },
            modifier = Modifier.testTag("task_category_badge_${task.id}"),
          )
        }
      }
    }
  }
}

@Composable
private fun MetadataBadge(
  text: String,
  contentColor: Color,
  containerColor: Color,
  modifier: Modifier = Modifier,
  useMonoFont: Boolean = false,
  leadingIcon: (@Composable () -> Unit)? = null,
) {
  Surface(
    color = containerColor,
    shape = MaterialTheme.shapes.small,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
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
