package com.mathapplocker.foss.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pauses media playback while a challenge is on screen.
 *
 * Two layers are used:
 * 1. A transient audio focus request, which pauses well-behaved players (ExoPlayer,
 *    Media3, Spotify, YouTube, ...).
 * 2. A media-key fallback for players that ignore audio focus (VLC, some TV/streaming apps).
 *
 * While the challenge is open, playback is re-paused if the user presses play again
 * (headset buttons, lockscreen controls, auto-play).
 */
class MediaPauseController(context: Context) {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private var focusRequest: AudioFocusRequest? = null
    private var monitorJob: Job? = null
    private var pausedViaKey = false
    private var mediaWasPlaying = false
    private var lastPauseDispatchAt = 0L

    fun start(scope: CoroutineScope) {
        if (monitorJob != null) return
        val manager = audioManager ?: return

        mediaWasPlaying = manager.isMusicActive

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        runCatching { manager.requestAudioFocus(request) }

        monitorJob = scope.launch {
            // Give focus-aware players a moment to react before using media keys.
            delay(300)
            ensurePaused(manager)
            while (isActive) {
                delay(1_000)
                if (manager.isMusicActive) ensurePaused(manager)
            }
        }
    }

    /**
     * Releases the pause. With [resumePlayback] the player that we paused is resumed;
     * otherwise playback stays paused (used when the user exits to home without
     * solving the challenge).
     */
    fun stop(scope: CoroutineScope, resumePlayback: Boolean) {
        monitorJob?.cancel()
        monitorJob = null
        val manager = audioManager ?: return

        focusRequest?.let { request -> runCatching { manager.abandonAudioFocusRequest(request) } }
        focusRequest = null
        pausedViaKey = false

        val shouldResume = mediaWasPlaying
        mediaWasPlaying = false

        scope.launch {
            if (resumePlayback) {
                // Some players (notably VLC) do not resume automatically when focus
                // is regained, so send an explicit play key if audio is still silent.
                delay(500)
                if (shouldResume && !manager.isMusicActive) {
                    dispatchKey(manager, KeyEvent.KEYCODE_MEDIA_PLAY)
                }
            } else {
                // Focus-paused players may auto-resume after focus is released.
                delay(600)
                ensurePaused(manager)
            }
        }
    }

    private fun ensurePaused(manager: AudioManager) {
        if (!manager.isMusicActive) return
        val now = SystemClock.elapsedRealtime()
        // Avoid spamming media keys if a player does not report state immediately.
        if (now - lastPauseDispatchAt < 250) return
        lastPauseDispatchAt = now
        pausedViaKey = true
        dispatchKey(manager, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    private fun dispatchKey(manager: AudioManager, keyCode: Int) {
        runCatching {
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }
}
