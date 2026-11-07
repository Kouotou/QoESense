package com.example.qoesense.ui.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.qoesense.data.CollectionStatePreferences
import com.example.qoesense.data.TelemetryRepository
import com.example.qoesense.data.model.TelemetrySample
import com.example.qoesense.data.upload.DeviceIdentityProvider
import com.example.qoesense.data.upload.FirebaseAuthDeviceIdentityProvider
import com.example.qoesense.service.TelemetryForegroundService
import com.example.qoesense.service.survey.MosSurveyScheduler
import com.example.qoesense.service.upload.UploadScheduler
import com.example.qoesense.telemetry.TelemetrySampler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives an on-demand capture for the Status screen's "Run telemetry test" button. */
class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val sampler = TelemetrySampler(application)
    private val repository = TelemetryRepository(application)
    private val collectionStatePreferences = CollectionStatePreferences(application)
    private val surveyScheduler = MosSurveyScheduler(application)
    private val deviceIdentityProvider: DeviceIdentityProvider = FirebaseAuthDeviceIdentityProvider()

    val isCollectionEnabled: StateFlow<Boolean> = collectionStatePreferences.isCollectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    private val _lastSample = MutableStateFlow<TelemetrySample?>(null)
    val lastSample: StateFlow<TelemetrySample?> = _lastSample.asStateFlow()

    private val _isSampling = MutableStateFlow(false)
    val isSampling: StateFlow<Boolean> = _isSampling.asStateFlow()

    val bufferedCount: StateFlow<Int> = repository.observeBufferedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastUploadTimestampMs: StateFlow<Long?> = collectionStatePreferences.lastUploadTimestampMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        sampler.start()
        viewModelScope.launch { _deviceId.value = deviceIdentityProvider.getDeviceId() }
    }

    override fun onCleared() {
        sampler.stop()
    }

    fun runTelemetryTest() {
        if (_isSampling.value) return
        viewModelScope.launch {
            _isSampling.value = true
            try {
                val sample = sampler.captureSample()
                repository.record(sample)
                _lastSample.value = sample
            } finally {
                _isSampling.value = false
            }
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            collectionStatePreferences.setCollectionEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                TelemetryForegroundService.start(context)
                UploadScheduler.schedule(context)
            } else {
                TelemetryForegroundService.stop(context)
                UploadScheduler.cancel(context)
            }
        }
    }

    fun sendTestMosSurvey() {
        surveyScheduler.showTestNotification()
    }
}
