package com.example.qoesense.ui.onboarding

import android.Manifest
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
import com.example.qoesense.util.PermissionUtils

@Composable
fun LocationPermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(PermissionUtils.hasForegroundLocation(context) && PermissionUtils.hasPhoneState(context))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        granted = PermissionUtils.hasForegroundLocation(context) && PermissionUtils.hasPhoneState(context)
    }

    OnboardingScaffold(
        title = "Location & network info",
        body = "Android requires location permission before it will return cell " +
            "signal readings (RSRP, RSRQ, SINR) at all - this is a platform " +
            "restriction, not something specific to this app. Approximate location " +
            "is also used to map coverage along your usual routes for the drive-test " +
            "portion of the study.\n\n" +
            "We also request phone-state access, needed on some Android versions to " +
            "read full cell information.",
        primaryButtonText = if (granted) "Continue" else "Grant permissions",
        onPrimaryClick = {
            if (granted) {
                onContinue()
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE,
                    )
                )
            }
        },
        secondaryButtonText = if (!granted) "Skip for now" else null,
        onSecondaryClick = if (!granted) onContinue else null,
        extraContent = {
            Column {
                if (!granted) {
                    Text(
                        text = "Without this, signal telemetry and route mapping cannot be " +
                            "collected. You can grant it later from the app's Permissions screen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
