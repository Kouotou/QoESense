package com.example.qoesense.data.upload

import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.TelemetryCapabilityTier
import com.example.qoesense.data.model.TelemetrySample
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchSerializerTest {

    // The uploaded JSON has one extra "deviceId" field beyond TelemetrySample's own properties.
    private val decodingJson = Json { ignoreUnknownKeys = true }

    private val deviceId = "test-device-uid"

    private fun sample(timestampMs: Long, rsrp: Int?) = TelemetrySample(
        timestampMs = timestampMs,
        rsrp = rsrp,
        rsrq = -10,
        rssnr = 12,
        timingAdvance = 3,
        networkType = "LTE",
        cellIdentityHash = "abc123",
        trackingAreaCode = 4567,
        signalStrengthLevel = 3,
        cqi = null,
        cqiSupported = false,
        cellBandwidthKhz = null,
        downlinkChannelNumber = null,
        uplinkChannelNumber = null,
        connectionType = "CELLULAR",
        isRoaming = false,
        throughputBytesPerSecond = 123_456,
        rttMs = 42,
        foregroundAppPackage = "com.whatsapp",
        appSessionType = AppSessionType.SOCIAL_MEDIA,
        latitude = 51.5074,
        longitude = -0.1278,
        locationAccuracyMeters = 5.5f,
        deviceModel = "Pixel Test",
        androidVersion = 34,
        appVersion = "1.0",
        telemetryCapabilityTier = TelemetryCapabilityTier.CQI_29,
    )

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toString(Charsets.UTF_8)

    @Test
    fun `each sample becomes exactly one NDJSON line`() {
        val samples = listOf(sample(1L, -90), sample(2L, -95), sample(3L, null))
        val ndjson = BatchSerializer.toNdjson(samples, deviceId)
        val lines = ndjson.split("\n")
        assertEquals(3, lines.size)
    }

    @Test
    fun `every line embeds the deviceId alongside the sample's own fields`() {
        val samples = listOf(sample(1L, -90), sample(2L, -95))
        val ndjson = BatchSerializer.toNdjson(samples, deviceId)
        ndjson.split("\n").forEach { line ->
            val parsed = Json.parseToJsonElement(line).jsonObject
            assertEquals(deviceId, parsed["deviceId"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `gzipped batch round-trips to the original samples via gunzip and JSON decode`() {
        val samples = listOf(sample(1L, -90), sample(2L, -95), sample(3L, null))
        val gzipped = BatchSerializer.toGzippedNdjson(samples, deviceId)

        val decoded = gunzip(gzipped).split("\n")
            .map { line -> decodingJson.decodeFromString<TelemetrySample>(line) }

        assertEquals(samples, decoded)
    }

    @Test
    fun `empty batch produces empty NDJSON`() {
        assertTrue(BatchSerializer.toNdjson(emptyList(), deviceId).isEmpty())
    }

    @Test
    fun `null fields are preserved through the gzip round-trip`() {
        val sparse = sample(1L, null).copy(rttMs = null, latitude = null, foregroundAppPackage = null)
        val gzipped = BatchSerializer.toGzippedNdjson(listOf(sparse), deviceId)
        val decoded = decodingJson.decodeFromString<TelemetrySample>(gunzip(gzipped))
        assertEquals(sparse, decoded)
    }
}
