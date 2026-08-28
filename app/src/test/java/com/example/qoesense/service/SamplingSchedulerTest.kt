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
    fun `RTT only forced every tick during video conferencing`() {
        assertTrue(SamplingScheduler.shouldAlwaysMeasureRtt(AppSessionType.VIDEO_CONFERENCING))
        assertFalse(SamplingScheduler.shouldAlwaysMeasureRtt(AppSessionType.SOCIAL_MEDIA))
        assertFalse(SamplingScheduler.shouldAlwaysMeasureRtt(AppSessionType.NONE))
    }
}
