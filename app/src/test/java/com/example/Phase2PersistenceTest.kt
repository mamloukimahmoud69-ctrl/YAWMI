package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.di.DefaultAppContainer
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.preferences.DataStoreAppAppearanceRepository
import com.example.data.local.db.YawmiDatabase
import com.example.data.local.db.YawmiMetadataKeys
import com.example.data.local.entity.AppMetadataEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DeliveryState
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.GoalHabitCrossRef
import com.example.data.local.entity.GoalReminderEntity
import com.example.data.local.entity.GoalStatus
import com.example.data.local.entity.GoalTaskCrossRef
import com.example.data.local.entity.HabitCompletionEntity
import com.example.data.local.entity.HabitEntity
import com.example.data.local.entity.HabitRecurrenceEntity
import com.example.data.local.entity.HabitReminderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.OutboxAction
import com.example.data.local.entity.OutboxStatus
import com.example.data.local.entity.OutboxSystemTarget
import com.example.data.local.entity.RecurrenceFrequency
import com.example.data.local.entity.ReminderOwnerType
import com.example.data.local.entity.ReminderScheduleState
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskRecurrenceEntity
import com.example.data.local.entity.TaskReminderEntity
import com.example.data.local.entity.TaskStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2PersistenceTest {

  private lateinit var context: Context
  private lateinit var db: YawmiDatabase
  private lateinit var container: DefaultAppContainer
  private val fileDatabasesToDelete = mutableListOf<String>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = YawmiDatabase.inMemory(context)
    container = DefaultAppContainer(context = context, databaseProvider = { db })
  }

  @After
  fun tearDown() {
    db.close()
    for (name in fileDatabasesToDelete) {
      context.deleteDatabase(name)
    }
  }

  @Test
  fun database_opensWithAll18Tables_foreignKeysEnabled_andSeededMetadata() = runBlocking {
    val sqliteDb = db.openHelper.writableDatabase
    val tables = mutableSetOf<String>()
    sqliteDb.query(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' AND name != 'room_master_table'"
    ).use { cursor ->
      while (cursor.moveToNext()) {
        tables.add(cursor.getString(0))
      }
    }

    val expectedTables = setOf(
      "categories",
      "tasks",
      "habits",
      "habit_completions",
      "goals",
      "goal_task_cross_refs",
      "goal_habit_cross_refs",
      "task_recurrences",
      "habit_recurrences",
      "task_reminders",
      "habit_reminders",
      "goal_reminders",
      "notes",
      "app_metadata",
      "alarm_sync_outbox",
      "task_reminder_deliveries",
      "habit_reminder_deliveries",
      "goal_reminder_deliveries",
    )
    assertEquals(18, expectedTables.size)
    assertEquals(expectedTables, tables)

    sqliteDb.query("PRAGMA foreign_keys").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1, cursor.getInt(0))
    }

    assertEquals("1", container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.SCHEMA_INITIALIZED))
    assertEquals("0", container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.OUTBOX_SEQ))
    assertEquals(
      YawmiMetadataKeys.MIN_REMINDER_REQUEST_CODE.toString(),
      container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE),
    )
    assertEquals("0", container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.DISPATCHER_LEASE_GENERATION))
  }

  @Test
  fun crud_worksAcrossAllEntitiesAndRepositories() = runBlocking {
    val t = 1_764_000_000_000L

    val category = CategoryEntity("cat-1", "Work", "amber", "work", sortOrder = 1, updatedAtEpochMs = t)
    container.categoryRepository.upsert(category)
    assertEquals(1, container.categoryRepository.observeActiveCategories().first().size)
    container.categoryRepository.upsert(category.copy(name = "Work Updated", updatedAtEpochMs = t + 10))
    assertEquals("Work Updated", container.categoryRepository.getById("cat-1")?.name)

    val task = TaskEntity(
      id = "task-1",
      title = "Plan sprint",
      scheduledDate = "2026-11-25",
      startTime = "09:00",
      endTime = "10:00",
      priority = TaskPriority.HIGH,
      status = TaskStatus.PENDING,
      categoryId = "cat-1",
      createdAtEpochMs = t,
      updatedAtEpochMs = t,
    )
    container.taskRepository.insert(task)
    assertEquals(1, container.taskRepository.observeTasksForDate("2026-11-25").first().size)
    container.taskRepository.update(task.copy(title = "Plan sprint v2", updatedAtEpochMs = t + 20))
    assertEquals("Plan sprint v2", container.taskRepository.getById("task-1")?.title)

    val taskRecurrence = TaskRecurrenceEntity(
      id = "trec-1",
      taskId = "task-1",
      frequency = RecurrenceFrequency.WEEKLY,
      interval = 1,
      daysOfWeekMask = 4,
      updatedAtEpochMs = t,
    )
    container.taskRepository.upsertTaskRecurrence(taskRecurrence)
    assertEquals(RecurrenceFrequency.WEEKLY, container.taskRepository.getTaskRecurrence("task-1")?.frequency)

    val habit = HabitEntity("habit-1", "Read", categoryId = "cat-1", targetPerPeriod = 1, createdAtEpochMs = t, updatedAtEpochMs = t)
    container.habitRepository.insertHabit(habit)
    assertEquals(1, container.habitRepository.observeActiveHabits().first().size)
    container.habitRepository.updateHabit(habit.copy(title = "Read 20 pages", updatedAtEpochMs = t + 30))
    assertEquals("Read 20 pages", container.habitRepository.getById("habit-1")?.title)

    val habitRecurrence = HabitRecurrenceEntity(
      id = "hrec-1",
      habitId = "habit-1",
      frequency = RecurrenceFrequency.DAILY,
      interval = 1,
      updatedAtEpochMs = t,
    )
    container.habitRepository.upsertHabitRecurrence(habitRecurrence)
    assertEquals(RecurrenceFrequency.DAILY, container.habitRepository.getHabitRecurrence("habit-1")?.frequency)

    val completion = HabitCompletionEntity("hcomp-1", "habit-1", "2026-11-25", count = 1, completedAtEpochMs = t, ruleSnapshot = "DAILY:1")
    container.habitRepository.insertCompletion(completion)
    assertEquals(1, container.habitRepository.observeCompletionsForHabit("habit-1").first().size)

    val goal = GoalEntity("goal-1", "Ship YAWMI", categoryId = "cat-1", startDate = "2026-11-01", targetValue = 10.0, createdAtEpochMs = t, updatedAtEpochMs = t)
    container.goalRepository.insertGoal(goal)
    container.goalRepository.linkTask("goal-1", "task-1")
    container.goalRepository.linkHabit("goal-1", "habit-1")
    assertEquals(1, container.goalRepository.observeLinkedTasks("goal-1").first().size)
    assertEquals(1, container.goalRepository.observeLinkedHabits("goal-1").first().size)

    val note = NoteEntity("note-1", "Architecture", "Offline first", linkedTaskId = "task-1", linkedGoalId = "goal-1", isPinned = true, createdAtEpochMs = t, updatedAtEpochMs = t)
    container.noteRepository.insert(note)
    assertEquals(1, container.noteRepository.observeAllNotes().first().size)

    val tr = container.reminderSyncRepository.createTaskReminder("trem-1", "task-1", "2026-11-25", "08:45", 15, t)
    val hr = container.reminderSyncRepository.createHabitReminder("hrem-1", "habit-1", "2026-11-25", "20:00", 0, t + 1)
    val gr = container.reminderSyncRepository.createGoalReminder("grem-1", "goal-1", "2026-12-01", "10:00", 0, t + 2)

    val td = container.reminderSyncRepository.recordPendingTaskDelivery("tdel-1", tr.id, "2026-11-25T08:45", t, tr.scheduleRevision, t)
    val hd = container.reminderSyncRepository.recordPendingHabitDelivery("hdel-1", hr.id, "2026-11-25T20:00", t, hr.scheduleRevision, t)
    val gd = container.reminderSyncRepository.recordPendingGoalDelivery("gdel-1", gr.id, "2026-12-01T10:00", t, gr.scheduleRevision, t)

    container.reminderSyncRepository.updateTaskDeliveryState(td.id, DeliveryState.POSTED_TO_SYSTEM, 1, t + 5, null, t + 5)
    container.reminderSyncRepository.updateHabitDeliveryState(hd.id, DeliveryState.SUPPRESSED_PERMISSION, 1, t + 5, "No permission", t + 5)
    container.reminderSyncRepository.updateGoalDeliveryState(gd.id, DeliveryState.SUPPRESSED_CHANNEL, 1, t + 5, "Channel muted", t + 5)

    assertEquals(DeliveryState.POSTED_TO_SYSTEM, container.reminderSyncRepository.observeTaskDeliveries(tr.id).first().single().state)
    assertEquals(DeliveryState.SUPPRESSED_PERMISSION, container.reminderSyncRepository.observeHabitDeliveries(hr.id).first().single().state)
    assertEquals(DeliveryState.SUPPRESSED_CHANNEL, container.reminderSyncRepository.observeGoalDeliveries(gr.id).first().single().state)
    assertEquals(3, container.reminderSyncRepository.getAllOutboxOrdered().size)
  }

  @Test
  fun foreignKeys_enforceCascadeAndSetNullBehaviors() = runBlocking {
    val t = 1_764_000_000_000L
    val cat = CategoryEntity("cat-1", "Health", "green", "favorite", updatedAtEpochMs = t)
    val task = TaskEntity("task-1", "Checkup", categoryId = "cat-1", createdAtEpochMs = t, updatedAtEpochMs = t)
    val habit = HabitEntity("habit-1", "Water", categoryId = "cat-1", createdAtEpochMs = t, updatedAtEpochMs = t)
    val goal = GoalEntity("goal-1", "Fitness", categoryId = "cat-1", startDate = "2026-11-01", createdAtEpochMs = t, updatedAtEpochMs = t)

    container.categoryRepository.upsert(cat)
    container.taskRepository.insert(task)
    container.habitRepository.insertHabit(habit)
    container.goalRepository.insertGoal(goal)
    container.goalRepository.linkTask("goal-1", "task-1")
    container.goalRepository.linkHabit("goal-1", "habit-1")
    container.taskRepository.upsertTaskRecurrence(TaskRecurrenceEntity("tr-1", "task-1", RecurrenceFrequency.DAILY, updatedAtEpochMs = t))
    container.habitRepository.upsertHabitRecurrence(HabitRecurrenceEntity("hr-1", "habit-1", RecurrenceFrequency.DAILY, updatedAtEpochMs = t))
    container.habitRepository.insertCompletion(HabitCompletionEntity("hc-1", "habit-1", "2026-11-25", 1, t, "DAILY:1"))
    container.noteRepository.insert(NoteEntity("note-1", "Linked", "Body", linkedTaskId = "task-1", linkedGoalId = "goal-1", createdAtEpochMs = t, updatedAtEpochMs = t))

    val taskRem = container.reminderSyncRepository.createTaskReminder("trem-1", "task-1", "2026-11-25", "09:00", 0, t)
    container.reminderSyncRepository.recordPendingTaskDelivery("td-1", taskRem.id, "occ-1", t, 1L, t)

    // Deleting category sets categoryId = null across Task, Habit, Goal (ON DELETE SET NULL)
    container.categoryRepository.deleteById("cat-1")
    assertNull(container.taskRepository.getById("task-1")?.categoryId)
    assertNull(container.habitRepository.getById("habit-1")?.categoryId)
    assertNull(container.goalRepository.getById("goal-1")?.categoryId)

    // Deleting task cascades to cross-ref, recurrence, reminder, delivery, stages outbox CANCEL_ALARM, and sets note.linkedTaskId = null
    container.taskRepository.deleteById("task-1")
    assertNull(container.taskRepository.getById("task-1"))
    assertNull(container.taskRepository.getTaskRecurrence("task-1"))
    assertTrue(container.goalRepository.observeLinkedTasks("goal-1").first().isEmpty())
    assertNull(db.reminderDao().getTaskReminderById("trem-1"))
    assertNull(db.reminderDeliveryDao().getTaskDelivery("trem-1", "occ-1"))
    assertNull(container.noteRepository.getById("note-1")?.linkedTaskId)
    assertEquals("goal-1", container.noteRepository.getById("note-1")?.linkedGoalId)
    assertTrue(
      container.reminderSyncRepository.getAllOutboxOrdered().any {
        it.reminderId == "trem-1" && it.action == OutboxAction.CANCEL_ALARM
      }
    )

    // Deleting habit cascades to completion, recurrence, goal cross-ref
    container.habitRepository.deleteHabitById("habit-1")
    assertNull(container.habitRepository.getCompletion("habit-1", "2026-11-25"))
    assertNull(container.habitRepository.getHabitRecurrence("habit-1"))
    assertTrue(container.goalRepository.observeLinkedHabits("goal-1").first().isEmpty())

    // Deleting goal sets note.linkedGoalId = null
    container.goalRepository.deleteGoalById("goal-1")
    val survivingNote = container.noteRepository.getById("note-1")
    assertNotNull(survivingNote)
    assertNull(survivingNote?.linkedGoalId)
  }

  @Test
  fun foreignKeys_rejectOrphanRecords() = runBlocking {
    val t = 1_764_000_000_000L

    assertThrowsConstraint {
      db.taskDao().insert(TaskEntity("orphan-task", "Orphan", categoryId = "missing-cat", createdAtEpochMs = t, updatedAtEpochMs = t))
    }
    assertThrowsConstraint {
      db.habitDao().insertCompletion(HabitCompletionEntity("orphan-hc", "missing-habit", "2026-11-25", 1, t, "DAILY:1"))
    }
    assertThrowsConstraint {
      db.recurrenceDao().insertTaskRecurrence(TaskRecurrenceEntity("orphan-tr", "missing-task", RecurrenceFrequency.DAILY, updatedAtEpochMs = t))
    }
    assertThrowsConstraint {
      db.reminderDao().insertTaskReminder(
        TaskReminderEntity("orphan-rem", "missing-task", "2026-11-25", "09:00", 0, 1000, 1L, true, ReminderScheduleState.PENDING_SYNC, t)
      )
    }
    assertThrowsConstraint {
      db.goalDao().linkTask(GoalTaskCrossRef("missing-goal", "missing-task"))
    }
    assertThrowsConstraint {
      db.noteDao().insert(NoteEntity("orphan-note", "Title", "Content", linkedTaskId = "missing-task", createdAtEpochMs = t, updatedAtEpochMs = t))
    }
  }

  @Test
  fun uniqueConstraintsAndCompositeKeys_rejectDuplicates() = runBlocking {
    val t = 1_764_000_000_000L
    val task = TaskEntity("t-1", "Task", createdAtEpochMs = t, updatedAtEpochMs = t)
    val habit = HabitEntity("h-1", "Habit", createdAtEpochMs = t, updatedAtEpochMs = t)
    val goal = GoalEntity("g-1", "Goal", startDate = "2026-11-01", createdAtEpochMs = t, updatedAtEpochMs = t)
    container.taskRepository.insert(task)
    container.habitRepository.insertHabit(habit)
    container.goalRepository.insertGoal(goal)

    // Unique (habitId, completedDate)
    db.habitDao().insertCompletion(HabitCompletionEntity("hc-1", "h-1", "2026-11-25", 1, t, "DAILY:1"))
    assertThrowsConstraint {
      db.habitDao().insertCompletion(HabitCompletionEntity("hc-2", "h-1", "2026-11-25", 2, t, "DAILY:1"))
    }

    // Unique recurrence per owner
    db.recurrenceDao().insertTaskRecurrence(TaskRecurrenceEntity("tr-1", "t-1", RecurrenceFrequency.DAILY, updatedAtEpochMs = t))
    assertThrowsConstraint {
      db.recurrenceDao().insertTaskRecurrence(TaskRecurrenceEntity("tr-2", "t-1", RecurrenceFrequency.WEEKLY, updatedAtEpochMs = t))
    }

    // Composite PK deduplication in cross refs
    db.goalDao().linkTask(GoalTaskCrossRef("g-1", "t-1"))
    db.goalDao().linkTask(GoalTaskCrossRef("g-1", "t-1"))
    assertEquals(1, db.goalDao().observeLinkedTasks("g-1").first().size)

    // Unique (reminderId, occurrenceKey) across all 3 delivery ledgers
    val tr = container.reminderSyncRepository.createTaskReminder("trem-1", "t-1", "2026-11-25", "09:00", 0, t)
    val hr = container.reminderSyncRepository.createHabitReminder("hrem-1", "h-1", "2026-11-25", "10:00", 0, t)
    val gr = container.reminderSyncRepository.createGoalReminder("grem-1", "g-1", "2026-11-25", "11:00", 0, t)

    container.reminderSyncRepository.recordPendingTaskDelivery("td-1", tr.id, "2026-11-25T09:00", t, 1L, t)
    assertThrowsConstraint {
      container.reminderSyncRepository.recordPendingTaskDelivery("td-2", tr.id, "2026-11-25T09:00", t, 1L, t)
    }

    container.reminderSyncRepository.recordPendingHabitDelivery("hd-1", hr.id, "2026-11-25T10:00", t, 1L, t)
    assertThrowsConstraint {
      container.reminderSyncRepository.recordPendingHabitDelivery("hd-2", hr.id, "2026-11-25T10:00", t, 1L, t)
    }

    container.reminderSyncRepository.recordPendingGoalDelivery("gd-1", gr.id, "2026-11-25T11:00", t, 1L, t)
    assertThrowsConstraint {
      container.reminderSyncRepository.recordPendingGoalDelivery("gd-2", gr.id, "2026-11-25T11:00", t, 1L, t)
    }
  }

  @Test
  fun transactionFailure_rollsBackAllWritesAndCounters() = runBlocking {
    val t = 1_764_000_000_000L
    try {
      db.withTransaction {
        container.categoryRepository.upsert(CategoryEntity("cat-tx", "TX", "amber", "star", updatedAtEpochMs = t))
        container.taskRepository.insert(TaskEntity("task-tx", "Inside TX", categoryId = "cat-tx", createdAtEpochMs = t, updatedAtEpochMs = t))
        container.reminderSyncRepository.createTaskReminder("rem-tx", "task-tx", "2026-11-25", "09:00", 0, t)
        error("Simulated failure mid-transaction")
      }
      fail("Expected transaction to throw")
    } catch (_: IllegalStateException) {
      // Expected
    }

    assertNull(container.categoryRepository.getById("cat-tx"))
    assertNull(container.taskRepository.getById("task-tx"))
    assertNull(db.reminderDao().getTaskReminderById("rem-tx"))
    assertTrue(container.reminderSyncRepository.getAllOutboxOrdered().isEmpty())
    assertEquals("0", container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.OUTBOX_SEQ))
    assertEquals("1000", container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE))
  }

  @Test
  fun outbox_enforcesMonotonicSeq_identityCoalescing_andDeleteSnapshotRetention() = runBlocking {
    val t = 1_764_000_000_000L
    container.taskRepository.insert(TaskEntity("t-1", "Task 1", createdAtEpochMs = t, updatedAtEpochMs = t))
    container.habitRepository.insertHabit(HabitEntity("h-1", "Habit 1", createdAtEpochMs = t, updatedAtEpochMs = t))

    val tr = container.reminderSyncRepository.createTaskReminder("rem-1", "t-1", "2026-11-25", "09:00", 0, t)
    val hr = container.reminderSyncRepository.createHabitReminder("rem-2", "h-1", "2026-11-25", "10:00", 0, t + 1)

    // Update Task reminder twice -> newer SCHEDULE supersedes earlier PENDING SCHEDULE for rem-1, NOT rem-2
    container.reminderSyncRepository.updateTaskReminderSchedule(tr.id, "2026-11-25", "09:30", 0, true, t + 2)
    container.reminderSyncRepository.updateTaskReminderSchedule(tr.id, "2026-11-25", "10:30", 0, true, t + 3)

    // Delete rem-1 -> emits CANCEL_ALARM (superseding earlier PENDING ALARM_MANAGER command for rem-1) and CANCEL_NOTIFICATION
    assertTrue(container.reminderSyncRepository.deleteTaskReminder(tr.id, t + 4))
    assertNull(db.reminderDao().getTaskReminderById(tr.id))

    val outbox = container.reminderSyncRepository.getAllOutboxOrdered()
    assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), outbox.map { it.seq })
    assertEquals("6", container.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.OUTBOX_SEQ))

    // rem-2 (distinct reminder) remains PENDING
    val rem2Entry = outbox.single { it.reminderId == hr.id }
    assertEquals(OutboxStatus.PENDING, rem2Entry.status)

    // rem-1's older ALARM_MANAGER commands are SUPERSEDED; latest CANCEL_ALARM & CANCEL_NOTIFICATION are PENDING with full snapshot
    val rem1AlarmEntries = outbox.filter { it.reminderId == tr.id && it.systemTarget == OutboxSystemTarget.ALARM_MANAGER }
    assertEquals(listOf(OutboxStatus.SUPERSEDED, OutboxStatus.SUPERSEDED, OutboxStatus.SUPERSEDED, OutboxStatus.PENDING), rem1AlarmEntries.map { it.status })
    val latestCancelAlarm = rem1AlarmEntries.last()
    assertEquals(OutboxAction.CANCEL_ALARM, latestCancelAlarm.action)
    assertEquals(tr.requestCode, latestCancelAlarm.requestCode)
    assertEquals("yawmi://reminder/task/${tr.id}", latestCancelAlarm.dataUri)
    assertTrue(latestCancelAlarm.pendingIntentIdentityKey.contains("req=${tr.requestCode}"))

    val cancelNotif = outbox.single { it.reminderId == tr.id && it.systemTarget == OutboxSystemTarget.NOTIFICATION_MANAGER }
    assertEquals(OutboxAction.CANCEL_NOTIFICATION, cancelNotif.action)
    assertEquals(OutboxStatus.PENDING, cancelNotif.status)
    assertEquals("yawmi:reminder:task:${tr.id}", cancelNotif.notificationTag)
    assertEquals(0, cancelNotif.notificationId)
  }

  @Test
  fun requestCode_isGloballyUniqueAcrossReminderTables_neverReused_andGuardsOverflow() = runBlocking {
    val t = 1_764_000_000_000L
    container.taskRepository.insert(TaskEntity("t-1", "Task", createdAtEpochMs = t, updatedAtEpochMs = t))
    container.habitRepository.insertHabit(HabitEntity("h-1", "Habit", createdAtEpochMs = t, updatedAtEpochMs = t))
    container.goalRepository.insertGoal(GoalEntity("g-1", "Goal", startDate = "2026-11-01", createdAtEpochMs = t, updatedAtEpochMs = t))

    val r1 = container.reminderSyncRepository.createTaskReminder("r1", "t-1", "2026-11-25", "08:00", 0, t)
    val r2 = container.reminderSyncRepository.createHabitReminder("r2", "h-1", "2026-11-25", "09:00", 0, t)
    val r3 = container.reminderSyncRepository.createGoalReminder("r3", "g-1", "2026-11-25", "10:00", 0, t)

    assertEquals(1000, r1.requestCode)
    assertEquals(1001, r2.requestCode)
    assertEquals(1002, r3.requestCode)

    // Delete r2 and allocate another reminder -> 1001 MUST NOT be reused
    assertTrue(container.reminderSyncRepository.deleteHabitReminder("r2", t + 1))
    val r4 = container.reminderSyncRepository.createTaskReminder("r4", "t-1", "2026-11-25", "11:00", 0, t + 2)
    assertEquals(1003, r4.requestCode)

    // SQLite cross-table trigger blocks direct duplicate requestCode insertion across tables
    assertThrowsConstraint {
      db.reminderDao().insertHabitReminder(
        HabitReminderEntity("dup-h", "h-1", "2026-11-25", "12:00", 0, requestCode = 1000, scheduleRevision = 1L, isEnabled = true, scheduleState = ReminderScheduleState.PENDING_SYNC, updatedAtEpochMs = t)
      )
    }
    assertThrowsConstraint {
      db.reminderDao().insertGoalReminder(
        GoalReminderEntity("dup-g", "g-1", "2026-11-25", "12:00", 0, requestCode = 1003, scheduleRevision = 1L, isEnabled = true, scheduleState = ReminderScheduleState.PENDING_SYNC, updatedAtEpochMs = t)
      )
    }

    // Overflow guard at Int.MAX_VALUE fails explicitly without wrap-around
    db.appMetadataDao().put(
      AppMetadataEntity(YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE, Int.MAX_VALUE.toString())
    )
    var overflowThrown = false
    try {
      container.reminderSyncRepository.createTaskReminder("r-overflow", "t-1", "2026-11-25", "13:00", 0, t + 3)
    } catch (_: IllegalStateException) {
      overflowThrown = true
    }
    assertTrue("Expected overflow IllegalStateException at Int.MAX_VALUE", overflowThrown)
  }

  @Test
  fun scheduleRevision_incrementsEvenWithinSameMillisecond() = runBlocking {
    val sameMs = 1_764_000_000_500L
    container.taskRepository.insert(TaskEntity("t-rev", "Rapid", createdAtEpochMs = sameMs, updatedAtEpochMs = sameMs))

    val v1 = container.reminderSyncRepository.createTaskReminder("r-rev", "t-rev", "2026-11-25", "09:00", 0, sameMs)
    val v2 = container.reminderSyncRepository.updateTaskReminderSchedule("r-rev", "2026-11-25", "09:15", 0, true, sameMs)
    val v3 = container.reminderSyncRepository.updateTaskReminderSchedule("r-rev", "2026-11-25", "09:30", 0, false, sameMs)

    assertEquals(1L, v1.scheduleRevision)
    assertEquals(2L, v2.scheduleRevision)
    assertEquals(3L, v3.scheduleRevision)
    assertFalse(v3.isEnabled)

    val outbox = container.reminderSyncRepository.getAllOutboxOrdered()
    assertEquals(listOf(1L, 2L, 3L, 3L), outbox.map { it.targetScheduleRevision })
  }

  @Test
  fun database_persistsAcrossCloseAndReopen_andFailsSafelyWithoutDestructiveMigration() = runBlocking {
    val dbName = "yawmi_reopen_test.db"
    fileDatabasesToDelete.add(dbName)
    context.deleteDatabase(dbName)

    val t = 1_764_000_000_000L
    val firstDb = YawmiDatabase.openWithName(context, dbName)
    val firstContainer = DefaultAppContainer(context = context, databaseProvider = { firstDb })
    firstContainer.categoryRepository.upsert(CategoryEntity("c1", "Focus", "amber", "bolt", updatedAtEpochMs = t))
    firstContainer.taskRepository.insert(TaskEntity("t1", "Persistent Task", categoryId = "c1", createdAtEpochMs = t, updatedAtEpochMs = t))
    val rem = firstContainer.reminderSyncRepository.createTaskReminder("r1", "t1", "2026-11-25", "18:00", 0, t)
    firstContainer.reminderSyncRepository.updateTaskReminderSchedule(rem.id, "2026-11-25", "18:30", 0, true, t + 1)
    firstDb.close()

    val reopenedDb = YawmiDatabase.openWithName(context, dbName)
    val reopenedContainer = DefaultAppContainer(context = context, databaseProvider = { reopenedDb })
    assertEquals("Focus", reopenedContainer.categoryRepository.getById("c1")?.name)
    assertEquals("Persistent Task", reopenedContainer.taskRepository.getById("t1")?.title)
    val reopenedRem = reopenedDb.reminderDao().getTaskReminderById("r1")
    assertEquals(1000, reopenedRem?.requestCode)
    assertEquals(2L, reopenedRem?.scheduleRevision)
    assertEquals("1001", reopenedContainer.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE))
    assertEquals("2", reopenedContainer.reminderSyncRepository.getMetadataValue(YawmiMetadataKeys.OUTBOX_SEQ))
    reopenedDb.close()

    // Simulate a future DB version (v2) with no Migration(2, 1) / missing migration path -> MUST throw IllegalStateException and MUST NOT drop user data
    val rawHelper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbName)
          .callback(
            object : SupportSQLiteOpenHelper.Callback(2) {
              override fun onCreate(db: SupportSQLiteDatabase) {}

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.version = 2
              }
            }
          )
          .build()
      )
    rawHelper.writableDatabase.use { rawDb ->
      rawDb.version = 2
    }
    rawHelper.close()

    val failingDb = YawmiDatabase.openWithName(context, dbName)
    var migrationErrorThrown = false
    try {
      failingDb.taskDao().getById("t1")
    } catch (_: IllegalStateException) {
      migrationErrorThrown = true
    } finally {
      failingDb.close()
    }
    assertTrue("Missing migration must throw IllegalStateException instead of wiping user data", migrationErrorThrown)

    // Verify underlying SQLite row still exists untouched after failed open
    val checkHelper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbName)
          .callback(
            object : SupportSQLiteOpenHelper.Callback(2) {
              override fun onCreate(db: SupportSQLiteDatabase) {}

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }
          )
          .build()
      )
    checkHelper.readableDatabase.use { rawDb ->
      rawDb.query("SELECT title FROM tasks WHERE id = 't1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Persistent Task", cursor.getString(0))
      }
    }
    checkHelper.close()
  }

  @Test
  fun dataStore_persistsThemeAndLanguagePreferences() = runBlocking {
    val repo1 = DataStoreAppAppearanceRepository(context)
    val initial = repo1.appearanceState.first()
    assertEquals(AppThemeMode.DARK, initial.themeMode)
    assertEquals(AppLanguage.ARABIC, initial.language)

    repo1.setThemeMode(AppThemeMode.LIGHT)
    repo1.setLanguage(AppLanguage.ENGLISH)

    val repo2 = DataStoreAppAppearanceRepository(context)
    val persisted = repo2.appearanceState.first()
    assertEquals(AppThemeMode.LIGHT, persisted.themeMode)
    assertEquals(AppLanguage.ENGLISH, persisted.language)

    // Restore defaults cleanly
    repo2.setThemeMode(AppThemeMode.DARK)
    repo2.setLanguage(AppLanguage.ARABIC)
  }

  @Test
  fun exportedRoomSchema_matches18TableSpecification() {
    val candidates = listOf(
      File("schemas/com.example.data.local.db.YawmiDatabase/1.json"),
      File("app/schemas/com.example.data.local.db.YawmiDatabase/1.json"),
    )
    val schemaFile = candidates.firstOrNull { it.exists() }
      ?: error("Exported Room schema 1.json not found in ${candidates.map { it.absolutePath }}")

    val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
    val databaseObj = root.getValue("database").jsonObject
    assertEquals(1, databaseObj.getValue("version").jsonPrimitive.int)

    val entities = databaseObj.getValue("entities").jsonArray
    assertEquals(18, entities.size)

    val entitiesByTable = entities.associateBy { it.jsonObject.getValue("tableName").jsonPrimitive.content }
    assertEquals(
      setOf(
        "categories",
        "tasks",
        "habits",
        "habit_completions",
        "goals",
        "goal_task_cross_refs",
        "goal_habit_cross_refs",
        "task_recurrences",
        "habit_recurrences",
        "task_reminders",
        "habit_reminders",
        "goal_reminders",
        "notes",
        "app_metadata",
        "alarm_sync_outbox",
        "task_reminder_deliveries",
        "habit_reminder_deliveries",
        "goal_reminder_deliveries",
      ),
      entitiesByTable.keys,
    )

    // Verify unique index on habit_completions(habitId, completedDate)
    val habitCompIndices = entitiesByTable.getValue("habit_completions").jsonObject.getValue("indices").jsonArray
    assertTrue(
      habitCompIndices.any { idx ->
        idx.jsonObject.getValue("unique").jsonPrimitive.content == "true" &&
          idx.jsonObject.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content } == listOf("habitId", "completedDate")
      }
    )

    // Verify unique index on all 3 delivery tables (reminderId, occurrenceKey)
    for ((table, fkCol) in listOf(
      "task_reminder_deliveries" to "taskReminderId",
      "habit_reminder_deliveries" to "habitReminderId",
      "goal_reminder_deliveries" to "goalReminderId",
    )) {
      val indices = entitiesByTable.getValue(table).jsonObject.getValue("indices").jsonArray
      assertTrue(
        "Missing unique occurrenceKey index on $table",
        indices.any { idx ->
          idx.jsonObject.getValue("unique").jsonPrimitive.content == "true" &&
            idx.jsonObject.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content } == listOf(fkCol, "occurrenceKey")
        },
      )
    }

    // Verify unique seq index on alarm_sync_outbox
    val outboxIndices = entitiesByTable.getValue("alarm_sync_outbox").jsonObject.getValue("indices").jsonArray
    assertTrue(
      outboxIndices.any { idx ->
        idx.jsonObject.getValue("unique").jsonPrimitive.content == "true" &&
          idx.jsonObject.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content } == listOf("seq")
      }
    )
  }

  private suspend fun assertThrowsConstraint(block: suspend () -> Unit) {
    var threw = false
    try {
      block()
    } catch (_: SQLiteConstraintException) {
      threw = true
    }
    assertTrue("Expected SQLiteConstraintException", threw)
  }
}
