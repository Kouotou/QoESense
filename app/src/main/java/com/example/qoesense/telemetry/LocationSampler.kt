package com.example.qoesense.telemetry

import android.annotation.SuppressLint
import android.content.Context
import com.example.qoesense.util.PermissionUtils
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot coarse location sampling via FusedLocationProviderClient, used to tag each
 * telemetry sample - not continuous tracking. Balanced-power accuracy is enough for
 * drive-test route mapping and keeps battery cost low over a weeks-long collection window.
 *
 * Bounded by an explicit timeout on both the request itself and the coroutine that awaits
 * it: on at least one tested OEM device, a getCurrentLocation() call made from a Service
 * (rather than an Activity-hosted caller) never invoked either callback when no fresh fix
 * was available, which silently stalled the entire capture pipeline forever since location
 * is awaited before the sample is recorded. Missing location must degrade to a null field,
 * never block collection.
 */
class LocationSampler(context: Context) {

    private val appContext = context.applicationContext
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    data class LocationReading(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    )

    @SuppressLint("MissingPermission") // guarded by PermissionUtils.hasForegroundLocation below
    suspend fun sampleOnce(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationReading? {
        if (!PermissionUtils.hasForegroundLocation(appContext)) return null
        val cancellationSource = CancellationTokenSource()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellationSource.cancel() }
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(timeoutMs)
                    .build()
                fusedLocationClient.getCurrentLocation(request, cancellationSource.token)
                    .addOnSuccessListener { location ->
                        val reading = location?.let {
                            LocationReading(it.latitude, it.longitude, it.accuracy)
                        }
                        if (continuation.isActive) continuation.resume(reading)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
