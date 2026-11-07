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
 *
 * Interval choice (Nov 2026, replaces the original 20 s / 5 s): 20 s produced long runs of
 * identical rows - cell-level signal metrics barely move on that timescale for a mostly
 * stationary participant - while costing a location fix every tick. 60 s still gives ~10
 * samples inside each 10-minute MOS window (enough for per-window mean/std/min/max features)
 * and keeps the RSRP anomaly detector's windows on a minutes scale. During a video call,
 * 15 s is dense enough to resolve in-call degradation and is actually achievable: see
 * [locationTimeoutFor] - the old 5 s elevated target was never reachable because each tick
 * could block for up to 15 s waiting on location.
 */
object SamplingScheduler {
    const val STANDARD_INTERVAL_MS = 60_000L
    const val ELEVATED_INTERVAL_MS = 15_000L

    /** Max wait for a fresh location fix at rest (unchanged from the original bound). */
    const val STANDARD_LOCATION_TIMEOUT_MS = 15_000L

    /**
     * During a call the location wait must fit inside the elevated interval, otherwise the
     * tick overruns and the "dense" cadence silently collapses. A missed fix just leaves the
     * location fields null for that sample - location isn't what dense sampling is for.
     */
    const val ELEVATED_LOCATION_TIMEOUT_MS = 8_000L

    /** At rest, RTT is probed every Nth sample - every 5th at 60 s = about every 5 minutes. */
    const val RTT_SAMPLE_EVERY_N = 5

    fun intervalFor(sessionType: AppSessionType): Long =
        if (sessionType == AppSessionType.VIDEO_CONFERENCING) ELEVATED_INTERVAL_MS else STANDARD_INTERVAL_MS

    fun locationTimeoutFor(sessionType: AppSessionType): Long =
        if (sessionType == AppSessionType.VIDEO_CONFERENCING) ELEVATED_LOCATION_TIMEOUT_MS else STANDARD_LOCATION_TIMEOUT_MS

    /** "Denser" telemetry also means RTT gets measured every tick during a call, not just every Nth sample. */
    fun shouldAlwaysMeasureRtt(sessionType: AppSessionType): Boolean =
        sessionType == AppSessionType.VIDEO_CONFERENCING

    fun shouldMeasureRtt(sessionType: AppSessionType, sampleCount: Int): Boolean =
        shouldAlwaysMeasureRtt(sessionType) || sampleCount % RTT_SAMPLE_EVERY_N == 0

    /**
     * Fixed-rate scheduling: ticks start every [intervalMs] measured from the previous tick's
     * *start*, not "capture time + interval" as before (which stretched every period by
     * however long the capture took). If a capture overran the interval, the next tick starts
     * immediately rather than trying to catch up with a burst.
     */
    fun delayUntilNextTick(tickStartMs: Long, nowMs: Long, intervalMs: Long): Long =
        (intervalMs - (nowMs - tickStartMs)).coerceIn(0L, intervalMs)
}
