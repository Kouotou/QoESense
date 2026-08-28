package com.example.qoesense.data.upload

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Signs in anonymously on first use and reuses the resulting UID from then on - Firebase
 * persists the anonymous account across app restarts on the same device/install, so this
 * effectively returns a stable per-install ID without any participant-facing sign-up step.
 */
class FirebaseAuthDeviceIdentityProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : DeviceIdentityProvider {

    override suspend fun getDeviceId(): String? {
        auth.currentUser?.let { return it.uid }
        return try {
            auth.signInAnonymously().await().user?.uid
        } catch (e: Exception) {
            null
        }
    }
}
