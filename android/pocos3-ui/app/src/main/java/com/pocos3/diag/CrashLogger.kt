package com.pocos3.diag

import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// Crash logger.
//
// Installs an UncaughtExceptionHandler that writes the crash stack trace
// to <cache>/crashes/<timestamp>.txt so the user can share it via the
// System Info screen. The handler also calls through to the previous
// handler so the system's default crash dialog still appears.
// =============================================================================

class CrashLogger private constructor(private val context: Context) {

    private val crashDir = File(context.cacheDir, "crashes").apply { mkdirs() }
    private val previousHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write the crash to a file synchronously - we have ~1 second
            // before Android kills the process.
            try {
                val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(crashDir, "crash-$ts.txt")
                file.bufferedWriter().use { w ->
                    w.write("=== PocoS3 crash report ===\n")
                    w.write("Time: ${Date()}\n")
                    w.write("Thread: ${thread.name} (id ${thread.id})\n")
                    w.write("Process: ${Process.myPid()}\n")
                    w.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    w.write("Board: ${Build.BOARD}\n")
                    w.write("Hardware: ${Build.HARDWARE}\n")
                    w.write("Android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})\n")
                    w.write("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n\n")
                    w.write("=== Stack trace ===\n")
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    w.write(sw.toString())
                    w.write("\n=== End of report ===\n")
                }
            } catch (_: Throwable) {
                // Best effort; we're about to die anyway.
            }

            // Call through to the previous handler so the system shows the
            // default crash dialog.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun listCrashes(): List<File> {
        return crashDir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun clearCrashes() = withContext(Dispatchers.IO) {
        crashDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    companion object {
        @Volatile private var instance: CrashLogger? = null
        fun get(context: Context): CrashLogger =
            instance ?: synchronized(this) {
                instance ?: CrashLogger(context.applicationContext).also {
                    instance = it
                    it.install()
                }
            }
    }
}
