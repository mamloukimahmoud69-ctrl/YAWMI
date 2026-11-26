package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.core.di.AppContainer
import com.example.core.preferences.AppAppearanceRepository
import com.example.core.preferences.AppAppearanceState
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.core.time.formatLocalizedHeader
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeAppearanceRepository(
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

private class FixedLocalClock(
  private val date: LocalDate = LocalDate.of(2026, 11, 25),
  private val time: LocalTime = LocalTime.of(9, 30),
  private val epochMs: Long = 1_764_063_000_000L,
  private val period: GreetingPeriod = GreetingPeriod.MORNING,
) : LocalClock {
  override fun currentDate(): LocalDate = date

  override fun currentTime(): LocalTime = time

  override fun currentEpochMs(): Long = epochMs

  override fun greetingPeriod(): GreetingPeriod = period
}

private class TestAppContainer(
  override val appearanceRepository: AppAppearanceRepository = FakeAppearanceRepository(),
  override val localClock: LocalClock = FixedLocalClock(),
) : AppContainer {
  private val backing by lazy {
    com.example.core.di.DefaultAppContainer(
      context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
      databaseProvider = { com.example.data.local.db.YawmiDatabase.inMemory(it) },
    )
  }
  override val database get() = backing.database
  override val categoryRepository get() = backing.categoryRepository
  override val taskRepository get() = backing.taskRepository
  override val habitRepository get() = backing.habitRepository
  override val goalRepository get() = backing.goalRepository
  override val noteRepository get() = backing.noteRepository
  override val reminderSyncRepository get() = backing.reminderSyncRepository
  override val taskUseCases get() = backing.taskUseCases
  override val categoryUseCases get() = backing.categoryUseCases
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase1FoundationTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mainActivity_launchesSuccessfully() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertTrue(activity.application is YawmiApplication)
  }

  @Test
  fun rootApp_defaultsToArabicRtl_andNavigatesAllFiveDestinations() {
    val container = TestAppContainer()

    composeTestRule.setContent {
      YawmiRootApp(container = container)
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("today_greeting_label").assertIsDisplayed()
    composeTestRule.onNodeWithText("صباح الخير").assertIsDisplayed()

    val rootBounds = composeTestRule.onRoot().getBoundsInRoot()
    val fabBounds = composeTestRule.onNodeWithTag("fab_quick_add").getBoundsInRoot()
    val rootCenterX = (rootBounds.left + rootBounds.right) / 2f
    assertTrue(
      "Expected Alignment.BottomEnd FAB to sit on the left side under Arabic RTL, but left=${fabBounds.left}, center=$rootCenterX",
      fabBounds.left < rootCenterX,
    )

    composeTestRule.onNodeWithTag("nav_tasks").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_tasks").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_calendar").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_calendar").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_habits_goals").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_habits_goals").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_stats_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_stats_settings").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_today").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("today_greeting_label").assertIsDisplayed()
  }

  @Test
  fun today_quickAddFab_opensTaskEditorSheet() {
    val container = TestAppContainer()

    composeTestRule.setContent {
      YawmiRootApp(container = container)
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("fab_quick_add").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("task_editor_sheet").assertIsDisplayed()
  }

  @Test
  fun settings_switchesLanguageToEnglishLtr_andBackToArabicRtl() {
    val fakeRepo = FakeAppearanceRepository()
    val container = TestAppContainer(appearanceRepository = fakeRepo)

    composeTestRule.setContent {
      YawmiRootApp(container = container)
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_stats_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("الإحصائيات والإعدادات").assertIsDisplayed()
    val arChipRtlBounds = composeTestRule.onNodeWithTag("lang_chip_ar").getBoundsInRoot()
    val enChipRtlBounds = composeTestRule.onNodeWithTag("lang_chip_en").getBoundsInRoot()
    assertTrue(
      "Expected Arabic chip to be on the right of English chip in RTL layout",
      arChipRtlBounds.left > enChipRtlBounds.left,
    )

    composeTestRule.onNodeWithTag("lang_chip_en").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Statistics & Settings").assertIsDisplayed()
    val arChipLtrBounds = composeTestRule.onNodeWithTag("lang_chip_ar").getBoundsInRoot()
    val enChipLtrBounds = composeTestRule.onNodeWithTag("lang_chip_en").getBoundsInRoot()
    assertTrue(
      "Expected Arabic chip to be on the left of English chip in LTR layout",
      arChipLtrBounds.left < enChipLtrBounds.left,
    )

    composeTestRule.onNodeWithTag("lang_chip_ar").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("الإحصائيات والإعدادات").assertIsDisplayed()
    val arChipRestoredBounds = composeTestRule.onNodeWithTag("lang_chip_ar").getBoundsInRoot()
    val enChipRestoredBounds = composeTestRule.onNodeWithTag("lang_chip_en").getBoundsInRoot()
    assertTrue(
      "Expected Arabic chip to return to the right of English chip in RTL layout",
      arChipRestoredBounds.left > enChipRestoredBounds.left,
    )
  }

  @Test
  fun settings_switchesThemeMode_betweenDarkLightAndSystem() = runBlocking {
    val fakeRepo = FakeAppearanceRepository()
    val container = TestAppContainer(appearanceRepository = fakeRepo)

    composeTestRule.setContent {
      YawmiRootApp(container = container)
    }

    composeTestRule.waitForIdle()
    assertEquals(AppThemeMode.DARK, fakeRepo.appearanceState.first().themeMode)

    composeTestRule.onNodeWithTag("nav_stats_settings").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("theme_chip_light").performClick()
    composeTestRule.waitForIdle()
    assertEquals(AppThemeMode.LIGHT, fakeRepo.appearanceState.first().themeMode)

    composeTestRule.onNodeWithTag("theme_chip_system").performClick()
    composeTestRule.waitForIdle()
    assertEquals(AppThemeMode.SYSTEM, fakeRepo.appearanceState.first().themeMode)

    composeTestRule.onNodeWithTag("theme_chip_dark").performClick()
    composeTestRule.waitForIdle()
    assertEquals(AppThemeMode.DARK, fakeRepo.appearanceState.first().themeMode)
  }

  @Test
  fun localDate_formatsArabicAndEnglishHeaders() {
    val date = LocalDate.of(2026, 11, 25)
    val ar = date.formatLocalizedHeader(AppLanguage.ARABIC)
    val en = date.formatLocalizedHeader(AppLanguage.ENGLISH)
    assertTrue(ar.contains("2026"))
    assertTrue(en.contains("2026"))
  }
}
