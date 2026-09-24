package com.pocos3.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes

// =============================================================================
// Settings screen.
//
// Three-tier: Simple / Advanced / Developer. The Simple tier is the only
// one visible to a normal user; the Advanced + Developer tiers unlock
// when the user taps "version" five times (classic Android Easter egg).
// =============================================================================

@Composable
fun SettingsScreen() {
    var tier by remember { mutableStateOf(SettingsTier.SIMPLE) }
    var devUnlocks by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(MornyShapes.spacingL).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Settings",
                 style = MaterialTheme.typography.displaySmall,
                 color = MornyColors.textPrimary,
                 fontWeight = FontWeight.Bold)
            // Easter egg: tap version 5x to unlock Developer tier.
            TextButton(onClick = {
                devUnlocks++
                if (devUnlocks >= 5) tier = SettingsTier.DEVELOPER
            }) {
                Text("v0.1.0", color = MornyColors.textTertiary)
            }
        }
        // Tier switcher chips.
        Row(horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingXs)) {
            SettingsTierChip("Simple", SettingsTier.SIMPLE, tier) { tier = SettingsTier.SIMPLE }
            SettingsTierChip("Advanced", SettingsTier.ADVANCED, tier) { tier = SettingsTier.ADVANCED }
            if (tier == SettingsTier.DEVELOPER) {
                SettingsTierChip("Developer", SettingsTier.DEVELOPER, tier) { tier = SettingsTier.DEVELOPER }
            }
        }
        when (tier) {
            SettingsTier.SIMPLE -> SimpleSettings()
            SettingsTier.ADVANCED -> AdvancedSettings()
            SettingsTier.DEVELOPER -> DeveloperSettings()
        }
    }
}

enum class SettingsTier { SIMPLE, ADVANCED, DEVELOPER }

@Composable
private fun SettingsTierChip(label: String, thisTier: SettingsTier, active: SettingsTier, onTap: () -> Unit) {
    val selected = thisTier == active
    FilterChip(
        selected = selected,
        onClick = onTap,
        label = { Text(label) },
        shape = RoundedCornerShape(MornyShapes.radiusChip),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MornyColors.bgSurface1,
            labelColor = MornyColors.textSecondary,
            selectedContainerColor = MornyColors.accentTealDim,
            selectedLabelColor = MornyColors.textPrimary,
        ),
    )
}

// Simple tier
@Composable
private fun SimpleSettings() {
    SettingCard("Resolution") {
        SettingChipRow(listOf("Native (720p)", "1080p", "1440p"), "Native (720p)") {}
    }
    SettingCard("FPS Limit") {
        SettingChipRow(listOf("30", "60", "Uncapped"), "60") {}
    }
    SettingCard("VSync / Frame Pacing") {
        SettingChipRow(listOf("On", "Adaptive", "Off"), "Adaptive") {}
    }
    SettingCard("Audio") {
        Text("48 kHz · 256 samples", color = MornyColors.textSecondary)
    }
    SettingCard("Controller") {
        Text("No controller paired", color = MornyColors.textSecondary)
    }
    SettingCard("Touch Overlay") {
        SliderRow(label = "Opacity", value = 0.55f, range = 0.2f..1.0f) {}
        SliderRow(label = "Scale", value = 1.0f, range = 0.6f..1.6f) {}
    }
    SettingCard("Graphics Quality") {
        SettingChipRow(listOf("Safe", "Balanced", "Performance", "Extreme"), "Balanced") {}
    }
    SettingCard("Cache") {
        Text("Shader cache: persistent, 412 MB", color = MornyColors.textSecondary)
    }
}

@Composable
private fun AdvancedSettings() {
    SettingCard("PPU Decoder") {
        SettingChipRow(listOf("Interpreter", "Static", "LLVM"), "LLVM") {}
    }
    SettingCard("SPU Decoder") {
        SettingChipRow(listOf("Interpreter", "Recompiler", "ASMJIT", "LLVM"), "LLVM") {}
    }
    SettingCard("SPU Block Size") {
        SettingChipRow(listOf("Small", "Medium", "Large"), "Medium") {}
    }
    SettingCard("RSX Vulkan") {
        Text("Capability tier: MALI_OPTIMIZED", color = MornyColors.textSecondary)
    }
    SettingCard("Synchronization") {
        Text("Accurate RSX reservations", color = MornyColors.textSecondary)
    }
    SettingCard("Number of SPU Threads") {
        SliderRow(label = "Threads", value = 6f, range = 1f..12f) {}
    }
}

@Composable
private fun DeveloperSettings() {
    SettingCard("Validation") {
        SwitchRow(label = "Vulkan validation layers", checked = false) {}
        SwitchRow(label = "Render doc", checked = false) {}
    }
    SettingCard("Tracing") {
        SwitchRow(label = "PPU trace", checked = false) {}
        SwitchRow(label = "SPU trace", checked = false) {}
        SwitchRow(label = "RSX FIFO trace", checked = false) {}
    }
    SettingCard("Diagnostics") {
        SwitchRow(label = "Performance HUD", checked = false) {}
        SwitchRow(label = "Shader compile logging", checked = false) {}
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(MornyShapes.radiusCard),
        colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MornyColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(MornyShapes.spacingM),
               verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                 color = MornyColors.textPrimary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SettingChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingXs)) {
        options.forEach { opt ->
            FilterChip(
                selected = (opt == selected),
                onClick = { onSelect(opt) },
                label = { Text(opt) },
                shape = RoundedCornerShape(MornyShapes.radiusChip),
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var v by remember { mutableStateOf(value) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(120.dp), color = MornyColors.textSecondary)
        Slider(
            value = v, onValueChange = { v = it; onChange(it) },
            valueRange = range, modifier = Modifier.weight(1f),
        )
        Text("%.2f".format(v), modifier = Modifier.width(56.dp),
             color = MornyColors.textTertiary)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var c by remember { mutableStateOf(checked) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MornyColors.textSecondary, modifier = Modifier.weight(1f))
        Switch(checked = c, onCheckedChange = { c = it; onChange(it) })
    }
}
