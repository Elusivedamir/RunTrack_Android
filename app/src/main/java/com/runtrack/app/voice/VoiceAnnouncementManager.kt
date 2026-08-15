package com.runtrack.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.runtrack.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Application-scoped, serialized offline voice playback.
 *
 * Only pre-generated PCM assets are read at runtime. Piper and its model are build-time-only and
 * never ship in the APK.
 */
class VoiceAnnouncementManager(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<List<String>>(Channel.UNLIMITED)
    private val tokenCache = mutableMapOf<String, ByteArray>()
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setAcceptsDelayedFocusGain(false)
        .build()

    @Volatile
    private var lastStartWorkoutId: String? = null

    init {
        scope.launch {
            for (tokens in queue) {
                if (!settingsRepository.settings.first().voiceAnnouncementsEnabled) continue
                runCatching { play(tokens) }
                    .onFailure { Log.w(TAG, "Offline voice prompt skipped", it) }
            }
        }
    }

    @Synchronized
    fun announceStart(workoutId: String) {
        if (workoutId == lastStartWorkoutId) return
        lastStartWorkoutId = workoutId
        enqueue(listOf(RussianVoiceTokens.START))
    }

    fun announceKilometer(kilometers: Int) {
        if (kilometers <= 0) return
        enqueue(RussianVoiceTokens.kilometerTokens(kilometers))
    }

    private fun enqueue(tokens: List<String>) {
        if (tokens.isEmpty()) return
        val unknown = tokens.firstOrNull { it !in RussianVoiceTokens.allTokenIds }
        if (unknown != null) {
            Log.e(TAG, "Unknown offline voice token: $unknown")
            return
        }
        if (queue.trySend(tokens).isFailure) {
            Log.w(TAG, "Offline voice queue is unavailable")
        }
    }

    private fun play(tokens: List<String>) {
        val clips = tokens.map { loadToken(it) }
        if (clips.any { it.isEmpty() }) throw IOException("Empty offline voice asset")

        val focusResult = audioManager.requestAudioFocus(focusRequest)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(TAG, "Audio focus not granted; prompt skipped")
            return
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            throw IllegalStateException("AudioTrack min buffer error: $minBuffer")
        }
        val bufferBytes = maxOf(minBuffer, 8_192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack failed to initialize" }

            val totalBytes = clips.sumOf { it.size.toLong() } +
                TOKEN_GAP.size.toLong() * (clips.size - 1).coerceAtLeast(0)
            val targetFrames = totalBytes / BYTES_PER_FRAME
            val timeoutMillis =
                (targetFrames * 1_000L / SAMPLE_RATE).coerceAtLeast(100L) + 2_000L
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis

            track.play()
            clips.forEachIndexed { index, clip ->
                writeFully(track, clip)
                if (index != clips.lastIndex) writeFully(track, TOKEN_GAP)
            }

            while (
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition.toLong() < targetFrames &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                Thread.sleep(10L)
            }
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
            track.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun loadToken(token: String): ByteArray =
        tokenCache.getOrPut(token) {
            appContext.assets.open("$ASSET_DIR/$token.pcm").use { it.readBytes() }
        }

    private fun writeFully(track: AudioTrack, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val written = track.write(data, offset, data.size - offset)
            if (written <= 0) throw IOException("AudioTrack write failed: $written")
            offset += written
        }
    }

    private companion object {
        const val TAG = "RunTrackVoice"
        const val ASSET_DIR = "voice_ru"
        const val SAMPLE_RATE = 22_050
        const val BYTES_PER_FRAME = 2L
        const val TOKEN_GAP_MS = 25
        val TOKEN_GAP = ByteArray(SAMPLE_RATE * BYTES_PER_FRAME.toInt() * TOKEN_GAP_MS / 1_000)
    }
}
