package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.example.core.time.formatLocalizedHeader
import com.example.data.local.db.YawmiDatabase
import com.example.domain.category.CategoryUseCases
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.feature.dashboard.TodayScreen
import com.example.feature.dashboard.TodayUiState
import com.example.feature.dashboard.TodayViewModel
import com.example.ui.theme.YawmiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private class ControllableTestClock(
  initialInstant: Instant = ZonedDateTime.of(2026, 11, 25, 9, 15, 0, 0, ZoneId.of("UTC")).toInstant(),
  initialZone: ZoneId = ZoneId.of("UTC"),
) : LocalClock {
  @Volatile private var instant: Instant = initialInstant
  @Volatile private var zone: ZoneId = initialZone

  fun setZonedDateTime(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId = zone,
  ) {
    zone = zoneId
    instant = ZonedDateTime.of(date, time, zoneId).toInstant()
  }

  fun setZone(zoneId: ZoneId) {
    zone = zoneId
  }

  fun advanceMillis(millis: Long) {
    instant = instant.plusMillis(millis)
  }

  override fun currentDate(): LocalDate = instant.atZone(zone).toLocalDate()

  override fun currentTime(): LocalTime = instant.atZone(zone).toLocalTime()

  override fun currentEpochMs(): Long = instant.toEpochMilli()

  override fun greetingPeriod(): GreetingPeriod {
    val hour = currentTime().hour
    return when {
      hour < 12 -> GreetingPeriod.MORNING
      hour < 17 -> GreetingPeriod.AFTERNOON
      else -> GreetingPeriod.EVENING
    }
  }

  override fun zoneId(): ZoneId = zone

  override fun millisUntilNextMidnight(): Long {
    val now = instant.atZone(zone)
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
  }
}

private class TestTodayLifecycleOwner(
  initialState: Lifecycle.State = Lifecycle.State.RESUMED,
) : LifecycleOwner {
  private val registry = LifecycleRegistry(this).apply { currentState = initialState }
  override val lifecycle: Lifecycle
    get() = registry

  fun handleEvent(event: Lifecycle.Event) {
    registry.handleLifecycleEvent(event)
  }
}

