package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.data.repository.ReminderOutboxCoordinator
import com.example.data.repository.RoomCategoryRepository
import com.example.data.repository.RoomTaskRepository
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class MutableTestClock(
  var date: LocalDate = LocalDate.of(2026, 11, 25),
  var time: LocalTime = LocalTime.of(10, 0),
  var epochMs: Long = 1_764_000_000_000L,
) : LocalClock {
  override fun currentDate(): LocalDate = date

  override fun currentTime(): LocalTime = time

  override fun currentEpochMs(): Long = epochMs

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit3ATaskPersistenceTest {

  private lateinit var context: Context
  private val dbName = "unit3a_task_persistence_test.db"
  private lateinit var clock: MutableTestClock

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(dbName)
    clock = MutableTestClock()
  }

  @After
  fun tearDown() {
    context.deleteDatabase(dbName)
  }

  private fun buildStack(db: YawmiDatabase): Triple<RoomTaskRepository, RoomCategoryRepository, TaskUseCases> {
    val categoryRepo = RoomCategoryRepository(db.categoryDao())
    val outboxCoordinator =
      ReminderOutboxCoordinator(
        appPackageName = context.packageName,
        appMetadataDao = db.appMetadataDao(),
        reminderDao = db.reminderDao(),
        alarmSyncOutboxDao = db.alarmSyncOutboxDao(),
      )
    val taskRepo =
      RoomTaskRepository(
        database = db,
        taskDao = db.taskDao(),
        recurrenceDao = db.recurrenceDao(),
        reminderDao = db.reminderDao(),
        deliveryDao = db.reminderDeliveryDao(),
        outboxCoordinator = outboxCoordinator,
      )
    val useCases = TaskUseCases(taskRepo, categoryRepo, clock)
    return Triple(taskRepo, categoryRepo, useCases)
  }

  @Test
  fun taskUseCases_persistLifecycleAcrossRestart_andEnforceValidation() = runBlocking {
    val db1 = YawmiDatabase.openWithName(context, dbName)
    val (taskRepo1, categoryRepo1, useCases1) = buildStack(db1)

    categoryRepo1.upsert(
      CategoryEntity("cat-work", "Work", "amber", "work", sortOrder = 0, updatedAtEpochMs = clock.epochMs)
    )

    // Reject blank title -> zero rows persisted
    val blankFail = useCases1.createTask(TaskInput(title = "   "))
    assertTrue(blankFail is TaskDomainResult.Failure)
    assertTrue(taskRepo1.observeAllTasks().first().isEmpty())

    // Reject non-existent categoryId
    val missingCategoryFail =
      useCases1.createTask(TaskInput(title = "Categorized", categoryId = "does-not-exist"))
    assertTrue(
      (missingCategoryFail as TaskDomainResult.Failure).errors.any {
        it is TaskValidationError.CategoryNotFound
      }
    )

    // Create minimal title-only task
    val minimalCreated =
      (useCases1.createTask(TaskInput(title = "Minimal task")) as TaskDomainResult.Success).value
    assertNull(minimalCreated.scheduledDate)
    assertEquals(TaskPriority.MEDIUM, minimalCreated.priority)
    assertEquals(TaskStatus.PENDING, minimalCreated.status)

    // Create full scheduled task and transition: PENDING -> COMPLETED -> Reopen -> Postpone
    val scheduled =
      (useCases1.createTask(
          TaskInput(
            title = "Quarterly plan",
            description = "Draft Q1 goals",
            scheduledDate = "2026-11-25",
            startTime = "09:00",
            endTime = "10:30",
            priority = TaskPriority.HIGH,
            categoryId = "cat-work",
          )
        ) as TaskDomainResult.Success)
        .value

    clock.epochMs += 5_000L
    val completed = (useCases1.completeTask(scheduled.id) as TaskDomainResult.Success).value
    assertEquals(TaskStatus.COMPLETED, completed.status)
    assertEquals(clock.epochMs, completed.completedAtEpochMs)

    // Direct COMPLETED -> CANCELLED rejected in Room too
    val badTransition = useCases1.cancelTask(scheduled.id)
    assertTrue(badTransition is TaskDomainResult.Failure)
    assertEquals(TaskStatus.COMPLETED, taskRepo1.getById(scheduled.id)?.status)

    // Reopen clears completedAtEpochMs in SQLite
    clock.epochMs += 5_000L
    val reopened = (useCases1.reopenTask(scheduled.id) as TaskDomainResult.Success).value
    assertEquals(TaskStatus.PENDING, reopened.status)
    assertNull(reopened.completedAtEpochMs)

    // Postpone active task
    clock.epochMs += 5_000L
    val postponed =
      (useCases1.postponeTask(scheduled.id, LocalDate.of(2026, 11, 29)) as TaskDomainResult.Success)
        .value
    assertEquals("2026-11-29", postponed.scheduledDate)

    db1.close()

    // Simulate app restart by reopening the same Room DB file
    val db2 = YawmiDatabase.openWithName(context, dbName)
    val (taskRepo2, _, useCases2) = buildStack(db2)

    val persistedMinimal = taskRepo2.getById(minimalCreated.id)
    assertEquals("Minimal task", persistedMinimal?.title)
    assertNull(persistedMinimal?.scheduledDate)
    assertEquals(TaskStatus.PENDING, persistedMinimal?.status)

    val persistedScheduled = taskRepo2.getById(scheduled.id)
    assertEquals("Quarterly plan", persistedScheduled?.title)
    assertEquals("2026-11-29", persistedScheduled?.scheduledDate)
    assertEquals(TaskStatus.PENDING, persistedScheduled?.status)
    assertNull(persistedScheduled?.completedAtEpochMs)

    // Complete again after restart and verify completedAt survives reopen
    clock.epochMs += 10_000L
    val reCompleted = (useCases2.completeTask(scheduled.id) as TaskDomainResult.Success).value
    assertEquals(clock.epochMs, reCompleted.completedAtEpochMs)
    db2.close()

    val db3 = YawmiDatabase.openWithName(context, dbName)
    val (taskRepo3, _, _) = buildStack(db3)
    val afterSecondRestart = taskRepo3.getById(scheduled.id)
    assertEquals(TaskStatus.COMPLETED, afterSecondRestart?.status)
    assertEquals(clock.epochMs, afterSecondRestart?.completedAtEpochMs)
    db3.close()
  }
}
