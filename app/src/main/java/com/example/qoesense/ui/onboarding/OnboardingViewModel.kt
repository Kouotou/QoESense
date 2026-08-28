package com.example.qoesense.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.qoesense.data.CollectionStatePreferences
import com.example.qoesense.data.ConsentRepository
import com.example.qoesense.data.OnboardingPreferences
import com.example.qoesense.service.TelemetryForegroundService
import com.example.qoesense.service.upload.UploadScheduler
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val onboardingPreferences = OnboardingPreferences(application)
    private val collectionStatePreferences = CollectionStatePreferences(application)
    private val consentRepository = ConsentRepository()

    /** Records consent locally (authoritative - gates nothing else until this is set) and
     * best-effort to Firestore as a durable research-integrity record. */
    fun recordConsent(onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            onboardingPreferences.setConsentTimestamp(now)
            consentRepository.recordConsentGiven(now)
            onDone()
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            onboardingPreferences.setOnboardingComplete(true)
            collectionStatePreferences.setCollectionEnabled(true)
            TelemetryForegroundService.start(getApplication())
            UploadScheduler.schedule(getApplication())
            onDone()
        }
    }
}
