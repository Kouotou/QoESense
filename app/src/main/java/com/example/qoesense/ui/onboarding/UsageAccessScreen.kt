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
import androidx.compose.ui.unit.dp
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
        title = "Usage access, battery & pop-ups",
        body = "To tag measurements with which app is active - specifically to detect " +
            "a social-media or video-call session for quality labelling - the app needs " +
            "Usage Access. This is a system-protected setting, so Android will take you " +
            "to a Settings screen where you can find \"QoESense\" and turn it on.\n\n" +
            "We also ask you to exempt the app from battery optimization, since Android " +
            "aggressively suspends background apps otherwise, which would create gaps " +
            "of missing data over the weeks-long study.\n\n" +
            "Finally, \"display over other apps\" lets the occasional one-tap quality " +
            "rating pop up right when it's relevant, over whatever app you're using at " +
            "the time, instead of only showing as a notification you'd have to open " +
            "yourself. It disappears on its own within a few seconds either way.",
        primaryButtonText = "Finish setup",
        onPrimaryClick = onFinish,
        extraContent = {
            Column {
                Text(
                    text = if (usageAccessGranted) "Usage access: granted" else "Usage access: not granted",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Usage Access settings")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (batteryExempt) "Battery optimization: disabled (good)" else "Battery optimization: enabled",
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
                    Text("Disable battery optimization")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (overlayGranted) "Display over other apps: granted" else "Display over other apps: not granted",
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
                    Text("Allow display over other apps")
                }
            }
        },
    )
}
