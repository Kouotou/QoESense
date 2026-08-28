package com.example.qoesense.data.upload

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/** Uploads to telemetry/{deviceId}/{dateFolder}/{batchId}.json.gz in the default Storage bucket. */
class FirebaseStorageUploadDestination(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : TelemetryUploadDestination {

    override suspend fun upload(deviceId: String, dateFolder: String, batchId: String, gzippedNdjson: ByteArray): Boolean {
        return try {
            val ref = storage.reference.child("telemetry/$deviceId/$dateFolder/$batchId.json.gz")
            ref.putBytes(gzippedNdjson).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
