package com.example.qoesense.data

import android.util.Log
import com.example.qoesense.data.model.MosResponse
import com.example.qoesense.data.upload.DeviceIdentityProvider
import com.example.qoesense.data.upload.FirebaseAuthDeviceIdentityProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Writes MOS responses to devices/{deviceId}/mosResponses/{auto-id} in Firestore. */
class MosSurveyRepository(
    private val deviceIdentityProvider: DeviceIdentityProvider = FirebaseAuthDeviceIdentityProvider(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun submit(response: MosResponse): Boolean {
        val deviceId = deviceIdentityProvider.getDeviceId() ?: return false
        val data = mapOf(
            "score" to response.score,
            "submittedAtMs" to response.submittedAtMs,
            "triggerType" to response.triggerType.name,
            "contextAppPackage" to response.contextAppPackage,
            "contextSessionType" to response.contextSessionType.name,
            "windowStartMs" to response.windowStartMs,
            "windowEndMs" to response.windowEndMs,
        )
        return try {
            firestore.collection("devices").document(deviceId)
                .collection("mosResponses")
                .add(data)
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "MOS response submission failed, dropped (not retried)", e)
            false
        }
    }

    private companion object {
        const val TAG = "MosSurveyRepository"
    }
}
