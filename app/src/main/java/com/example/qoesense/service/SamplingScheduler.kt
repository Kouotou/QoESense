package com.example.qoesense.service

import com.example.qoesense.data.model.AppSessionType

/**
 * Adaptive sampling rate: standard interval at rest, elevated (denser) sampling for the
 * duration of a detected video-conferencing session, dropping straight back to standard the
 * next tick after the session ends - a step change, not a gradual ramp, matching "temporarily
 * increase ... then drop back" rather than a decay curve.
 *
 * This only changes how densely *this device's own* telemetry is captured - it never touches
 * actual network traffic (no shaping, no prioritization). Purely observational.
 */
object SamplingScheduler {
    const val STANDARD_INTERVAL_MS = 20_000L
    const val ELEVATED_INTERVAL_MS = 5_000L

    fun intervalFor(sessionType: AppSessionType): Long =
        if (sessionType == AppSessionType.VIDEO_CONFERENCING) ELEVATED_INTERVAL_MS else STANDARD_INTERVAL_MS

    /** "Denser" telemetry also means RTT gets measured every tick during a call, not just every Nth sample. */
    fun shouldAlwaysMeasureRtt(sessionType: AppSessionType): Boolean =
        sessionType == AppSessionType.VIDEO_CONFERENCING
}
