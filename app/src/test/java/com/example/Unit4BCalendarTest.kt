package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.core.di.DefaultAppContainer
import com.example.core.i18n.ProvideYawmiLocale
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.task.CalendarViewMode
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.feature.calendar.CalendarScreen
import com.example.feature.calendar.CalendarUiState
import com.example.feature.calendar.CalendarViewModel
import com.example.ui.theme.YawmiTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

private class ControllableCalendarClock(
  initialInstant: Instant = ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
  initialZone: ZoneId = ZoneId.of("UTC"),
) : LocalClock {
  @Volatile private var instant: Instant = initialInstant
  @Volatile private var zone: ZoneId = initialZone

  fun advanceMillis(millis: Long) {
    instant = instant.plusMillis(millis)
  }

  override fun currentDate(): LocalDate = instant.atZone(zone).toLocalDate()

  override fun currentTime(): LocalTime = instant.atZone(zone).toLocalTime()

  override fun currentEpochMs(): Long = instant.toEpochMilli()

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING

  override fun zoneId(): ZoneId = zone

  override fun millisUntilNextMidnight(): Long {
    val now = instant.atZone(zone)
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
  }
}

private class Test4BLifecycleOwner(
  initialState: Lifecycle.State = Lifecycle.State.RESUMED,
) : LifecycleOwner {
  private val registry = LifecycleRegistry(this).apply { currentState = initialState }
  override val lifecycle: Lifecycle
    get() = registry

  fun handleEvent(event: Lifecycle.Event) {
    registry.handleLifecycleEvent(event)
  }
}

