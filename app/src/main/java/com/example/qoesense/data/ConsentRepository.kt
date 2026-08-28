package com.example.qoesense.data

import com.example.qoesense.data.upload.DeviceIdentityProvider
import com.example.qoesense.data.upload.FirebaseAuthDeviceIdentityProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Best-effort server-side record that consent was given, alongside the local
 * OnboardingPreferences flag that actually gates the app (which is authoritative - this is
 * just a durable research-integrity record, not something the app's behavior depends on).
 */
class ConsentRepository(
    private val deviceIdentityProvider: DeviceIdentityProvider = FirebaseAuthDeviceIdentityProvider(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun recordConsentGiven(timestampMs: Long): Boolean {
        val deviceId = deviceIdentityProvider.getDeviceId() ?: return false
        return try {
            firestore.collection("devices").document(deviceId)
                .set(mapOf("consentGivenAtMs" to timestampMs), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
