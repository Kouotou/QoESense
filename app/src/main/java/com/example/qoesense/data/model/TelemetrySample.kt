package com.example.qoesense.data.model

import kotlinx.serialization.Serializable

/**
 * One capture window's worth of telemetry. All fields beyond the required identifiers are
 * nullable by design: a missing field means "not available on this device/API level/carrier",
 * never a failed capture - a partial sample is still written, not dropped.
 *
 * @Serializable backs the NDJSON batch upload format - see BatchSerializer.
 */
@Serializable
data class TelemetrySample(
    val timestampMs: Long,

    // Cellular - BASE_26 tier
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val timingAdvance: Int?,
    val networkType: String?,
    val cellIdentityHash: String?,
    val trackingAreaCode: Int?,
    val signalStrengthLevel: Int?,

    // Cellular - CQI_29 tier
    val cqi: Int?,
    val cqiSupported: Boolean,

    // Cellular - PHYSCHAN_30 tier
    val cellBandwidthKhz: Int?,
    val downlinkChannelNumber: Int?,
    val uplinkChannelNumber: Int?,

    // Connectivity
    val connectionType: String?,
    val isRoaming: Boolean?,
    val throughputBytesPerSecond: Long?,
    val rttMs: Long?,

    // App/session context
    val foregroundAppPackage: String?,
    val appSessionType: AppSessionType,

    // Location
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,

    // Device
    val deviceModel: String,
    val androidVersion: Int,
    val appVersion: String,
    val telemetryCapabilityTier: TelemetryCapabilityTier,
)
