package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AlarmSyncOutboxEntity
import com.example.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmSyncOutboxDao {
  @Query("SELECT * FROM alarm_sync_outbox ORDER BY seq ASC")
  fun observeAllOrdered(): Flow<List<AlarmSyncOutboxEntity>>

  @Query("SELECT * FROM alarm_sync_outbox WHERE status IN ('PENDING', 'CLAIMED') ORDER BY seq ASC")
  fun observeActiveQueue(): Flow<List<AlarmSyncOutboxEntity>>

  @Query("SELECT * FROM alarm_sync_outbox WHERE status IN ('PENDING', 'CLAIMED') ORDER BY seq ASC")
  suspend fun getActiveQueueOrdered(): List<AlarmSyncOutboxEntity>

  @Query("SELECT * FROM alarm_sync_outbox ORDER BY seq ASC")
  suspend fun getAllOrdered(): List<AlarmSyncOutboxEntity>

  @Query("SELECT * FROM alarm_sync_outbox WHERE id = :id")
  suspend fun getById(id: String): AlarmSyncOutboxEntity?

  @Query("SELECT MAX(seq) FROM alarm_sync_outbox")
  suspend fun getMaxSeq(): Long?

  @Query("SELECT MAX(requestCode) FROM alarm_sync_outbox")
  suspend fun getMaxRequestCode(): Int?

  @Query("SELECT EXISTS(SELECT 1 FROM alarm_sync_outbox WHERE requestCode = :requestCode)")
  suspend fun isRequestCodeReservedInOutbox(requestCode: Int): Boolean

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(entry: AlarmSyncOutboxEntity)

  @Query(
    """
    UPDATE alarm_sync_outbox
    SET status = :status,
        claimedLeaseGeneration = :claimedLeaseGeneration,
        claimedAtEpochMs = :claimedAtEpochMs,
        attemptCount = :attemptCount,
        lastAttemptAtEpochMs = :lastAttemptAtEpochMs,
        failureReason = :failureReason
    WHERE id = :id
    """
  )
  suspend fun updateLifecycle(
    id: String,
    status: OutboxStatus,
    claimedLeaseGeneration: Long?,
    claimedAtEpochMs: Long?,
    attemptCount: Int,
    lastAttemptAtEpochMs: Long?,
    failureReason: String?,
  ): Int

  @Query(
    """
    UPDATE alarm_sync_outbox
    SET status = 'SUPERSEDED'
    WHERE status = 'PENDING'
      AND systemTarget = 'ALARM_MANAGER'
      AND pendingIntentIdentityKey = :pendingIntentIdentityKey
      AND seq < :newerSeq
    """
  )
  suspend fun supersedePendingAlarmByIdentityBeforeSeq(
    pendingIntentIdentityKey: String,
    newerSeq: Long,
  ): Int

  @Query(
    """
    UPDATE alarm_sync_outbox
    SET status = 'SUPERSEDED'
    WHERE status = 'PENDING'
      AND systemTarget = 'NOTIFICATION_MANAGER'
      AND notificationTag = :notificationTag
      AND notificationId = :notificationId
      AND seq < :newerSeq
    """
  )
  suspend fun supersedePendingNotificationByTagIdBeforeSeq(
    notificationTag: String,
    notificationId: Int,
    newerSeq: Long,
  ): Int
}
