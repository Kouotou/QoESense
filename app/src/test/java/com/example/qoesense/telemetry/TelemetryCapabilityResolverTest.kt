package com.example.qoesense.telemetry

import com.example.qoesense.data.model.TelemetryCapabilityTier
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryCapabilityResolverTest {

    private fun reading(
        cqiSupported: Boolean = false,
        cellBandwidthKhz: Int? = null,
        downlinkChannelNumber: Int? = null,
        uplinkChannelNumber: Int? = null,
    ) = CellularTelemetrySource.Reading(
        rsrp = -95,
        rsrq = -10,
        rssnr = 12,
        timingAdvance = 3,
        networkType = "LTE",
        cellIdentityHash = "abc",
        trackingAreaCode = 1,
        signalStrengthLevel = 3,
        cqi = if (cqiSupported) 9 else null,
        cqiSupported = cqiSupported,
        cellBandwidthKhz = cellBandwidthKhz,
        downlinkChannelNumber = downlinkChannelNumber,
        uplinkChannelNumber = uplinkChannelNumber,
    )

    @Test
    fun `null reading resolves to BASE_26`() {
        assertEquals(TelemetryCapabilityTier.BASE_26, TelemetryCapabilityResolver.resolve(null))
    }

    @Test
    fun `no CQI and no physical channel data resolves to BASE_26`() {
        assertEquals(TelemetryCapabilityTier.BASE_26, TelemetryCapabilityResolver.resolve(reading()))
    }

    @Test
    fun `CQI supported without physical channel data resolves to CQI_29`() {
        val result = TelemetryCapabilityResolver.resolve(reading(cqiSupported = true))
        assertEquals(TelemetryCapabilityTier.CQI_29, result)
    }

    @Test
    fun `physical channel data present resolves to PHYSCHAN_30 even without CQI`() {
        val result = TelemetryCapabilityResolver.resolve(reading(cellBandwidthKhz = 20000))
        assertEquals(TelemetryCapabilityTier.PHYSCHAN_30, result)
    }

    @Test
    fun `physical channel data and CQI both present resolves to PHYSCHAN_30`() {
        val result = TelemetryCapabilityResolver.resolve(
            reading(cqiSupported = true, downlinkChannelNumber = 1850, uplinkChannelNumber = 19850)
        )
        assertEquals(TelemetryCapabilityTier.PHYSCHAN_30, result)
    }
}
