package com.example.qoesense.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.qoesense.MainActivity
import com.example.qoesense.R
import com.example.qoesense.data.TelemetryRepository
import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.service.survey.MosSurveyScheduler
import com.example.qoesense.telemetry.TelemetryAnomalyDetector
import com.example.qoesense.telemetry.TelemetrySampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuously samples telemetry, buffering each sample in Room. The interval adapts via
 * [SamplingScheduler]: standard rate at rest, denser sampling for the duration of a detected
 * video-conferencing session, dropping straight back to standard the tick after it ends. This
 * only affects how densely this device's own telemetry is captured - never actual network
 * traffic (no shaping/prioritization of anything).
 *
 * Also feeds a rolling window of RSRP readings to [TelemetryAnomalyDetector] every tick, and
 * asks [MosSurveyScheduler] to prompt for a rating right away when it flags a sharp signal
 * change or unusually prolonged stability - the goal being to catch a participant's rating as
 * close as possible to the network event it's about, rather than relying on them to open the
 * app on their own.
 */
class TelemetryForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sampler: TelemetrySampler
    private lateinit var repository: TelemetryRepository
    private lateinit var surveyScheduler: MosSurveyScheduler
    private var sampleCount = 0
    private var samplingJob: Job? = null
    private var lastSessionType: AppSessionType = AppSessionType.NONE
    private val recentRsrpReadings = ArrayDeque<Int>()

    override fun onCreate() {
        super.onCreate()
        sampler = TelemetrySampler(this)
        repository = TelemetryRepository(this)
        surveyScheduler = MosSurveyScheduler(this)
        sampler.start()
        createNotificationChannel()
        serviceScope.launch { surveyScheduler.ensureBaseline() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startSamplingLoop()
        return START_STICKY
    }

    /** Guarded against relaunching a second concurrent loop if a duplicate start intent arrives. */
    private fun startSamplingLoop() {
        if (samplingJob?.isActive == true) return
        samplingJob = serviceScope.launch {
            while (isActive) {
                val tickStartMs = SystemClock.elapsedRealtime()
                sampleCount++
                try {
                    val sample = sampler.captureSample(
                        measureRtt = SamplingScheduler.shouldMeasureRtt(lastSessionType, sampleCount),
                        locationTimeoutMs = SamplingScheduler.locationTimeoutFor(lastSessionType),
                    )
                    repository.record(sample)
                    surveyScheduler.maybeTriggerPeriodic()

                    val previousSessionType = lastSessionType
                    lastSessionType = sample.appSessionType
                    val sessionJustEnded = previousSessionType != AppSessionType.NONE &&
                        previousSessionType != lastSessionType
                    if (sessionJustEnded) {
                        surveyScheduler.maybeTriggerPostSession()
                    }

                    sample.rsrp?.let { rsrp ->
                        recentRsrpReadings.addLast(rsrp)
                        while (recentRsrpReadings.size > TelemetryAnomalyDetector.STABILITY_WINDOW_SIZE) {
                            recentRsrpReadings.removeFirst()
                        }
                    }
                    TelemetryAnomalyDetector.detect(recentRsrpReadings.toList())?.let { reason ->
                        surveyScheduler.maybeTriggerAnomaly(reason)
                    }
                } catch (e: Throwable) {
                    // One bad sample must not take down weeks of collection.
                    Log.e(TAG, "Sampling iteration $sampleCount failed, will retry next interval", e)
                }
                delay(
                    SamplingScheduler.delayUntilNextTick(
                        tickStartMs = tickStartMs,
                        nowMs = SystemClock.elapsedRealtime(),
                        intervalMs = SamplingScheduler.intervalFor(lastSessionType),
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        sampler.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_collection_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_collection_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_collection_title))
            .setContentText(getString(R.string.notif_collection_text))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    companion object {
        private const val TAG = "QoESenseService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "telemetry_collection"

        fun start(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TelemetryForegroundService::class.java))
        }
    }
}
