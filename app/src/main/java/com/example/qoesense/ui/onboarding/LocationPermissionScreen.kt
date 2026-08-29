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
import androidx.compose.ui.res.stringResource
import com.example.qoesense.R
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
        title = stringResource(R.string.location_title),
        body = stringResource(R.string.location_body),
        primaryButtonText = if (granted) stringResource(R.string.action_continue) else stringResource(R.string.location_button_grant),
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
        secondaryButtonText = if (!granted) stringResource(R.string.action_skip_for_now) else null,
        onSecondaryClick = if (!granted) onContinue else null,
        extraContent = {
            Column {
                if (!granted) {
                    Text(
                        text = stringResource(R.string.location_hint_denied),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
