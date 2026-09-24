package com.pocos3.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// =============================================================================
// Emulator foreground service.
//
// Keeps the emulator process alive while the user has a game booted, even
// if they background the app briefly. The foreground-service type is
// mediaPlayback so audio continues.
//
// The service does NOT own emulation state; the core lives in the
// PocoS3Application's process and is shared by all activities.
// =============================================================================

class EmulatorService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocoS3")
            .setContentText("Emulator running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "PocoS3 Emulator",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when the PS3 emulator is running."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "pocos3_emulator"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, EmulatorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EmulatorService::class.java))
        }
    }
}
