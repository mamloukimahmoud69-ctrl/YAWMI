package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AppMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {
  @Query("SELECT * FROM app_metadata ORDER BY `key` ASC")
  fun observeAll(): Flow<List<AppMetadataEntity>>

  @Query("SELECT value FROM app_metadata WHERE `key` = :key")
  suspend fun getValue(key: String): String?

  @Query("SELECT * FROM app_metadata WHERE `key` = :key")
  suspend fun getEntry(key: String): AppMetadataEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun put(entry: AppMetadataEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIfAbsent(entry: AppMetadataEntity)

  @Query("DELETE FROM app_metadata WHERE `key` = :key")
  suspend fun deleteKey(key: String): Int
}