private class TestViewModelStoreOwner : ViewModelStoreOwner {
  override val viewModelStore: ViewModelStore = ViewModelStore()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit3B4TodayLifecycleAndTimeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var application: Application
  private val openDbs = mutableListOf<YawmiDatabase>()
  private val storeOwners = mutableListOf<TestViewModelStoreOwner>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    application = context as Application
  }

  @After
  fun tearDown() {
    storeOwners.forEach { it.viewModelStore.clear() }
    storeOwners.clear()
    openDbs.forEach { db ->
      if (db.isOpen) db.close()
    }
    openDbs.clear()
  }

  private fun newInMemoryContainer(clock: ControllableTestClock): DefaultAppContainer {
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
    if (!predicate()) {
      throw AssertionError("Condition still not satisfied after $timeoutMs ms")
    }
  }

  private fun mountTodayScreen(
    container: DefaultAppContainer,
    clock: ControllableTestClock,
    lifecycleOwner: TestTodayLifecycleOwner,
    viewModelStoreOwner: TestViewModelStoreOwner,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): TodayViewModel {
    storeOwners += viewModelStoreOwner
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalViewModelStoreOwner provides viewModelStoreOwner,
      ) {
        ProvideYawmiLocale(language = language) {
          YawmiTheme(themeMode = AppThemeMode.DARK) {
            TodayScreen(
              language = language,
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
    }

    waitUntilWithIdle {
      composeTestRule.onAllNodesWithTag("today_metric_total_value", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    return ViewModelProvider(viewModelStoreOwner)[TodayViewModel::class.java]
  }

  @Test
  fun localClock_millisUntilNextMidnight_respectsDstSpringForwardAndFallBack() {
    val nyZone = ZoneId.of("America/New_York")

    // 2026-03-08 is US Spring Forward (23-hour day)
    val springClock =
      ControllableTestClock(
        initialInstant = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, nyZone).toInstant(),
        initialZone = nyZone,
      )
    assertEquals(22L * 3_600_000L, springClock.millisUntilNextMidnight())

    // 2026-11-01 is US Fall Back (25-hour day)
    val fallClock =
      ControllableTestClock(
        initialInstant = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, nyZone).toInstant(),
        initialZone = nyZone,
      )
    assertEquals(24L * 3_600_000L + 30L * 60_000L, fallClock.millisUntilNextMidnight())
  }

  @Test
  fun todayViewModel_midnightRollover_updatesDateMetricsAndReschedulesNextMidnightDeterministically() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      Dispatchers.setMain(testDispatcher)
      try {
        val clock =
          ControllableTestClock(
            initialInstant =
              ZonedDateTime.of(2026, 11, 25, 23, 59, 50, 0, ZoneId.of("UTC")).toInstant(),
            initialZone = ZoneId.of("UTC"),
          )
        val container = newInMemoryContainer(clock)
        val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
        val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

        taskUseCases.createTask(TaskInput(title = "End of day task", scheduledDate = "2026-11-25"))

        val viewModel =
          TodayViewModel(
            taskRepository = container.taskRepository,
            categoryUseCases = categoryUseCases,
            taskUseCases = taskUseCases,
            localClock = clock,
            midnightDispatcher = testDispatcher,
          )

        val collected = mutableListOf<TodayUiState>()
        val collectJob = launch { viewModel.uiState.collect { collected += it } }
        testScheduler.runCurrent()

        // Allow Room in-memory query flow to emit initial snapshot
        waitUntilCondition(onPoll = { testScheduler.runCurrent() }) {
          collected.any { !it.isLoading && it.metrics.totalCount == 1 }
        }
        val beforeMidnight = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 11, 25), beforeMidnight.todayDate)
        assertEquals(1, beforeMidnight.metrics.remainingCount)
        assertEquals(0, beforeMidnight.metrics.overdueCount)
        assertEquals(1, viewModel.activeMidnightTimerCount)

        // Advance virtual clock across midnight (10 seconds) -> updates immediately to 2026-11-26
        clock.advanceMillis(10_000L)
        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()

        val afterFirstMidnight = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 11, 26), afterFirstMidnight.todayDate)
        assertEquals(0, afterFirstMidnight.metrics.remainingCount)
        assertEquals(1, afterFirstMidnight.metrics.overdueCount)
        assertEquals(1, afterFirstMidnight.metrics.totalCount)
        assertEquals(1, viewModel.activeMidnightTimerCount)

        // Advance another 24h -> proves next midnight deadline was recalculated after first rollover
        clock.advanceMillis(86_400_000L)
        testScheduler.advanceTimeBy(86_400_000L)
        testScheduler.runCurrent()

        assertEquals(LocalDate.of(2026, 11, 27), viewModel.uiState.value.todayDate)
        assertEquals(1, viewModel.activeMidnightTimerCount)

        viewModel.onForegroundStopped()
        collectJob.cancel()
        testScheduler.runCurrent()
      } finally {
        Dispatchers.resetMain()
      }
    }

  @Test
  fun todayScreen_midnightRolloverWhileInForeground_updatesHeaderAndMetricsLive() {
    val clock =
      ControllableTestClock(
        initialInstant =
          ZonedDateTime.of(2026, 11, 25, 23, 59, 50, 0, ZoneId.of("UTC")).toInstant(),
        initialZone = ZoneId.of("UTC"),
      )
    val container = newInMemoryContainer(clock)
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    runBlocking {
      taskUseCases.createTask(TaskInput(title = "Midnight watch", scheduledDate = "2026-11-25"))
    }

    val lifecycleOwner = TestTodayLifecycleOwner()
    val storeOwner = TestViewModelStoreOwner()
    val viewModel = mountTodayScreen(container, clock, lifecycleOwner, storeOwner)

    waitUntilWithIdle {
      viewModel.uiState.value.let { !it.isLoading && it.metrics.remainingCount == 1 }
    }
    composeTestRule.onNodeWithTag("today_date_label")
      .assertTextEquals(LocalDate.of(2026, 11, 25).formatLocalizedHeader(AppLanguage.ENGLISH))
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true)
      .assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_overdue_value", useUnmergedTree = true)
      .assertTextEquals("0")

    // Cross midnight while app remains in foreground
    clock.advanceMillis(10_000L)
    ShadowLooper.idleMainLooper(10_000L, TimeUnit.MILLISECONDS)

    waitUntilWithIdle { viewModel.uiState.value.todayDate == LocalDate.of(2026, 11, 26) }
    composeTestRule.onNodeWithTag("today_date_label")
      .assertTextEquals(LocalDate.of(2026, 11, 26).formatLocalizedHeader(AppLanguage.ENGLISH))
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true)
      .assertTextEquals("0")
    composeTestRule.onNodeWithTag("today_metric_overdue_value", useUnmergedTree = true)
      .assertTextEquals("1")
  }

  @Test
  fun todayScreen_returnFromBackgroundUnder5Seconds_updatesMetricsImmediatelyOnResume() {
    val clock =
      ControllableTestClock(
        initialInstant = ZonedDateTime.of(2026, 11, 25, 23, 50, 0, 0, ZoneId.of("UTC")).toInstant(),
        initialZone = ZoneId.of("UTC"),
      )
    val container = newInMemoryContainer(clock)
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    runBlocking {
      taskUseCases.createTask(TaskInput(title = "Short background task", scheduledDate = "2026-11-25"))
    }

    val lifecycleOwner = TestTodayLifecycleOwner()
    val storeOwner = TestViewModelStoreOwner()
    val viewModel = mountTodayScreen(container, clock, lifecycleOwner, storeOwner)

    waitUntilWithIdle { viewModel.uiState.value.metrics.remainingCount == 1 }

    // Background for only 500ms (< SharingStarted.WhileSubscribed(5_000L))
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
    composeTestRule.waitForIdle()
    ShadowLooper.idleMainLooper(500L, TimeUnit.MILLISECONDS)
    assertEquals(0, viewModel.activeMidnightTimerCount)

    // Date rolls over while backgrounded
    clock.setZonedDateTime(LocalDate.of(2026, 11, 26), LocalTime.of(8, 0))

    // Return to foreground -> ON_RESUME explicitly refreshes even under 5s
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle { viewModel.uiState.value.todayDate == LocalDate.of(2026, 11, 26) }
    composeTestRule.onNodeWithTag("today_date_label")
      .assertTextEquals(LocalDate.of(2026, 11, 26).formatLocalizedHeader(AppLanguage.ENGLISH))
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true)
      .assertTextEquals("0")
    composeTestRule.onNodeWithTag("today_metric_overdue_value", useUnmergedTree = true)
      .assertTextEquals("1")
    assertEquals(1, viewModel.activeMidnightTimerCount)
  }

  @Test
  fun todayScreen_returnFromBackgroundOver5Seconds_reloadsAndRecalculatesMetricsOnResume() {
    val clock =
      ControllableTestClock(
        initialInstant = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
        initialZone = ZoneId.of("UTC"),
      )
    val container = newInMemoryContainer(clock)
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    runBlocking {
      taskUseCases.createTask(TaskInput(title = "Long background task", scheduledDate = "2026-11-25"))
    }

    val lifecycleOwner = TestTodayLifecycleOwner()
    val storeOwner = TestViewModelStoreOwner()
    val viewModel = mountTodayScreen(container, clock, lifecycleOwner, storeOwner)

    waitUntilWithIdle { viewModel.uiState.value.metrics.remainingCount == 1 }

    // Background for 6_000ms (> SharingStarted.WhileSubscribed(5_000L))
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
    composeTestRule.waitForIdle()
    ShadowLooper.idleMainLooper(6_000L, TimeUnit.MILLISECONDS)

    // Date changes & new task exists when returning after > 5s
    clock.setZonedDateTime(LocalDate.of(2026, 11, 26), LocalTime.of(14, 0))
    runBlocking {
      taskUseCases.createTask(TaskInput(title = "Next day task", scheduledDate = "2026-11-26"))
    }

    lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
    lifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle {
      viewModel.uiState.value.let {
        it.todayDate == LocalDate.of(2026, 11, 26) &&
          it.metrics.totalCount == 2 &&
          it.metrics.overdueCount == 1 &&
          it.metrics.remainingCount == 1
      }
    }
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true)
      .assertTextEquals("2")
    composeTestRule.onNodeWithTag("today_metric_overdue_value", useUnmergedTree = true)
      .assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_remaining_value", useUnmergedTree = true)
      .assertTextEquals("1")
    composeTestRule.onNodeWithText("Afternoon", substring = true, ignoreCase = true)
      .assertIsDisplayed()
    assertEquals(1, viewModel.activeMidnightTimerCount)
  }

  @Test
  fun todayScreen_systemTimezoneDateAndTimeBroadcasts_updateMetricsAndScheduleLive() {
    // Task scheduled 2026-11-24, completed at 2026-11-24T22:30:00Z:
    // - In America/Los_Angeles (UTC-8): 2026-11-24 14:30 -> NOT completed today (2026-11-25)
    // - In Asia/Riyadh (UTC+3): 2026-11-25 01:30 -> COMPLETED today (2026-11-25)
    val laZone = ZoneId.of("America/Los_Angeles")
    val riyadhZone = ZoneId.of("Asia/Riyadh")
    val completedInstantMs = Instant.parse("2026-11-24T22:30:00Z").toEpochMilli()

    val clock =
      ControllableTestClock(
        initialInstant = ZonedDateTime.of(2026, 11, 25, 8, 0, 0, 0, laZone).toInstant(),
        initialZone = laZone,
      )
    val container = newInMemoryContainer(clock)
    runBlocking<Unit> {
      val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
      val created =
        (taskUseCases.createTask(
          TaskInput(title = "Timezone sensitive", scheduledDate = "2026-11-24")
        ) as TaskDomainResult.Success)
          .value
      container.taskRepository.update(
        created.copy(
          status = com.example.data.local.entity.TaskStatus.COMPLETED,
          completedAtEpochMs = completedInstantMs,
          updatedAtEpochMs = completedInstantMs,
        )
      )
    }

    val lifecycleOwner = TestTodayLifecycleOwner()
    val storeOwner = TestViewModelStoreOwner()
    val viewModel = mountTodayScreen(container, clock, lifecycleOwner, storeOwner)

    waitUntilWithIdle { !viewModel.uiState.value.isLoading }
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true)
      .assertTextEquals("0")
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true)
      .assertTextEquals("0")

    // 1. Broadcast ACTION_TIMEZONE_CHANGED to Asia/Riyadh
    clock.setZonedDateTime(LocalDate.of(2026, 11, 25), LocalTime.of(19, 0), riyadhZone)
    context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle { viewModel.uiState.value.metrics.completedCount == 1 }
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true)
      .assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_metric_total_value", useUnmergedTree = true)
      .assertTextEquals("1")
    composeTestRule.onNodeWithTag("today_progress_percent").assertTextEquals("100%")

    // 2. Broadcast ACTION_DATE_CHANGED to 2026-11-26
    clock.setZonedDateTime(LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), riyadhZone)
    context.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle { viewModel.uiState.value.todayDate == LocalDate.of(2026, 11, 26) }
    composeTestRule.onNodeWithTag("today_metric_completed_value", useUnmergedTree = true)
      .assertTextEquals("0")

    // 3. Broadcast ACTION_TIME_CHANGED to evening
    clock.setZonedDateTime(LocalDate.of(2026, 11, 26), LocalTime.of(20, 0), riyadhZone)
    context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle { viewModel.uiState.value.greetingPeriod == GreetingPeriod.EVENING }
    assertEquals(1, viewModel.activeMidnightTimerCount)
  }

  @Test
  fun todayScreen_repeatedLifecycleEvents_doNotDuplicateReceiversTimersOrRedundantComputations() {
    val clock = ControllableTestClock()
    val container = newInMemoryContainer(clock)
    val lifecycleOwner = TestTodayLifecycleOwner()
    val storeOwner = TestViewModelStoreOwner()
    var mountToday by mutableStateOf(true)

    storeOwners += storeOwner
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalViewModelStoreOwner provides storeOwner,
      ) {
        ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
          YawmiTheme(themeMode = AppThemeMode.DARK) {
            if (mountToday) {
              TodayScreen(
                language = AppLanguage.ENGLISH,
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
      }
    }

    val viewModel = ViewModelProvider(storeOwner)[TodayViewModel::class.java]
    waitUntilWithIdle { !viewModel.uiState.value.isLoading }

    assertEquals(1, registeredTimeReceiverCount())
    assertEquals(1, viewModel.activeMidnightTimerCount)
    val baselineComputeCount = viewModel.dashboardComputeCount

    // Repeated ON_PAUSE -> ON_RESUME without time/data change should not duplicate or recompute
    repeat(4) {
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
      ShadowLooper.idleMainLooper()
    }
    assertEquals(1, registeredTimeReceiverCount())
    assertEquals(1, viewModel.activeMidnightTimerCount)
    assertEquals(baselineComputeCount, viewModel.dashboardComputeCount)

    // Repeated full background/foreground cycles should register/unregister cleanly with no leaks
    repeat(4) {
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
      ShadowLooper.idleMainLooper()
      assertEquals(0, registeredTimeReceiverCount())
      assertEquals(0, viewModel.activeMidnightTimerCount)

      lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
      ShadowLooper.idleMainLooper()
      assertEquals(1, registeredTimeReceiverCount())
      assertEquals(1, viewModel.activeMidnightTimerCount)
    }

    // Disposing TodayScreen unregisters receiver cleanly
    mountToday = false
    composeTestRule.waitForIdle()
    ShadowLooper.idleMainLooper()
    assertEquals(0, registeredTimeReceiverCount())
    assertEquals(0, viewModel.activeMidnightTimerCount)
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
