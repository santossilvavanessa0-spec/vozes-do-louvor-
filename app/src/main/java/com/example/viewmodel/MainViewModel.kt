package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.MultiTrackAudioPlayer
import com.example.data.local.PraiseDatabase
import com.example.data.model.PraiseProject
import com.example.data.model.ProjectWithStems
import com.example.data.model.StemTrack
import com.example.data.model.StemType
import com.example.data.repository.PraiseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen {
    HOME,
    NEW_PROJECT,
    PROCESSING,
    RESULT_STUDIO,
    MY_PRAISES,
    SETTINGS
}

data class ProcessingUiState(
    val progress: Float = 0f,
    val currentStep: Int = 1,
    val stepTitle: String = "Iniciando processamento...",
    val stepDescription: String = "Preparando buffers de áudio",
    val songTitle: String = "",
    val artist: String = ""
)

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class ShareFile(val file: File, val mimeType: String, val title: String) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PraiseDatabase.getInstance(application)
    private val repository = PraiseRepository(application, database.praiseDao())

    val audioPlayer = MultiTrackAudioPlayer(application, viewModelScope)

    val projects: StateFlow<List<PraiseProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedProjectWithStems = MutableStateFlow<ProjectWithStems?>(null)
    val selectedProjectWithStems: StateFlow<ProjectWithStems?> = _selectedProjectWithStems.asStateFlow()

    private val _processingState = MutableStateFlow(ProcessingUiState())
    val processingState: StateFlow<ProcessingUiState> = _processingState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Settings State
    val backendApiUrl = MutableStateFlow("http://localhost:8000")
    val apiKey = MutableStateFlow("")
    val preferredFormat = MutableStateFlow("WAV (Fidelidade Máxima)")
    val isDemoMode = MutableStateFlow(true)

    private var processingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
    }

    fun navigateTo(screen: Screen) {
        if (screen != Screen.RESULT_STUDIO) {
            audioPlayer.pause()
        }
        _currentScreen.value = screen
    }

    fun openProjectInStudio(projectId: Long) {
        viewModelScope.launch {
            repository.getProjectWithStems(projectId).collect { projectWithStems ->
                if (projectWithStems != null) {
                    _selectedProjectWithStems.value = projectWithStems
                    audioPlayer.loadTracks(projectWithStems.stems)
                    _currentScreen.value = Screen.RESULT_STUDIO
                }
            }
        }
    }

    fun startSeparation(
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        modelName: String,
        audioFormat: String
    ) {
        if (title.isBlank()) {
            emitToast("Por favor, informe o nome do louvor")
            return
        }

        _processingState.value = ProcessingUiState(
            progress = 0.05f,
            currentStep = 1,
            stepTitle = "Decodificando Áudio do Louvor",
            stepDescription = "Lendo frequências espectrais e metadados",
            songTitle = title,
            artist = artist.ifBlank { "Ministério de Louvor" }
        )
        _currentScreen.value = Screen.PROCESSING

        processingJob = viewModelScope.launch {
            try {
                // Step 1: Decode
                delay(900)
                _processingState.value = _processingState.value.copy(
                    progress = 0.28f,
                    currentStep = 2,
                    stepTitle = "Demucs AI: Isolamento de Fontes",
                    stepDescription = "Separando instrumental da faixa vocal principal"
                )

                // Step 2: Demucs separation
                delay(1200)
                _processingState.value = _processingState.value.copy(
                    progress = 0.55f,
                    currentStep = 3,
                    stepTitle = "IA Coral: Análise de Naipes Vocais",
                    stepDescription = "Desentrelaçando formantes de Soprano, Contralto, Tenor e Baixo"
                )

                // Step 3: Vocal Stems formant disentanglement
                delay(1300)
                _processingState.value = _processingState.value.copy(
                    progress = 0.85f,
                    currentStep = 4,
                    stepTitle = "Renderização e Equalização",
                    stepDescription = "Gerando faixas independentes e equalização de ensaio"
                )

                // Step 4: Real WAV stems creation and persistence
                val newProjectId = repository.createProjectWithStems(
                    title = title,
                    artist = artist.ifBlank { "Ministério de Louvor" },
                    musicalKey = musicalKey,
                    notes = notes,
                    modelName = modelName,
                    audioFormat = audioFormat
                )

                delay(600)
                _processingState.value = _processingState.value.copy(
                    progress = 1.0f,
                    currentStep = 4,
                    stepTitle = "Processamento Concluído!",
                    stepDescription = "Suas faixas estão prontas no Estúdio"
                )

                delay(500)
                openProjectInStudio(newProjectId)
            } catch (e: Exception) {
                emitToast("Erro durante o processamento: ${e.localizedMessage}")
                _currentScreen.value = Screen.NEW_PROJECT
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _currentScreen.value = Screen.NEW_PROJECT
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            emitToast("Louvor excluído com sucesso")
            if (_selectedProjectWithStems.value?.project?.id == projectId) {
                audioPlayer.release()
                _selectedProjectWithStems.value = null
                _currentScreen.value = Screen.MY_PRAISES
            }
        }
    }

    fun exportSingleStem(stem: StemTrack, songTitle: String) {
        viewModelScope.launch {
            val file = File(stem.audioFilePath)
            if (file.exists()) {
                val safeTitle = songTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val safeStem = stem.title.replace(" ", "_")
                val exportDir = File(getApplication<Application>().cacheDir, "exports")
                exportDir.mkdirs()
                val targetFile = File(exportDir, "${safeTitle}_${safeStem}.wav")
                file.copyTo(targetFile, overwrite = true)
                _uiEvents.emit(
                    UiEvent.ShareFile(
                        file = targetFile,
                        mimeType = "audio/wav",
                        title = "Compartilhar Faixa ${stem.title}"
                    )
                )
            } else {
                emitToast("Arquivo da faixa não encontrado")
            }
        }
    }

    fun exportAllStemsZip() {
        val current = _selectedProjectWithStems.value ?: return
        viewModelScope.launch {
            emitToast("Compactando todas as faixas em arquivo ZIP...")
            val zipFile = repository.createZipExport(current.project, current.stems)
            _uiEvents.emit(
                UiEvent.ShareFile(
                    file = zipFile,
                    mimeType = "application/zip",
                    title = "Baixar Todas as Faixas (${current.project.title})"
                )
            )
        }
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast(msg))
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
