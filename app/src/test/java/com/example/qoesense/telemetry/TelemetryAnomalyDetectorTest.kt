package com.example.qoesense.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryAnomalyDetectorTest {

    @Test
    fun `not enough readings yet returns null`() {
        val result = TelemetryAnomalyDetector.detect(listOf(-90, -91, -89))
        assertNull(result)
    }

    @Test
    fun `steady signal short of the stability window returns null`() {
        // Enough history for the drop-or-spike check (no delta to find) but one short of
        // STABILITY_WINDOW_SIZE, so prolonged-stability shouldn't fire yet either.
        val readings = List(TelemetryAnomalyDetector.STABILITY_WINDOW_SIZE - 1) { -90 }
        assertNull(TelemetryAnomalyDetector.detect(readings))
    }

    @Test
    fun `sharp drop from baseline to recent is flagged as SIGNAL_DROP`() {
        // 10 baseline readings around -80, then 3 recent readings around -95 (15 dB worse)
        val baseline = List(10) { -80 }
        val recent = List(3) { -95 }
        val readings = baseline + recent
        assertEquals(TelemetryAnomalyDetector.Reason.SIGNAL_DROP, TelemetryAnomalyDetector.detect(readings))
    }

    @Test
    fun `sharp rise from baseline to recent is flagged as SIGNAL_SPIKE`() {
        val baseline = List(10) { -100 }
        val recent = List(3) { -85 }
        val readings = baseline + recent
        assertEquals(TelemetryAnomalyDetector.Reason.SIGNAL_SPIKE, TelemetryAnomalyDetector.detect(readings))
    }

    @Test
    fun `a small change within threshold is not flagged`() {
        val baseline = List(10) { -90 }
        val recent = List(3) { -93 } // only 3 dB - below the 10 dB threshold
        val readings = baseline + recent
        assertNull(TelemetryAnomalyDetector.detect(readings))
    }

    @Test
    fun `long flat run is flagged as PROLONGED_STABILITY once enough history exists`() {
        val readings = List(TelemetryAnomalyDetector.STABILITY_WINDOW_SIZE) { -90 }
        assertEquals(TelemetryAnomalyDetector.Reason.PROLONGED_STABILITY, TelemetryAnomalyDetector.detect(readings))
    }

    @Test
    fun `noisy signal is not flagged as stable`() {
        val readings = (1..TelemetryAnomalyDetector.STABILITY_WINDOW_SIZE).map { i ->
            if (i % 2 == 0) -80 else -100
        }
        assertNull(TelemetryAnomalyDetector.detect(readings))
    }
}
