package com.example.qoesense.ui.survey

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.qoesense.data.MosSurveyRepository
import com.example.qoesense.data.TelemetryRepository
import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.MosResponse
import com.example.qoesense.data.model.MosTriggerType
import kotlinx.coroutines.launch

class MosSurveyViewModel(application: Application) : AndroidViewModel(application) {

    private val telemetryRepository = TelemetryRepository(application)
    private val surveyRepository = MosSurveyRepository()

    /** Fire-and-forget: the activity finishes immediately regardless of submission outcome. */
    fun submit(score: Int, triggerType: MosTriggerType) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowStart = now - CONTEXT_WINDOW_MS
            val recentSamples = telemetryRepository.samplesSince(windowStart)
            val latest = recentSamples.lastOrNull()

            surveyRepository.submit(
                MosResponse(
                    score = score,
                    submittedAtMs = now,
                    triggerType = triggerType,
                    contextAppPackage = latest?.foregroundAppPackage,
                    contextSessionType = latest?.appSessionType ?: AppSessionType.NONE,
                    windowStartMs = windowStart,
                    windowEndMs = now,
                )
            )
        }
    }

    private companion object {
        const val CONTEXT_WINDOW_MS = 10 * 60 * 1000L
    }
}
