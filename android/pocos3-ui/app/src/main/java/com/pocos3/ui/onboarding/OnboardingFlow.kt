package com.pocos3.ui.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocos3.storage.FirmwareInstaller
import com.pocos3.ui.settings.PocoS3Settings
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes
import kotlinx.coroutines.launch

// =============================================================================
// Onboarding flow.
//
// Three steps:
//   1. Pick PS3 firmware file (PS3UPDAT.PUP) - install it.
//   2. Pick game directory (SAF tree URI).
//   3. Pair a Bluetooth controller (system settings).
//
// SAF-first. No MANAGE_EXTERNAL_STORAGE prompt; all access via SAF URIs.
// =============================================================================

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { PocoS3Settings.get(context) }

    // Step state.
    var firmwareDone by remember {
        mutableStateOf(settings.firstRunDone)
    }
    var firmwareInstalling by remember { mutableStateOf(false) }
    var firmwareError by remember { mutableStateOf<String?>(null) }
    var gameDirDone by remember { mutableStateOf(false) }
    var controllerDone by remember { mutableStateOf(false) }

    // SAF picker for game directory.
    val pickGameDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission so we can read this directory across launches.
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch {
                settings.setGameDirUri(uri.toString())
                gameDirDone = true
            }
        }
    }

    // SAF picker for firmware file.
    val pickFirmware = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Persist permission; we'll need to read this later too.
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            firmwareInstalling = true
            firmwareError = null
            scope.launch {
                val installer = FirmwareInstaller(context)
                val ok = installer.install(uri) { /* TODO: progress */ }
                firmwareInstalling = false
                if (ok) {
                    settings.setFirmwareInstalled(true)
                    firmwareDone = true
                } else {
                    firmwareError = "Firmware install failed. Check the log."
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(MornyShapes.spacingXxl),
        verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingL),
    ) {
        Text(
            "Welcome to PocoS3",
            style = MaterialTheme.typography.displayLarge,
            color = MornyColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A native ARM64 PlayStation 3 emulator. Before playing, " +
            "PocoS3 needs PS3 firmware and a game directory.",
            style = MaterialTheme.typography.bodyLarge,
            color = MornyColors.textSecondary,
        )

        Spacer(Modifier.height(MornyShapes.spacingM))

        // 1. Firmware
        OnboardingStep(
            title = "1. Firmware",
            desc = "Install the PS3UPDAT.PUP that you legally obtained from your PS3.",
            actionLabel = if (firmwareInstalling) "Installing..."
                          else if (firmwareDone) "Reinstall"
                          else "Pick Firmware File",
            actionEnabled = !firmwareInstalling,
            isDone = firmwareDone,
            error = firmwareError,
            onAction = { pickFirmware.launch(arrayOf("application/octet-stream", "*/*")) },
        )

        // 2. Game directory
        OnboardingStep(
            title = "2. Game Directory",
            desc = "Pick the folder where your PS3 games live. PocoS3 will scan it.",
            actionLabel = if (gameDirDone) "Re-pick" else "Pick Game Folder",
            isDone = gameDirDone,
            onAction = { pickGameDir.launch(null) },
        )

        // 3. Controller
        OnboardingStep(
            title = "3. Controller",
            desc = "Pair a Bluetooth controller, or use the on-screen touch overlay.",
            actionLabel = if (controllerDone) "Done" else "Open Bluetooth Settings",
            isDone = controllerDone,
            onAction = {
                controllerDone = true
                // Open Bluetooth system settings so the user can pair a controller.
                val intent = Intent(android.bluetooth.bluetooth.adapter.
                    actionRequestDiscoverable)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                runCatching { context.startActivity(intent) }
            },
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                scope.launch { settings.markFirstRunDone() }
                onComplete()
            },
            shape = RoundedCornerShape(MornyShapes.radiusButton),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = firmwareDone && gameDirDone,
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
private fun OnboardingStep(
    title: String,
    desc: String,
    actionLabel: String,
    actionEnabled: Boolean = true,
    isDone: Boolean = false,
    error: String? = null,
    onAction: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(MornyShapes.radiusCard),
        colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MornyColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MornyShapes.spacingL),
            verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDone) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Done",
                        tint = MornyColors.signalSuccess,
                    )
                    Spacer(Modifier.width(MornyShapes.spacingS))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MornyColors.textPrimary,
                )
            }
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MornyColors.textSecondary,
            )
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MornyColors.signalDanger,
                )
            }
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                shape = RoundedCornerShape(MornyShapes.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MornyColors.accentTealDim,
                    contentColor = MornyColors.textPrimary,
                ),
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
