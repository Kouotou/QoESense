package com.example.qoesense.ui.status

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qoesense.data.model.TelemetrySample
import com.example.qoesense.ui.onboarding.rememberOnResumeTick
import com.example.qoesense.util.PermissionUtils

/**
 * Home / debug screen. Doubles as the place participants (or us, during testing) can
 * re-grant anything they skipped or later revoked in onboarding. The "collection status"
 * section is a placeholder until the telemetry capture and Room buffering layers land.
 */
@Composable
fun StatusScreen() {
    val context = LocalContext.current
    rememberOnResumeTick()

    val statusViewModel: StatusViewModel = viewModel()
    val lastSample by statusViewModel.lastSample.collectAsState()
    val isSampling by statusViewModel.isSampling.collectAsState()
    val bufferedCount by statusViewModel.bufferedCount.collectAsState()
    val isCollectionEnabled by statusViewModel.isCollectionEnabled.collectAsState()
    val lastUploadTimestampMs by statusViewModel.lastUploadTimestampMs.collectAsState()
    val deviceId by statusViewModel.deviceId.collectAsState()

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* state re-read on next recomposition / resume */ }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = "QoESense", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Background collection status",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Collection", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCollectionEnabled) {
                        "The background service is sampling every ~20s and buffering into Room. " +
                            "A Wi-Fi-only worker uploads batches to Cloud Storage roughly hourly."
                    } else {
                        "Background collection is off. It normally starts automatically after " +
                            "onboarding - this toggle is mainly for testing."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatusRow(label = "Buffered samples", value = bufferedCount.toString())
                StatusRow(label = "Last upload", value = formatLastUpload(lastUploadTimestampMs))
                StatusRow(label = "Your anonymous ID", value = deviceId ?: "…")

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { statusViewModel.setCollectionEnabled(!isCollectionEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isCollectionEnabled) "Stop background collection" else "Start background collection")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { statusViewModel.runTelemetryTest() },
                    enabled = !isSampling,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSampling) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text("Run telemetry test")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { statusViewModel.sendTestMosSurvey() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send test MOS survey")
                }

                lastSample?.let { sample ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = formatSample(sample), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Permissions & access", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow(
                    label = "Location",
                    granted = PermissionUtils.hasForegroundLocation(context),
                    actionLabel = "Grant",
                    onAction = {
                        runtimePermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = "Background location",
                    granted = PermissionUtils.hasBackgroundLocation(context),
                    actionLabel = "Grant",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = "Notifications",
                    granted = PermissionUtils.hasNotifications(context),
                    actionLabel = "Grant",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = "Phone state",
                    granted = PermissionUtils.hasPhoneState(context),
                    actionLabel = "Grant",
                    onAction = {
                        runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = "Usage access",
                    granted = PermissionUtils.hasUsageAccess(context),
                    actionLabel = "Open settings",
                    onAction = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
                HorizontalDivider()
                PermissionRow(
                    label = "Battery optimization disabled",
                    granted = PermissionUtils.isIgnoringBatteryOptimizations(context),
                    actionLabel = "Open settings",
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = "Display over other apps",
                    granted = PermissionUtils.hasOverlayPermission(context),
                    actionLabel = "Open settings",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (granted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!granted) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun formatLastUpload(timestampMs: Long?): String {
    if (timestampMs == null) return "never"
    val elapsedMinutes = (System.currentTimeMillis() - timestampMs) / 60_000
    return when {
        elapsedMinutes < 1 -> "just now"
        elapsedMinutes < 60 -> "$elapsedMinutes min ago"
        else -> "${elapsedMinutes / 60}h ago"
    }
}

private fun formatSample(sample: TelemetrySample): String = buildString {
    appendLine("Capability tier: ${sample.telemetryCapabilityTier}")
    appendLine("Network type: ${sample.networkType ?: "n/a"}")
    appendLine("RSRP: ${sample.rsrp ?: "n/a"} dBm")
    appendLine("RSRQ: ${sample.rsrq ?: "n/a"} dB")
    appendLine("SINR (RSSNR): ${sample.rssnr ?: "n/a"} dB")
    appendLine("Timing advance: ${sample.timingAdvance ?: "n/a"}")
    appendLine("Signal level: ${sample.signalStrengthLevel ?: "n/a"} (0-4)")
    appendLine("CQI: ${if (sample.cqiSupported) sample.cqi else "unsupported on this device"}")
    appendLine("Cell bandwidth: ${sample.cellBandwidthKhz ?: "n/a"} kHz")
    appendLine("DL/UL channel: ${sample.downlinkChannelNumber ?: "n/a"} / ${sample.uplinkChannelNumber ?: "n/a"}")
    appendLine("Cell identity hash: ${sample.cellIdentityHash ?: "n/a"}")
    appendLine("TAC: ${sample.trackingAreaCode ?: "n/a"}")
    appendLine("Connection: ${sample.connectionType ?: "n/a"} (roaming: ${sample.isRoaming ?: "n/a"})")
    appendLine("Throughput: ${sample.throughputBytesPerSecond?.let { "$it B/s" } ?: "n/a (needs a second sample)"}")
    appendLine("RTT: ${sample.rttMs?.let { "$it ms" } ?: "n/a"}")
    appendLine("Foreground app: ${sample.foregroundAppPackage ?: "n/a"} (${sample.appSessionType})")
    appendLine(
        "Location: " + if (sample.latitude != null && sample.longitude != null) {
            "${sample.latitude}, ${sample.longitude} (±${sample.locationAccuracyMeters}m)"
        } else {
            "n/a"
        }
    )
    appendLine("Device: ${sample.deviceModel}, API ${sample.androidVersion}, app v${sample.appVersion}")
}
