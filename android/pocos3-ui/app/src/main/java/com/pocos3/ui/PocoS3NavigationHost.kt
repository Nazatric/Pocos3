package com.pocos3.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocos3.ui.home.HomeScreen
import com.pocos3.ui.onboarding.OnboardingFlow
import com.pocos3.ui.settings.SettingsScreen
import com.pocos3.ui.system.SystemInfoScreen

// =============================================================================
// PocoS3 navigation host.
//
// The app has three top-level destinations (Home, Settings, System) plus
// an Onboarding flow that runs on first launch. The EmulationActivity is
// a separate Activity, not a navigation destination, because it owns its
// own window flags (fullscreen, sensor landscape).
// =============================================================================

@Composable
fun PocoS3NavigationHost() {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenSystem = { navController.navigate("system") },
                    onPlayGame = { path ->
                        // Launching EmulationActivity is the Activity's job; the
                        // UI fires an intent via the LocalContext.
                        // TODO: wire LocalContext.current.startActivity(Intent(...))
                    },
                )
            }
            composable("settings") { SettingsScreen() }
            composable("system") { SystemInfoScreen() }
            composable("onboarding") { OnboardingFlow(onComplete = { navController.popBackStack() }) }
        }
    }
}
