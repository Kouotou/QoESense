package com.example.qoesense.data.model

/** What prompted a MOS survey to be shown. */
enum class MosTriggerType {
    PERIODIC,
    POST_SESSION,
    /** A sharp signal drop/spike, or unusually prolonged stability - see TelemetryAnomalyDetector. */
    ANOMALY,
}

/**
 * One participant rating, tied to the telemetry window immediately preceding it rather than
 * a single instant - matches the early-fusion labelling approach (rating + preceding window
 * of signal telemetry) rather than trying to label a single sample.
 */
data class MosResponse(
    val score: Int,
    val submittedAtMs: Long,
    val triggerType: MosTriggerType,
    val contextAppPackage: String?,
    val contextSessionType: AppSessionType,
    val windowStartMs: Long,
    val windowEndMs: Long,
)
