package com.example

import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus
import com.example.domain.task.TaskDomainResult
import com.example.domain.task.TaskInput
import com.example.domain.task.TaskRules
import com.example.domain.task.TaskValidationError
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Unit3ATaskRulesTest {

  @Test
  fun createWithTitleOnly_appliesManualDefaultsWithoutFabricatedDate() {
    val result =
      TaskRules.buildNewTaskEntity(
        input = TaskInput(title = "  Write architecture tests  "),
        nowEpochMs = 1_764_000_000_000L,
        idGenerator = { "task-uuid-1" },
      )

    assertTrue(result is TaskDomainResult.Success)
    val task = (result as TaskDomainResult.Success).value
    assertEquals("task-uuid-1", task.id)
    assertEquals("Write architecture tests", task.title)
    assertNull(task.description)
    assertNull(task.scheduledDate)
    assertNull(task.startTime)
    assertNull(task.endTime)
    assertEquals(TaskPriority.MEDIUM, task.priority)
    assertEquals(TaskStatus.PENDING, task.status)
    assertNull(task.categoryId)
    assertNull(task.notes)
    assertEquals(1_764_000_000_000L, task.createdAtEpochMs)
    assertEquals(1_764_000_000_000L, task.updatedAtEpochMs)
    assertNull(task.completedAtEpochMs)
  }

  @Test
  fun blankOrWhitespaceTitle_isRejected() {
    for (badTitle in listOf("", "   ", "\t\n  ")) {
      val result = TaskRules.buildNewTaskEntity(TaskInput(title = badTitle), nowEpochMs = 1000L)
      assertTrue("Expected failure for title '$badTitle'", result is TaskDomainResult.Failure)
      val errors = (result as TaskDomainResult.Failure).errors
      assertTrue(errors.contains(TaskValidationError.EmptyTitle))
    }
  }

  @Test
  fun omittedAndBlankOptionalFields_normalizeToNull() {
    val result =
      TaskRules.buildNewTaskEntity(
        input =
          TaskInput(
            title = "Focus block",
            description = "   ",
            scheduledDate = "",
            startTime = " ",
            endTime = null,
            categoryId = "  ",
            notes = "\n",
          ),
        nowEpochMs = 5000L,
      )

    val task = (result as TaskDomainResult.Success).value
    assertNull(task.description)
    assertNull(task.scheduledDate)
    assertNull(task.startTime)
    assertNull(task.endTime)
    assertNull(task.categoryId)
    assertNull(task.notes)
  }

  @Test
  fun startAndEndTimeValidation_enforcesOrderAndStartRequirement() {
    // End without start -> rejected
    val noStart =
      TaskRules.buildNewTaskEntity(
        TaskInput(title = "Task", scheduledDate = "2026-11-25", endTime = "11:00"),
        nowEpochMs = 1000L,
      )
    assertTrue((noStart as TaskDomainResult.Failure).errors.contains(TaskValidationError.EndTimeWithoutStartTime))

    // End before start -> rejected
    val endBeforeStart =
      TaskRules.buildNewTaskEntity(
        TaskInput(title = "Task", startTime = "14:00", endTime = "13:59"),
        nowEpochMs = 1000L,
      )
    assertTrue(
      (endBeforeStart as TaskDomainResult.Failure).errors.contains(TaskValidationError.EndTimeBeforeStartTime)
    )

    // Invalid date / time formats -> rejected
    val invalidFormats =
      TaskRules.buildNewTaskEntity(
        TaskInput(title = "Task", scheduledDate = "2026-02-30", startTime = "25:10", endTime = "09:99"),
        nowEpochMs = 1000L,
      )
    val formatErrors = (invalidFormats as TaskDomainResult.Failure).errors
    assertTrue(formatErrors.any { it is TaskValidationError.InvalidScheduledDate })
    assertTrue(formatErrors.any { it is TaskValidationError.InvalidStartTime })
    assertTrue(formatErrors.any { it is TaskValidationError.InvalidEndTime })

    // Equal start and end -> allowed
    val zeroDuration =
      TaskRules.buildNewTaskEntity(
        TaskInput(title = "Deadline", scheduledDate = "2026-11-25", startTime = "9:05", endTime = "09:05"),
        nowEpochMs = 1000L,
      )
    val task = (zeroDuration as TaskDomainResult.Success).value
    assertEquals("09:05", task.startTime)
    assertEquals("09:05", task.endTime)
  }

  @Test
  fun statusTransitions_enforceStateMachineAndCompletedAtAccuracy() {
    val initial =
      (TaskRules.buildNewTaskEntity(TaskInput(title = "Review"), nowEpochMs = 1000L)
          as TaskDomainResult.Success)
        .value

    // PENDING -> IN_PROGRESS
    val inProgress =
      (TaskRules.applyStatusTransition(initial, TaskStatus.IN_PROGRESS, nowEpochMs = 2000L)
          as TaskDomainResult.Success)
        .value
    assertEquals(TaskStatus.IN_PROGRESS, inProgress.status)
    assertNull(inProgress.completedAtEpochMs)
    assertEquals(2000L, inProgress.updatedAtEpochMs)

    // IN_PROGRESS -> COMPLETED sets completedAtEpochMs
    val completed =
      (TaskRules.applyStatusTransition(inProgress, TaskStatus.COMPLETED, nowEpochMs = 3000L)
          as TaskDomainResult.Success)
        .value
    assertEquals(TaskStatus.COMPLETED, completed.status)
    assertEquals(3000L, completed.completedAtEpochMs)
    assertEquals(3000L, completed.updatedAtEpochMs)

    // COMPLETED -> CANCELLED or IN_PROGRESS directly is rejected
    val completedToCancelled =
      TaskRules.applyStatusTransition(completed, TaskStatus.CANCELLED, nowEpochMs = 4000L)
    assertTrue(completedToCancelled is TaskDomainResult.Failure)
    val completedToInProgress =
      TaskRules.applyStatusTransition(completed, TaskStatus.IN_PROGRESS, nowEpochMs = 4000L)
    assertTrue(completedToInProgress is TaskDomainResult.Failure)

    // Reopen COMPLETED -> PENDING clears completedAtEpochMs
    val reopened =
      (TaskRules.reopenTask(completed, nowEpochMs = 5000L) as TaskDomainResult.Success).value
    assertEquals(TaskStatus.PENDING, reopened.status)
    assertNull(reopened.completedAtEpochMs)
    assertEquals(5000L, reopened.updatedAtEpochMs)

    // PENDING -> CANCELLED -> Reopen -> PENDING
    val cancelled =
      (TaskRules.applyStatusTransition(reopened, TaskStatus.CANCELLED, nowEpochMs = 6000L)
          as TaskDomainResult.Success)
        .value
    assertEquals(TaskStatus.CANCELLED, cancelled.status)
    assertNull(cancelled.completedAtEpochMs)

    val cancelledToCompleted =
      TaskRules.applyStatusTransition(cancelled, TaskStatus.COMPLETED, nowEpochMs = 7000L)
    assertTrue(cancelledToCompleted is TaskDomainResult.Failure)

    val reopenedCancelled =
      (TaskRules.reopenTask(cancelled, nowEpochMs = 8000L) as TaskDomainResult.Success).value
    assertEquals(TaskStatus.PENDING, reopenedCancelled.status)
    assertNull(reopenedCancelled.completedAtEpochMs)
  }

  @Test
  fun postpone_updatesDateForActiveTasks_andRejectsTerminalTasks() {
    val active =
      (TaskRules.buildNewTaskEntity(
          TaskInput(title = "Call", scheduledDate = "2026-11-25", startTime = "10:00", endTime = "10:30"),
          nowEpochMs = 1000L,
        ) as TaskDomainResult.Success)
        .value

    val postponed =
      (TaskRules.applyPostpone(
          existing = active,
          targetDate = LocalDate.of(2026, 11, 27),
          clearTimeWindow = false,
          nowEpochMs = 2000L,
        ) as TaskDomainResult.Success)
        .value
    assertEquals("2026-11-27", postponed.scheduledDate)
    assertEquals("10:00", postponed.startTime)
    assertEquals("10:30", postponed.endTime)

    val completed =
      (TaskRules.applyStatusTransition(postponed, TaskStatus.COMPLETED, nowEpochMs = 3000L)
          as TaskDomainResult.Success)
        .value
    val terminalPostpone =
      TaskRules.applyPostpone(completed, LocalDate.of(2026, 11, 28), false, nowEpochMs = 4000L)
    assertTrue(terminalPostpone is TaskDomainResult.Failure)
  }
}
