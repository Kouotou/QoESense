package com.example.qoesense.service

import com.example.qoesense.data.model.AppSessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingSchedulerTest {

    @Test
    fun `standard interval at rest`() {
        assertEquals(SamplingScheduler.STANDARD_INTERVAL_MS, SamplingScheduler.intervalFor(AppSessionType.NONE))
        assertEquals(SamplingScheduler.STANDARD_INTERVAL_MS, SamplingScheduler.intervalFor(AppSessionType.SOCIAL_MEDIA))
    }

    @Test
    fun `elevated interval only during video conferencing`() {
        assertEquals(
            SamplingScheduler.ELEVATED_INTERVAL_MS,
            SamplingScheduler.intervalFor(AppSessionType.VIDEO_CONFERENCING),
        )
    }

    @Test
    fun `elevated interval is denser than standard`() {
        assertTrue(SamplingScheduler.ELEVATED_INTERVAL_MS < SamplingScheduler.STANDARD_INTERVAL_MS)
    }

    @Test
    fun `agreed intervals - 60s at rest, 15s during a video call`() {
        assertEquals(60_000L, SamplingScheduler.STANDARD_INTERVAL_MS)
        assertEquals(15_000L, SamplingScheduler.ELEVATED_INTERVAL_MS)
    }

    @Test
    fun `location wait fits inside the elevated interval so dense sampling is reachable`() {
        assertTrue(SamplingScheduler.ELEVATED_LOCATION_TIMEOUT_MS < SamplingScheduler.ELEVATED_INTERVAL_MS)
        assertEquals(
            SamplingScheduler.ELEVATED_LOCATION_TIMEOUT_MS,
            SamplingScheduler.locationTimeoutFor(AppSessionType.VIDEO_CONFERENCING),
        )
        assertEquals(
            SamplingScheduler.STANDARD_LOCATION_TIMEOUT_MS,
            SamplingScheduler.locationTimeoutFor(AppSessionType.NONE),
        )
    }

    @Test
    fun `fixed-rate - capture time is subtracted from the wait`() {
        // Tick started at t=1000, capture took 9s, 15s interval -> wait the remaining 6s.
        assertEquals(6_000L, SamplingScheduler.delayUntilNextTick(1_000L, 10_000L, 15_000L))
    }

    @Test
    fun `fixed-rate - overrun starts the next tick immediately, never negative`() {
        assertEquals(0L, SamplingScheduler.delayUntilNextTick(0L, 20_000L, 15_000L))
    }

    @Test
    fun `fixed-rate - a clock oddity never waits longer than one interval`() {
        assertEquals(15_000L, SamplingScheduler.delayUntilNextTick(10_000L, 5_000L, 15_000L))
    }

    @Test
    fun `RTT at rest about every 5 minutes, every tick during a call`() {
        val ticksWithRtt = (1..10).count { SamplingScheduler.shouldMeasureRtt(AppSessionType.NONE, it) }
        assertEquals(2, ticksWithRtt) // 10 ticks x 60s = 10 min -> 2 probes
        assertTrue((1..10).all { SamplingScheduler.shouldMeasureRtt(AppSessionType.VIDEO_CONFERENCING, it) })
    }

    @Test
    fun `RTT only forced every tick during video conferencing`() {
        assertTrue(SamplingScheduler.shouldAlwaysMeasureRtt(AppSessionType.VIDEO_CONFERENCING))
        assertFalse(SamplingScheduler.shouldAlwaysMeasureRtt(AppSessionType.SOCIAL_MEDIA))
        assertFalse(SamplingScheduler.shouldAlwaysMeasureRtt(AppSessionType.NONE))
    }
}
