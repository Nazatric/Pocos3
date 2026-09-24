package com.pocos3.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes

// =============================================================================
// Onboarding flow.
//
// Three steps: firmware install, game directory pick, controller test.
// SAF-first: no MANAGE_EXTERNAL_STORAGE prompt; the user picks a folder
// via ACTION_OPEN_DOCUMENT_TREE.
// =============================================================================

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MornyShapes.spacingXxl),
        verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingL),
    ) {
        Text("Welcome to PocoS3",
             style = MaterialTheme.typography.displayLarge,
             color = MornyColors.textPrimary,
             fontWeight = FontWeight.Bold)
        Text("A native ARM64 PlayStation 3 emulator. Before playing, PocoS3 " +
             "needs two things: PS3 firmware and a game directory.",
             style = MaterialTheme.typography.bodyLarge,
             color = MornyColors.textSecondary)

        Spacer(Modifier.height(MornyShapes.spacingM))
        OnboardingStep(
            title = "1. Firmware",
            desc = "Install PS3UPDAT.PUP that you legally obtained from your PS3.",
            actionLabel = "Pick Firmware File",
            onAction = { /* TODO: ACTION_OPEN_DOCUMENT */ },
        )
        OnboardingStep(
            title = "2. Game Directory",
            desc = "Pick the folder where your PS3 games live. PocoS3 will scan it.",
            actionLabel = "Pick Game Folder",
            onAction = { /* TODO: ACTION_OPEN_DOCUMENT_TREE */ },
        )
        OnboardingStep(
            title = "3. Controller",
            desc = "Pair a Bluetooth controller, or use the on-screen touch overlay.",
            actionLabel = "Test Controller",
            onAction = { /* TODO: open controller test */ },
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onComplete,
            shape = RoundedCornerShape(MornyShapes.radiusButton),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MornyColors.accentAmber,
                contentColor = MornyColors.bgDeep,
            ),
        ) {
            Text("Start Playing", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OnboardingStep(title: String, desc: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(MornyShapes.radiusCard),
        colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MornyColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(MornyShapes.spacingL),
               verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MornyColors.textPrimary)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MornyColors.textSecondary)
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MornyColors.accentTeal, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
