package com.example.qoesense.data

import android.content.Context
import com.example.qoesense.data.local.QoESenseDatabase
import com.example.qoesense.data.local.TelemetrySampleEntity
import com.example.qoesense.data.local.toDomain
import com.example.qoesense.data.local.toEntity
import com.example.qoesense.data.model.TelemetrySample
import kotlinx.coroutines.flow.Flow

/**
 * Local buffer in front of the Cloud Storage upload pipeline. A sample is only ever removed
 * once that pipeline confirms a successful upload.
 */
class TelemetryRepository(context: Context) {

    private val dao = QoESenseDatabase.getInstance(context).telemetryDao()

    data class PendingBatch(val ids: List<Long>, val samples: List<TelemetrySample>)

    suspend fun record(sample: TelemetrySample) {
        dao.insert(sample.toEntity())
    }

    fun observeBufferedCount(): Flow<Int> = dao.observeCount()

    suspend fun oldestPending(limit: Int): List<TelemetrySample> =
        dao.getOldest(limit).map { it.toDomain() }

    suspend fun samplesSince(sinceMs: Long): List<TelemetrySample> =
        dao.getSince(sinceMs).map { it.toDomain() }

    /** Like [oldestPending], but keeps row IDs so the caller can delete exactly this batch after upload. */
    suspend fun oldestPendingBatch(limit: Int): PendingBatch {
        val entities: List<TelemetrySampleEntity> = dao.getOldest(limit)
        return PendingBatch(
            ids = entities.map { it.id },
            samples = entities.map { it.toDomain() },
        )
    }

    /** Call only after a batch has been durably confirmed uploaded. */
    suspend fun deleteBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    /** Drops rows older than [maxAgeMs] that were never successfully uploaded. */
    suspend fun enforceRetention(maxAgeMs: Long = DEFAULT_RETENTION_MS): Int =
        dao.deleteOlderThan(System.currentTimeMillis() - maxAgeMs)

    companion object {
        // Temporarily widened from 14 to 45 days (Sept 2026) while the Firebase project's
        // billing is suspended pending a payment fix - protects already-buffered participant
        // samples from being purged unuploaded during the outage. Safe to shrink back to 14
        // once uploads are confirmed flowing normally again for a few days.
        const val DEFAULT_RETENTION_MS = 45L * 24 * 60 * 60 * 1000
    }
}
