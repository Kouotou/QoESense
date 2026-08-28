package com.example.qoesense.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mosSurveyDataStore by preferencesDataStore(name = "mos_survey_prefs")

class MosSurveyPreferences(private val context: Context) {

    data class State(val lastPromptMs: Long?, val promptsShownToday: Int, val promptsDayEpoch: Int)

    private object Keys {
        val LAST_PROMPT_MS = longPreferencesKey("last_prompt_ms")
        val PROMPTS_SHOWN_TODAY = intPreferencesKey("prompts_shown_today")
        val PROMPTS_DAY_EPOCH = intPreferencesKey("prompts_day_epoch")
    }

    val state: Flow<State> = context.mosSurveyDataStore.data.map { prefs ->
        State(
            lastPromptMs = prefs[Keys.LAST_PROMPT_MS],
            promptsShownToday = prefs[Keys.PROMPTS_SHOWN_TODAY] ?: 0,
            promptsDayEpoch = prefs[Keys.PROMPTS_DAY_EPOCH] ?: 0,
        )
    }

    /** Seeds lastPromptMs to "now" the first time collection ever starts, without counting as a shown prompt - otherwise a null baseline would look "infinitely overdue" and fire immediately. */
    suspend fun ensureBaseline() {
        context.mosSurveyDataStore.edit { prefs ->
            if (prefs[Keys.LAST_PROMPT_MS] == null) {
                prefs[Keys.LAST_PROMPT_MS] = System.currentTimeMillis()
            }
        }
    }

    suspend fun recordShown(nowMs: Long, currentDayEpoch: Int) {
        context.mosSurveyDataStore.edit { prefs ->
            val storedDayEpoch = prefs[Keys.PROMPTS_DAY_EPOCH]
            val previousCount = if (storedDayEpoch == currentDayEpoch) prefs[Keys.PROMPTS_SHOWN_TODAY] ?: 0 else 0
            prefs[Keys.LAST_PROMPT_MS] = nowMs
            prefs[Keys.PROMPTS_SHOWN_TODAY] = previousCount + 1
            prefs[Keys.PROMPTS_DAY_EPOCH] = currentDayEpoch
        }
    }
}
