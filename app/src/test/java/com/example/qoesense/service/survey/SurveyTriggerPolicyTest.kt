package com.example.qoesense.service.survey

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyTriggerPolicyTest {

    private val dayEpoch = 20_000
    private val hourMs = 60 * 60 * 1000L

    @Test
    fun `never shown before does not trigger - baseline must be seeded first`() {
        val result = SurveyTriggerPolicy.shouldTriggerPeriodic(
            nowMs = 1_000_000L,
            lastPromptMs = null,
            promptsShownToday = 0,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `does not trigger before the periodic interval has elapsed`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPeriodic(
            nowMs = now,
            lastPromptMs = now - hourMs, // only 1h ago, interval is 2h
            promptsShownToday = 1,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `triggers once the periodic interval has fully elapsed`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPeriodic(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.PERIODIC_INTERVAL_MS,
            promptsShownToday = 1,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertTrue(result)
    }

    @Test
    fun `does not trigger once the daily cap is reached`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPeriodic(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.PERIODIC_INTERVAL_MS,
            promptsShownToday = SurveyTriggerPolicy.MAX_PROMPTS_PER_DAY,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `daily cap resets on a new day even if promptsShownToday is stale`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPeriodic(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.PERIODIC_INTERVAL_MS,
            promptsShownToday = SurveyTriggerPolicy.MAX_PROMPTS_PER_DAY, // stale count from yesterday
            promptsDayEpoch = dayEpoch - 1,
            currentDayEpoch = dayEpoch,
        )
        assertTrue(result)
    }

    @Test
    fun `post-session does not trigger before baseline is seeded`() {
        val result = SurveyTriggerPolicy.shouldTriggerPostSession(
            nowMs = 1_000_000L,
            lastPromptMs = null,
            promptsShownToday = 0,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `post-session triggers much sooner than the periodic interval`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPostSession(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.POST_SESSION_COOLDOWN_MS,
            promptsShownToday = 1,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertTrue(result)
    }

    @Test
    fun `post-session respects its own cooldown even if a prompt was shown recently`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPostSession(
            nowMs = now,
            lastPromptMs = now - 60_000L, // 1 min ago, cooldown is 15 min
            promptsShownToday = 1,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `post-session respects the shared daily cap`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerPostSession(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.POST_SESSION_COOLDOWN_MS,
            promptsShownToday = SurveyTriggerPolicy.MAX_PROMPTS_PER_DAY,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `anomaly does not trigger before baseline is seeded`() {
        val result = SurveyTriggerPolicy.shouldTriggerAnomaly(
            nowMs = 1_000_000L,
            lastPromptMs = null,
            promptsShownToday = 0,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `anomaly triggers once its shorter cooldown has elapsed`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerAnomaly(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.ANOMALY_COOLDOWN_MS,
            promptsShownToday = 1,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertTrue(result)
    }

    @Test
    fun `anomaly respects its own cooldown even if a prompt was shown recently`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerAnomaly(
            nowMs = now,
            lastPromptMs = now - 60_000L, // 1 min ago, cooldown is 10 min
            promptsShownToday = 1,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `anomaly respects the shared daily cap`() {
        val now = 10_000_000L
        val result = SurveyTriggerPolicy.shouldTriggerAnomaly(
            nowMs = now,
            lastPromptMs = now - SurveyTriggerPolicy.ANOMALY_COOLDOWN_MS,
            promptsShownToday = SurveyTriggerPolicy.MAX_PROMPTS_PER_DAY,
            promptsDayEpoch = dayEpoch,
            currentDayEpoch = dayEpoch,
        )
        assertFalse(result)
    }

    @Test
    fun `local day - just before local midnight east of UTC is already the next day`() {
        val douala = ZoneId.of("Africa/Douala") // UTC+1
        // 23:30 UTC on 2026-11-07 is 00:30 on 2026-11-08 in Douala.
        val nowMs = ZonedDateTime.of(2026, 11, 7, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val utcDay = SurveyTriggerPolicy.localDayEpoch(nowMs, ZoneId.of("UTC"))
        assertEquals(utcDay + 1, SurveyTriggerPolicy.localDayEpoch(nowMs, douala))
    }

    @Test
    fun `local day - west of UTC the day changes later than UTC`() {
        val newYork = ZoneId.of("America/New_York") // UTC-5 in November
        // 02:00 UTC on 2026-11-08 is still 21:00 on 2026-11-07 in New York.
        val nowMs = ZonedDateTime.of(2026, 11, 8, 2, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val utcDay = SurveyTriggerPolicy.localDayEpoch(nowMs, ZoneId.of("UTC"))
        assertEquals(utcDay - 1, SurveyTriggerPolicy.localDayEpoch(nowMs, newYork))
    }

    @Test
    fun `local day - matches java LocalDate epoch day`() {
        val zone = ZoneId.of("Africa/Douala")
        val local = ZonedDateTime.of(2026, 11, 7, 12, 0, 0, 0, zone)
        assertEquals(
            local.toLocalDate().toEpochDay().toInt(),
            SurveyTriggerPolicy.localDayEpoch(local.toInstant().toEpochMilli(), zone),
        )
    }
}
