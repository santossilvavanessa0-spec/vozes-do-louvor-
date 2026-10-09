package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PreviewPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val isReady: Boolean = false
)

class PreviewAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PreviewPlayerState())
    val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

    fun loadAudio(file: File) {
        release()
        try {
            val player = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                setOnCompletionListener {
                    _state.value = _state.value.copy(isPlaying = false, currentPositionMs = 0)
                    stopProgressTracker()
                }
            }
            mediaPlayer = player
            _state.value = PreviewPlayerState(
                isPlaying = false,
                currentPositionMs = 0,
                durationMs = player.duration,
                isReady = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = PreviewPlayerState(isReady = false)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        try {
            player.start()
            _state.value = _state.value.copy(isPlaying = true)
            startProgressTracker()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            }
            _state.value = _state.value.copy(isPlaying = false)
            stopProgressTracker()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0, player.duration)
        try {
            player.seekTo(clamped)
            _state.value = _state.value.copy(currentPositionMs = clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _state.value = _state.value.copy(currentPositionMs = player.currentPosition)
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = null
        _state.value = PreviewPlayerState()
    }
}
