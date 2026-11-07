package com.example.qoesense.service.survey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.qoesense.R
import com.example.qoesense.data.MosSurveyPreferences
import com.example.qoesense.data.model.MosTriggerType
import com.example.qoesense.telemetry.TelemetryAnomalyDetector
import com.example.qoesense.ui.survey.MosSurveyActivity
import com.example.qoesense.util.PermissionUtils
import kotlinx.coroutines.flow.first

/**
 * Decides when a MOS prompt is allowed to fire (via [SurveyTriggerPolicy]) and presents it.
 * When overlay permission is granted, the survey pops directly over whatever app the
 * participant is using - overlay permission is one of the documented exemptions from
 * Android 10+'s background-activity-start restriction, so this can start [MosSurveyActivity]
 * with no user interaction needed first. Without that permission it falls back to a
 * notification the participant has to tap, same as before. Called from the foreground
 * service's sampling loop, so it piggybacks on a timer that's already running.
 */
class MosSurveyScheduler(private val context: Context) {

    private val preferences = MosSurveyPreferences(context)

    suspend fun ensureBaseline() = preferences.ensureBaseline()

    suspend fun maybeTriggerPeriodic() {
        val state = preferences.state.first()
        val now = System.currentTimeMillis()
        val currentDayEpoch = dayEpoch(now)
        val shouldTrigger = SurveyTriggerPolicy.shouldTriggerPeriodic(
            nowMs = now,
            lastPromptMs = state.lastPromptMs,
            promptsShownToday = state.promptsShownToday,
            promptsDayEpoch = state.promptsDayEpoch,
            currentDayEpoch = currentDayEpoch,
        )
        if (!shouldTrigger) return
        preferences.recordShown(now, currentDayEpoch)
        presentSurvey(MosTriggerType.PERIODIC)
    }

    /** Call when a social-media/video-conferencing session is detected to have just ended. */
    suspend fun maybeTriggerPostSession() {
        val state = preferences.state.first()
        val now = System.currentTimeMillis()
        val currentDayEpoch = dayEpoch(now)
        val shouldTrigger = SurveyTriggerPolicy.shouldTriggerPostSession(
            nowMs = now,
            lastPromptMs = state.lastPromptMs,
            promptsShownToday = state.promptsShownToday,
            promptsDayEpoch = state.promptsDayEpoch,
            currentDayEpoch = currentDayEpoch,
        )
        if (!shouldTrigger) return
        preferences.recordShown(now, currentDayEpoch)
        presentSurvey(MosTriggerType.POST_SESSION)
    }

    /** Call whenever TelemetryAnomalyDetector flags a reason on the latest sampling tick. */
    suspend fun maybeTriggerAnomaly(reason: TelemetryAnomalyDetector.Reason) {
        val state = preferences.state.first()
        val now = System.currentTimeMillis()
        val currentDayEpoch = dayEpoch(now)
        val shouldTrigger = SurveyTriggerPolicy.shouldTriggerAnomaly(
            nowMs = now,
            lastPromptMs = state.lastPromptMs,
            promptsShownToday = state.promptsShownToday,
            promptsDayEpoch = state.promptsDayEpoch,
            currentDayEpoch = currentDayEpoch,
        )
        if (!shouldTrigger) return
        preferences.recordShown(now, currentDayEpoch)
        presentSurvey(MosTriggerType.ANOMALY)
    }

    /** Shows the prompt immediately, bypassing cooldown/cap - for the Status screen's test button only. */
    fun showTestNotification() {
        presentSurvey(MosTriggerType.PERIODIC)
    }

    private fun presentSurvey(triggerType: MosTriggerType) {
        if (PermissionUtils.hasOverlayPermission(context)) {
            context.startActivity(surveyIntent(triggerType))
        } else {
            showNotification(triggerType)
        }
    }

    private fun surveyIntent(triggerType: MosTriggerType): Intent =
        Intent(context, MosSurveyActivity::class.java).apply {
            putExtra(MosSurveyActivity.EXTRA_TRIGGER_TYPE, triggerType.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_HISTORY
        }

    private fun showNotification(triggerType: MosTriggerType) {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            surveyIntent(triggerType),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_survey_title))
            .setContentText(context.getString(R.string.notif_survey_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_survey_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_survey_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun dayEpoch(nowMs: Long): Int = SurveyTriggerPolicy.localDayEpoch(nowMs)

    private companion object {
        const val CHANNEL_ID = "mos_survey"
        const val NOTIFICATION_ID = 2
    }
}
