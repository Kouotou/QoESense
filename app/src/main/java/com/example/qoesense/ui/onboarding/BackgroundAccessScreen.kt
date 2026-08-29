package com.example.qoesense.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.qoesense.R
import com.example.qoesense.util.PermissionUtils

/**
 * Requests ACCESS_BACKGROUND_LOCATION and POST_NOTIFICATIONS. Background location must be
 * requested as its own step, separate from foreground location (Android enforces this on
 * API 30+ and silently ignores it if bundled with foreground permissions in one request).
 */
@Composable
fun BackgroundAccessScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var backgroundGranted by remember { mutableStateOf(PermissionUtils.hasBackgroundLocation(context)) }
    var notificationsGranted by remember { mutableStateOf(PermissionUtils.hasNotifications(context)) }
    val foregroundLocationGranted = PermissionUtils.hasForegroundLocation(context)

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsGranted = PermissionUtils.hasNotifications(context) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { backgroundGranted = PermissionUtils.hasBackgroundLocation(context) }

    val allDone = backgroundGranted && notificationsGranted

    OnboardingScaffold(
        title = stringResource(R.string.background_title),
        body = stringResource(R.string.background_body),
        primaryButtonText = if (allDone) stringResource(R.string.action_continue) else stringResource(R.string.background_button_grant),
        onPrimaryClick = {
            when {
                allDone -> onContinue()
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        !backgroundGranted &&
                        foregroundLocationGranted
                    ) {
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            }
        },
        secondaryButtonText = if (!allDone) stringResource(R.string.action_skip_for_now) else null,
        onSecondaryClick = if (!allDone) onContinue else null,
        extraContent = {
            Column {
                if (!foregroundLocationGranted) {
                    Text(
                        text = stringResource(R.string.background_hint_no_foreground),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
