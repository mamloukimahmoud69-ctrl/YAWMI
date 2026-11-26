package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryDomainResult
import com.example.domain.category.CategoryInput
import com.example.domain.category.CategoryUseCases
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.feature.tasks.TasksScreen
import com.example.ui.theme.YawmiTheme
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class Unit3B2Clock(
  private val date: LocalDate = LocalDate.of(2026, 11, 25),
  private val time: LocalTime = LocalTime.of(9, 0),
  private var epochMs: Long = 1_764_000_000_000L,
) : LocalClock {
  override fun currentDate(): LocalDate = date

  override fun currentTime(): LocalTime = time

  override fun currentEpochMs(): Long {
    epochMs += 100L
    return epochMs
  }

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING
}

private class Unit3B2AppearanceRepo(
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
class Unit3B2TasksScreenTest {

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

  private fun newInMemoryContainer(): Pair<DefaultAppContainer, Unit3B2Clock> {
    val db = YawmiDatabase.inMemory(context)
    openDbs += db
    val clock = Unit3B2Clock()
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

  private fun scrollTasksListToTag(tag: String) {
    composeTestRule.onNodeWithTag("tasks_list").performScrollToNode(hasTestTag(tag))
    composeTestRule.waitForIdle()
  }

  @Test
  fun tasksScreen_displaysRealRoomTasks_searchesFiltersSortsAndHandlesUnscheduledInRtl() {
    val (container, clock) = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    val (workCat, healthCat, tToday, tOverdue, tUnscheduled) =
      runBlocking {
        val work =
          (categoryUseCases.createCategory(CategoryInput(name = "العمل", colorToken = "amber"))
              as CategoryDomainResult.Success)
            .value
        val health =
          (categoryUseCases.createCategory(CategoryInput(name = "الصحة", colorToken = "emerald"))
              as CategoryDomainResult.Success)
            .value

        val todayTask =
          (taskUseCases.createTask(
            TaskInput(
              title = "اجتماع الفريق",
              scheduledDate = "2026-11-25",
              startTime = "10:00",
              endTime = "11:00",
              priority = TaskPriority.HIGH,
              categoryId = work.id,
            )
          ) as TaskDomainResult.Success)
            .value

        val overdueTask =
          (taskUseCases.createTask(
            TaskInput(
              title = "دفع الفاتورة",
              scheduledDate = "2026-11-23",
              priority = TaskPriority.LOW,
              categoryId = work.id,
            )
          ) as TaskDomainResult.Success)
            .value

        val unscheduledTask =
          (taskUseCases.createTask(
            TaskInput(
              title = "قراءة كتاب",
              scheduledDate = null,
              priority = TaskPriority.MEDIUM,
              categoryId = health.id,
            )
          ) as TaskDomainResult.Success)
            .value

        listOf(work, health, todayTask, overdueTask, unscheduledTask)
      }
        .let {
          @Suppress("UNCHECKED_CAST")
          Tuple5(
            it[0] as com.example.data.local.entity.CategoryEntity,
            it[1] as com.example.data.local.entity.CategoryEntity,
            it[2] as com.example.data.local.entity.TaskEntity,
            it[3] as com.example.data.local.entity.TaskEntity,
            it[4] as com.example.data.local.entity.TaskEntity,
          )
        }

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ARABIC) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          TasksScreen(
            taskRepository = container.taskRepository,
            taskUseCases = taskUseCases,
            categoryUseCases = categoryUseCases,
            localClock = clock,
          )
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${tToday.id}").fetchSemanticsNodes().isNotEmpty()
    }

    // Scheduled & Unscheduled sections + metadata rendered
    composeTestRule.onNodeWithTag("tasks_section_scheduled").assertIsDisplayed()
    scrollTasksListToTag("task_time_badge_${tToday.id}")
    composeTestRule.onNodeWithTag("task_time_badge_${tToday.id}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_category_badge_${tToday.id}").assertIsDisplayed()

    scrollTasksListToTag("task_item_${tOverdue.id}")
    composeTestRule.onNodeWithTag("task_overdue_badge_${tOverdue.id}").assertIsDisplayed()

    scrollTasksListToTag("tasks_section_unscheduled")
    composeTestRule.onNodeWithTag("tasks_section_unscheduled").assertIsDisplayed()
    scrollTasksListToTag("task_item_${tUnscheduled.id}")
    composeTestRule.onNodeWithTag("task_unscheduled_badge_${tUnscheduled.id}").assertIsDisplayed()

    // Real search by title
    composeTestRule.onNodeWithTag("tasks_search_input").performTextInput("قراءة")
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${tToday.id}").fetchSemanticsNodes().isEmpty() &&
        composeTestRule.onAllNodesWithTag("task_item_${tUnscheduled.id}").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_item_${tUnscheduled.id}").assertIsDisplayed()

    // Clear search
    composeTestRule.onNodeWithTag("tasks_search_clear_btn").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${tToday.id}").fetchSemanticsNodes().isNotEmpty()
    }

    // Open filter panel & combine multi-criteria filtering: Overdue + High -> 0 results, then Overdue + Low + Work -> tOverdue
    composeTestRule.onNodeWithTag("tasks_toggle_filters_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tasks_filter_schedule_overdue").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tasks_filter_priority_high").performScrollTo().performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("tasks_filtered_empty_state").fetchSemanticsNodes().isNotEmpty()
    }
    scrollTasksListToTag("tasks_filtered_empty_state")
    composeTestRule.onNodeWithTag("tasks_filtered_empty_state").assertIsDisplayed()

    scrollTasksListToTag("filters_panel")
    composeTestRule.onNodeWithTag("tasks_filter_priority_low").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tasks_filter_category_${workCat.id}").performScrollTo().performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${tOverdue.id}").fetchSemanticsNodes().isNotEmpty()
    }
    scrollTasksListToTag("task_item_${tOverdue.id}")
    composeTestRule.onNodeWithTag("task_item_${tOverdue.id}").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_item_${tUnscheduled.id}").fetchSemanticsNodes().size)

    // Clear filters and verify unscheduled filter + category filter
    composeTestRule.onNodeWithTag("tasks_clear_filters_btn").performClick()
    composeTestRule.waitForIdle()
    scrollTasksListToTag("filters_panel")
    composeTestRule.onNodeWithTag("tasks_filter_schedule_unscheduled").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tasks_filter_category_${healthCat.id}").performScrollTo().performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${tUnscheduled.id}").fetchSemanticsNodes().isNotEmpty()
    }
    scrollTasksListToTag("task_item_${tUnscheduled.id}")
    composeTestRule.onNodeWithTag("task_item_${tUnscheduled.id}").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_item_${tOverdue.id}").fetchSemanticsNodes().size)

    // Clear filters, collapse filters, & switch sort to Title
    composeTestRule.onNodeWithTag("tasks_clear_filters_btn").performClick()
    composeTestRule.onNodeWithTag("tasks_toggle_filters_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tasks_sort_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tasks_sort_title").performClick()
    composeTestRule.waitForIdle()
    // Under TITLE_ASC, unscheduled section header is hidden and all 3 tasks remain accessible
    assertEquals(0, composeTestRule.onAllNodesWithTag("tasks_section_unscheduled").fetchSemanticsNodes().size)
    scrollTasksListToTag("task_item_${tToday.id}")
    composeTestRule.onNodeWithTag("task_item_${tToday.id}").assertIsDisplayed()
    scrollTasksListToTag("task_item_${tOverdue.id}")
    composeTestRule.onNodeWithTag("task_item_${tOverdue.id}").assertIsDisplayed()
    scrollTasksListToTag("task_item_${tUnscheduled.id}")
    composeTestRule.onNodeWithTag("task_item_${tUnscheduled.id}").assertIsDisplayed()
  }

  @Test
  fun tasksScreen_executesCompletePostponeDeleteAndPersistsAcrossRestartInEnglishLtr() {
    val dbName = "unit3b2_restart_${System.nanoTime()}.db"
    context.deleteDatabase(dbName)
    val db1 = YawmiDatabase.openWithName(context, dbName)
    openDbs += db1
    val clock = Unit3B2Clock()
    val container1 = DefaultAppContainer(context = context, databaseProvider = { db1 })
    val taskUseCases1 = TaskUseCases(container1.taskRepository, container1.categoryRepository, clock)
    val categoryUseCases1 = CategoryUseCases(container1.categoryRepository, clock)

    val (tPostpone, tComplete, tDelete) =
      runBlocking {
        val a =
          (taskUseCases1.createTask(TaskInput(title = "Postpone me", scheduledDate = "2026-11-25"))
              as TaskDomainResult.Success)
            .value
        val b =
          (taskUseCases1.createTask(TaskInput(title = "Complete me", scheduledDate = "2026-11-25"))
              as TaskDomainResult.Success)
            .value
        val c =
          (taskUseCases1.createTask(TaskInput(title = "Delete me", scheduledDate = null))
              as TaskDomainResult.Success)
            .value
        Triple(a, b, c)
      }

    var showFirstInstance by mutableStateOf(true)

    composeTestRule.setContent {
      if (showFirstInstance) {
        ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
          YawmiTheme(themeMode = AppThemeMode.LIGHT) {
            TasksScreen(
              taskRepository = container1.taskRepository,
              taskUseCases = taskUseCases1,
              categoryUseCases = categoryUseCases1,
              localClock = clock,
            )
          }
        }
      } else {
        Box(modifier = Modifier.testTag("session_closed"))
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${tPostpone.id}").fetchSemanticsNodes().isNotEmpty()
    }

    // 1. Complete task -> live UI updates badge to Completed
    scrollTasksListToTag("task_item_${tComplete.id}")
    composeTestRule.onNodeWithTag("task_complete_btn_${tComplete.id}").performClick()
    waitUntilWithIdle {
      runBlocking { container1.taskRepository.getById(tComplete.id)?.status == TaskStatus.COMPLETED }
    }
    scrollTasksListToTag("task_status_badge_${tComplete.id}")
    composeTestRule.onNodeWithTag("task_status_badge_${tComplete.id}").assertIsDisplayed()

    // 2. Postpone task to Tomorrow -> scheduledDate moves to 2026-11-26 immediately
    scrollTasksListToTag("task_item_${tPostpone.id}")
    composeTestRule.onNodeWithTag("task_postpone_btn_${tPostpone.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_postpone_tomorrow_${tPostpone.id}").performClick()
    waitUntilWithIdle {
      runBlocking { container1.taskRepository.getById(tPostpone.id)?.scheduledDate == "2026-11-26" }
    }

    // 3. Delete task with confirmation dialog (cancel first, then confirm)
    scrollTasksListToTag("task_item_${tDelete.id}")
    composeTestRule.onNodeWithTag("task_delete_btn_${tDelete.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_delete_confirm_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_delete_cancel_btn").performClick()
    composeTestRule.waitForIdle()
    scrollTasksListToTag("task_item_${tDelete.id}")
    composeTestRule.onNodeWithTag("task_item_${tDelete.id}").assertIsDisplayed()

    composeTestRule.onNodeWithTag("task_delete_btn_${tDelete.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_delete_confirm_btn").performClick()
    waitUntilWithIdle {
      runBlocking { container1.taskRepository.getById(tDelete.id) == null }
    }
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_item_${tDelete.id}").fetchSemanticsNodes().size)

    // Simulate closing app & reopening database
    composeTestRule.runOnIdle { showFirstInstance = false }
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("session_closed").fetchSemanticsNodes().isNotEmpty()
    }
    db1.close()

    val db2 = YawmiDatabase.openWithName(context, dbName)
    openDbs += db2
    val container2 = DefaultAppContainer(context = context, databaseProvider = { db2 })
    val reloadedAll = runBlocking { container2.taskRepository.observeAllTasks().first() }
    assertEquals(2, reloadedAll.size)
    assertEquals("2026-11-26", reloadedAll.first { it.id == tPostpone.id }.scheduledDate)
    assertEquals(TaskStatus.COMPLETED, reloadedAll.first { it.id == tComplete.id }.status)
    assertNull(reloadedAll.firstOrNull { it.id == tDelete.id })

    db2.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun yawmiRootApp_navigatesToTasksTabAndCreatesTaskLive() {
    val (container, clock) = newInMemoryContainer()
    val appearanceRepo = Unit3B2AppearanceRepo(AppAppearanceState(language = AppLanguage.ARABIC))
    val rootContainer =
      object : com.example.core.di.AppContainer by container {
        override val appearanceRepository = appearanceRepo
        override val localClock = clock
      }

    composeTestRule.setContent { YawmiRootApp(container = rootContainer) }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tasks").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_tasks").assertIsDisplayed()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("tasks_empty_state").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("tasks_empty_state").assertIsDisplayed()

    // Create task via TasksScreen FAB (reuses TaskEditorSheet) and verify live list update
    composeTestRule.onNodeWithTag("tasks_add_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("مهمة حية جديدة")
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithText("مهمة حية جديدة").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("tasks_list").performScrollToNode(hasText("مهمة حية جديدة"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("مهمة حية جديدة").assertIsDisplayed()
  }
}

private data class Tuple5<A, B, C, D, E>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
)
