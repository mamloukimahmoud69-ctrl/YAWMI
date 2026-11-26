package com.example.data.local.db

object YawmiMetadataKeys {
  const val SCHEMA_INITIALIZED = "schema_initialized"
  const val OUTBOX_SEQ = "outbox_seq"
  const val NEXT_REMINDER_REQUEST_CODE = "next_reminder_request_code"
  const val DISPATCHER_LEASE_OWNER = "dispatcherLeaseOwner"
  const val DISPATCHER_LEASE_UNTIL_EPOCH_MS = "dispatcherLeaseUntilEpochMs"
  const val DISPATCHER_LEASE_GENERATION = "dispatcherLeaseGeneration"

  const val MIN_REMINDER_REQUEST_CODE = 1000

  val INTERNAL_NON_IMPORTABLE_KEYS: Set<String> =
    setOf(
      SCHEMA_INITIALIZED,
      OUTBOX_SEQ,
      NEXT_REMINDER_REQUEST_CODE,
      DISPATCHER_LEASE_OWNER,
      DISPATCHER_LEASE_UNTIL_EPOCH_MS,
      DISPATCHER_LEASE_GENERATION,
    )
}