private class Test4BStoreOwner : ViewModelStoreOwner {
  override val viewModelStore: ViewModelStore = ViewModelStore()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit4BCalendarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var application: Application
  private val openDbs = mutableListOf<YawmiDatabase>()
  private val storeOwners = mutableListOf<Test4BStoreOwner>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    application = context as Application
  }

  @After
  fun tearDown() {
    storeOwners.forEach { it.viewModelStore.clear() }
    storeOwners.clear()
    openDbs.forEach { if (it.isOpen) it.close() }
    openDbs.clear()
  }

  private fun newInMemoryContainer(): DefaultAppContainer {
    val db = YawmiDatabase.inMemory(context)
    openDbs += db
    return DefaultAppContainer(context = context, databaseProvider = { db })
  }

  private fun registeredTimeReceiverCount(): Int {
    val registered = shadowOf(application).registeredReceivers
    return registered.count { wrapper ->
      val filter = wrapper.intentFilter ?: return@count false
      filter.hasAction(Intent.ACTION_DATE_CHANGED) &&
        filter.hasAction(Intent.ACTION_TIME_CHANGED) &&
        filter.hasAction(Intent.ACTION_TIMEZONE_CHANGED)
    }
  }

  private fun waitUntilWithIdle(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      composeTestRule.waitForIdle()
      if (predicate()) return
      Thread.sleep(20)
    }
    composeTestRule.waitForIdle()
    assertTrue("Condition not met within $timeoutMs ms", predicate())
  }

  @Test
  fun calendarViewModel_navigatesModesPeriodsJumpToTodayAndMidnightRollover() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(testDispatcher)
    try {
      val clock =
        ControllableCalendarClock(
          initialInstant = ZonedDateTime.of(2026, 11, 25, 23, 59, 50, 0, ZoneId.of("UTC")).toInstant(),
        )
      val container = newInMemoryContainer()
      val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
      val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)
      val viewModel =
        CalendarViewModel(
          taskRepository = container.taskRepository,
          categoryUseCases = categoryUseCases,
          taskUseCases = taskUseCases,
          localClock = clock,
          initialFirstDayOfWeek = DayOfWeek.SATURDAY,
          midnightDispatcher = testDispatcher,
        )
      val collected = mutableListOf<CalendarUiState>()
      val job = launch { viewModel.uiState.collect { collected += it } }
      testScheduler.runCurrent()

      waitUntilCondition(onPoll = { testScheduler.runCurrent() }) {
        collected.any { !it.isLoading }
      }
      assertEquals(LocalDate.of(2026, 11, 25), viewModel.uiState.value.projection.selectedDate)

      // Unpinned selectedDate follows midnight rollover
      clock.advanceMillis(10_000L)
      testScheduler.advanceTimeBy(10_000L)
      testScheduler.runCurrent()
      assertEquals(LocalDate.of(2026, 11, 26), viewModel.uiState.value.projection.selectedDate)

      // Month navigation
      viewModel.navigateNext()
      assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.projection.focusedMonth)
      viewModel.navigatePrevious()
      assertEquals(YearMonth.of(2026, 11), viewModel.uiState.value.projection.focusedMonth)

      // Week & Day navigation
      viewModel.setViewMode(CalendarViewMode.WEEK)
      viewModel.navigateNext()
      assertEquals(LocalDate.of(2026, 12, 3), viewModel.uiState.value.projection.selectedDate)
      viewModel.setViewMode(CalendarViewMode.DAY)
      viewModel.navigatePrevious()
      assertEquals(LocalDate.of(2026, 12, 2), viewModel.uiState.value.projection.selectedDate)

      // Jump back to today
      viewModel.jumpToToday()
      assertEquals(LocalDate.of(2026, 11, 26), viewModel.uiState.value.projection.selectedDate)

      viewModel.onForegroundStopped()
      job.cancel()
      testScheduler.runCurrent()
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun calendarScreen_displaysMonthWeekDayAgendaPrefillsSelectedDateAndCleansUp() {
    val clock = ControllableCalendarClock()
    val container = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    val (task25, task28) =
      runBlocking {
        val t1 =
          (taskUseCases.createTask(
            TaskInput(
              title = "مراجعة الخطة",
              scheduledDate = "2026-11-25",
              startTime = "10:00",
              priority = TaskPriority.HIGH,
            )
          ) as TaskDomainResult.Success).value
        val t2 =
          (taskUseCases.createTask(
            TaskInput(
              title = "موعد الطبيب",
              scheduledDate = "2026-11-28",
              startTime = "14:30",
              priority = TaskPriority.MEDIUM,
            )
          ) as TaskDomainResult.Success).value
        t1 to t2
      }

    val lifecycleOwner = Test4BLifecycleOwner()
    val storeOwner = Test4BStoreOwner()
    storeOwners += storeOwner
    val openedTaskIds = mutableListOf<String>()

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalViewModelStoreOwner provides storeOwner,
      ) {
        ProvideYawmiLocale(language = AppLanguage.ARABIC) {
          YawmiTheme(themeMode = AppThemeMode.DARK) {
            CalendarScreen(
              language = AppLanguage.ARABIC,
              taskRepository = container.taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = clock,
              onOpenTaskDetail = { openedTaskIds += it },
            )
          }
        }
      }
    }

    val viewModel = ViewModelProvider(storeOwner)[CalendarViewModel::class.java]
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${task25.id}").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("calendar_month_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("calendar_agenda_count").assertTextEquals("1")
    assertEquals(1, registeredTimeReceiverCount())

    // Select 2026-11-28 -> agenda switches to task28
    composeTestRule.onNodeWithTag("calendar_day_2026-11-28").performClick()
    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("task_item_${task28.id}").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("calendar_today_btn").assertIsDisplayed()

    // Tap task title -> emits onOpenTaskDetail
    composeTestRule.onNodeWithTag("task_title_${task28.id}").performClick()
    assertEquals(listOf(task28.id), openedTaskIds)

    // Toggle Week and Day modes cleanly
    composeTestRule.onNodeWithTag("calendar_mode_week").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("calendar_week_strip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("calendar_week_day_2026-11-28").assertIsDisplayed()

    composeTestRule.onNodeWithTag("calendar_mode_day").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("calendar_day_summary").assertIsDisplayed()

    // Add task for selected day (2026-11-28) -> scheduledDate is prefilled!
    composeTestRule.onNodeWithTag("calendar_agenda_add_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("قراءة مقال تقني")
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()

    waitUntilWithIdle {
      runBlocking { container.taskRepository.observeTasksForDate("2026-11-28").first().size == 2 }
    }
    composeTestRule.onNodeWithTag("calendar_agenda_count").assertTextEquals("2")
    composeTestRule.onNodeWithText("قراءة مقال تقني").assertIsDisplayed()

    // Toggle completion on task28 directly from Calendar agenda
    composeTestRule.onNodeWithTag("task_complete_btn_${task28.id}").performClick()
    waitUntilWithIdle {
      runBlocking { container.taskRepository.getById(task28.id)?.status == TaskStatus.COMPLETED }
    }

    // Lifecycle stop unregisters cleanly
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
    ShadowLooper.idleMainLooper()
    assertEquals(0, registeredTimeReceiverCount())
    assertEquals(0, viewModel.activeMidnightTimerCount)
  }

  @Test
  fun calendarScreen_wideTabletLayout_rendersTwoPaneSideBySide() {
    val clock = ControllableCalendarClock()
    val container = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    runBlocking {
      taskUseCases.createTask(
        TaskInput(title = "Tablet agenda item", scheduledDate = "2026-11-25")
      )
    }

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
        YawmiTheme(themeMode = AppThemeMode.LIGHT) {
          Box(modifier = Modifier.size(width = 960.dp, height = 700.dp)) {
            CalendarScreen(
              language = AppLanguage.ENGLISH,
              taskRepository = container.taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = clock,
              onOpenTaskDetail = {},
            )
          }
        }
      }
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithText("Tablet agenda item").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("calendar_month_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("calendar_agenda_list").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tablet agenda item").assertIsDisplayed()
  }

  private inline fun waitUntilCondition(
    timeoutMs: Long = 5_000L,
    onPoll: () -> Unit = {},
    condition: () -> Boolean,
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
      onPoll()
      if (condition()) return
      assertTrue("Timed out waiting for condition", System.currentTimeMillis() < deadline)
      Thread.sleep(10)
    }
  }
}
