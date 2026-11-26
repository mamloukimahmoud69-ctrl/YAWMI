package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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
import com.example.core.time.DatePickerLocalDateMapper
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryUseCases
import com.example.domain.task.CalendarProjectionRules
import com.example.domain.task.CalendarViewMode
import com.example.domain.task.DstAdjustmentPolicy
import com.example.domain.task.ReminderTriggerCalculator
import com.example.domain.task.ScheduleTimingDecision
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TriggerEvaluationStatus
import com.example.feature.tasks.TasksScreen
import com.example.feature.tasks.TasksUiState
import com.example.feature.tasks.TasksViewModel
import com.example.feature.tasks.detail.TaskDetailScreen
import com.example.feature.tasks.detail.TaskDetailUiState
import com.example.feature.tasks.detail.TaskDetailViewModel
import com.example.ui.theme.YawmiTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.concurrent.TimeUnit
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

private class ControllableUnit4AClock(
  initialInstant: Instant = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
  initialZone: ZoneId = ZoneId.of("UTC"),
) : LocalClock {
  @Volatile private var instant: Instant = initialInstant
  @Volatile private var zone: ZoneId = initialZone

  fun setZonedDateTime(date: LocalDate, time: LocalTime, zoneId: ZoneId = zone) {
    zone = zoneId
    instant = ZonedDateTime.of(date, time, zoneId).toInstant()
  }

  fun setZoneOnly(zoneId: ZoneId) {
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

private class Test4ALifecycleOwner(
  initialState: Lifecycle.State = Lifecycle.State.RESUMED,
) : LifecycleOwner {
  private val registry = LifecycleRegistry(this).apply { currentState = initialState }
  override val lifecycle: Lifecycle
    get() = registry

  fun handleEvent(event: Lifecycle.Event) {
    registry.handleLifecycleEvent(event)
  }
}

private class Test4AStoreOwner : ViewModelStoreOwner {
  override val viewModelStore: ViewModelStore = ViewModelStore()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit4ATimeAndDstTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var application: Application
  private val openDbs = mutableListOf<YawmiDatabase>()
  private val storeOwners = mutableListOf<Test4AStoreOwner>()
  private var originalDefaultTimeZone: TimeZone? = null

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    application = context as Application
    originalDefaultTimeZone = TimeZone.getDefault()
  }

  @After
  fun tearDown() {
    originalDefaultTimeZone?.let { TimeZone.setDefault(it) }
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
  fun datePickerLocalDateMapper_respectsExistingDateAndLocalClockFallback() {
    val laZone = ZoneId.of("America/Los_Angeles")
    // Instant is 2026-11-26T04:00Z, which is still 2026-11-25 20:00 in America/Los_Angeles!
    val clock =
      ControllableUnit4AClock(
        initialInstant = Instant.parse("2026-11-26T04:00:00Z"),
        initialZone = laZone,
      )
    val fallbackMillis = DatePickerLocalDateMapper.initialPickerMillis(null, clock)
    assertEquals(LocalDate.of(2026, 11, 25), DatePickerLocalDateMapper.pickerMillisToLocalDate(fallbackMillis))

    val existingMillis = DatePickerLocalDateMapper.initialPickerMillis("2026-12-03", clock)
    assertEquals(LocalDate.of(2026, 12, 3), DatePickerLocalDateMapper.pickerMillisToLocalDate(existingMillis))
  }

  @Test
  fun reminderTriggerCalculator_handlesDstGapOverlapAnd15MinGraceWindow() {
    val nyZone = ZoneId.of("America/New_York")

    // 1. DST Spring-Forward Gap: 2026-03-08 02:30 jumps 02:00 -> 03:00 (shifted forward to 03:30 EDT)
    val gapResolved = ReminderTriggerCalculator.resolveTrigger("2026-03-08", "02:30", nyZone)
    assertNotNull(gapResolved)
    assertEquals(DstAdjustmentPolicy.GAP_SHIFTED_FORWARD, gapResolved!!.dstAdjustment)
    assertEquals(LocalTime.of(3, 30), gapResolved.zonedDateTime.toLocalTime())
    assertEquals(ZoneOffset.ofHours(-4), gapResolved.zonedDateTime.offset)

    // 2. DST Fall-Back Overlap: 2026-11-01 01:30 selects earlier offset (-04:00 EDT)
    val overlapResolved = ReminderTriggerCalculator.resolveTrigger("2026-11-01", "01:30", nyZone)
    assertNotNull(overlapResolved)
    assertEquals(DstAdjustmentPolicy.OVERLAP_EARLIER_OFFSET, overlapResolved!!.dstAdjustment)
    assertEquals(LocalTime.of(1, 30), overlapResolved.zonedDateTime.toLocalTime())
    assertEquals(ZoneOffset.ofHours(-4), overlapResolved.zonedDateTime.offset)

    // 3. Normal day & Grace Window checks (15 min inclusive)
    val normalResolved = ReminderTriggerCalculator.resolveTrigger("2026-11-25", "10:00", nyZone)!!
    assertEquals(DstAdjustmentPolicy.NONE, normalResolved.dstAdjustment)
    val triggerMs = normalResolved.triggerEpochMs

    assertEquals(
      TriggerEvaluationStatus.ON_TIME_OR_FUTURE,
      ReminderTriggerCalculator.evaluateAtFire("2026-11-25", "10:00", 1L, triggerMs, nyZone).status,
    )
    assertEquals(
      TriggerEvaluationStatus.FIRE_WITHIN_GRACE,
      ReminderTriggerCalculator.evaluateAtFire(
          "2026-11-25",
          "10:00",
          1L,
          triggerMs + 15L * 60_000L,
          nyZone,
        )
        .status,
    )
    assertEquals(
      TriggerEvaluationStatus.EXPIRED,
      ReminderTriggerCalculator.evaluateAtFire(
          "2026-11-25",
          "10:00",
          1L,
          triggerMs + 15L * 60_000L + 1L,
          nyZone,
        )
        .status,
    )
    assertEquals(
      TriggerEvaluationStatus.PREMATURE_CLOCK_ROLLBACK,
      ReminderTriggerCalculator.evaluateAtFire(
          "2026-11-25",
          "10:00",
          1L,
          triggerMs - 120_000L,
          nyZone,
        )
        .status,
    )

    assertEquals(
      ScheduleTimingDecision.FIRE_IMMEDIATELY_WITHIN_GRACE,
      ReminderTriggerCalculator.evaluateForSchedule(
          "2026-11-25",
          "10:00",
          2L,
          triggerMs + 5L * 60_000L,
          nyZone,
        )
        .decision,
    )
    assertEquals(
      ScheduleTimingDecision.EXPIRED_OUTSIDE_GRACE,
      ReminderTriggerCalculator.evaluateForSchedule(
          "2026-11-25",
          "10:00",
          2L,
          triggerMs + 20L * 60_000L,
          nyZone,
        )
        .decision,
    )
  }

  @Test
  fun calendarProjectionRules_buildsMonthGridWeekStripAndDayAgendaAccurately() = runBlocking {
    val container = newInMemoryContainer()
    val clock = ControllableUnit4AClock()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)

    val tUntimed =
      (taskUseCases.createTask(
        TaskInput(title = "Untimed high", scheduledDate = "2026-11-25", priority = TaskPriority.HIGH)
      ) as TaskDomainResult.Success).value
    val tLate =
      (taskUseCases.createTask(
        TaskInput(title = "Afternoon", scheduledDate = "2026-11-25", startTime = "15:00")
      ) as TaskDomainResult.Success).value
    val tEarlyDone =
      (taskUseCases.createTask(
        TaskInput(title = "Early done", scheduledDate = "2026-11-25", startTime = "08:00")
      ) as TaskDomainResult.Success).value
    taskUseCases.completeTask(tEarlyDone.id)
    val tEarlyActive =
      (taskUseCases.createTask(
        TaskInput(title = "Morning", scheduledDate = "2026-11-25", startTime = "09:00")
      ) as TaskDomainResult.Success).value
    taskUseCases.createTask(
      TaskInput(title = "Overdue earlier", scheduledDate = "2026-11-23")
    )

    val allTasks = container.taskRepository.observeAllTasks().first()

    val projection =
      CalendarProjectionRules.compute(
        tasks = allTasks,
        categoriesById = emptyMap(),
        selectedDate = LocalDate.of(2026, 11, 25),
        focusedMonth = YearMonth.of(2026, 11),
        todayDate = LocalDate.of(2026, 11, 25),
        mode = CalendarViewMode.MONTH,
        firstDayOfWeek = DayOfWeek.SATURDAY,
      )

    assertEquals(DayOfWeek.SATURDAY, projection.weekdayHeaders.first())
    assertEquals(0, projection.monthDays.size % 7)
    assertEquals(7, projection.weekDays.size)
    assertEquals(5, projection.scheduledTaskCountInMonth)

    val day25 = projection.monthDays.first { it.date == LocalDate.of(2026, 11, 25) }
    assertTrue(day25.isToday)
    assertTrue(day25.isSelected)
    assertEquals(4, day25.totalCount)
    assertEquals(3, day25.activeCount)
    assertEquals(1, day25.completedCount)
    assertEquals(0, day25.overdueCount)

    val day23 = projection.weekDays.first { it.date == LocalDate.of(2026, 11, 23) }
    assertEquals(1, day23.overdueCount)

    // Agenda order: active timed ascending -> active untimed -> completed
    assertEquals(
      listOf(tEarlyActive.id, tLate.id, tUntimed.id, tEarlyDone.id),
      projection.selectedDayTasks.map { it.task.id },
    )
  }

  @Test
  fun tasksViewModel_midnightRolloverAndTimezoneUpdatesOverdueDeterministically() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(testDispatcher)
    try {
      val clock =
        ControllableUnit4AClock(
          initialInstant = ZonedDateTime.of(2026, 11, 25, 23, 59, 50, 0, ZoneId.of("UTC")).toInstant(),
          initialZone = ZoneId.of("UTC"),
        )
      val container = newInMemoryContainer()
      val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
      val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)
      val task =
        (taskUseCases.createTask(
          TaskInput(title = "Task rollover", scheduledDate = "2026-11-25")
        ) as TaskDomainResult.Success).value

      val viewModel =
        TasksViewModel(
          taskRepository = container.taskRepository,
          categoryUseCases = categoryUseCases,
          taskUseCases = taskUseCases,
          localClock = clock,
          midnightDispatcher = testDispatcher,
        )
      val collected = mutableListOf<TasksUiState>()
      val job = launch { viewModel.uiState.collect { collected += it } }
      testScheduler.runCurrent()

      waitUntilCondition(onPoll = { testScheduler.runCurrent() }) {
        collected.any { !it.isLoading && it.items.size == 1 }
      }
      assertEquals(false, viewModel.uiState.value.items.single { it.task.id == task.id }.isOverdue)

      clock.advanceMillis(10_000L)
      testScheduler.advanceTimeBy(10_000L)
      testScheduler.runCurrent()

      assertEquals(LocalDate.of(2026, 11, 26), viewModel.uiState.value.todayDate)
      assertEquals(true, viewModel.uiState.value.items.single { it.task.id == task.id }.isOverdue)

      viewModel.onForegroundStopped()
      job.cancel()
      testScheduler.runCurrent()
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun taskDetailScreen_updatesFormattedTimestampsOnTimezoneBroadcastAndMidnightRollover() {
    val utcZone = ZoneId.of("UTC")
    val riyadhZone = ZoneId.of("Asia/Riyadh")
    val fixedCreatedInstant = Instant.parse("2026-11-25T20:00:00Z")
    val clock = ControllableUnit4AClock(initialInstant = fixedCreatedInstant, initialZone = utcZone)
    val container = newInMemoryContainer()
    val taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    val categoryUseCases = CategoryUseCases(container.categoryRepository, clock)

    val task =
      runBlocking {
        (taskUseCases.createTask(
          TaskInput(title = "Zone watch", scheduledDate = "2026-11-25")
        ) as TaskDomainResult.Success).value
      }

    val lifecycleOwner = Test4ALifecycleOwner()
    val storeOwner = Test4AStoreOwner()
    storeOwners += storeOwner

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalViewModelStoreOwner provides storeOwner,
      ) {
        ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
          YawmiTheme(themeMode = AppThemeMode.DARK) {
            TaskDetailScreen(
              taskId = task.id,
              taskRepository = container.taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = clock,
              onNavigateBack = {},
            )
          }
        }
      }
    }

    val viewModel = ViewModelProvider(storeOwner)["task_detail_${task.id}", TaskDetailViewModel::class.java]
    waitUntilWithIdle { viewModel.uiState.value is TaskDetailUiState.Loaded }

    composeTestRule.onNodeWithTag("task_detail_created_at").performScrollTo().assertTextEquals("2026-11-25 · 20:00")
    assertEquals(0, composeTestRule.onAllNodesWithTag("task_detail_overdue_badge").fetchSemanticsNodes().size)
    assertEquals(1, registeredTimeReceiverCount())

    // Broadcast timezone change to Asia/Riyadh (UTC+3): 20:00Z -> 23:00 local, still 2026-11-25
    clock.setZoneOnly(riyadhZone)
    context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle {
      (viewModel.uiState.value as? TaskDetailUiState.Loaded)?.zoneId == riyadhZone
    }
    composeTestRule.onNodeWithTag("task_detail_created_at").performScrollTo().assertTextEquals("2026-11-25 · 23:00")

    // Advance clock to 2026-11-26 in Riyadh and send ACTION_DATE_CHANGED -> task becomes overdue
    clock.setZonedDateTime(LocalDate.of(2026, 11, 26), LocalTime.of(8, 0), riyadhZone)
    context.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
    ShadowLooper.idleMainLooper()

    waitUntilWithIdle {
      (viewModel.uiState.value as? TaskDetailUiState.Loaded)?.isOverdue == true
    }
    composeTestRule.onNodeWithTag("task_detail_overdue_badge").performScrollTo().assertIsDisplayed()

    lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
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
