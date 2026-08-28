package com.example.qoesense.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.PhysicalChannelConfig
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.example.qoesense.util.PermissionUtils
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads LTE signal/cell telemetry via the public TelephonyManager/CellInfo APIs. This app
 * only targets LTE cells (CellInfoLte) - non-LTE cell types are ignored, matching the
 * project's LTE drive-test scope.
 *
 * PhysicalChannelConfig is only available via an asynchronous listener, not a synchronous
 * getter, so [start] must be called once (e.g. when the foreground service starts) to keep
 * [latestPhysicalChannelConfigs] warm; [readCurrent] then folds the latest cached value into
 * an otherwise-synchronous reading. The listener path (TelephonyCallback) only exists from
 * API 31 - the older PhoneStateListener.LISTEN_PHYSICAL_CHANNEL_CONFIGS equivalent has since
 * been removed from the public SDK, so devices on API 29-30 fall back to CQI_29 tier without
 * physical-channel data.
 *
 * In practice PHYSCHAN_30 is rarely reachable at all: registering
 * TelephonyCallback.PhysicalChannelConfigListener throws a SecurityException on any device
 * where the app doesn't hold carrier privileges (confirmed via
 * TelephonyPermissions.enforceCarrierPrivilege on-device), which is essentially every
 * participant phone. [start] catches that exception and silently leaves the physical-channel
 * fields null rather than crash - this is a real platform restriction, not a bug to work around.
 *
 * PhysicalChannelConfig.getFrequencyRange() is deliberately not read - it's a @hide/SystemApi
 * method not present in the public android.jar.
 */
class CellularTelemetrySource(private val context: Context) {

    data class Reading(
        val rsrp: Int?,
        val rsrq: Int?,
        val rssnr: Int?,
        val timingAdvance: Int?,
        val networkType: String,
        val cellIdentityHash: String?,
        val trackingAreaCode: Int?,
        val signalStrengthLevel: Int?,
        val cqi: Int?,
        val cqiSupported: Boolean,
        val cellBandwidthKhz: Int?,
        val downlinkChannelNumber: Int?,
        val uplinkChannelNumber: Int?,
    )

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val latestPhysicalChannelConfigs = AtomicReference<List<PhysicalChannelConfig>>(emptyList())

    private var telephonyCallback: TelephonyCallback? = null
    private var started = false

    @SuppressLint("MissingPermission") // guarded by PermissionUtils.hasPhoneState below
    fun start() {
        if (started || !PermissionUtils.hasPhoneState(context)) return
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.PhysicalChannelConfigListener {
                override fun onPhysicalChannelConfigChanged(configs: MutableList<PhysicalChannelConfig>) {
                    latestPhysicalChannelConfigs.set(configs.toList())
                }
            }
            try {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                telephonyCallback = callback
            } catch (e: SecurityException) {
                // PhysicalChannelConfigListener requires carrier privileges - most participant
                // devices won't have them. PhysicalChannelConfig data just stays unavailable;
                // the sample still falls back to the CQI_29/BASE_26 tier.
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        }
        telephonyCallback = null
    }

    /** Returns null if required permissions are missing or no registered LTE cell is visible. */
    @SuppressLint("MissingPermission") // guarded by PermissionUtils checks below
    fun readCurrent(): Reading? {
        if (!PermissionUtils.hasPhoneState(context) || !PermissionUtils.hasForegroundLocation(context)) return null
        val lteCells = telephonyManager.allCellInfo.orEmpty().filterIsInstance<CellInfoLte>()
        val cell = lteCells.firstOrNull { it.isRegistered } ?: lteCells.firstOrNull() ?: return null

        val signalStrength = cell.cellSignalStrength
        val identity = cell.cellIdentity

        val cqiRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) signalStrength.cqi.takeIfAvailable() else null
        val cqiSupported = cqiRaw != null

        val physChan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) readPhysicalChannelFields() else null

        return Reading(
            rsrp = signalStrength.rsrp.takeIfAvailable(),
            rsrq = signalStrength.rsrq.takeIfAvailable(),
            rssnr = signalStrength.rssnr.takeIfAvailable(),
            timingAdvance = signalStrength.timingAdvance.takeIfAvailable(),
            networkType = networkTypeName(),
            cellIdentityHash = hashCellIdentity(identity.ci, identity.tac),
            trackingAreaCode = identity.tac.takeIfAvailable(),
            signalStrengthLevel = signalStrength.level,
            cqi = cqiRaw,
            cqiSupported = cqiSupported,
            cellBandwidthKhz = physChan?.cellBandwidthKhz,
            downlinkChannelNumber = physChan?.downlinkChannelNumber,
            uplinkChannelNumber = physChan?.uplinkChannelNumber,
        )
    }

    private data class PhysChanFields(
        val cellBandwidthKhz: Int?,
        val downlinkChannelNumber: Int?,
        val uplinkChannelNumber: Int?,
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readPhysicalChannelFields(): PhysChanFields? {
        val config = latestPhysicalChannelConfigs.get().firstOrNull() ?: return null
        return PhysChanFields(
            cellBandwidthKhz = config.cellBandwidthDownlinkKhz.takeIfAvailable(),
            downlinkChannelNumber = config.downlinkChannelNumber.takeIfAvailable(),
            uplinkChannelNumber = config.uplinkChannelNumber.takeIfAvailable(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun networkTypeName(): String {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.dataNetworkType
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.networkType
        }
        return when (type) {
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "OTHER"
        }
    }

    private fun Int.takeIfAvailable(): Int? =
        if (this == CellInfo.UNAVAILABLE || this == Int.MAX_VALUE) null else this

    private fun hashCellIdentity(ci: Int, tac: Int): String? {
        if (ci == Int.MAX_VALUE || ci == CellInfo.UNAVAILABLE) return null
        val digest = MessageDigest.getInstance("SHA-256").digest("$ci:$tac".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
