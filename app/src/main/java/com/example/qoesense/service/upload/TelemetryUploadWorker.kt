package com.example.qoesense.service.upload

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.qoesense.data.CollectionStatePreferences
import com.example.qoesense.data.TelemetryRepository
import com.example.qoesense.data.upload.BatchSerializer
import com.example.qoesense.data.upload.DeviceIdentityProvider
import com.example.qoesense.data.upload.FirebaseAuthDeviceIdentityProvider
import com.example.qoesense.data.upload.FirebaseStorageUploadDestination
import com.example.qoesense.data.upload.TelemetryUploadDestination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Reads the oldest buffered batch from Room, serializes it, and hands it to
 * [TelemetryUploadDestination]. Rows are deleted only after a confirmed upload - if the
 * upload fails or a device ID can't be obtained, this retries later rather than dropping
 * data. The two dependencies are swappable (see the constructor) purely so tests can inject
 * stubs instead of hitting real Firebase.
 */
class TelemetryUploadWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val deviceIdentityProvider: DeviceIdentityProvider = FirebaseAuthDeviceIdentityProvider(),
    private val uploadDestination: TelemetryUploadDestination = FirebaseStorageUploadDestination(),
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() started")
        val repository = TelemetryRepository(applicationContext)
        val result = uploadPendingBatches(repository)

        // Retention runs only after this run's upload attempts, never before: when uploads are
        // working the backlog has just been drained and nothing old is left to purge, so this
        // only ever drops rows that genuinely could not be uploaded within the retention
        // window (e.g. a long backend outage). Running it here - rather than when the Status
        // screen happens to be opened - means it applies whether or not the participant ever
        // opens the app.
        val purged = repository.enforceRetention()
        if (purged > 0) Log.w(TAG, "Retention purged $purged never-uploaded samples")
        return result
    }

    /**
     * Uploads oldest-first batches until the buffer is empty, a batch fails, or
     * [MAX_BATCHES_PER_RUN] is reached (keeps one run well inside WorkManager's execution
     * window). Draining in a loop matters after an outage: one batch per hourly run could
     * otherwise take days to clear a backlog.
     */
    private suspend fun uploadPendingBatches(repository: TelemetryRepository): Result {
        var deviceId: String? = null
        repeat(MAX_BATCHES_PER_RUN) {
            val batch = repository.oldestPendingBatch(BATCH_LIMIT)
            Log.d(TAG, "batch size=${batch.samples.size}")
            if (batch.samples.isEmpty()) return Result.success()

            if (deviceId == null) deviceId = deviceIdentityProvider.getDeviceId()
            val id = deviceId ?: return Result.retry()

            val gzippedNdjson = BatchSerializer.toGzippedNdjson(batch.samples, id)
            val dateFolder = dateFolderFormat.format(Date())
            val batchId = UUID.randomUUID().toString()
            Log.d(TAG, "uploading batchId=$batchId, ${gzippedNdjson.size} bytes")

            val uploaded = uploadDestination.upload(id, dateFolder, batchId, gzippedNdjson)
            Log.d(TAG, "upload result=$uploaded")
            if (!uploaded) return Result.retry()

            repository.deleteBatch(batch.ids)
            CollectionStatePreferences(applicationContext).setLastUploadTimestamp(System.currentTimeMillis())
        }
        Log.d(TAG, "reached $MAX_BATCHES_PER_RUN batches this run; remainder goes next run")
        return Result.success()
    }

    companion object {
        private const val TAG = "QoESenseUploadWorker"
        const val BATCH_LIMIT = 2_000
        const val MAX_BATCHES_PER_RUN = 20
        const val WORK_NAME = "telemetry_upload"

        private val dateFolderFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
