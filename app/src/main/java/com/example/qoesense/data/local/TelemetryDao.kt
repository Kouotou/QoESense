package com.example.qoesense.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {

    @Insert
    suspend fun insert(sample: TelemetrySampleEntity): Long

    @Query("SELECT * FROM telemetry_samples ORDER BY timestampMs ASC LIMIT :limit")
    suspend fun getOldest(limit: Int): List<TelemetrySampleEntity>

    @Query("SELECT * FROM telemetry_samples WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    suspend fun getSince(sinceMs: Long): List<TelemetrySampleEntity>

    @Query("SELECT COUNT(*) FROM telemetry_samples")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM telemetry_samples WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Retention cap: drop anything that was never successfully uploaded within [cutoffMs]. */
    @Query("DELETE FROM telemetry_samples WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
