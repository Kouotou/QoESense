package com.example.qoesense.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.qoesense.R
import com.example.qoesense.util.PermissionUtils

/**
 * Usage Access, battery-optimization exemption, and "display over other apps" are all granted
 * via system Settings screens, not runtime dialogs, so this screen just deep-links to each and
 * re-checks status whenever the user returns to the app (see [rememberOnResumeTick]).
 */
@Composable
fun UsageAccessScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    rememberOnResumeTick()
    val usageAccessGranted = PermissionUtils.hasUsageAccess(context)
    val batteryExempt = PermissionUtils.isIgnoringBatteryOptimizations(context)
    val overlayGranted = PermissionUtils.hasOverlayPermission(context)

    OnboardingScaffold(
        title = stringResource(R.string.usage_title),
        body = stringResource(R.string.usage_body),
        primaryButtonText = stringResource(R.string.usage_button_finish),
        onPrimaryClick = onFinish,
        extraContent = {
            Column {
                Text(
                    text = if (usageAccessGranted) {
                        stringResource(R.string.usage_access_status_granted)
                    } else {
                        stringResource(R.string.usage_access_status_not_granted)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.usage_access_button))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (batteryExempt) {
                        stringResource(R.string.battery_status_granted)
                    } else {
                        stringResource(R.string.battery_status_not_granted)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    enabled = !batteryExempt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.battery_button))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (overlayGranted) {
                        stringResource(R.string.overlay_status_granted)
                    } else {
                        stringResource(R.string.overlay_status_not_granted)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    },
                    enabled = !overlayGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.overlay_button))
                }
            }
        },
    )
}
