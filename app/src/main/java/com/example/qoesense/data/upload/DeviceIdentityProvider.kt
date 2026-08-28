package com.example.qoesense.data.upload

/**
 * Supplies the stable per-install device identifier used both as the Cloud Storage path
 * prefix and the Firestore document ID. This must be the Firebase anonymous-auth UID (so
 * Storage/Firestore security rules can scope access by request.auth.uid) - it deliberately
 * isn't a locally-generated UUID, since that would need a migration once Firebase Auth lands.
 * See CLAUDE.md for the console setup checklist.
 */
interface DeviceIdentityProvider {
    suspend fun getDeviceId(): String?
}

/** Placeholder used until Firebase Auth is wired in. */
class UnconfiguredDeviceIdentityProvider : DeviceIdentityProvider {
    override suspend fun getDeviceId(): String? = null
}
