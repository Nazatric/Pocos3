package com.pocos3

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.pocos3.ui.theme.MornyTheme
import kotlinx.coroutines.delay

// =============================================================================
// Boot splash.
//
// Shows the Mornye launch screen for ~600ms, then bounces to MainActivity.
// Long enough for the Mornye colour system to "land" without a flash; short
// enough that the user does not stare at a logo.
// =============================================================================

class BootSplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MornyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // TODO: Mornye logo draw. For now, empty surface so the
                    // colour system reads cleanly.
                }
                // Delay then bounce.
                LaunchedEffect(Unit) {
                    delay(600)
                    startActivity(Intent(this@BootSplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}
