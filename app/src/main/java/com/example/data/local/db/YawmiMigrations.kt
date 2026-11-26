package com.example.data.local.db

import androidx.room.migration.Migration

object YawmiMigrations {
  /**
   * Explicit non-destructive migration registry.
   * Version 1 is the baseline schema. Every future schema bump MUST add an explicit
   * Migration(n, n + 1) here; destructive fallback builders are strictly forbidden.
   */
  val ALL_MIGRATIONS: Array<Migration> = emptyArray()
}
