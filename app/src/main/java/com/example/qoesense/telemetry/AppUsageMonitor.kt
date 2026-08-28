package com.example.qoesense.telemetry

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.MonitoredApps
import com.example.qoesense.util.PermissionUtils

/**
 * Polls the foreground app (intended to be called every ~5-10s, not continuously) via
 * UsageStatsManager and classifies it for QoE labelling / elevated-sampling purposes.
 *
 * WhatsApp calls can't be distinguished from WhatsApp chat by package name alone (both are
 * `com.whatsapp`). As a heuristic, we treat WhatsApp as VIDEO_CONFERENCING when the device's
 * audio mode indicates an active call (MODE_IN_COMMUNICATION) - this can't distinguish a
 * voice call from a video call either, since Android doesn't expose that distinction without
 * reading the app's own call state, which requires no public API. Document this as a known
 * limitation rather than trying to infer further.
 */
class AppUsageMonitor(private val context: Context) {

    data class ForegroundSession(
        val packageName: String?,
        val sessionType: AppSessionType,
    )

    fun currentForegroundSession(): ForegroundSession {
        val packageName = queryForegroundPackage()
            ?: return ForegroundSession(packageName = null, sessionType = AppSessionType.NONE)

        val sessionType = when {
            packageName in MonitoredApps.VIDEO_CONFERENCING_PACKAGES -> AppSessionType.VIDEO_CONFERENCING
            packageName == MonitoredApps.WHATSAPP_PACKAGE && isAudioCallActive() -> AppSessionType.VIDEO_CONFERENCING
            packageName in MonitoredApps.SOCIAL_MEDIA_PACKAGES -> AppSessionType.SOCIAL_MEDIA
            else -> AppSessionType.NONE
        }
        return ForegroundSession(packageName, sessionType)
    }

    private fun queryForegroundPackage(): String? {
        if (!PermissionUtils.hasUsageAccess(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - QUERY_WINDOW_MS

        val events = usageStatsManager.queryEvents(begin, end)
        var lastForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND is deprecated in favor of ACTIVITY_RESUMED (same value),
            // which only exists from API 29 - kept for minSdk 26 compatibility.
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage
    }

    private fun isAudioCallActive(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private companion object {
        const val QUERY_WINDOW_MS = 10_000L
    }
}
