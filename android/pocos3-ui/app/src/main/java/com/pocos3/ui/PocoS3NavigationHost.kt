package com.pocos3.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocos3.EmulationActivity
import com.pocos3.ui.home.HomeScreen
import com.pocos3.ui.onboarding.OnboardingFlow
import com.pocos3.ui.settings.PocoS3Settings
import com.pocos3.ui.settings.SettingsScreen
import com.pocos3.ui.system.SystemInfoScreen
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// =============================================================================
// PocoS3 navigation host.
//
// Top-level navigation graph. Three destinations:
//   * "home"     - the game library (HomeScreen)
//   * "settings" - the SettingsScreen
//   * "system"   - the SystemInfoScreen
//
// Plus "onboarding" which is shown on first launch if !firstRunDone.
// EmulationActivity is a separate Activity (owns its own window flags).
// =============================================================================

@Composable
fun PocoS3NavigationHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settings = remember { PocoS3Settings.get(context) }

    // Async-load firstRunDone; show a blank surface until resolved (avoids
    // runBlocking on the main thread).
    var firstRunDone by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        firstRunDone = settings.firstRunDone.first()
    }

    val resolved = firstRunDone
    if (resolved == null) {
        Surface(modifier = Modifier.fillMaxSize()) { /* brief splash */ }
        return
    }
    val startDestination = if (resolved) "home" else "onboarding"

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("onboarding") {
                OnboardingFlow(onComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                })
            }
            composable("home") {
                HomeScreen(
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenSystem = { navController.navigate("system") },
                    onPlayGame = { path ->
                        val intent = Intent(context, EmulationActivity::class.java).apply {
                            putExtra(EmulationActivity.EXTRA_GAME_PATH, path)
                        }
                        context.startActivity(intent)
                    },
                )
            }
            composable("settings") { SettingsScreen() }
            composable("system") { SystemInfoScreen() }
        }
    }
}
