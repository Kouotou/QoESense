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
        val batch = repository.oldestPendingBatch(BATCH_LIMIT)
        Log.d(TAG, "batch size=${batch.samples.size}")
        if (batch.samples.isEmpty()) return Result.success()

        val deviceId = deviceIdentityProvider.getDeviceId()
        Log.d(TAG, "deviceId=$deviceId")
        if (deviceId == null) return Result.retry()

        val gzippedNdjson = BatchSerializer.toGzippedNdjson(batch.samples, deviceId)
        val dateFolder = dateFolderFormat.format(Date())
        val batchId = UUID.randomUUID().toString()
        Log.d(TAG, "uploading batchId=$batchId, ${gzippedNdjson.size} bytes")

        val uploaded = uploadDestination.upload(deviceId, dateFolder, batchId, gzippedNdjson)
        Log.d(TAG, "upload result=$uploaded")
        if (!uploaded) return Result.retry()

        repository.deleteBatch(batch.ids)
        CollectionStatePreferences(applicationContext).setLastUploadTimestamp(System.currentTimeMillis())
        Log.d(TAG, "doWork() finished successfully")
        return Result.success()
    }

    companion object {
        private const val TAG = "QoESenseUploadWorker"
        const val BATCH_LIMIT = 2_000
        const val WORK_NAME = "telemetry_upload"

        private val dateFolderFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
