package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AlarmSyncOutboxDao
import com.example.data.local.dao.AppMetadataDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.GoalDao
import com.example.data.local.dao.HabitDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.RecurrenceDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.ReminderDeliveryDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.entity.AlarmSyncOutboxEntity
import com.example.data.local.entity.AppMetadataEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.GoalEntity
import com.example.data.local.entity.GoalHabitCrossRef
import com.example.data.local.entity.GoalReminderDeliveryEntity
import com.example.data.local.entity.GoalReminderEntity
import com.example.data.local.entity.GoalTaskCrossRef
import com.example.data.local.entity.HabitCompletionEntity
import com.example.data.local.entity.HabitEntity
import com.example.data.local.entity.HabitRecurrenceEntity
import com.example.data.local.entity.HabitReminderDeliveryEntity
import com.example.data.local.entity.HabitReminderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskRecurrenceEntity
import com.example.data.local.entity.TaskReminderDeliveryEntity
import com.example.data.local.entity.TaskReminderEntity

@Database(
  entities = [
    CategoryEntity::class,
    TaskEntity::class,
    HabitEntity::class,
    HabitCompletionEntity::class,
    GoalEntity::class,
    GoalTaskCrossRef::class,
    GoalHabitCrossRef::class,
    TaskRecurrenceEntity::class,
    HabitRecurrenceEntity::class,
    TaskReminderEntity::class,
    HabitReminderEntity::class,
    GoalReminderEntity::class,
    NoteEntity::class,
    AppMetadataEntity::class,
    AlarmSyncOutboxEntity::class,
    TaskReminderDeliveryEntity::class,
    HabitReminderDeliveryEntity::class,
    GoalReminderDeliveryEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(YawmiTypeConverters::class)
abstract class YawmiDatabase : RoomDatabase() {
  abstract fun categoryDao(): CategoryDao

  abstract fun taskDao(): TaskDao

  abstract fun habitDao(): HabitDao

  abstract fun goalDao(): GoalDao

  abstract fun recurrenceDao(): RecurrenceDao

  abstract fun reminderDao(): ReminderDao

  abstract fun noteDao(): NoteDao

  abstract fun appMetadataDao(): AppMetadataDao

  abstract fun alarmSyncOutboxDao(): AlarmSyncOutboxDao

  abstract fun reminderDeliveryDao(): ReminderDeliveryDao

  companion object {
    const val DATABASE_NAME = "yawmi.db"
    const val CURRENT_DB_VERSION = 1

    fun build(context: Context): YawmiDatabase = openWithName(context, DATABASE_NAME)

    fun openWithName(context: Context, name: String): YawmiDatabase =
      Room.databaseBuilder(context.applicationContext, YawmiDatabase::class.java, name)
        .addMigrations(*YawmiMigrations.ALL_MIGRATIONS)
        .addCallback(IntegrityCallback)
        .build()

    fun inMemory(context: Context): YawmiDatabase =
      Room.inMemoryDatabaseBuilder(context.applicationContext, YawmiDatabase::class.java)
        .addMigrations(*YawmiMigrations.ALL_MIGRATIONS)
        .addCallback(IntegrityCallback)
        .build()

    internal object IntegrityCallback : Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedMetadata(db)
        installGlobalRequestCodeTriggers(db)
      }

      override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
      }

      private fun seedMetadata(db: SupportSQLiteDatabase) {
        val defaults =
          listOf(
            YawmiMetadataKeys.SCHEMA_INITIALIZED to "1",
            YawmiMetadataKeys.OUTBOX_SEQ to "0",
            YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE to
              YawmiMetadataKeys.MIN_REMINDER_REQUEST_CODE.toString(),
            YawmiMetadataKeys.DISPATCHER_LEASE_OWNER to "",
            YawmiMetadataKeys.DISPATCHER_LEASE_UNTIL_EPOCH_MS to "0",
            YawmiMetadataKeys.DISPATCHER_LEASE_GENERATION to "0",
          )
        for ((key, value) in defaults) {
          db.execSQL(
            "INSERT OR IGNORE INTO app_metadata (`key`, `value`) VALUES (?, ?)",
            arrayOf(key, value),
          )
        }
      }

      private fun installGlobalRequestCodeTriggers(db: SupportSQLiteDatabase) {
        val rules =
          listOf(
            Triple("task_reminders", "habit_reminders", "goal_reminders"),
            Triple("habit_reminders", "task_reminders", "goal_reminders"),
            Triple("goal_reminders", "task_reminders", "habit_reminders"),
          )
        for ((table, otherA, otherB) in rules) {
          val checkPredicate =
            "EXISTS(SELECT 1 FROM $otherA WHERE requestCode = NEW.requestCode) " +
              "OR EXISTS(SELECT 1 FROM $otherB WHERE requestCode = NEW.requestCode)"

          db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_${table}_global_reqcode_insert
            BEFORE INSERT ON $table
            WHEN $checkPredicate
            BEGIN
              SELECT RAISE(ABORT, 'Duplicate global reminder requestCode across reminder tables');
            END
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_${table}_global_reqcode_update
            BEFORE UPDATE OF requestCode ON $table
            WHEN NEW.requestCode != OLD.requestCode AND ($checkPredicate)
            BEGIN
              SELECT RAISE(ABORT, 'Duplicate global reminder requestCode across reminder tables');
            END
            """.trimIndent()
          )
        }
      }
    }
  }
}
