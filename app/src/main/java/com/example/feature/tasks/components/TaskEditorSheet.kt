package com.example.feature.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.time.LocalClock
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import com.example.core.time.DatePickerLocalDateMapper
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
  existingTask: TaskEntity?,
  categories: List<CategoryEntity>,
  taskUseCases: TaskUseCases,
  localClock: LocalClock,
  onManageCategoriesClick: () -> Unit,
  onSaved: (TaskEntity) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  initialScheduledDate: String? = null,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  var title by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.title.orEmpty()) }
  var description by rememberSaveable(existingTask?.id) {
    mutableStateOf(existingTask?.description.orEmpty())
  }
  var scheduledDate by rememberSaveable(existingTask?.id, initialScheduledDate) {
    mutableStateOf(existingTask?.scheduledDate ?: initialScheduledDate.orEmpty())
  }
  var startTime by rememberSaveable(existingTask?.id) {
    mutableStateOf(existingTask?.startTime.orEmpty())
  }
  var endTime by rememberSaveable(existingTask?.id) {
    mutableStateOf(existingTask?.endTime.orEmpty())
  }
  var priority by rememberSaveable(existingTask?.id) {
    mutableStateOf(existingTask?.priority ?: TaskPriority.MEDIUM)
  }
  var selectedCategoryId by rememberSaveable(existingTask?.id) {
    mutableStateOf(existingTask?.categoryId)
  }
  var notes by rememberSaveable(existingTask?.id) { mutableStateOf(existingTask?.notes.orEmpty()) }

  var titleError by remember(existingTask?.id) { mutableStateOf<String?>(null) }
  var dateError by remember(existingTask?.id) { mutableStateOf<String?>(null) }
  var startTimeError by remember(existingTask?.id) { mutableStateOf<String?>(null) }
  var endTimeError by remember(existingTask?.id) { mutableStateOf<String?>(null) }
  var generalError by remember(existingTask?.id) { mutableStateOf<String?>(null) }
  var isSaving by remember(existingTask?.id) { mutableStateOf(false) }
  var showDatePicker by remember(existingTask?.id) { mutableStateOf(false) }

  val todayStr = remember(localClock) { localClock.currentDate().format(DateTimeFormatter.ISO_LOCAL_DATE) }
  val tomorrowStr =
    remember(localClock) { localClock.currentDate().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) }

  if (showDatePicker) {
    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis =
          DatePickerLocalDateMapper.initialPickerMillis(
            existingIsoDate = scheduledDate,
            localClock = localClock,
          )
      )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              scheduledDate =
                DatePickerLocalDateMapper.pickerMillisToLocalDate(millis)
                  .format(DateTimeFormatter.ISO_LOCAL_DATE)
              dateError = null
            }
            showDatePicker = false
          }
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = modifier.testTag("task_editor_sheet"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text =
          if (existingTask == null) stringResource(R.string.task_editor_new_title)
          else stringResource(R.string.task_editor_edit_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("task_editor_heading"),
      )

      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it
          titleError = null
          generalError = null
        },
        label = { Text(stringResource(R.string.task_field_title_label)) },
        placeholder = { Text(stringResource(R.string.task_field_title_hint)) },
        singleLine = true,
        isError = titleError != null,
        supportingText = titleError?.let { msg -> { Text(msg, modifier = Modifier.testTag("task_title_error")) } },
        modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(R.string.task_field_description_label)) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("task_description_input"),
      )

      Text(
        text = stringResource(R.string.task_field_schedule_section),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = scheduledDate.isBlank(),
          onClick = {
            scheduledDate = ""
            dateError = null
          },
          label = { Text(stringResource(R.string.task_schedule_unscheduled)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_date_unscheduled"),
        )
        FilterChip(
          selected = scheduledDate == todayStr,
          onClick = {
            scheduledDate = todayStr
            dateError = null
          },
          label = { Text(stringResource(R.string.task_schedule_today)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_date_today"),
        )
        FilterChip(
          selected = scheduledDate == tomorrowStr,
          onClick = {
            scheduledDate = tomorrowStr
            dateError = null
          },
          label = { Text(stringResource(R.string.task_schedule_tomorrow)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_date_tomorrow"),
        )
        FilterChip(
          selected = false,
          onClick = { showDatePicker = true },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.CalendarToday,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text(stringResource(R.string.task_schedule_pick_date)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_date_picker_btn"),
        )
      }

      if (scheduledDate.isNotBlank() || dateError != null) {
        OutlinedTextField(
          value = scheduledDate,
          onValueChange = {
            scheduledDate = it
            dateError = null
          },
          label = { Text(stringResource(R.string.task_schedule_pick_date)) },
          singleLine = true,
          isError = dateError != null,
          supportingText = dateError?.let { msg -> { Text(msg, modifier = Modifier.testTag("task_date_error")) } },
          modifier = Modifier.fillMaxWidth().testTag("task_date_input"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = startTime,
          onValueChange = {
            startTime = it
            startTimeError = null
            endTimeError = null
          },
          label = { Text(stringResource(R.string.task_field_start_time)) },
          singleLine = true,
          isError = startTimeError != null,
          supportingText =
            startTimeError?.let { msg -> { Text(msg, modifier = Modifier.testTag("task_start_time_error")) } },
          modifier = Modifier.weight(1f).testTag("task_start_time_input"),
        )
        OutlinedTextField(
          value = endTime,
          onValueChange = {
            endTime = it
            endTimeError = null
          },
          label = { Text(stringResource(R.string.task_field_end_time)) },
          singleLine = true,
          isError = endTimeError != null,
          supportingText =
            endTimeError?.let { msg -> { Text(msg, modifier = Modifier.testTag("task_end_time_error")) } },
          modifier = Modifier.weight(1f).testTag("task_end_time_input"),
        )
      }

      Text(
        text = stringResource(R.string.task_field_priority_section),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW).forEach { option ->
          FilterChip(
            selected = priority == option,
            onClick = { priority = option },
            label = { Text(stringResource(option.labelRes())) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("task_priority_${option.name.lowercase()}"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.task_field_category_section),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = onManageCategoriesClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_manage_categories_btn"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(stringResource(R.string.category_manage))
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = selectedCategoryId == null,
          onClick = { selectedCategoryId = null },
          label = { Text(stringResource(R.string.category_none)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("task_category_none"),
        )
        categories.forEach { cat ->
          val dotColor = CategoryTokens.resolveColor(cat.colorToken)
          FilterChip(
            selected = selectedCategoryId == cat.id,
            onClick = { selectedCategoryId = cat.id },
            leadingIcon = {
              Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            },
            label = { Text(cat.name) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("task_category_${cat.id}"),
          )
        }
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text(stringResource(R.string.task_field_notes_label)) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("task_notes_input"),
      )

      generalError?.let { msg ->
        Text(
          text = msg,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("task_general_error"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("task_cancel_btn"),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
        Button(
          enabled = !isSaving,
          onClick = {
            titleError = null
            dateError = null
            startTimeError = null
            endTimeError = null
            generalError = null
            isSaving = true

            scope.launch {
              val result =
                try {
                  val input =
                    TaskInput(
                      title = title,
                      description = description,
                      scheduledDate = scheduledDate,
                      startTime = startTime,
                      endTime = endTime,
                      priority = priority,
                      categoryId = selectedCategoryId,
                      notes = notes,
                    )
                  if (existingTask == null) {
                    taskUseCases.createTask(input)
                  } else {
                    taskUseCases.updateTask(existingTask.id, input)
                  }
                } finally {
                  isSaving = false
                }
              when (result) {
                is TaskDomainResult.Success -> onSaved(result.value)
                is TaskDomainResult.Failure -> {
                  result.errors.forEach { err ->
                    val localized = err.toLocalizedMessage(context)
                    when (err) {
                      TaskValidationError.EmptyTitle -> titleError = localized
                      is TaskValidationError.InvalidScheduledDate -> dateError = localized
                      is TaskValidationError.InvalidStartTime -> startTimeError = localized
                      is TaskValidationError.InvalidEndTime,
                      TaskValidationError.EndTimeWithoutStartTime,
                      TaskValidationError.EndTimeBeforeStartTime -> endTimeError = localized
                      is TaskValidationError.CategoryNotFound,
                      is TaskValidationError.TaskNotFound,
                      is TaskValidationError.InvalidStatusTransition,
                      is TaskValidationError.InvalidRecurrenceInterval,
                      is TaskValidationError.InvalidRecurrenceEndDate,
                      TaskValidationError.RecurrenceEndDateBeforeScheduledDate,
                      TaskValidationError.RecurrenceRequiresScheduledDate,
                      TaskValidationError.RecurrenceRequiresDaysMask -> generalError = localized
                    }
                  }
                }
              }
            }
          },
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("task_save_btn"),
        ) {
          Text(stringResource(R.string.action_save))
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
