package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.category.CategoryDomainResult
import com.example.domain.category.CategoryInput
import com.example.domain.category.CategoryUseCases
import com.example.domain.category.CategoryValidationError
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.feature.tasks.components.CategoryManagerSheet
import com.example.feature.tasks.components.TaskEditorSheet
import com.example.ui.theme.YawmiTheme
import java.time.LocalDate
import java.time.LocalTime
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

private class Unit3B1Clock(
  private val date: LocalDate = LocalDate.of(2026, 11, 25),
  private val time: LocalTime = LocalTime.of(9, 0),
  private val epochMs: Long = 1_764_000_000_000L,
) : LocalClock {
  override fun currentDate(): LocalDate = date

  override fun currentTime(): LocalTime = time

  override fun currentEpochMs(): Long = epochMs

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit3B1EditorAndCategoryTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: YawmiDatabase
  private lateinit var container: DefaultAppContainer
  private lateinit var clock: Unit3B1Clock
  private lateinit var taskUseCases: TaskUseCases
  private lateinit var categoryUseCases: CategoryUseCases

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = YawmiDatabase.inMemory(context)
    container = DefaultAppContainer(context = context, databaseProvider = { db })
    clock = Unit3B1Clock()
    taskUseCases = TaskUseCases(container.taskRepository, container.categoryRepository, clock)
    categoryUseCases = CategoryUseCases(container.categoryRepository, clock)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun categoryUseCases_validatesCrudAndSetNullOnTasks() = runBlocking {
    val blankResult = categoryUseCases.createCategory(CategoryInput(name = "   "))
    assertTrue(blankResult is CategoryDomainResult.Failure)
    assertEquals(
      CategoryValidationError.EmptyName,
      (blankResult as CategoryDomainResult.Failure).error,
    )

    val created =
      (categoryUseCases.createCategory(CategoryInput(name = "Work", colorToken = "amber"))
          as CategoryDomainResult.Success)
        .value
    val task =
      (taskUseCases.createTask(TaskInput(title = "Linked task", categoryId = created.id))
          as TaskDomainResult.Success)
        .value
    assertEquals(created.id, task.categoryId)

    val updated =
      (categoryUseCases.updateCategory(created.id, CategoryInput(name = "Career", colorToken = "emerald"))
          as CategoryDomainResult.Success)
        .value
    assertEquals("Career", updated.name)
    assertEquals("emerald", updated.colorToken)

    assertTrue(categoryUseCases.deleteCategory(created.id))
    assertNull(container.categoryRepository.getById(created.id))
    assertNull(container.taskRepository.getById(task.id)?.categoryId)
  }

  @Test
  fun categoryManagerSheet_addsEditsAndDeletesCategoriesInRoom() {
    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ARABIC) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          val categories by categoryUseCases.observeActiveCategories().collectAsState(initial = emptyList())
          CategoryManagerSheet(
            categories = categories,
            categoryUseCases = categoryUseCases,
            onDismiss = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("category_empty_text").assertIsDisplayed()

    // Submit empty -> error shown
    composeTestRule.onNodeWithTag("category_save_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("category_name_error", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("category_name_error", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("اسم التصنيف لا يمكن أن يكون فارغاً.").assertIsDisplayed()

    // Create category
    composeTestRule.onNodeWithTag("category_name_input").performTextInput("العمل")
    composeTestRule.onNodeWithTag("category_color_emerald").performClick()
    composeTestRule.onNodeWithTag("category_save_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithText("العمل").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("العمل").assertIsDisplayed()

    val saved = runBlocking { container.categoryRepository.observeActiveCategories().first().single() }
    assertEquals("العمل", saved.name)
    assertEquals("emerald", saved.colorToken)

    // Edit category
    composeTestRule.onNodeWithTag("category_edit_${saved.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("category_name_input").performTextClearance()
    composeTestRule.onNodeWithTag("category_name_input").performTextInput("المشاريع")
    composeTestRule.onNodeWithTag("category_save_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithText("المشاريع").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("المشاريع").assertIsDisplayed()

    // Delete category
    composeTestRule.onNodeWithTag("category_delete_${saved.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("category_empty_text").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("category_empty_text").assertIsDisplayed()
    assertTrue(runBlocking { container.categoryRepository.observeActiveCategories().first().isEmpty() })
  }

  @Test
  fun taskEditorSheet_createsTitleOnlyTask_andValidatesInvalidTimes() {
    var savedTask: TaskEntity? = null

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ARABIC) {
        YawmiTheme(themeMode = AppThemeMode.DARK) {
          val categories by categoryUseCases.observeActiveCategories().collectAsState(initial = emptyList())
          TaskEditorSheet(
            existingTask = null,
            categories = categories,
            taskUseCases = taskUseCases,
            localClock = clock,
            onManageCategoriesClick = {},
            onSaved = { savedTask = it },
            onDismiss = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    // Blank title shows error and persists nothing
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("task_title_error", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_title_error", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    assertTrue(runBlocking { container.taskRepository.observeAllTasks().first().isEmpty() })

    // Invalid end time earlier than start time
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("مراجعة المسودة")
    composeTestRule.onNodeWithTag("task_start_time_input").performScrollTo().performTextInput("10:00")
    composeTestRule.onNodeWithTag("task_end_time_input").performScrollTo().performTextInput("09:00")
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("task_end_time_error", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("task_end_time_error", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    assertTrue(runBlocking { container.taskRepository.observeAllTasks().first().isEmpty() })

    // Clear times and submit title only -> default MEDIUM, PENDING, null scheduledDate
    composeTestRule.onNodeWithTag("task_start_time_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("task_end_time_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) { savedTask != null }
    composeTestRule.waitForIdle()

    val created = savedTask!!
    assertEquals("مراجعة المسودة", created.title)
    assertNull(created.scheduledDate)
    assertEquals(TaskPriority.MEDIUM, created.priority)
    assertEquals(TaskStatus.PENDING, created.status)
    assertEquals(created.id, runBlocking { container.taskRepository.getById(created.id)?.id })
  }

  @Test
  fun taskEditorSheet_createsFullScheduledTask_andEditsExistingTaskInEnglishLtr() {
    val category = runBlocking {
      (categoryUseCases.createCategory(CategoryInput(name = "Study", colorToken = "amber"))
          as CategoryDomainResult.Success)
        .value
    }
    var editingTask by mutableStateOf<TaskEntity?>(null)
    var savedCount by mutableStateOf(0)

    composeTestRule.setContent {
      ProvideYawmiLocale(language = AppLanguage.ENGLISH) {
        YawmiTheme(themeMode = AppThemeMode.LIGHT) {
          val categories by categoryUseCases.observeActiveCategories().collectAsState(initial = emptyList())
          TaskEditorSheet(
            existingTask = editingTask,
            categories = categories,
            taskUseCases = taskUseCases,
            localClock = clock,
            onManageCategoriesClick = {},
            onSaved = {
              editingTask = it
              savedCount += 1
            },
            onDismiss = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("New Task").assertIsDisplayed()

    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("Read chapters 1-3")
    composeTestRule.onNodeWithTag("task_date_tomorrow").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_start_time_input").performScrollTo().performTextInput("09:30")
    composeTestRule.onNodeWithTag("task_end_time_input").performScrollTo().performTextInput("10:45")
    composeTestRule.onNodeWithTag("task_priority_high").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_category_${category.id}").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) { savedCount == 1 }
    composeTestRule.waitForIdle()

    val persisted = runBlocking { container.taskRepository.observeAllTasks().first().single() }
    assertEquals("Read chapters 1-3", persisted.title)
    assertEquals("2026-11-26", persisted.scheduledDate)
    assertEquals("09:30", persisted.startTime)
    assertEquals("10:45", persisted.endTime)
    assertEquals(TaskPriority.HIGH, persisted.priority)
    assertEquals(category.id, persisted.categoryId)

    // Edit existing task
    composeTestRule.onNodeWithTag("task_editor_heading").performScrollTo()
    composeTestRule.onNodeWithText("Edit Task").assertIsDisplayed()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("task_title_input").performScrollTo().performTextInput("Read chapters 1-4")
    composeTestRule.onNodeWithTag("task_priority_low").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("task_save_btn").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) { savedCount == 2 }
    composeTestRule.waitForIdle()

    val updated = runBlocking { container.taskRepository.getById(persisted.id)!! }
    assertEquals("Read chapters 1-4", updated.title)
    assertEquals(TaskPriority.LOW, updated.priority)
  }
}
