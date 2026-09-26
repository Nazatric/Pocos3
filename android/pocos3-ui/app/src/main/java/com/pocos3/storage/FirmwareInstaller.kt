package com.pocos3.storage

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.pocos3.runtime.PocoS3Core
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =============================================================================
// Firmware installer (SAF-based).
//
// Picks PS3UPDAT.PUP via ACTION_OPEN_DOCUMENT, opens the file descriptor,
// and hands it to the core's installFw() entry point.
// =============================================================================

class FirmwareInstaller(private val context: Context) {

    suspend fun install(firmwareUri: Uri, onProgress: (Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            // Resolve the file descriptor via ContentResolver.
            val pfd = context.contentResolver.openFileDescriptor(firmwareUri, "r")
                ?: return@withContext false
            pfd.use {
                val fd = it.fd
                val progressId = System.currentTimeMillis()
                // The core invokes a registered progress callback (see
                // _pocos3_initialize which sets up the global progress bridge).
                val ok = PocoS3Core.nativeInstallFirmware(fd, progressId)
                if (!ok) {
                    android.util.Log.e("PocoS3", "Firmware install failed; see core log")
                }
                ok
            }
        }
}
