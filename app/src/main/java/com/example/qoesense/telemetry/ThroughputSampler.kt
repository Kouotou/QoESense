package com.example.qoesense.telemetry

import android.net.TrafficStats

/**
 * Device-wide throughput estimate from cumulative TrafficStats counters. Per-app throughput
 * isn't needed here since the study cares about link-level QoE, not attribution to a specific
 * app. The first call after process start always returns null - there's no prior baseline yet
 * to compute a delta from.
 */
class ThroughputSampler {

    private var lastTotalBytes: Long? = null
    private var lastTimestampMs: Long? = null

    data class ThroughputReading(val bytesPerSecond: Long)

    fun sample(): ThroughputReading? {
        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return null
        }
        val totalBytes = rx + tx

        val reading = computeReading(totalBytes, now)
        lastTotalBytes = totalBytes
        lastTimestampMs = now
        return reading
    }

    private fun computeReading(totalBytes: Long, now: Long): ThroughputReading? {
        val prevBytes = lastTotalBytes ?: return null
        val prevTime = lastTimestampMs ?: return null
        val deltaSeconds = (now - prevTime) / 1000.0
        if (deltaSeconds <= 0) return null
        val deltaBytes = (totalBytes - prevBytes).coerceAtLeast(0)
        return ThroughputReading((deltaBytes / deltaSeconds).toLong())
    }
}
