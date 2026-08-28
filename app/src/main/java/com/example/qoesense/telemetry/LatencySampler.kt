package com.example.qoesense.telemetry

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures RTT with a lightweight GET against a near-zero-payload connectivity-check endpoint.
 * This is deliberately not run on every sample (that would waste participants' data and
 * battery) - the caller decides how often to invoke it.
 */
class LatencySampler {

    suspend fun measureRttMs(): Long? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val start = SystemClock.elapsedRealtime()
            connection = (URL(PROBE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            connection.responseCode // forces the request to actually execute
            SystemClock.elapsedRealtime() - start
        } catch (e: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
        const val TIMEOUT_MS = 4000
    }
}
