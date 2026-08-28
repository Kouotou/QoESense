package com.example.qoesense.ui.onboarding

import androidx.compose.runtime.Composable

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    OnboardingScaffold(
        title = "QoESense research study",
        body = "This app is part of an MSc research project studying mobile network " +
            "quality of experience. Running quietly in the background, it records " +
            "network signal metrics (like signal strength and quality), approximate " +
            "location, and which app is in the foreground - only to tag *when* and " +
            "*under what conditions* a measurement was taken.\n\n" +
            "It never reads message content, browsing history, or anything you type. " +
            "Over the next few screens we'll ask for the permissions the app needs to " +
            "do this reliably, and explain exactly why each one is needed before asking.",
        primaryButtonText = "Get started",
        onPrimaryClick = onContinue,
    )
}
