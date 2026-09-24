package com.pocos3.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pocos3.runtime.PocoS3Core
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =============================================================================
// Firmware installer.
//
// Takes a content:// URI pointing at PS3UPDAT.PUP that the user picked
// via ACTION_OPEN_DOCUMENT, opens the file descriptor, and hands it to
// the core's installFw() entry point.
//
// The core owns the actual install logic; this wrapper only handles the
// SAF→fd translation and progress callback plumbing.
// =============================================================================

class FirmwareInstaller {
    suspend fun install(context: Context, firmwareUri: Uri, onProgress: (Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(firmwareUri, "r")
                ?: return@withContext false
            val fd = pfd.detachFd()
            try {
                // The core pushes progress events through a C++-side callback
                // that we registered at initialise() time; here we just wait
                // for the install to complete.
                val progressId = System.currentTimeMillis()
                // Forward progress callbacks (the core invokes a registered Java
                // callback; the registration is done in nativeInitialise via
                // SetGlobalProgressCallback - see android/src/pocos3-android.cpp TODO).
                PocoS3Core.nativeInstallFirmware(fd, progressId)
            } finally {
                // detachFd gave us ownership; close it.
                android.system.Os.close(java.io.FileDescriptor())  // placeholder
            }
            true
        }
}
