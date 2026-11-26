package com.example.data.repository

import com.example.data.local.dao.AlarmSyncOutboxDao
import com.example.data.local.dao.AppMetadataDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.db.PendingIntentIdentitySnapshot
import com.example.data.local.db.YawmiMetadataKeys
import com.example.data.local.entity.AlarmSyncOutboxEntity
import com.example.data.local.entity.AppMetadataEntity
import com.example.data.local.entity.OutboxAction
import com.example.data.local.entity.OutboxStatus
import com.example.data.local.entity.OutboxSystemTarget
import com.example.data.local.entity.ReminderOwnerType
import java.util.UUID

class ReminderOutboxCoordinator(
  private val appPackageName: String,
  private val appMetadataDao: AppMetadataDao,
  private val reminderDao: ReminderDao,
  private val alarmSyncOutboxDao: AlarmSyncOutboxDao,
) {
  suspend fun allocateNextRequestCodeLocked(): Int {
    val storedCounter =
      appMetadataDao.getValue(YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE)?.toIntOrNull()
        ?: YawmiMetadataKeys.MIN_REMINDER_REQUEST_CODE
    val maxReminderCode = reminderDao.getMaxReminderRequestCode() ?: 0
    val maxOutboxCode = alarmSyncOutboxDao.getMaxRequestCode() ?: 0
    val candidate =
      maxOf(
        storedCounter.toLong(),
        maxReminderCode.toLong() + 1L,
        maxOutboxCode.toLong() + 1L,
        YawmiMetadataKeys.MIN_REMINDER_REQUEST_CODE.toLong(),
      )
    check(candidate < Int.MAX_VALUE.toLong()) {
      "Reminder requestCode allocator exhausted at Int.MAX_VALUE"
    }
    val allocated = candidate.toInt()
    appMetadataDao.put(
      AppMetadataEntity(
        key = YawmiMetadataKeys.NEXT_REMINDER_REQUEST_CODE,
        value = (allocated + 1).toString(),
      )
    )
    return allocated
  }

  suspend fun allocateNextOutboxSeqLocked(): Long {
    val storedSeq = appMetadataDao.getValue(YawmiMetadataKeys.OUTBOX_SEQ)?.toLongOrNull() ?: 0L
    val maxOutboxSeq = alarmSyncOutboxDao.getMaxSeq() ?: 0L
    val nextSeq = maxOf(storedSeq, maxOutboxSeq) + 1L
    appMetadataDao.put(
      AppMetadataEntity(
        key = YawmiMetadataKeys.OUTBOX_SEQ,
        value = nextSeq.toString(),
      )
    )
    return nextSeq
  }

  suspend fun stageScheduleAlarmLocked(
    ownerType: ReminderOwnerType,
    reminderId: String,
    requestCode: Int,
    targetScheduleRevision: Long,
    nowEpochMs: Long,
  ): AlarmSyncOutboxEntity {
    val identity =
      PendingIntentIdentitySnapshot.forReminder(
        ownerType = ownerType,
        reminderId = reminderId,
        requestCode = requestCode,
        packageName = appPackageName,
      )
    val seq = allocateNextOutboxSeqLocked()
    val entry =
      AlarmSyncOutboxEntity(
        id = UUID.randomUUID().toString(),
        seq = seq,
        reminderId = reminderId,
        ownerType = ownerType,
        requestCode = requestCode,
        systemTarget = OutboxSystemTarget.ALARM_MANAGER,
        action = OutboxAction.SCHEDULE_ALARM,
        component = identity.component,
        packageName = identity.packageName,
        actionName = identity.actionName,
        dataUri = identity.dataUri,
        mimeType = identity.mimeType,
        intentIdentifier = identity.intentIdentifier,
        categoriesCanonical = identity.categoriesCanonical,
        pendingIntentIdentityKey = identity.pendingIntentIdentityKey,
        notificationTag = identity.notificationTag,
        notificationId = identity.notificationId,
        targetScheduleRevision = targetScheduleRevision,
        status = OutboxStatus.PENDING,
        createdAtEpochMs = nowEpochMs,
      )
    alarmSyncOutboxDao.insert(entry)
    alarmSyncOutboxDao.supersedePendingAlarmByIdentityBeforeSeq(
      pendingIntentIdentityKey = entry.pendingIntentIdentityKey,
      newerSeq = entry.seq,
    )
    return entry
  }

  suspend fun stageCancelAlarmAndNotificationLocked(
    ownerType: ReminderOwnerType,
    reminderId: String,
    requestCode: Int,
    targetScheduleRevision: Long,
    nowEpochMs: Long,
  ) {
    val identity =
      PendingIntentIdentitySnapshot.forReminder(
        ownerType = ownerType,
        reminderId = reminderId,
        requestCode = requestCode,
        packageName = appPackageName,
      )
    val alarmSeq = allocateNextOutboxSeqLocked()
    val cancelAlarm =
      AlarmSyncOutboxEntity(
        id = UUID.randomUUID().toString(),
        seq = alarmSeq,
        reminderId = reminderId,
        ownerType = ownerType,
        requestCode = requestCode,
        systemTarget = OutboxSystemTarget.ALARM_MANAGER,
        action = OutboxAction.CANCEL_ALARM,
        component = identity.component,
        packageName = identity.packageName,
        actionName = identity.actionName,
        dataUri = identity.dataUri,
        mimeType = identity.mimeType,
        intentIdentifier = identity.intentIdentifier,
        categoriesCanonical = identity.categoriesCanonical,
        pendingIntentIdentityKey = identity.pendingIntentIdentityKey,
        notificationTag = identity.notificationTag,
        notificationId = identity.notificationId,
        targetScheduleRevision = targetScheduleRevision,
        status = OutboxStatus.PENDING,
        createdAtEpochMs = nowEpochMs,
      )
    alarmSyncOutboxDao.insert(cancelAlarm)
    alarmSyncOutboxDao.supersedePendingAlarmByIdentityBeforeSeq(
      pendingIntentIdentityKey = cancelAlarm.pendingIntentIdentityKey,
      newerSeq = cancelAlarm.seq,
    )

    val notifSeq = allocateNextOutboxSeqLocked()
    val cancelNotification =
      AlarmSyncOutboxEntity(
        id = UUID.randomUUID().toString(),
        seq = notifSeq,
        reminderId = reminderId,
        ownerType = ownerType,
        requestCode = requestCode,
        systemTarget = OutboxSystemTarget.NOTIFICATION_MANAGER,
        action = OutboxAction.CANCEL_NOTIFICATION,
        component = identity.component,
        packageName = identity.packageName,
        actionName = identity.actionName,
        dataUri = identity.dataUri,
        mimeType = identity.mimeType,
        intentIdentifier = identity.intentIdentifier,
        categoriesCanonical = identity.categoriesCanonical,
        pendingIntentIdentityKey = identity.pendingIntentIdentityKey,
        notificationTag = identity.notificationTag,
        notificationId = identity.notificationId,
        targetScheduleRevision = targetScheduleRevision,
        status = OutboxStatus.PENDING,
        createdAtEpochMs = nowEpochMs,
      )
    alarmSyncOutboxDao.insert(cancelNotification)
    alarmSyncOutboxDao.supersedePendingNotificationByTagIdBeforeSeq(
      notificationTag = identity.notificationTag,
      notificationId = identity.notificationId,
      newerSeq = cancelNotification.seq,
    )
  }

  suspend fun stageCancelNotificationLocked(
    ownerType: ReminderOwnerType,
    reminderId: String,
    requestCode: Int,
    targetScheduleRevision: Long,
    nowEpochMs: Long,
  ) {
    val identity =
      PendingIntentIdentitySnapshot.forReminder(
        ownerType = ownerType,
        reminderId = reminderId,
        requestCode = requestCode,
        packageName = appPackageName,
      )
    val notifSeq = allocateNextOutboxSeqLocked()
    val cancelNotification =
      AlarmSyncOutboxEntity(
        id = UUID.randomUUID().toString(),
        seq = notifSeq,
        reminderId = reminderId,
        ownerType = ownerType,
        requestCode = requestCode,
        systemTarget = OutboxSystemTarget.NOTIFICATION_MANAGER,
        action = OutboxAction.CANCEL_NOTIFICATION,
        component = identity.component,
        packageName = identity.packageName,
        actionName = identity.actionName,
        dataUri = identity.dataUri,
        mimeType = identity.mimeType,
        intentIdentifier = identity.intentIdentifier,
        categoriesCanonical = identity.categoriesCanonical,
        pendingIntentIdentityKey = identity.pendingIntentIdentityKey,
        notificationTag = identity.notificationTag,
        notificationId = identity.notificationId,
        targetScheduleRevision = targetScheduleRevision,
        status = OutboxStatus.PENDING,
        createdAtEpochMs = nowEpochMs,
      )
    alarmSyncOutboxDao.insert(cancelNotification)
    alarmSyncOutboxDao.supersedePendingNotificationByTagIdBeforeSeq(
      notificationTag = identity.notificationTag,
      notificationId = identity.notificationId,
      newerSeq = cancelNotification.seq,
    )
  }
}
