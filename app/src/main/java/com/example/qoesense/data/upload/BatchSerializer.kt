package com.example.qoesense.data.upload

import com.example.qoesense.data.model.TelemetrySample
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Turns a batch of samples into the exact bytes that get uploaded to Cloud Storage: one JSON
 * object per line (NDJSON), gzip-compressed. Pure/JVM-only - no Android dependency - so it's
 * fully covered by `./gradlew test` without a device or emulator.
 *
 * Each line embeds [deviceId] alongside the sample's own fields (rather than leaving it only
 * in the Storage path) so the Cloud Function that loads these files into BigQuery can load
 * them as-is, with no path parsing - see functions/index.js's SCHEMA, which mirrors
 * TelemetrySample plus this one extra field.
 */
object BatchSerializer {

    private val json = Json { encodeDefaults = true }

    fun toNdjson(samples: List<TelemetrySample>, deviceId: String): String =
        samples.joinToString(separator = "\n") { sample ->
            val fields = json.encodeToJsonElement(sample).jsonObject.toMutableMap()
            fields["deviceId"] = JsonPrimitive(deviceId)
            json.encodeToString(JsonObject(fields))
        }

    fun toGzippedNdjson(samples: List<TelemetrySample>, deviceId: String): ByteArray {
        val ndjson = toNdjson(samples, deviceId)
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzip ->
            gzip.write(ndjson.toByteArray(Charsets.UTF_8))
        }
        return byteStream.toByteArray()
    }
}
