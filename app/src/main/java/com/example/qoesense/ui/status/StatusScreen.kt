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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qoesense.R
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
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.status_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.status_collection_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCollectionEnabled) {
                        stringResource(R.string.status_collection_active_desc)
                    } else {
                        stringResource(R.string.status_collection_inactive_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatusRow(label = stringResource(R.string.status_label_buffered_samples), value = bufferedCount.toString())
                StatusRow(label = stringResource(R.string.status_label_last_upload), value = formatLastUpload(lastUploadTimestampMs))
                StatusRow(label = stringResource(R.string.status_label_anonymous_id), value = deviceId ?: "…")

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { statusViewModel.setCollectionEnabled(!isCollectionEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (isCollectionEnabled) {
                            stringResource(R.string.status_button_stop_collection)
                        } else {
                            stringResource(R.string.status_button_start_collection)
                        }
                    )
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
                        Text(stringResource(R.string.status_button_run_test))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { statusViewModel.sendTestMosSurvey() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.status_button_send_test_survey))
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
                Text(text = stringResource(R.string.status_permissions_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow(
                    label = stringResource(R.string.status_permission_location),
                    granted = PermissionUtils.hasForegroundLocation(context),
                    actionLabel = stringResource(R.string.action_grant),
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
                    label = stringResource(R.string.status_permission_background_location),
                    granted = PermissionUtils.hasBackgroundLocation(context),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = stringResource(R.string.status_permission_notifications),
                    granted = PermissionUtils.hasNotifications(context),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = stringResource(R.string.status_permission_phone_state),
                    granted = PermissionUtils.hasPhoneState(context),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = {
                        runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = stringResource(R.string.status_permission_usage_access),
                    granted = PermissionUtils.hasUsageAccess(context),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
                HorizontalDivider()
                PermissionRow(
                    label = stringResource(R.string.status_permission_battery),
                    granted = PermissionUtils.isIgnoringBatteryOptimizations(context),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                )
                HorizontalDivider()
                PermissionRow(
                    label = stringResource(R.string.status_permission_overlay),
                    granted = PermissionUtils.hasOverlayPermission(context),
                    actionLabel = stringResource(R.string.action_open_settings),
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
                text = if (granted) stringResource(R.string.permission_granted) else stringResource(R.string.permission_not_granted),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!granted) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun formatLastUpload(timestampMs: Long?): String {
    if (timestampMs == null) return stringResource(R.string.status_last_upload_never)
    val elapsedMinutes = (System.currentTimeMillis() - timestampMs) / 60_000
    return when {
        elapsedMinutes < 1 -> stringResource(R.string.status_last_upload_just_now)
        elapsedMinutes < 60 -> stringResource(R.string.status_last_upload_minutes_ago, elapsedMinutes)
        else -> stringResource(R.string.status_last_upload_hours_ago, elapsedMinutes / 60)
    }
}

// Raw telemetry field dump for the "Run telemetry test" debug button - technical field names
// (RSRP, RSRQ, TAC, ...) aren't natural-language content, so this is left untranslated.
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
