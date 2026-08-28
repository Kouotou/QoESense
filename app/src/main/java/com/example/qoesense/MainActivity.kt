package com.example.qoesense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.qoesense.data.OnboardingPreferences
import com.example.qoesense.ui.onboarding.BackgroundAccessScreen
import com.example.qoesense.ui.onboarding.ConsentScreen
import com.example.qoesense.ui.onboarding.LocationPermissionScreen
import com.example.qoesense.ui.onboarding.OnboardingViewModel
import com.example.qoesense.ui.onboarding.UsageAccessScreen
import com.example.qoesense.ui.onboarding.WelcomeScreen
import com.example.qoesense.ui.status.StatusScreen
import com.example.qoesense.ui.theme.QoESenseTheme

private object Routes {
    const val WELCOME = "onboarding_welcome"
    const val CONSENT = "onboarding_consent"
    const val LOCATION = "onboarding_location"
    const val BACKGROUND_ACCESS = "onboarding_background"
    const val USAGE_AND_BATTERY = "onboarding_usage_battery"
    const val STATUS = "status"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QoESenseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QoESenseApp()
                }
            }
        }
    }
}

@Composable
fun QoESenseApp() {
    val context = LocalContext.current
    val onboardingPreferences = remember { OnboardingPreferences(context) }
    val onboardingComplete by onboardingPreferences.isOnboardingComplete.collectAsState(initial = null)

    // Wait for the DataStore read before deciding a start destination, otherwise a
    // returning participant would briefly flash the onboarding wizard on every launch.
    val startDestination = when (onboardingComplete) {
        true -> Routes.STATUS
        false -> Routes.WELCOME
        null -> return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onContinue = { navController.navigate(Routes.CONSENT) })
        }
        composable(Routes.CONSENT) {
            val onboardingViewModel: OnboardingViewModel = viewModel()
            ConsentScreen(
                onAgree = {
                    onboardingViewModel.recordConsent {
                        navController.navigate(Routes.LOCATION)
                    }
                }
            )
        }
        composable(Routes.LOCATION) {
            LocationPermissionScreen(onContinue = { navController.navigate(Routes.BACKGROUND_ACCESS) })
        }
        composable(Routes.BACKGROUND_ACCESS) {
            BackgroundAccessScreen(onContinue = { navController.navigate(Routes.USAGE_AND_BATTERY) })
        }
        composable(Routes.USAGE_AND_BATTERY) {
            val onboardingViewModel: OnboardingViewModel = viewModel()
            UsageAccessScreen(
                onFinish = {
                    onboardingViewModel.completeOnboarding {
                        navController.navigate(Routes.STATUS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.STATUS) {
            StatusScreen()
        }
    }
}
