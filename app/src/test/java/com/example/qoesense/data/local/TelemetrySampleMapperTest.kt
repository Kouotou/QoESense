package com.example.qoesense.data.local

import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.TelemetryCapabilityTier
import com.example.qoesense.data.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySampleMapperTest {

    private val fullSample = TelemetrySample(
        timestampMs = 1_700_000_000_000,
        rsrp = -95,
        rsrq = -10,
        rssnr = 12,
        timingAdvance = 3,
        networkType = "LTE",
        cellIdentityHash = "abc123",
        trackingAreaCode = 4567,
        signalStrengthLevel = 3,
        cqi = 9,
        cqiSupported = true,
        cellBandwidthKhz = 20000,
        downlinkChannelNumber = 1850,
        uplinkChannelNumber = 19850,
        connectionType = "CELLULAR",
        isRoaming = false,
        throughputBytesPerSecond = 123_456,
        rttMs = 42,
        foregroundAppPackage = "com.whatsapp",
        appSessionType = AppSessionType.VIDEO_CONFERENCING,
        latitude = 51.5074,
        longitude = -0.1278,
        locationAccuracyMeters = 5.5f,
        deviceModel = "Pixel Test",
        androidVersion = 34,
        appVersion = "1.0",
        telemetryCapabilityTier = TelemetryCapabilityTier.PHYSCHAN_30,
    )

    @Test
    fun `entity round-trip preserves every field`() {
        val roundTripped = fullSample.toEntity().toDomain()
        assertEquals(fullSample, roundTripped)
    }

    @Test
    fun `entity round-trip preserves nulls for an unavailable-fields sample`() {
        val sparseSample = fullSample.copy(
            rsrp = null,
            rsrq = null,
            rssnr = null,
            timingAdvance = null,
            cqi = null,
            cqiSupported = false,
            cellBandwidthKhz = null,
            downlinkChannelNumber = null,
            uplinkChannelNumber = null,
            isRoaming = null,
            throughputBytesPerSecond = null,
            rttMs = null,
            latitude = null,
            longitude = null,
            locationAccuracyMeters = null,
            foregroundAppPackage = null,
            appSessionType = AppSessionType.NONE,
            telemetryCapabilityTier = TelemetryCapabilityTier.BASE_26,
        )
        val roundTripped = sparseSample.toEntity().toDomain()
        assertEquals(sparseSample, roundTripped)
    }
}
