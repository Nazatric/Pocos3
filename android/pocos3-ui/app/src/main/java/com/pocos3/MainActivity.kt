package com.pocos3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocos3.ui.PocoS3NavigationHost
import com.pocos3.ui.theme.MornyTheme

// =============================================================================
// Main activity - hosts the Compose UI root.
//
// This activity does NOT host the emulator surface; that lives in
// EmulationActivity. MainActivity is the settings / library / system UI.
// =============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MornyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PocoS3NavigationHost()
                }
            }
        }
    }
}
