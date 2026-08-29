package com.example.qoesense.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.qoesense.R

/**
 * Full informed-consent disclosure, shown before any permission is requested or any data is
 * collected. Text lives in strings.xml (R.string.consent_body) - localized into French
 * (res/values-fr) alongside English, and mirrors PRIVACY_POLICY.md in the project root; keep
 * all three in sync if any changes. The English copy holds the source-of-truth study details
 * (researcher name, institution, contact) - update the French string too if those ever change.
 */
@Composable
fun ConsentScreen(onAgree: () -> Unit) {
    OnboardingScaffold(
        title = stringResource(R.string.consent_title),
        body = stringResource(R.string.consent_body),
        primaryButtonText = stringResource(R.string.consent_button),
        onPrimaryClick = onAgree,
    )
}
