package com.example.qoesense.service.survey

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.qoesense.data.MosSurveyRepository
import com.example.qoesense.data.TelemetryRepository
import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.MosResponse
import com.example.qoesense.data.model.MosTriggerType
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Submits one MOS rating to Firestore outside the survey Activity's lifecycle.
 *
 * Previously the rating was written from a ViewModel coroutine that the Activity cancelled
 * by calling finish() right after the tap, so the write raced the Activity's destruction.
 * Enqueuing a WorkManager job is synchronous from the caller's point of view and survives
 * the Activity (and process death), and it also gives MOS responses the retry behavior
 * telemetry samples already had instead of being fire-and-forget.
 *
 * The score, trigger, submission time and window bounds are fixed at tap time and passed in;
 * only the foreground-app context is looked up here, from the samples buffered in that window.
 */
class MosSubmitWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val responseId = inputData.getString(KEY_RESPONSE_ID) ?: return Result.failure()
        val score = inputData.getInt(KEY_SCORE, -1)
        if (score !in 1..5) return Result.failure()
        val triggerType = inputData.getString(KEY_TRIGGER_TYPE)
            ?.let { runCatching { MosTriggerType.valueOf(it) }.getOrNull() }
            ?: MosTriggerType.PERIODIC
        val submittedAtMs = inputData.getLong(KEY_SUBMITTED_AT_MS, 0L)
        val windowStartMs = inputData.getLong(KEY_WINDOW_START_MS, 0L)
        val windowEndMs = inputData.getLong(KEY_WINDOW_END_MS, 0L)

        val latest = TelemetryRepository(applicationContext)
            .samplesSince(windowStartMs)
            .lastOrNull { it.timestampMs <= windowEndMs }

        val response = MosResponse(
            score = score,
            submittedAtMs = submittedAtMs,
            triggerType = triggerType,
            contextAppPackage = latest?.foregroundAppPackage,
            contextSessionType = latest?.appSessionType ?: AppSessionType.NONE,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
        )

        val submitted = withTimeoutOrNull(SUBMIT_TIMEOUT_MS) {
            MosSurveyRepository().submit(responseId, response)
        } ?: false
        if (submitted) return Result.success()

        return if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            Log.e(TAG, "MOS response $responseId gave up after $MAX_ATTEMPTS attempts")
            Result.failure()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "QoESenseMosSubmit"
        private const val KEY_RESPONSE_ID = "response_id"
        private const val KEY_SCORE = "score"
        private const val KEY_TRIGGER_TYPE = "trigger_type"
        private const val KEY_SUBMITTED_AT_MS = "submitted_at_ms"
        private const val KEY_WINDOW_START_MS = "window_start_ms"
        private const val KEY_WINDOW_END_MS = "window_end_ms"
        private const val SUBMIT_TIMEOUT_MS = 60_000L
        private const val MAX_ATTEMPTS = 10

        /** The telemetry window a rating applies to: the 10 minutes before the tap. */
        const val CONTEXT_WINDOW_MS = 10 * 60 * 1000L

        /** Call from the tap handler; returns immediately, safe to finish() right after. */
        fun enqueue(context: Context, score: Int, triggerType: MosTriggerType) {
            val now = System.currentTimeMillis()
            val responseId = UUID.randomUUID().toString()
            val request = OneTimeWorkRequestBuilder<MosSubmitWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RESPONSE_ID to responseId,
                        KEY_SCORE to score,
                        KEY_TRIGGER_TYPE to triggerType.name,
                        KEY_SUBMITTED_AT_MS to now,
                        KEY_WINDOW_START_MS to now - CONTEXT_WINDOW_MS,
                        KEY_WINDOW_END_MS to now,
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("mos_submit_$responseId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
