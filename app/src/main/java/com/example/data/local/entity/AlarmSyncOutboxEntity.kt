package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "alarm_sync_outbox",
  indices = [
    Index(value = ["seq"], unique = true),
    Index(value = ["status", "seq"]),
    Index(value = ["pendingIntentIdentityKey"]),
    Index(value = ["requestCode"]),
  ],
)
data class AlarmSyncOutboxEntity(
  @PrimaryKey val id: String,
  val seq: Long,
  val reminderId: String,
  val ownerType: ReminderOwnerType,
  val requestCode: Int,
  val systemTarget: OutboxSystemTarget,
  val action: OutboxAction,
  val component: String,
  @ColumnInfo(name = "package") val packageName: String,
  val actionName: String,
  val dataUri: String,
  val mimeType: String? = null,
  val intentIdentifier: String? = null,
  val categoriesCanonical: String,
  val pendingIntentIdentityKey: String,
  val notificationTag: String? = null,
  val notificationId: Int? = null,
  val targetScheduleRevision: Long? = null,
  val status: OutboxStatus = OutboxStatus.PENDING,
  val claimedLeaseGeneration: Long? = null,
  val claimedAtEpochMs: Long? = null,
  val createdAtEpochMs: Long,
  val attemptCount: Int = 0,
  val lastAttemptAtEpochMs: Long? = null,
  val failureReason: String? = null,
)
