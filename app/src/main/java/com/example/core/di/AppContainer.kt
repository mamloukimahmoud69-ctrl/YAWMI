package com.example.core.di

import android.content.Context
import com.example.core.preferences.AppAppearanceRepository
import com.example.core.preferences.DataStoreAppAppearanceRepository
import com.example.core.time.LocalClock
import com.example.core.time.SystemLocalClock
import com.example.data.local.db.YawmiDatabase
import com.example.data.repository.ReminderOutboxCoordinator
import com.example.data.repository.RoomCategoryRepository
import com.example.data.repository.RoomGoalRepository
import com.example.data.repository.RoomHabitRepository
import com.example.data.repository.RoomNoteRepository
import com.example.data.repository.RoomReminderSyncRepository
import com.example.data.repository.RoomTaskRepository
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.GoalRepository
import com.example.domain.repository.HabitRepository
import com.example.domain.repository.NoteRepository
import com.example.domain.repository.ReminderSyncRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskReminderUseCases
import com.example.domain.task.TaskUseCases

interface AppContainer {
  val appearanceRepository: AppAppearanceRepository
  val localClock: LocalClock
  val database: YawmiDatabase
  val categoryRepository: CategoryRepository
  val taskRepository: TaskRepository
  val habitRepository: HabitRepository
  val goalRepository: GoalRepository
  val noteRepository: NoteRepository
  val reminderSyncRepository: ReminderSyncRepository
  val taskUseCases: TaskUseCases
  val taskReminderUseCases: TaskReminderUseCases
    get() = TaskReminderUseCases(taskRepository, reminderSyncRepository, localClock)
  val categoryUseCases: CategoryUseCases
}

class DefaultAppContainer(
  context: Context,
  databaseProvider: ((Context) -> YawmiDatabase)? = null,
) : AppContainer {
  private val appContext = context.applicationContext

  override val appearanceRepository: AppAppearanceRepository by lazy {
    DataStoreAppAppearanceRepository(appContext)
  }

  override val localClock: LocalClock by lazy { SystemLocalClock() }

  override val database: YawmiDatabase by lazy {
    databaseProvider?.invoke(appContext) ?: YawmiDatabase.build(appContext)
  }

  private val outboxCoordinator: ReminderOutboxCoordinator by lazy {
    ReminderOutboxCoordinator(
      appPackageName = appContext.packageName,
      appMetadataDao = database.appMetadataDao(),
      reminderDao = database.reminderDao(),
      alarmSyncOutboxDao = database.alarmSyncOutboxDao(),
    )
  }

  override val categoryRepository: CategoryRepository by lazy {
    RoomCategoryRepository(database.categoryDao())
  }

  override val taskRepository: TaskRepository by lazy {
    RoomTaskRepository(
      database = database,
      taskDao = database.taskDao(),
      recurrenceDao = database.recurrenceDao(),
      reminderDao = database.reminderDao(),
      deliveryDao = database.reminderDeliveryDao(),
      outboxCoordinator = outboxCoordinator,
      localClock = localClock,
      goalDao = database.goalDao(),
    )
  }

  override val habitRepository: HabitRepository by lazy {
    RoomHabitRepository(
      database = database,
      habitDao = database.habitDao(),
      recurrenceDao = database.recurrenceDao(),
      reminderDao = database.reminderDao(),
      deliveryDao = database.reminderDeliveryDao(),
      outboxCoordinator = outboxCoordinator,
    )
  }

  override val goalRepository: GoalRepository by lazy {
    RoomGoalRepository(
      database = database,
      goalDao = database.goalDao(),
      reminderDao = database.reminderDao(),
      deliveryDao = database.reminderDeliveryDao(),
      outboxCoordinator = outboxCoordinator,
    )
  }

  override val noteRepository: NoteRepository by lazy {
    RoomNoteRepository(database.noteDao())
  }

  override val reminderSyncRepository: ReminderSyncRepository by lazy {
    RoomReminderSyncRepository(
      database = database,
      reminderDao = database.reminderDao(),
      deliveryDao = database.reminderDeliveryDao(),
      outboxDao = database.alarmSyncOutboxDao(),
      metadataDao = database.appMetadataDao(),
      outboxCoordinator = outboxCoordinator,
    )
  }

  override val taskUseCases: TaskUseCases by lazy {
    TaskUseCases(
      taskRepository = taskRepository,
      categoryRepository = categoryRepository,
      localClock = localClock,
    )
  }

  override val taskReminderUseCases: TaskReminderUseCases by lazy {
    TaskReminderUseCases(
      taskRepository = taskRepository,
      reminderSyncRepository = reminderSyncRepository,
      localClock = localClock,
    )
  }

  override val categoryUseCases: CategoryUseCases by lazy {
    CategoryUseCases(
      categoryRepository = categoryRepository,
      localClock = localClock,
    )
  }
}
