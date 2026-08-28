package com.example.qoesense.data.model

import kotlinx.serialization.Serializable

/**
 * Not all telemetry fields are available on all devices - it depends on API level, chipset,
 * and carrier. Every sample is tagged with the tier it actually achieved (derived from what
 * the platform returned, not just SDK_INT) so the ML pipeline can condition on data
 * completeness instead of silently averaging over missing fields.
 */
@Serializable
enum class TelemetryCapabilityTier {
    /** RSRP, RSRQ, SINR, timing advance, network type, cell identity. All API 26+. */
    BASE_26,

    /** BASE_26 plus CQI - API 29+, and only where the chipset/carrier actually reports it. */
    CQI_29,

    /**
     * CQI_29 plus PhysicalChannelConfig (cell bandwidth, DL/UL channel numbers) - API 31+.
     * Rarely reached in practice: the underlying listener requires carrier privileges that
     * ordinary participant devices don't have. See [com.example.qoesense.telemetry.CellularTelemetrySource].
     */
    PHYSCHAN_30,
}
