package com.example.qoesense.telemetry

import com.example.qoesense.data.model.TelemetryCapabilityTier

/**
 * Derives the achieved capability tier from what a [CellularTelemetrySource.Reading] actually
 * contains, not just SDK_INT - a device can be API 30+ and still report no PhysicalChannelConfig
 * for a given carrier/chipset, in which case the sample should be tagged CQI_29 or lower.
 */
object TelemetryCapabilityResolver {
    fun resolve(reading: CellularTelemetrySource.Reading?): TelemetryCapabilityTier {
        if (reading == null) return TelemetryCapabilityTier.BASE_26
        val hasPhysChan = reading.cellBandwidthKhz != null ||
            reading.downlinkChannelNumber != null ||
            reading.uplinkChannelNumber != null
        return when {
            hasPhysChan -> TelemetryCapabilityTier.PHYSCHAN_30
            reading.cqiSupported -> TelemetryCapabilityTier.CQI_29
            else -> TelemetryCapabilityTier.BASE_26
        }
    }
}
