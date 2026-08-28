package com.example.qoesense.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.TelemetryCapabilityTier

/**
 * Room row for one buffered TelemetrySample. A row's presence in the table *is* the "pending
 * upload" state - rows are only deleted once the WorkManager upload pipeline confirms a
 * successful write to Cloud Storage, never before.
 */
@Entity(tableName = "telemetry_samples")
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestampMs: Long,

    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val timingAdvance: Int?,
    val networkType: String?,
    val cellIdentityHash: String?,
    val trackingAreaCode: Int?,
    val signalStrengthLevel: Int?,

    val cqi: Int?,
    val cqiSupported: Boolean,

    val cellBandwidthKhz: Int?,
    val downlinkChannelNumber: Int?,
    val uplinkChannelNumber: Int?,

    val connectionType: String?,
    val isRoaming: Boolean?,
    val throughputBytesPerSecond: Long?,
    val rttMs: Long?,

    val foregroundAppPackage: String?,
    val appSessionType: AppSessionType,

    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,

    val deviceModel: String,
    val androidVersion: Int,
    val appVersion: String,
    val telemetryCapabilityTier: TelemetryCapabilityTier,
)
