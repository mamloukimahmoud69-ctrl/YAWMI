package com.example

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.core.di.AppContainer
import com.example.core.di.DefaultAppContainer
import com.example.core.i18n.ProvideYawmiLocale
import com.example.core.preferences.AppAppearanceRepository
import com.example.core.preferences.AppAppearanceState
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryDomainResult
import com.example.domain.category.CategoryInput
import com.example.domain.category.CategoryUseCases
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.feature.dashboard.TodayScreen
import com.example.ui.theme.YawmiTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class Unit3B4Clock(
  private val date: LocalDate = LocalDate.of(2026, 11, 25),
  private val time: LocalTime = LocalTime.of(9, 15),
  private var epochMs: Long = 1_764_062_100_000L,
  private val zone: ZoneId = ZoneId.of("UTC"),
) : LocalClock {
  override fun currentDate(): LocalDate = date

  override fun currentTime(): LocalTime = time

  override fun currentEpochMs(): Long {
    epochMs += 1_000L
    return epochMs
  }

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING

  override fun zoneId(): ZoneId = zone
}

private class Unit3B4AppearanceRepo(
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
class Unit3B4TodayScreenTest {

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

  private fun newInMemoryContainer(): Pair<DefaultAppContainer, Unit3B4Clock> {
    val db = YawmiDatabase.inMemory(context)
    openDbs += db
    val clock = Unit3B4Clock()
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

  private fun scrollTodayToTag(tag: String, sectionTag: String? = null) {
    if (sectionTag != null) {
      composeTestRule.onNodeWithTag("today_scroll_list").performScrollToNode(hasTestTag(sectionTag))
      composeTestRule.waitForIdle()
    }
    if (composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
    } else {
      composeTestRule.onNodeWithTag("today_scroll_list").performScrollToNode(hasTestTag(tag))
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun todayScreen_emptyState_displaysZeroMetricsAndNoPhase3Placeholder() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ARABIC) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          TodayScreen(
            language = AppLanguage.ARABIC,
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
            onOpenTaskDetail = {},
            onNavigateToTasks = {},
          )
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("today_progress_percent").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("today_live_badge").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("foundation_badge").fetchSemanticsNodes().size)
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("0%")
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true).assertTextEquals("0")
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true).assertTextEquals("0")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("0")
    composeTestRule.onNodeWithTag("today_metric_overdue_value", useUnmergedTree = true).assertTextEquals("0")

    scrollTodayToTag("today_priorities_card")
    composeTestRule.onNodeWithTag("today_priorities_empty").assertIsDisplayed()
    assertEquals(
      0,
      composeTestRule
        .onAllNodesWithText("المرحلة الثالثة", substring = true)
        .fetchSemanticsNodes()
        .size,
    )

    scrollTodayToTag("today_upcoming_card")
    composeTestRule.onNodeWithTag("today_upcoming_empty").assertIsDisplayed()
  }

  @Test
  fun todayScreen_displaysRoomMetricsPrioritiesUpcomingAndUnscheduledWithoutInventingDatesInArabicRtl() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    val (todayHigh, overdueTask, upcomingTask, unscheduledTask) =
      runBlocking {
        val work =
          (categoryUseCases.createCategory(CategoryInput(name = "العمل", colorToken = "amber"))
              as CategoryDomainResult.Success)
            .value

        val doneToday =
          (taskUseCases.createTask(
            TaskInput(title = "مراجعة صباحية", scheduledDate = "2026-11-25")
          ) as TaskDomainResult.Success)
            .value
        taskUseCases.completeTask(doneToday.id)

        val tTodayHigh =
          (taskUseCases.createTask(
            TaskInput(
              title = "تسليم العرض",
              scheduledDate = "2026-11-25",
              startTime = "11:00",
              endTime = "12:00",
              priority = TaskPriority.HIGH,
              categoryId = work.id,
            )
          ) as TaskDomainResult.Success)
            .value

        val tOverdue =
          (taskUseCases.createTask(
            TaskInput(
              title = "تحديث الخطة المتأخرة",
              scheduledDate = "2026-11-23",
              priority = TaskPriority.MEDIUM,
            )
          ) as TaskDomainResult.Success)
            .value

        val tUpcoming =
          (taskUseCases.createTask(
            TaskInput(
              title = "اجتماع الغد",
              scheduledDate = "2026-11-26",
              startTime = "09:30",
              priority = TaskPriority.LOW,
            )
          ) as TaskDomainResult.Success)
            .value

        val tUnscheduled =
          (taskUseCases.createTask(
            TaskInput(
              title = "قراءة مقال تقني",
              scheduledDate = null,
              priority = TaskPriority.LOW,
            )
          ) as TaskDomainResult.Success)
            .value

        listOf(tTodayHigh, tOverdue, tUpcoming, tUnscheduled)
      }

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ARABIC) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          TodayScreen(
            language = AppLanguage.ARABIC,
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
            onOpenTaskDetail = {},
            onNavigateToTasks = {},
          )
        }
      }
    }

    // Today scope: 1 completed + 1 remaining + 1 overdue = 3 total -> 33%
    waitUntilWithIdle {
      composeTestRule
        .onAllNodesWithText("33%")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true).assertTextEquals("3")
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_overdue_value", useUnmergedTree = true).assertTextEquals("1")

    // Important Priorities shows high-priority task + category
    scrollTodayToTag("today_priority_item_${todayHigh.id}", sectionTag = "today_priorities_card")
    composeTestRule.onNodeWithTag("today_priority_item_${todayHigh.id}").assertIsDisplayed()
    composeTestRule.onNodeWithText("العمل", useUnmergedTree = true).assertIsDisplayed()

    // Upcoming Schedule shows overdue, today, and tomorrow tasks + unscheduled section
    scrollTodayToTag("today_schedule_item_${overdueTask.id}", sectionTag = "today_upcoming_card")
    composeTestRule.onNodeWithTag("today_schedule_item_${overdueTask.id}").assertIsDisplayed()
    composeTestRule.onNodeWithText("متأخرة", useUnmergedTree = true).assertIsDisplayed()

    scrollTodayToTag("today_schedule_item_${upcomingTask.id}", sectionTag = "today_upcoming_card")
    composeTestRule.onNodeWithTag("today_schedule_item_${upcomingTask.id}").assertIsDisplayed()
    composeTestRule.onNodeWithText("09:30", useUnmergedTree = true).assertIsDisplayed()

    scrollTodayToTag("today_unscheduled_item_${unscheduledTask.id}", sectionTag = "today_upcoming_card")
    composeTestRule.onNodeWithTag("today_unscheduled_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("today_unscheduled_item_${unscheduledTask.id}").assertIsDisplayed()
    assertNull(runBlocking { container.taskRepository.getById(unscheduledTask.id)?.scheduledDate })
  }

  @Test
  fun todayScreen_quickAddFabCreatesTaskLive_andTogglingCompleteUpdatesMetricsInEnglishLtr() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)
    val snackbarMessages = mutableListOf<String>()

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
        YawmiTheme(themeMode = AppThemeMode.LIGHT) {
          TodayScreen(
            language = AppLanguage.ENGLISH,
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
            onOpenTaskDetail = {},
            onNavigateToTasks = {},
            onShowSnackbar = { snackbarMessages += it },
          )
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("fab_quick_add").fetchSemanticsNodes().isNotEmpty()
    }

    // 1. Quick add Today High-priority task via FAB -> TaskEditorSheet
    composeTestRule.onNodeWithTag("fab_quick_add").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_editor_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("Finalize budget")
    composeTestRule.onNodeWithTag("task_date_today").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_priority_high").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_editor_sheet").fetchSemanticsNodes().isEmpty() &&
        composeTestRule.onAllNodesWithTag("today_metric_total_value", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    waitUntilWithIdle {
      runBlocking {
        container.taskRepository.observeAllTasks().first().any { it.title == "Finalize budget" }
      }
    }

    // Easier: verify metrics updated to 1 total, 1 remaining, 0%
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithText("Finalize budget", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("0%")
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("1")
    assertTrue(snackbarMessages.contains("Task created."))

    // 2. Quick add Unscheduled task -> appears under Unscheduled, does NOT inflate Today Total
    composeTestRule.onNodeWithTag("fab_quick_add").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("Buy notebooks")
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_editor_sheet").fetchSemanticsNodes().isEmpty() &&
        runBlocking {
          container.taskRepository.observeAllTasks().first().any { it.title == "Buy notebooks" }
        }
    }
    scrollTodayToTag("today_unscheduled_section", sectionTag = "today_upcoming_card")
    composeTestRule.onNodeWithText("Buy notebooks", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    scrollTodayToTag("today_metric_total")
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true).assertTextEquals("1")

    // 3. Toggle "Finalize budget" complete -> Progress jumps to 100%, Completed=1, Remaining=0
    val budgetTaskId =
      runBlocking {
        container.taskRepository.observeAllTasks().first().first { it.title == "Finalize budget" }.id
      }

    scrollTodayToTag("today_priority_complete_btn_$budgetTaskId", sectionTag = "today_priorities_card")
    composeTestRule.onNodeWithTag("today_priority_complete_btn_$budgetTaskId").performClick()

    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(budgetTaskId)?.status == TaskStatus.COMPLETED }
    }
    scrollTodayToTag("today_progress_card")
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("100%")
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("0")
    assertTrue(snackbarMessages.contains("Task marked as completed."))
  }

  @Test
  fun yawmiRootApp_navigatesFromTodayToTaskDetail_updatesOnEditCompleteReopenDelete_andNavigatesToTasks() {
    val (container, clock) = newInMemoryContainer()
    val appearanceRepo = Unit3B4AppearanceRepo(AppAppearanceState(language = AppLanguage.ENGLISH))
    val rootContainer =
      object : AppContainer by container {
        override val appearanceRepository = appearanceRepo
        override val localClock = clock
      }

    val task =
      runBlocking {
        (rootContainer.taskUseCases.createTask(
          TaskInput(
            title = "Write quarterly review",
            scheduledDate = "2026-11-25",
            priority = TaskPriority.HIGH,
          )
        ) as TaskDomainResult.Success)
          .value
      }

    composeTestRule.setContent { YawmiRootApp(container = rootContainer) }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag("today_metric_total_value", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("1")

    // 1. Open TaskDetailScreen from Today priority item -> edit & complete -> return to Today
    scrollTodayToTag("today_priority_item_${task.id}")
    composeTestRule.onNodeWithTag("today_priority_item_${task.id}").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_task_detail").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("task_detail_edit_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("Write final quarterly review")
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_editor_sheet").fetchSemanticsNodes().isEmpty() &&
        runBlocking { rootContainer.taskRepository.getById(task.id)?.title == "Write final quarterly review" }
    }

    composeTestRule.onNodeWithTag("task_detail_scroll").performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_detail_complete_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      runBlocking { rootContainer.taskRepository.getById(task.id)?.status == TaskStatus.COMPLETED }
    }

    // Back to TodayScreen -> reflects 100% completion
    composeTestRule.onNodeWithTag("task_detail_back_btn").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty()
    }
    scrollTodayToTag("today_progress_card")
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("100%")
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("0")

    // 2. Navigate from Today -> TasksScreen ("View all") -> reopen task -> return to Today -> reflects updated title & 0%
    scrollTodayToTag("today_priorities_view_all_btn")
    composeTestRule.onNodeWithTag("today_priorities_view_all_btn").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_tasks").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag("task_complete_btn_${task.id}").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_complete_btn_${task.id}").performClick()
    waitUntilWithIdle {
      runBlocking { rootContainer.taskRepository.getById(task.id)?.status == TaskStatus.PENDING }
    }

    composeTestRule.onNodeWithTag("nav_today").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty()
    }
    scrollTodayToTag("today_progress_card")
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("0%")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true).assertTextEquals("1")
    scrollTodayToTag("today_priority_item_${task.id}", sectionTag = "today_priorities_card")
    composeTestRule
      .onNodeWithTag("today_priority_item_${task.id}")
      .assertIsDisplayed()
      .assert(hasText("Write final quarterly review"))

    // 3. Open TaskDetailScreen again -> delete task -> auto-returns to TodayScreen -> metrics reset to 0
    composeTestRule.onNodeWithTag("today_priority_item_${task.id}").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("screen_task_detail").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag("task_detail_delete_btn").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_detail_delete_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_detail_delete_confirm_btn").performClick()

    waitUntilWithIdle {
      runBlocking { rootContainer.taskRepository.getById(task.id) == null } &&
        composeTestRule.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty()
    }
    scrollTodayToTag("today_progress_card")
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("0%")
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true).assertTextEquals("0")
  }
}
