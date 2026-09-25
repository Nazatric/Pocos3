package com.pocos3.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =============================================================================
// Audio router.
//
// PocoS3 uses Oboe (AAudio) for low-latency PS3 audio output. The core
// owns the actual audio synthesis (cellAudio); this class wraps the Oboe
// stream creation so the C++ side can request a stream with the right
// parameters without having to know Android audio details.
//
// The C++ side calls into this via a JNI callback registered at
// _pocos3_initialize time. The stream is created here, samples are
// written from the core via a ring buffer.
//
// For now, this is a Kotlin-side config + JNI bridge stub. The actual
// Oboe stream creation lives in android/src/pocos3_audio.cpp (to be
// written when the audio path lands).
// =============================================================================

data class AudioConfig(
    val sampleRate: Int = 48000,
    val channels: Int = 2,           // stereo (PS3 cellAudio is 2ch primary)
    val bufferSize: Int = 256,        // ~5ms at 48kHz
    val performanceMode: PerformanceMode = PerformanceMode.LOW_LATENCY,
    val sharingMode: SharingMode = SharingMode.EXCLUSIVE,
)

enum class PerformanceMode { NONE, LOW_LATENCY, POWER_SAVING }
enum class SharingMode { SHARED, EXCLUSIVE }

class AudioRouter private constructor(private val context: Context) {

    private var config: AudioConfig = AudioConfig()
    @Volatile private var started = false

    fun configure(config: AudioConfig) {
        if (started) {
            android.util.Log.w("PocoS3.Audio", "Audio already started; config ignored")
            return
        }
        this.config = config
    }

    // The actual Oboe stream creation lives in native code. This is the
    // Kotlin-side entry point; the JNI bridge calls into the C++ audio
    // module which creates the Oboe::ManagedStream.
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        // TODO: implement Oboe stream creation in android/src/pocos3_audio.cpp
        // and call via JNI. For now this is a stub that reports success so
        // the boot path can proceed; the actual audio will be silent.
        started = true
        true
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        started = false
    }

    companion object {
        @Volatile private var instance: AudioRouter? = null
        fun get(context: Context): AudioRouter =
            instance ?: synchronized(this) {
                instance ?: AudioRouter(context.applicationContext).also { instance = it }
            }
    }
}
