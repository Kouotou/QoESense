package com.example.qoesense.telemetry

/**
 * Extension point for a future rooted-device / Qualcomm-DIAG-based telemetry subset (true MCS
 * index, HARQ retransmission counts, PRB allocation, BLER), per the research proposal's
 * two-tier collection strategy. None of these are available without root or OEM diagnostic
 * mode, and this app must never fake or estimate them - they stay null until an implementation
 * exists. Not implemented; disabled by default. A future implementation plugs in here without
 * touching the rest of the telemetry pipeline.
 */
interface RootedTelemetrySource {
    val isAvailable: Boolean
    fun readCurrent(): Map<String, Any?>?
}

class DisabledRootedTelemetrySource : RootedTelemetrySource {
    override val isAvailable: Boolean = false
    override fun readCurrent(): Map<String, Any?>? = null
}
