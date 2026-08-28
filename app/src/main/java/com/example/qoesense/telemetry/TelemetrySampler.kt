package com.example.qoesense.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.qoesense.data.model.TelemetrySample

/**
 * Folds every telemetry source's reading into one [TelemetrySample]. Shared by the manual
 * "Run telemetry test" button (StatusViewModel) and the periodic foreground-service sampling
 * loop, so the two capture paths can never drift apart.
 */
class TelemetrySampler(context: Context) {

    private val appContext = context.applicationContext
    private val cellularSource = CellularTelemetrySource(appContext)
    private val appUsageMonitor = AppUsageMonitor(appContext)
    private val locationSampler = LocationSampler(appContext)
    private val throughputSampler = ThroughputSampler()
    private val latencySampler = LatencySampler()
    private val connectivitySampler = ConnectivitySampler(appContext)

    fun start() = cellularSource.start()
    fun stop() = cellularSource.stop()

    /**
     * [measureRtt] defaults to true for on-demand captures (manual test button). The periodic
     * sampling loop passes false most of the time - RTT probing hits the network on every call
     * and shouldn't run on every 15-30s tick, per the "not on every sample" constraint.
     */
    suspend fun captureSample(measureRtt: Boolean = true): TelemetrySample {
        val cellularReading = cellularSource.readCurrent()
        val connectivityReading = connectivitySampler.readCurrent()
        val foregroundSession = appUsageMonitor.currentForegroundSession()
        val locationReading = locationSampler.sampleOnce()
        val throughputReading = throughputSampler.sample()
        val rttMs = if (measureRtt) latencySampler.measureRttMs() else null

        return TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            rsrp = cellularReading?.rsrp,
            rsrq = cellularReading?.rsrq,
            rssnr = cellularReading?.rssnr,
            timingAdvance = cellularReading?.timingAdvance,
            networkType = cellularReading?.networkType,
            cellIdentityHash = cellularReading?.cellIdentityHash,
            trackingAreaCode = cellularReading?.trackingAreaCode,
            signalStrengthLevel = cellularReading?.signalStrengthLevel,
            cqi = cellularReading?.cqi,
            cqiSupported = cellularReading?.cqiSupported ?: false,
            cellBandwidthKhz = cellularReading?.cellBandwidthKhz,
            downlinkChannelNumber = cellularReading?.downlinkChannelNumber,
            uplinkChannelNumber = cellularReading?.uplinkChannelNumber,
            connectionType = connectivityReading?.connectionType,
            isRoaming = connectivityReading?.isRoaming,
            throughputBytesPerSecond = throughputReading?.bytesPerSecond,
            rttMs = rttMs,
            foregroundAppPackage = foregroundSession.packageName,
            appSessionType = foregroundSession.sessionType,
            latitude = locationReading?.latitude,
            longitude = locationReading?.longitude,
            locationAccuracyMeters = locationReading?.accuracyMeters,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.SDK_INT,
            appVersion = appVersionName(appContext),
            telemetryCapabilityTier = TelemetryCapabilityResolver.resolve(cellularReading),
        )
    }

    private fun appVersionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
}
