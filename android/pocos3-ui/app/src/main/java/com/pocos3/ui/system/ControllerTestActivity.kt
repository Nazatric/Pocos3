package com.pocos3.ui.system

import android.content.Context
import android.os.Bundle
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocos3.input.ControllerManager
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes
import com.pocos3.ui.theme.MornyTheme

// =============================================================================
// Controller test activity.
//
// Lists the connected InputDevices that report gamepad/joystick sources,
// shows their vendor/product IDs, and lets the user press buttons + sticks
// to verify the mappings are correct.
// =============================================================================

class ControllerTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MornyTheme {
                ControllerTestScreen()
            }
        }
    }
}

@Composable
private fun ControllerTestScreen() {
    val context = LocalContext.current
    var controllers by remember { mutableStateOf<List<ControllerInfo>>(emptyList()) }
    var hotplugCounter by remember { mutableStateOf(0) }

    LaunchedEffect(hotplugCounter) {
        controllers = enumerateControllers(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(MornyShapes.spacingL),
           verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Gamepad, contentDescription = null,
                 tint = MornyColors.accentAmber)
            Spacer(Modifier.width(MornyShapes.spacingS))
            Text("Controller Test",
                 style = MaterialTheme.typography.displaySmall,
                 color = MornyColors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = { hotplugCounter++ }) {
                Text("Rescan")
            }
        }

        if (controllers.isEmpty()) {
            Card(
                shape = RoundedCornerShape(MornyShapes.radiusCard),
                colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
            ) {
                Column(modifier = Modifier.padding(MornyShapes.spacingL),
                       verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingXs)) {
                    Text("No controllers detected.",
                         style = MaterialTheme.typography.titleMedium,
                         color = MornyColors.textPrimary)
                    Text("Pair a Bluetooth controller (Android Settings → Bluetooth) " +
                         "or connect a USB controller.",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MornyColors.textSecondary)
                    Text("On-screen touch overlay is always available during gameplay.",
                         style = MaterialTheme.typography.bodySmall,
                         color = MornyColors.textTertiary)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS)) {
                items(controllers) { c -> ControllerCard(c) }
            }
        }
    }
}

@Composable
private fun ControllerCard(c: ControllerInfo) {
    Card(
        shape = RoundedCornerShape(MornyShapes.radiusCard),
        colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MornyColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(MornyShapes.spacingM),
               verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingXxs)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium,
                 color = MornyColors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text("Vendor: 0x${"%04X".format(c.vendorId)}  Product: 0x${"%04X".format(c.productId)}",
                 style = MaterialTheme.typography.bodySmall, color = MornyColors.textSecondary)
            Text("Device ID: ${c.deviceId}",
                 style = MaterialTheme.typography.bodySmall, color = MornyColors.textTertiary)
            Text("Sources: 0x${"%X".format(c.sources)}",
                 style = MaterialTheme.typography.bodySmall, color = MornyColors.textTertiary)
            if (c.isDualShock) Text("Sony DualShock",
                 style = MaterialTheme.typography.labelMedium, color = MornyColors.accentTeal)
            if (c.isGenericXbox) Text("Xbox controller",
                 style = MaterialTheme.typography.labelMedium, color = MornyColors.accentTeal)
        }
    }
}

data class ControllerInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val isDualShock: Boolean,
    val isGenericXbox: Boolean,
)

private fun enumerateControllers(context: Context): List<ControllerInfo> {
    val ids = InputDevice.getDeviceIds()
    return ids.mapNotNull { id ->
        val dev = InputDevice.getDevice(id) ?: return@mapNotNull null
        if (!dev.supportsSource(InputDevice.SOURCE_GAMEPAD) &&
            !dev.supportsSource(InputDevice.SOURCE_JOYSTICK)) return@mapNotNull null
        ControllerInfo(
            deviceId = dev.id,
            name = dev.name,
            vendorId = dev.vendorId,
            productId = dev.productId,
            sources = dev.sources,
            isDualShock = dev.vendorId == 0x054C,
            isGenericXbox = dev.vendorId == 0x045E,
        )
    }
}
