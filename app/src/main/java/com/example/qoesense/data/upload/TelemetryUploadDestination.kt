package com.example.qoesense.data.upload

/**
 * Where a serialized batch actually gets uploaded to. This is the seam that will be
 * implemented against Firebase Cloud Storage (path: telemetry/{deviceId}/{dateFolder}/{batchId}.json.gz)
 * once the Firebase project exists - see CLAUDE.md for the console setup checklist.
 */
interface TelemetryUploadDestination {
    /** Returns true only once the batch is durably persisted remotely - never optimistically. */
    suspend fun upload(deviceId: String, dateFolder: String, batchId: String, gzippedNdjson: ByteArray): Boolean
}

/** Placeholder used until a real destination (Firebase Storage) is wired in. Always fails closed. */
class UnconfiguredUploadDestination : TelemetryUploadDestination {
    override suspend fun upload(deviceId: String, dateFolder: String, batchId: String, gzippedNdjson: ByteArray): Boolean {
        return false
    }
}
