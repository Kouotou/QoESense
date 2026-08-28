package com.example.qoesense.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/** Current transport type and roaming status, via the active network's capabilities. */
class ConnectivitySampler(private val context: Context) {

    data class ConnectivityReading(
        val connectionType: String,
        // NET_CAPABILITY_NOT_ROAMING doesn't exist before API 28; null on 26-27.
        val isRoaming: Boolean?,
    )

    fun readCurrent(): ConnectivityReading? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        val connectionType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }
        val isRoaming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        } else {
            null
        }

        return ConnectivityReading(connectionType, isRoaming)
    }
}
