package com.example.qoesense.service.survey

import java.time.Instant
import java.time.ZoneId

/**
 * Pure decision logic for when a MOS prompt is allowed to fire, for any trigger type. All
 * trigger types share the same lastPromptMs/daily-cap state, so periodic, post-session, and
 * anomaly-driven prompts can't stack and annoy participants - whichever fires first resets
 * the shared cooldown for all of them.
 */
object SurveyTriggerPolicy {
    const val PERIODIC_INTERVAL_MS = 2 * 60 * 60 * 1000L
    const val POST_SESSION_COOLDOWN_MS = 15 * 60 * 1000L
    const val ANOMALY_COOLDOWN_MS = 10 * 60 * 1000L
    const val MAX_PROMPTS_PER_DAY = 6

    fun shouldTriggerPeriodic(
        nowMs: Long,
        lastPromptMs: Long?,
        promptsShownToday: Int,
        promptsDayEpoch: Int,
        currentDayEpoch: Int,
    ): Boolean {
        if (lastPromptMs == null) return false
        if (isOverDailyCap(promptsShownToday, promptsDayEpoch, currentDayEpoch)) return false
        return nowMs - lastPromptMs >= PERIODIC_INTERVAL_MS
    }

    /**
     * Fires right after a social-media/video-conferencing session ends, so long as it's been
     * at least [POST_SESSION_COOLDOWN_MS] since any prompt - much more responsive than the
     * periodic interval, since a session boundary is a specifically meaningful moment to ask
     * about, but still rate-limited so back-to-back short sessions don't spam the participant.
     */
    fun shouldTriggerPostSession(
        nowMs: Long,
        lastPromptMs: Long?,
        promptsShownToday: Int,
        promptsDayEpoch: Int,
        currentDayEpoch: Int,
    ): Boolean {
        if (lastPromptMs == null) return false
        if (isOverDailyCap(promptsShownToday, promptsDayEpoch, currentDayEpoch)) return false
        return nowMs - lastPromptMs >= POST_SESSION_COOLDOWN_MS
    }

    /**
     * Fires when TelemetryAnomalyDetector flags a sharp signal change or unusual prolonged
     * stability - the shortest cooldown of the three, since this is the trigger meant to
     * catch a QoE-relevant moment as it happens rather than waiting for the participant to
     * open the app, minimizing the gap between the network event and the rating.
     */
    fun shouldTriggerAnomaly(
        nowMs: Long,
        lastPromptMs: Long?,
        promptsShownToday: Int,
        promptsDayEpoch: Int,
        currentDayEpoch: Int,
    ): Boolean {
        if (lastPromptMs == null) return false
        if (isOverDailyCap(promptsShownToday, promptsDayEpoch, currentDayEpoch)) return false
        return nowMs - lastPromptMs >= ANOMALY_COOLDOWN_MS
    }

    /**
     * The participant's *local* calendar day as an epoch-day number, used for the daily
     * prompt cap - so the cap resets at the participant's midnight, not at 00:00 UTC (which
     * is 01:00 in Cameroon, for example, and would be hours off elsewhere). Same epoch-day
     * number space as the previous UTC-based value, so stored counters stay comparable.
     */
    fun localDayEpoch(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay().toInt()

    private fun isOverDailyCap(promptsShownToday: Int, promptsDayEpoch: Int, currentDayEpoch: Int): Boolean {
        val effectivePromptsToday = if (promptsDayEpoch == currentDayEpoch) promptsShownToday else 0
        return effectivePromptsToday >= MAX_PROMPTS_PER_DAY
    }
}
