package com.example.qoesense.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.qoesense.R

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    OnboardingScaffold(
        title = stringResource(R.string.welcome_title),
        body = stringResource(R.string.welcome_body),
        primaryButtonText = stringResource(R.string.welcome_button),
        onPrimaryClick = onContinue,
    )
}
