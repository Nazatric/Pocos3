package com.pocos3.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocos3.platform.CpuTopology
import com.pocos3.platform.DeviceProfileRegistry
import com.pocos3.platform.DisplayProbe
import com.pocos3.platform.MemoryProbe
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes

// =============================================================================
// System Information screen.
//
// Dumps the runtime-detected device profile, CPU topology, RAM, display,
// and Vulkan capabilities. Useful for bug reports.
// =============================================================================

@Composable
fun SystemInfoScreen() {
    var profile by remember { mutableStateOf("loading...") }
    var cpu by remember { mutableStateOf("loading...") }
    var mem by remember { mutableStateOf("loading...") }
    var display by remember { mutableStateOf("loading...") }

    LaunchedEffect(Unit) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        profile = DeviceProfileRegistry.detect(ctx).toString()
        cpu = CpuTopology.probe().toString()
        // Run on IO; the memory probe reads ActivityManager on the main thread.
        mem = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MemoryProbe.probe(ctx).toString()
        }
        display = DisplayProbe.probe(ctx).toString()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(MornyShapes.spacingL).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
    ) {
        Text("System Information",
             style = MaterialTheme.typography.displaySmall,
             color = MornyColors.textPrimary, fontWeight = FontWeight.Bold)
        InfoCard("Device") {
            Text("Manufacturer: ${android.os.Build.MANUFACTURER}", color = MornyColors.textSecondary)
            Text("Model: ${android.os.Build.MODEL}", color = MornyColors.textSecondary)
            Text("Board: ${android.os.Build.BOARD}", color = MornyColors.textSecondary)
            Text("Hardware: ${android.os.Build.HARDWARE}", color = MornyColors.textSecondary)
        }
        InfoCard("CPU") {
            Text(cpu, color = MornyColors.textSecondary)
        }
        InfoCard("RAM") {
            Text(mem, color = MornyColors.textSecondary)
        }
        InfoCard("Display") {
            Text(display, color = MornyColors.textSecondary)
        }
        InfoCard("Android") {
            Text("Version: ${android.os.Build.VERSION.RELEASE}", color = MornyColors.textSecondary)
            Text("SDK: ${android.os.Build.VERSION.SDK_INT}", color = MornyColors.textSecondary)
        }
        InfoCard("ABI") {
            Text("Primary: ${android.os.Build.SUPPORTED_ABIS.joinToString()}", color = MornyColors.textSecondary)
        }
        InfoCard("Selected Profile") {
            Text(profile, color = MornyColors.accentAmber)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(MornyShapes.radiusCard),
        colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MornyColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(MornyShapes.spacingM),
               verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingXxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                 color = MornyColors.textPrimary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
