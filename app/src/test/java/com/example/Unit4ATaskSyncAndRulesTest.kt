package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.time.GreetingPeriod
import com.example.core.time.LocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.entity.DeliveryState
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.OutboxAction
import com.example.data.local.entity.OutboxStatus
import com.example.data.local.entity.OutboxSystemTarget
import com.example.data.local.entity.RecurrenceFrequency
import com.example.data.local.entity.ReminderScheduleState
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.data.repository.ReminderOutboxCoordinator
import com.example.data.repository.RoomCategoryRepository
import com.example.data.repository.RoomGoalRepository
import com.example.data.repository.RoomReminderSyncRepository
import com.example.data.repository.RoomTaskRepository
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskRecurrenceInput
import com.example.domain.task.TaskRecurrenceRules
import com.example.domain.task.TaskReminderError
import com.example.domain.task.TaskReminderResult
import com.example.domain.task.TaskReminderUseCases
import com.example.domain.task.TaskUseCases
import com.example.domain.task.TaskValidationError
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class Unit4TestClock(
  date: LocalDate = LocalDate.of(2026, 11, 25),
  time: LocalTime = LocalTime.of(9, 0),
  private var zone: ZoneId = ZoneId.of("UTC"),
) : LocalClock {
  var epochMs: Long = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

  fun advanceMinutes(minutes: Long) {
    epochMs += minutes * 60_000L
  }

  override fun currentDate(): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

  override fun currentTime(): LocalTime = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()

  override fun currentEpochMs(): Long = epochMs

  override fun greetingPeriod(): GreetingPeriod = GreetingPeriod.MORNING

  override fun zoneId(): ZoneId = zone
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Unit4ATaskSyncAndRulesTest {

  private lateinit var context: Context
  private lateinit var db: YawmiDatabase
  private lateinit var clock: Unit4TestClock
  private lateinit var taskRepo: RoomTaskRepository
  private lateinit var reminderSyncRepo: RoomReminderSyncRepository
  private lateinit var goalRepo: RoomGoalRepository
  private lateinit var taskUseCases: TaskUseCases
  private lateinit var taskReminderUseCases: TaskReminderUseCases

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = YawmiDatabase.inMemory(context)
    clock = Unit4TestClock()
    val outboxCoordinator =
      ReminderOutboxCoordinator(
        appPackageName = context.packageName,
        appMetadataDao = db.appMetadataDao(),
        reminderDao = db.reminderDao(),
        alarmSyncOutboxDao = db.alarmSyncOutboxDao(),
      )
    val categoryRepo = RoomCategoryRepository(db.categoryDao())
    taskRepo =
      RoomTaskRepository(
        database = db,
        taskDao = db.taskDao(),
        recurrenceDao = db.recurrenceDao(),
        reminderDao = db.reminderDao(),
        deliveryDao = db.reminderDeliveryDao(),
        outboxCoordinator = outboxCoordinator,
        localClock = clock,
        goalDao = db.goalDao(),
      )
    reminderSyncRepo =
      RoomReminderSyncRepository(
        database = db,
        reminderDao = db.reminderDao(),
        deliveryDao = db.reminderDeliveryDao(),
        outboxDao = db.alarmSyncOutboxDao(),
        metadataDao = db.appMetadataDao(),
        outboxCoordinator = outboxCoordinator,
      )
    goalRepo =
      RoomGoalRepository(
        database = db,
        goalDao = db.goalDao(),
        reminderDao = db.reminderDao(),
        deliveryDao = db.reminderDeliveryDao(),
        outboxCoordinator = outboxCoordinator,
      )
    taskUseCases = TaskUseCases(taskRepo, categoryRepo, clock)
    taskReminderUseCases = TaskReminderUseCases(taskRepo, reminderSyncRepo, clock)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun editOrPostponeTimedTask_recalculatesReminders_marksPendingDeliveriesStale_andStagesSchedule() =
    runBlocking {
      val task =
        (taskUseCases.createTask(
          TaskInput(
            title = "Team review",
            scheduledDate = "2026-11-25",
            startTime = "10:00",
            endTime = "11:00",
          )
        ) as TaskDomainResult.Success)
          .value

      val rem =
        (taskReminderUseCases.createRelativeReminder(task.id, offsetMinutes = 15)
          as TaskReminderResult.Success)
          .value
      assertEquals("2026-11-25", rem.triggerDate)
      assertEquals("09:45", rem.triggerTime)
      assertEquals(1L, rem.scheduleRevision)

      val pendingDelivery =
        reminderSyncRepo.recordPendingTaskDelivery(
          deliveryId = "del-1",
          taskReminderId = rem.id,
          occurrenceKey = "2026-11-25T09:45",
          scheduledAtEpochMs = clock.currentEpochMs(),
          firedScheduleRevision = 1L,
          nowEpochMs = clock.currentEpochMs(),
        )

      clock.advanceMinutes(1)
      // Edit start time across midnight backward offset boundary
      taskUseCases.updateTask(
        task.id,
        TaskInput(
          title = "Team review",
          scheduledDate = "2026-11-26",
          startTime = "00:10",
          endTime = "01:00",
        ),
      )

      val afterEdit = reminderSyncRepo.getTaskReminders(task.id).single()
      assertEquals("2026-11-25", afterEdit.triggerDate)
      assertEquals("23:55", afterEdit.triggerTime)
      assertEquals(2L, afterEdit.scheduleRevision)
      assertEquals(ReminderScheduleState.PENDING_SYNC, afterEdit.scheduleState)
      assertTrue(afterEdit.isEnabled)
      assertEquals(
        DeliveryState.STALE,
        db.reminderDeliveryDao().getTaskDelivery(rem.id, pendingDelivery.occurrenceKey)?.state,
      )

      // Postpone task to 2026-11-28 while preserving time window -> recalculates reminder again
      clock.advanceMinutes(1)
      taskUseCases.postponeTask(task.id, LocalDate.of(2026, 11, 28), clearTimeWindow = false)
      val afterPostpone = reminderSyncRepo.getTaskReminders(task.id).single()
      assertEquals("2026-11-27", afterPostpone.triggerDate)
      assertEquals("23:55", afterPostpone.triggerTime)
      assertEquals(3L, afterPostpone.scheduleRevision)

      val outbox = reminderSyncRepo.getAllOutboxOrdered().filter { it.reminderId == rem.id }
      assertEquals(
        listOf(OutboxStatus.SUPERSEDED, OutboxStatus.SUPERSEDED, OutboxStatus.PENDING),
        outbox.map { it.status },
      )
      val latest = outbox.last()
      assertEquals(OutboxAction.SCHEDULE_ALARM, latest.action)
      assertEquals(3L, latest.targetScheduleRevision)
    }

  @Test
  fun clearingTaskDateOrStartTime_pausesEnabledReminders_andStagesCancels() = runBlocking {
    val task =
      (taskUseCases.createTask(
        TaskInput(title = "Dentist", scheduledDate = "2026-11-25", startTime = "14:00")
      ) as TaskDomainResult.Success)
        .value

    val rem =
      (taskReminderUseCases.createRelativeReminder(task.id, offsetMinutes = 30)
        as TaskReminderResult.Success)
        .value

    clock.advanceMinutes(5)
    taskUseCases.postponeTask(task.id, LocalDate.of(2026, 11, 27), clearTimeWindow = true)

    val pausedAfterClearTime = reminderSyncRepo.getTaskReminders(task.id).single()
    assertFalse(pausedAfterClearTime.isEnabled)
    assertEquals(ReminderScheduleState.PAUSED, pausedAfterClearTime.scheduleState)
    assertEquals(2L, pausedAfterClearTime.scheduleRevision)

    val outbox = reminderSyncRepo.getAllOutboxOrdered().filter { it.reminderId == rem.id }
    assertTrue(
      outbox.any {
        it.action == OutboxAction.CANCEL_ALARM &&
          it.status == OutboxStatus.PENDING &&
          it.targetScheduleRevision == 2L
      }
    )
    assertTrue(
      outbox.any {
        it.action == OutboxAction.CANCEL_NOTIFICATION &&
          it.status == OutboxStatus.PENDING &&
          it.targetScheduleRevision == 2L
      }
    )

    // Unscheduling date completely on an explicit reminder also pauses and cancels it
    val untimed =
      (taskUseCases.createTask(
        TaskInput(title = "Call bank", scheduledDate = "2026-11-25", startTime = null)
      ) as TaskDomainResult.Success)
        .value
    val explicitRem =
      (taskReminderUseCases.createExplicitReminder(untimed.id, triggerTime = "15:30")
        as TaskReminderResult.Success)
        .value
    taskUseCases.updateTask(
      untimed.id,
      TaskInput(title = "Call bank", scheduledDate = null, startTime = null),
    )
    val pausedUnscheduled = db.reminderDao().getTaskReminderById(explicitRem.id)!!
    assertFalse(pausedUnscheduled.isEnabled)
    assertEquals(ReminderScheduleState.PAUSED, pausedUnscheduled.scheduleState)
  }

  @Test
  fun terminalTransitions_setCancelledScheduleState_andDeleteUsesInjectedClock() = runBlocking {
    val task1 =
      (taskUseCases.createTask(
        TaskInput(title = "Finish report", scheduledDate = "2026-11-25", startTime = "16:00")
      ) as TaskDomainResult.Success)
        .value
    val rem1 =
      (taskReminderUseCases.createRelativeReminder(task1.id, offsetMinutes = 0)
        as TaskReminderResult.Success)
        .value

    clock.advanceMinutes(10)
    taskUseCases.cancelTask(task1.id)
    val cancelledRem = db.reminderDao().getTaskReminderById(rem1.id)!!
    assertFalse(cancelledRem.isEnabled)
    assertEquals(ReminderScheduleState.CANCELLED, cancelledRem.scheduleState)
    assertEquals(2L, cancelledRem.scheduleRevision)
    assertEquals(clock.currentEpochMs(), cancelledRem.updatedAtEpochMs)

    // Verify delete uses injected LocalClock instead of wall-clock System.currentTimeMillis()
    val task2 =
      (taskUseCases.createTask(
        TaskInput(title = "Temp task", scheduledDate = "2026-11-25", startTime = "17:00")
      ) as TaskDomainResult.Success)
        .value
    val rem2 =
      (taskReminderUseCases.createRelativeReminder(task2.id, offsetMinutes = 0)
        as TaskReminderResult.Success)
        .value
    clock.epochMs = 1_900_000_123_456L
    assertTrue(taskUseCases.deleteTask(task2.id))
    val cancelEntries =
      reminderSyncRepo.getAllOutboxOrdered().filter {
        it.reminderId == rem2.id && it.action != OutboxAction.SCHEDULE_ALARM
      }
    assertEquals(2, cancelEntries.size)
    assertTrue(cancelEntries.all { it.createdAtEpochMs == 1_900_000_123_456L })
  }

  @Test
  fun untimedAndTerminalTasks_rejectRelativeOrSynthesizedDefaults_whileExplicitUntimedPostponesCleanly() =
    runBlocking {
      val untimed =
        (taskUseCases.createTask(
          TaskInput(title = "Buy groceries", scheduledDate = "2026-11-25", startTime = null)
        ) as TaskDomainResult.Success)
          .value

      // Reject relative reminder when startTime is null (never invent 09:00)
      val relFail = taskReminderUseCases.createRelativeReminder(untimed.id, offsetMinutes = 15)
      assertEquals(
        TaskReminderResult.Failure(TaskReminderError.RelativeReminderRequiresStartTime),
        relFail,
      )
      val atStartFail = taskReminderUseCases.createRelativeReminder(untimed.id, offsetMinutes = 0)
      assertEquals(
        TaskReminderResult.Failure(TaskReminderError.RelativeReminderRequiresStartTime),
        atStartFail,
      )

      // Permit explicit user-chosen time with offsetMinutes = 0
      val explicit =
        (taskReminderUseCases.createExplicitReminder(untimed.id, triggerTime = "18:15")
          as TaskReminderResult.Success)
          .value
      assertEquals("2026-11-25", explicit.triggerDate)
      assertEquals("18:15", explicit.triggerTime)
      assertEquals(0, explicit.offsetMinutes)

      // Postponing untimed task moves explicit reminder's date while keeping user's chosen time
      clock.advanceMinutes(2)
      taskUseCases.postponeTask(untimed.id, LocalDate.of(2026, 11, 29), clearTimeWindow = false)
      val movedExplicit = db.reminderDao().getTaskReminderById(explicit.id)!!
      assertEquals("2026-11-29", movedExplicit.triggerDate)
      assertEquals("18:15", movedExplicit.triggerTime)
      assertEquals(2L, movedExplicit.scheduleRevision)

      // Unscheduled task rejected
      val unscheduled =
        (taskUseCases.createTask(TaskInput(title = "Someday")) as TaskDomainResult.Success).value
      assertEquals(
        TaskReminderResult.Failure(TaskReminderError.MissingScheduledDate),
        taskReminderUseCases.createExplicitReminder(unscheduled.id, triggerTime = "10:00"),
      )

      // Completed task rejected
      taskUseCases.completeTask(untimed.id)
      assertEquals(
        TaskReminderResult.Failure(TaskReminderError.TaskNotActive(TaskStatus.COMPLETED)),
        taskReminderUseCases.createExplicitReminder(untimed.id, triggerTime = "19:00"),
      )
    }

  @Test
  fun snoozeReminder_updatesOnlyReminderAndStagesAlarmAndNotificationCancel_withoutMutatingTask() =
    runBlocking {
      val task =
        (taskUseCases.createTask(
          TaskInput(title = "Medication", scheduledDate = "2026-11-25", startTime = "10:00")
        ) as TaskDomainResult.Success)
          .value
      val rem =
        (taskReminderUseCases.createRelativeReminder(task.id, offsetMinutes = 0)
          as TaskReminderResult.Success)
          .value

      // Advance clock to 23:55 UTC and snooze 15 minutes -> crosses to 2026-11-26 00:10
      clock.epochMs =
        ZonedDateTime.of(2026, 11, 25, 23, 55, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
      val snoozed =
        (taskReminderUseCases.snoozeReminder(task.id, rem.id, snoozeMinutes = 15)
          as TaskReminderResult.Success)
          .value

      assertEquals("2026-11-26", snoozed.triggerDate)
      assertEquals("00:10", snoozed.triggerTime)
      assertEquals(2L, snoozed.scheduleRevision)
      assertEquals(ReminderScheduleState.PENDING_SYNC, snoozed.scheduleState)

      // Original task schedule MUST remain unchanged
      val taskAfterSnooze = taskRepo.getById(task.id)!!
      assertEquals("2026-11-25", taskAfterSnooze.scheduledDate)
      assertEquals("10:00", taskAfterSnooze.startTime)

      val outbox = reminderSyncRepo.getAllOutboxOrdered().filter { it.reminderId == rem.id }
      assertTrue(
        outbox.any {
          it.systemTarget == OutboxSystemTarget.NOTIFICATION_MANAGER &&
            it.action == OutboxAction.CANCEL_NOTIFICATION &&
            it.status == OutboxStatus.PENDING
        }
      )
      assertTrue(
        outbox.any {
          it.systemTarget == OutboxSystemTarget.ALARM_MANAGER &&
            it.action == OutboxAction.SCHEDULE_ALARM &&
            it.status == OutboxStatus.PENDING &&
            it.targetScheduleRevision == 2L
        }
      )
    }

  @Test
  fun completingRecurringTask_keepsCompletedHistory_createsNextTaskRecurrenceReminderAndGoalLinks() =
    runBlocking {
      goalRepo.insertGoal(
        GoalEntity(
          id = "goal-1",
          name = "Health",
          startDate = "2026-11-01",
          createdAtEpochMs = clock.currentEpochMs(),
          updatedAtEpochMs = clock.currentEpochMs(),
        )
      )

      val task =
        (taskUseCases.createTask(
          TaskInput(
            title = "Weekly standup",
            description = "Sync with team",
            scheduledDate = "2026-11-25", // Wednesday
            startTime = "11:00",
            endTime = "11:30",
            priority = TaskPriority.HIGH,
            notes = "Bring metrics",
          )
        ) as TaskDomainResult.Success)
          .value
      goalRepo.linkTask("goal-1", task.id)

      val monWedMask =
        TaskRecurrenceRules.maskFromDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
      val recurrence =
        (taskUseCases.saveTaskRecurrence(
          task.id,
          TaskRecurrenceInput(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            daysOfWeekMask = monWedMask,
            endDate = "2026-12-10",
          ),
        ) as TaskDomainResult.Success)
          .value

      val rem =
        (taskReminderUseCases.createRelativeReminder(task.id, offsetMinutes = 15)
          as TaskReminderResult.Success)
          .value

      clock.advanceMinutes(30)
      val completion =
        (taskUseCases.completeTaskWithNextOccurrence(task.id) as TaskDomainResult.Success).value

      // 1. Original task stays COMPLETED and keeps its original TaskRecurrenceEntity untouched
      assertEquals(TaskStatus.COMPLETED, completion.completedTask.status)
      assertEquals(recurrence.id, taskRepo.getTaskRecurrence(task.id)?.id)
      val oldReminder = db.reminderDao().getTaskReminderById(rem.id)!!
      assertFalse(oldReminder.isEnabled)
      assertEquals(ReminderScheduleState.CANCELLED, oldReminder.scheduleState)

      // 2. New task created for Mon Dec 7 (next week Monday after 2-week step from Nov 23 week)
      val nextTask = completion.generatedNextTask
      assertNotNull(nextTask)
      nextTask!!
      assertNotEquals(task.id, nextTask.id)
      assertEquals("2026-12-07", nextTask.scheduledDate)
      assertEquals("11:00", nextTask.startTime)
      assertEquals("11:30", nextTask.endTime)
      assertEquals(TaskStatus.PENDING, nextTask.status)
      assertNull(nextTask.completedAtEpochMs)
      assertEquals("Bring metrics", nextTask.notes)

      // 3. New TaskRecurrenceEntity attached to new task (does NOT steal old recurrence row)
      val nextRecurrence = completion.generatedRecurrence!!
      assertNotEquals(recurrence.id, nextRecurrence.id)
      assertEquals(nextTask.id, nextRecurrence.taskId)
      assertEquals(monWedMask, nextRecurrence.daysOfWeekMask)

      // 4. Goal link preserved for new occurrence
      val linkedGoalTasks = goalRepo.observeLinkedTasks("goal-1").first().map { it.taskId }.toSet()
      assertEquals(setOf(task.id, nextTask.id), linkedGoalTasks)

      // 5. New TaskReminderEntity created and staged for SCHEDULE_ALARM
      assertEquals(1, completion.generatedReminders.size)
      val nextRem = completion.generatedReminders.single()
      assertEquals("2026-12-07", nextRem.triggerDate)
      assertEquals("10:45", nextRem.triggerTime)
      assertEquals(15, nextRem.offsetMinutes)
      assertNotEquals(rem.requestCode, nextRem.requestCode)
      assertTrue(
        reminderSyncRepo.getAllOutboxOrdered().any {
          it.reminderId == nextRem.id &&
            it.action == OutboxAction.SCHEDULE_ALARM &&
            it.status == OutboxStatus.PENDING
        }
      )

      // 6. Completing already completed task again fails and never creates duplicate occurrence
      val duplicateComplete = taskUseCases.completeTaskWithNextOccurrence(task.id)
      assertTrue(duplicateComplete is TaskDomainResult.Failure)
      assertEquals(2, taskRepo.observeAllTasks().first().size)

      // 7. Complete second occurrence (Dec 7 -> Dec 9 <= endDate Dec 10) generates 3rd;
      //    completing 3rd (Dec 9 -> Dec 21 > endDate Dec 10) stops cleanly without generating 4th.
      clock.advanceMinutes(1)
      val secondCompletion =
        (taskUseCases.completeTaskWithNextOccurrence(nextTask.id) as TaskDomainResult.Success).value
      val thirdTask = secondCompletion.generatedNextTask!!
      assertEquals("2026-12-09", thirdTask.scheduledDate)

      clock.advanceMinutes(1)
      val thirdCompletion =
        (taskUseCases.completeTaskWithNextOccurrence(thirdTask.id) as TaskDomainResult.Success).value
      assertNull(thirdCompletion.generatedNextTask)
      assertEquals(3, taskRepo.observeAllTasks().first().size)
    }

  @Test
  fun recurrenceRules_handleAllFrequenciesAndValidationBoundaries() {
    val jan31 = LocalDate.of(2026, 1, 31)
    val dailyRec =
      com.example.data.local.entity.TaskRecurrenceEntity(
        id = "r1",
        taskId = "t1",
        frequency = RecurrenceFrequency.DAILY,
        interval = 3,
        updatedAtEpochMs = 0L,
      )
    assertEquals(LocalDate.of(2026, 2, 3), TaskRecurrenceRules.computeNextOccurrenceDate(jan31, dailyRec))

    val monthlyRec = dailyRec.copy(frequency = RecurrenceFrequency.MONTHLY, interval = 1)
    assertEquals(LocalDate.of(2026, 2, 28), TaskRecurrenceRules.computeNextOccurrenceDate(jan31, monthlyRec))

    val leapYear = LocalDate.of(2024, 2, 29)
    val yearlyRec = dailyRec.copy(frequency = RecurrenceFrequency.YEARLY, interval = 1)
    assertEquals(LocalDate.of(2025, 2, 28), TaskRecurrenceRules.computeNextOccurrenceDate(leapYear, yearlyRec))

    val customFriSun =
      dailyRec.copy(
        frequency = RecurrenceFrequency.CUSTOM_DAYS,
        interval = 1,
        daysOfWeekMask = TaskRecurrenceRules.maskFromDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)),
      )
    // Wednesday 2026-11-25 -> Friday 2026-11-27
    assertEquals(
      LocalDate.of(2026, 11, 27),
      TaskRecurrenceRules.computeNextOccurrenceDate(LocalDate.of(2026, 11, 25), customFriSun),
    )

    val invalidCustom =
      TaskRecurrenceRules.validateInput(
        TaskRecurrenceInput(frequency = RecurrenceFrequency.CUSTOM_DAYS, interval = 0, daysOfWeekMask = 0),
        taskScheduledDate = null,
      )
    val errors = (invalidCustom as TaskDomainResult.Failure).errors
    assertTrue(errors.contains(TaskValidationError.RecurrenceRequiresScheduledDate))
    assertTrue(errors.contains(TaskValidationError.InvalidRecurrenceInterval(0)))
    assertTrue(errors.contains(TaskValidationError.RecurrenceRequiresDaysMask))
  }
}
