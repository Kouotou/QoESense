package com.example.qoesense.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "onboarding_prefs")

/** Tracks whether the first-run onboarding wizard has been completed, and when consent was given. */
class OnboardingPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val CONSENT_TIMESTAMP_MS = longPreferencesKey("consent_timestamp_ms")
    }

    val isOnboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
    }

    val consentTimestampMs: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[Keys.CONSENT_TIMESTAMP_MS] }

    suspend fun setConsentTimestamp(timestampMs: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.CONSENT_TIMESTAMP_MS] = timestampMs }
    }
}
