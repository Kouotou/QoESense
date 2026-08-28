package com.example.qoesense.telemetry

import kotlin.math.sqrt

/**
 * Watches a rolling window of recent RSRP readings for a reason to prompt a MOS survey right
 * away instead of waiting for the periodic timer: a sharp drop, a sharp rise, or the signal
 * having sat unusually flat for longer than normal. Pure/stateless - the caller
 * (TelemetryForegroundService) owns the actual rolling window and calls [detect] each tick.
 * How often a detected reason actually results in a survey being shown is separately
 * rate-limited by SurveyTriggerPolicy, so this doesn't need its own re-arming logic: a
 * true anomaly naturally keeps registering until the rolling baseline catches up to the new
 * level, and the policy layer is what prevents that from spamming the participant.
 */
object TelemetryAnomalyDetector {

    enum class Reason { SIGNAL_DROP, SIGNAL_SPIKE, PROLONGED_STABILITY }

    /** Most-recent readings treated as "now". */
    const val RECENT_WINDOW_SIZE = 3

    /** Readings immediately before the recent window, treated as the baseline to compare against. */
    const val BASELINE_WINDOW_SIZE = 10

    /** A swing of at least this many dB between recent and baseline average RSRP counts as "drastic". */
    const val DRASTIC_CHANGE_DB = 10

    /** Minimum readings needed before "stable for a while" is even considered - avoids flagging a short lull. */
    const val STABILITY_WINDOW_SIZE = 15

    /** At or below this standard deviation (dB) over the stability window, the signal counts as "flat". */
    const val STABILITY_STD_DEV_DB = 2.0

    fun detect(recentRsrpReadings: List<Int>): Reason? =
        detectDropOrSpike(recentRsrpReadings) ?: detectProlongedStability(recentRsrpReadings)

    private fun detectDropOrSpike(readings: List<Int>): Reason? {
        if (readings.size < RECENT_WINDOW_SIZE + BASELINE_WINDOW_SIZE) return null
        val recent = readings.takeLast(RECENT_WINDOW_SIZE)
        val baseline = readings.takeLast(RECENT_WINDOW_SIZE + BASELINE_WINDOW_SIZE).take(BASELINE_WINDOW_SIZE)
        val delta = recent.average() - baseline.average()
        return when {
            delta <= -DRASTIC_CHANGE_DB -> Reason.SIGNAL_DROP
            delta >= DRASTIC_CHANGE_DB -> Reason.SIGNAL_SPIKE
            else -> null
        }
    }

    private fun detectProlongedStability(readings: List<Int>): Reason? {
        if (readings.size < STABILITY_WINDOW_SIZE) return null
        val window = readings.takeLast(STABILITY_WINDOW_SIZE)
        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
        return if (sqrt(variance) <= STABILITY_STD_DEV_DB) Reason.PROLONGED_STABILITY else null
    }
}
