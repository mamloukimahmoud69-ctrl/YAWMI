package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.di.DefaultAppContainer
import com.example.core.i18n.ProvideYawmiLocale
import com.example.core.preferences.AppAppearanceRepository
import com.example.core.preferences.AppAppearanceState
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.OutboxAction
import com.example.data.local.entity.RecurrenceFrequency
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskRecurrenceEntity
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryDomainResult
import com.example.domain.category.CategoryInput
import com.example.domain.category.CategoryUseCases
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import com.example.feature.tasks.detail.TaskDetailScreen
import com.example.feature.tasks.detail.TaskDetailUiEvent
import com.example.feature.tasks.detail.TaskDetailUiState
import com.example.feature.tasks.detail.TaskDetailViewModel
import com.example.ui.theme.YawmiTheme
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class Unit3B3Clock(
  private val date: LocalDate = LocalDate.of(2026, 11, 25),
  private val time: LocalTime = LocalTime.of(9, 15),
  private var epochMs: Long = 1_764_062_100_000L,
) : LocalClock {
  override fun currentDate(): LocalDate = date

  override fun currentTime(): LocalTime = time

  override fun currentEpochMs(): Long {
    epochMs += 1_000L
    return epochMs
  }

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING
}

private class Unit3B3AppearanceRepo(
  initial: AppAppearanceState = AppAppearanceState(),
) : AppAppearanceRepository {
  private val state = MutableStateFlow(initial)
  override val appearanceState: Flow<AppAppearanceState> = state

  override suspend fun setThemeMode(mode: AppThemeMode) {
    state.value = state.value.copy(themeMode = mode)
  }

  override suspend fun setLanguage(language: AppLanguage) {
    state.value = state.value.copy(language = language)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit3B3TaskDetailTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private val openDbs = mutableListOf<YawmiDatabase>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    openDbs.forEach { db ->
      if (db.isOpen) db.close()
    }
    openDbs.clear()
  }

  private fun newInMemoryContainer(): Pair<DefaultAppContainer, Unit3B3Clock> {
    val db = YawmiDatabase.inMemory(context)
    openDbs += db
    val clock = Unit3B3Clock()
    val container = DefaultAppContainer(context = context, databaseProvider = { db })
    return container to clock
  }

  private fun waitUntilWithIdle(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      composeTestRule.waitForIdle()
      if (predicate()) return
      Thread.sleep(20)
    }
    composeTestRule.waitForIdle()
    if (!predicate()) {
      throw AssertionError("Condition still not satisfied after $timeoutMs ms")
    }
  }

  @Test
  fun taskDetailScreen_displaysRoomTaskAndUpdatesLiveAfterEditInArabicRtl() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    val task =
      runBlocking {
        val work =
          (categoryUseCases.createCategory(CategoryInput(name = "العمل", colorToken = "amber"))
              as CategoryDomainResult.Success)
            .value
        (taskUseCases.createTask(
          TaskInput(
            title = "إعداد التقرير الفصلي",
            description = "مراجعة الأرقام المالية للربع الرابع",
            notes = "التأكد من جدول المصروفات",
            scheduledDate = "2026-11-25",
            startTime = "10:00",
            endTime = "11:30",
            priority = TaskPriority.HIGH,
            categoryId = work.id,
          )
        ) as TaskDomainResult.Success)
          .value
      }

    val snackbarMessages = mutableListOf<String>()

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ARABIC) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          TaskDetailScreen(
            taskId = task.id,
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
            onNavigateBack = {},
            onShowSnackbar = { snackbarMessages += it },
          )
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_detail_title").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("task_detail_title").assertIsDisplayed()
    composeTestRule.onNodeWithText("إعداد التقرير الفصلي").assertIsDisplayed()
    composeTestRule.onNodeWithText("قيد الانتظار").assertIsDisplayed()
    composeTestRule.onNodeWithText("عالية").assertIsDisplayed()
    composeTestRule.onNodeWithText("العمل").assertIsDisplayed()

    composeTestRule.onNodeWithTag("task_detail_date_value").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("10:00 – 11:30").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_description_value").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_notes_value").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_created_at").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_updated_at").performScrollTo().assertIsDisplayed()

    // Pending task must NOT show completedAt or Reopen action
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_completed_at").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_reopen_btn").fetchSemanticsNodes().size)

    // Edit via reused TaskEditorSheet and verify live Room update on TaskDetailScreen
    composeTestRule.onNodeWithTag("task_detail_edit_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("إعداد التقرير النهائي")
    composeTestRule.onNodeWithTag("task_notes_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("task_notes_input").performScrollTo().performTextInput("تمت مراجعة الميزانية")
    composeTestRule.onNodeWithTag("task_priority_low").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_editor_sheet").fetchSemanticsNodes().isEmpty() &&
        composeTestRule.onAllNodesWithTag("task_detail_title").fetchSemanticsNodes().isNotEmpty() &&
        runBlocking { container.taskRepository.getById(task.id)?.title == "إعداد التقرير النهائي" }
    }
    composeTestRule.onNodeWithTag("task_detail_title").performScrollTo()
    composeTestRule.onNodeWithText("إعداد التقرير النهائي").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_notes_value").performScrollTo()
    composeTestRule.onNodeWithText("تمت مراجعة الميزانية").assertIsDisplayed()
    composeTestRule.onNodeWithText("منخفضة").performScrollTo().assertIsDisplayed()
    assertTrue(snackbarMessages.contains("تم تحديث المهمة."))
  }

  @Test
  fun taskDetailScreen_executesLifecycleActionsStartPostponeCompleteReopenCancelInEnglishLtr() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    val task =
      runBlocking {
        (taskUseCases.createTask(
          TaskInput(title = "Prepare release notes", scheduledDate = "2026-11-25")
        ) as TaskDomainResult.Success)
          .value
      }

    val snackbarMessages = mutableListOf<String>()

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
        YawmiTheme(themeMode = AppThemeMode.LIGHT) {
          TaskDetailScreen(
            taskId = task.id,
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
            onNavigateBack = {},
            onShowSnackbar = { snackbarMessages += it },
          )
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_detail_start_btn").fetchSemanticsNodes().isNotEmpty()
    }

    // 1. Start task (PENDING -> IN_PROGRESS)
    composeTestRule.onNodeWithTag("task_detail_start_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(task.id)?.status == TaskStatus.IN_PROGRESS }
    }
    composeTestRule.onNodeWithText("In progress").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_start_btn").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_reopen_btn").fetchSemanticsNodes().size)
    assertNull(runBlocking { container.taskRepository.getById(task.id)?.completedAtEpochMs })

    // 2. Postpone task (Tomorrow -> 2026-11-26)
    composeTestRule.onNodeWithTag("task_detail_postpone_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      composeTestRule
        .onAllNodesWithTag("task_detail_postpone_tomorrow")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_detail_postpone_tomorrow").performClick()
    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(task.id)?.scheduledDate == "2026-11-26" }
    }
    composeTestRule.onNodeWithTag("task_detail_date_value").performScrollTo()
    composeTestRule.onNodeWithText("Tomorrow (2026-11-26)").assertIsDisplayed()

    // 3. Complete task (IN_PROGRESS -> COMPLETED) -> completedAtEpochMs populated & terminal UI shown
    composeTestRule.onNodeWithTag("task_detail_complete_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(task.id)?.status == TaskStatus.COMPLETED }
    }
    val completedTask = runBlocking { container.taskRepository.getById(task.id)!! }
    assertNotNull(completedTask.completedAtEpochMs)
    composeTestRule.onNodeWithTag("task_detail_completed_at").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_terminal_hint").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_start_btn").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_complete_btn").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_postpone_btn").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_cancel_btn").fetchSemanticsNodes().size)
    composeTestRule.onNodeWithTag("task_detail_reopen_btn").performScrollTo().assertIsDisplayed()

    // 4. Reopen task (COMPLETED -> PENDING) -> completedAtEpochMs cleared
    composeTestRule.onNodeWithTag("task_detail_reopen_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(task.id)?.status == TaskStatus.PENDING }
    }
    val reopenedTask = runBlocking { container.taskRepository.getById(task.id)!! }
    assertNull(reopenedTask.completedAtEpochMs)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_completed_at").fetchSemanticsNodes().size)
    composeTestRule.onNodeWithTag("task_detail_start_btn").performScrollTo().assertIsDisplayed()

    // 5. Cancel task with confirmation dialog (dismiss first, then confirm)
    composeTestRule.onNodeWithTag("task_detail_cancel_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_detail_cancel_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_cancel_dismiss_btn").performClick()
    composeTestRule.waitForIdle()
    assertEquals(TaskStatus.PENDING, runBlocking { container.taskRepository.getById(task.id)?.status })

    composeTestRule.onNodeWithTag("task_detail_cancel_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_detail_cancel_confirm_btn").performClick()
    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(task.id)?.status == TaskStatus.CANCELLED }
    }
    val cancelledTask = runBlocking { container.taskRepository.getById(task.id)!! }
    assertNull(cancelledTask.completedAtEpochMs)
    composeTestRule.onNodeWithTag("task_detail_terminal_hint").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_reopen_btn").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_complete_btn").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_postpone_btn").fetchSemanticsNodes().size)
    assertTrue(snackbarMessages.contains("Task is now in progress."))
    assertTrue(snackbarMessages.contains("Task postponed to 2026-11-26."))
    assertTrue(snackbarMessages.contains("Task marked as completed."))
    assertTrue(snackbarMessages.contains("Task reopened."))
    assertTrue(snackbarMessages.contains("Task marked as cancelled."))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun taskDetailViewModel_rejectsInvalidTransitionsAndKeepsRoomUnchanged() {
    Dispatchers.setMain(Dispatchers.Default)
    try {
      runBlocking {
        val (container, clock) = newInMemoryContainer()
        val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
        val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

        val task =
          (taskUseCases.createTask(TaskInput(title = "State machine test", scheduledDate = "2026-11-25"))
              as TaskDomainResult.Success)
            .value

        val viewModel =
          TaskDetailViewModel(
            taskId = task.id,
            taskRepository = container.taskRepository,
            categoryUseCases = categoryUseCases,
            taskUseCases = taskUseCases,
            localClock = clock,
          )

        // Invalid reopen while PENDING
        val reopenPendingEvent =
          async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.reopenTask()
        val pendingFailure = reopenPendingEvent.await()
        assertTrue(pendingFailure is TaskDetailUiEvent.ActionFailed)
        assertTrue(
          (pendingFailure as TaskDetailUiEvent.ActionFailed).error is
            TaskValidationError.InvalidStatusTransition
        )
        assertEquals(TaskStatus.PENDING, container.taskRepository.getById(task.id)?.status)

        // Complete task legitimately
        taskUseCases.completeTask(task.id)
        val completedSnapshot = container.taskRepository.getById(task.id)!!
        assertEquals(TaskStatus.COMPLETED, completedSnapshot.status)
        assertNotNull(completedSnapshot.completedAtEpochMs)

        // Reject start, complete, cancel, postpone on COMPLETED task
        val startCompletedEvent =
          async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.startTask()
        assertTrue(
          (startCompletedEvent.await() as TaskDetailUiEvent.ActionFailed).error is
            TaskValidationError.InvalidStatusTransition
        )

        val completeAgainEvent =
          async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.completeTask()
        assertTrue(
          (completeAgainEvent.await() as TaskDetailUiEvent.ActionFailed).error is
            TaskValidationError.InvalidStatusTransition
        )

        val cancelCompletedEvent =
          async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.cancelTask()
        assertTrue(
          (cancelCompletedEvent.await() as TaskDetailUiEvent.ActionFailed).error is
            TaskValidationError.InvalidStatusTransition
        )

        val postponeCompletedEvent =
          async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.postponeToDate(LocalDate.of(2026, 12, 1))
        assertTrue(
          (postponeCompletedEvent.await() as TaskDetailUiEvent.ActionFailed).error is
            TaskValidationError.InvalidStatusTransition
        )

        // Room state remains intact
        val afterFailures = container.taskRepository.getById(task.id)!!
        assertEquals(TaskStatus.COMPLETED, afterFailures.status)
        assertEquals("2026-11-25", afterFailures.scheduledDate)
        assertEquals(completedSnapshot.completedAtEpochMs, afterFailures.completedAtEpochMs)

        val loaded =
          viewModel.uiState.first { it is TaskDetailUiState.Loaded } as TaskDetailUiState.Loaded
        assertEquals(setOf(TaskStatus.PENDING), loaded.allowedTransitions)
        assertTrue(loaded.canReopen)
        assertEquals(false, loaded.canPostpone)
      }
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun yawmiRootApp_navigatesToTaskDetailAndDeletesTaskWithLinkedRecords() {
    val (container, clock) = newInMemoryContainer()
    val appearanceRepo = Unit3B3AppearanceRepo(AppAppearanceState(language = AppLanguage.ARABIC))
    val rootContainer =
      object : com.example.core.di.AppContainer by container {
        override val appearanceRepository = appearanceRepo
        override val localClock = clock
      }

    val (task, reminder) =
      runBlocking {
        val created =
          (rootContainer.taskUseCases.createTask(
            TaskInput(
              title = "مهمة مرتبطة بالتذكير والتكرار",
              scheduledDate = "2026-11-25",
              startTime = "08:30",
            )
          ) as TaskDomainResult.Success)
            .value

        rootContainer.taskRepository.upsertTaskRecurrence(
          TaskRecurrenceEntity(
            id = "rec-${created.id}",
            taskId = created.id,
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            updatedAtEpochMs = clock.currentEpochMs(),
          )
        )

        val rem =
          rootContainer.reminderSyncRepository.createTaskReminder(
            reminderId = "rem-${created.id}",
            taskId = created.id,
            triggerDate = "2026-11-25",
            triggerTime = "08:30",
            offsetMinutes = 0,
            nowEpochMs = clock.currentEpochMs(),
          )

        created to rem
      }

    composeTestRule.setContent { YawmiRootApp(container = rootContainer) }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tasks").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_tasks").fetchSemanticsNodes().isNotEmpty()
    }
    waitUntilWithIdle {
      composeTestRule
        .onAllNodesWithTag("task_title_${task.id}", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    // Open TaskDetailScreen by tapping task title
    composeTestRule.onNodeWithTag("task_title_${task.id}", useUnmergedTree = true).performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_task_detail").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag("task_detail_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("مهمة مرتبطة بالتذكير والتكرار").assertIsDisplayed()

    // Delete with confirmation (cancel first, then confirm)
    composeTestRule.onNodeWithTag("task_detail_delete_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_detail_delete_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_delete_cancel_btn").performClick()
    composeTestRule.waitForIdle()
    assertNotNull(runBlocking { rootContainer.taskRepository.getById(task.id) })

    composeTestRule.onNodeWithTag("task_detail_delete_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_detail_delete_confirm_btn").performClick()

    // Navigates back to TasksScreen and cascades/cleans linked data
    waitUntilWithIdle {
      runBlocking { rootContainer.taskRepository.getById(task.id) == null }
    }
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_tasks").fetchSemanticsNodes().isNotEmpty()
    }

    runBlocking {
      assertNull(rootContainer.taskRepository.getById(task.id))
      assertNull(rootContainer.taskRepository.getTaskRecurrence(task.id))
      assertTrue(rootContainer.reminderSyncRepository.observeTaskReminders(task.id).first().isEmpty())
      val outbox = rootContainer.reminderSyncRepository.getAllOutboxOrdered()
      assertTrue(
        outbox.any {
          it.reminderId == reminder.id && it.action == OutboxAction.CANCEL_ALARM
        }
      )
    }
  }

  @Test
  fun taskDetailScreen_handlesMissingTaskGracefullyWithNotFoundState() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)
    var backPressed = false

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          TaskDetailScreen(
            taskId = "non-existent-task-id",
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
            onNavigateBack = { backPressed = true },
          )
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_detail_not_found").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_detail_not_found").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_detail_not_found_back_btn").performClick()
    assertTrue(backPressed)
  }
}
