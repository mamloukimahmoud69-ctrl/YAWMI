package com.example.data.local.db

import androidx.room.TypeConverter
import com.example.data.local.entity.DeliveryState
import com.example.data.local.entity.GoalStatus
import com.example.data.local.entity.OutboxAction
import com.example.data.local.entity.OutboxStatus
import com.example.data.local.entity.OutboxSystemTarget
import com.example.data.local.entity.RecurrenceFrequency
import com.example.data.local.entity.ReminderOwnerType
import com.example.data.local.entity.ReminderScheduleState
import com.example.data.local.entity.TaskPriority
import com.example.data.local.entity.TaskStatus

class YawmiTypeConverters {
  @TypeConverter fun fromTaskPriority(value: TaskPriority): String = value.name

  @TypeConverter fun toTaskPriority(raw: String): TaskPriority = TaskPriority.valueOf(raw)

  @TypeConverter fun fromTaskStatus(value: TaskStatus): String = value.name

  @TypeConverter fun toTaskStatus(raw: String): TaskStatus = TaskStatus.valueOf(raw)

  @TypeConverter fun fromGoalStatus(value: GoalStatus): String = value.name

  @TypeConverter fun toGoalStatus(raw: String): GoalStatus = GoalStatus.valueOf(raw)

  @TypeConverter fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.name

  @TypeConverter
  fun toRecurrenceFrequency(raw: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(raw)

  @TypeConverter fun fromReminderScheduleState(value: ReminderScheduleState): String = value.name

  @TypeConverter
  fun toReminderScheduleState(raw: String): ReminderScheduleState =
    ReminderScheduleState.valueOf(raw)

  @TypeConverter fun fromReminderOwnerType(value: ReminderOwnerType): String = value.name

  @TypeConverter
  fun toReminderOwnerType(raw: String): ReminderOwnerType = ReminderOwnerType.valueOf(raw)

  @TypeConverter fun fromOutboxSystemTarget(value: OutboxSystemTarget): String = value.name

  @TypeConverter
  fun toOutboxSystemTarget(raw: String): OutboxSystemTarget = OutboxSystemTarget.valueOf(raw)

  @TypeConverter fun fromOutboxAction(value: OutboxAction): String = value.name

  @TypeConverter fun toOutboxAction(raw: String): OutboxAction = OutboxAction.valueOf(raw)

  @TypeConverter fun fromOutboxStatus(value: OutboxStatus): String = value.name

  @TypeConverter fun toOutboxStatus(raw: String): OutboxStatus = OutboxStatus.valueOf(raw)

  @TypeConverter fun fromDeliveryState(value: DeliveryState): String = value.name

  @TypeConverter fun toDeliveryState(raw: String): DeliveryState = DeliveryState.valueOf(raw)
}
