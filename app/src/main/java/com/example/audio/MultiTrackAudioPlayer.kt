package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import com.example.data.model.StemTrack
import com.example.data.model.StemType
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

data class TrackPlayState(
    val stemTrack: StemTrack,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isPlaying: Boolean = false
)

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val transposeSemitones: Int = 0,
    val trackStates: Map<Long, TrackPlayState> = emptyMap(),
    val isReady: Boolean = false
)

class MultiTrackAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val players = mutableMapOf<Long, MediaPlayer>()
    private var progressJob: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadTracks(stems: List<StemTrack>) {
        release()
        val initialTrackStates = mutableMapOf<Long, TrackPlayState>()
        var detectedDuration = 0

        stems.forEach { stem ->
            try {
                val file = File(stem.audioFilePath)
                if (file.exists()) {
                    val player = MediaPlayer().apply {
                        setDataSource(context, Uri.fromFile(file))
                        isLooping = true
                        prepare()
                    }
                    players[stem.id] = player
                    if (player.duration > detectedDuration) {
                        detectedDuration = player.duration
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            initialTrackStates[stem.id] = TrackPlayState(
                stemTrack = stem,
                volume = stem.defaultVolume,
                isMuted = false,
                isSolo = false
            )
        }

        _uiState.value = PlayerUiState(
            isPlaying = false,
            currentPositionMs = 0,
            durationMs = if (detectedDuration > 0) detectedDuration else 18000,
            trackStates = initialTrackStates,
            isReady = players.isNotEmpty()
        )
        updateAllVolumes()
    }

    fun toggleMasterPlay() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (players.isEmpty()) return
        val currentSpeed = _uiState.value.playbackSpeed
        players.values.forEach { player ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    player.playbackParams = PlaybackParams().apply { speed = currentSpeed }
                }
                player.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.value = _uiState.value.copy(isPlaying = true)
        startProgressTracker()
    }

    fun pause() {
        players.values.forEach { player ->
            try {
                if (player.isPlaying) player.pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.value = _uiState.value.copy(isPlaying = false)
        stopProgressTracker()
    }

    fun seekTo(positionMs: Int) {
        val clamped = positionMs.coerceIn(0, _uiState.value.durationMs)
        players.values.forEach { player ->
            try {
                player.seekTo(clamped)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.value = _uiState.value.copy(currentPositionMs = clamped)
    }

    fun setSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            players.values.forEach { player ->
                try {
                    player.playbackParams = PlaybackParams().apply { this.speed = speed }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setTranspose(semitones: Int) {
        _uiState.value = _uiState.value.copy(transposeSemitones = semitones)
        // Transposition label indication for rehearsals
    }

    fun setTrackVolume(stemId: Long, volume: Float) {
        val currentStates = _uiState.value.trackStates.toMutableMap()
        val current = currentStates[stemId] ?: return
        currentStates[stemId] = current.copy(volume = volume.coerceIn(0.0f, 1.0f))
        _uiState.value = _uiState.value.copy(trackStates = currentStates)
        updateAllVolumes()
    }

    fun toggleMute(stemId: Long) {
        val currentStates = _uiState.value.trackStates.toMutableMap()
        val current = currentStates[stemId] ?: return
        currentStates[stemId] = current.copy(isMuted = !current.isMuted)
        _uiState.value = _uiState.value.copy(trackStates = currentStates)
        updateAllVolumes()
    }

    fun toggleSolo(stemId: Long) {
        val currentStates = _uiState.value.trackStates.toMutableMap()
        val current = currentStates[stemId] ?: return
        val newSoloState = !current.isSolo

        // Update target
        currentStates[stemId] = current.copy(isSolo = newSoloState)
        _uiState.value = _uiState.value.copy(trackStates = currentStates)
        updateAllVolumes()
    }

    fun soloOnly(stemId: Long) {
        val currentStates = _uiState.value.trackStates.toMutableMap()
        currentStates.keys.forEach { id ->
            val track = currentStates[id] ?: return@forEach
            currentStates[id] = track.copy(isSolo = (id == stemId))
        }
        _uiState.value = _uiState.value.copy(trackStates = currentStates)
        updateAllVolumes()
    }

    fun resetAllTracks() {
        val currentStates = _uiState.value.trackStates.toMutableMap()
        currentStates.keys.forEach { id ->
            val track = currentStates[id] ?: return@forEach
            currentStates[id] = track.copy(volume = 1.0f, isMuted = false, isSolo = false)
        }
        _uiState.value = _uiState.value.copy(trackStates = currentStates)
        updateAllVolumes()
    }

    private fun updateAllVolumes() {
        val trackStates = _uiState.value.trackStates
        val hasAnySolo = trackStates.values.any { it.isSolo }

        trackStates.forEach { (stemId, state) ->
            val player = players[stemId] ?: return@forEach
            val effectiveVolume = when {
                state.isMuted -> 0f
                hasAnySolo -> if (state.isSolo) state.volume else 0f
                else -> state.volume
            }
            try {
                player.setVolume(effectiveVolume, effectiveVolume)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val primaryPlayer = players.values.firstOrNull()
                if (primaryPlayer != null) {
                    try {
                        val pos = primaryPlayer.currentPosition
                        _uiState.value = _uiState.value.copy(currentPositionMs = pos)
                    } catch (e: Exception) {
                        e.printStackTrace()
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
        players.values.forEach { player ->
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        players.clear()
        _uiState.value = PlayerUiState()
    }
}
