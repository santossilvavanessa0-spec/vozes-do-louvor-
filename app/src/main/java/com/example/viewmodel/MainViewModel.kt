package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioFileManager
import com.example.audio.AudioLoadState
import com.example.audio.LoadedAudioInfo
import com.example.audio.MultiTrackAudioPlayer
import com.example.audio.PreviewAudioPlayer
import com.example.data.local.PraiseDatabase
import com.example.data.model.PraiseProject
import com.example.data.model.ProjectWithStems
import com.example.data.model.StemTrack
import com.example.data.repository.PraiseRepository
import com.example.data.repository.SeparationServerException
import com.example.network.SeparationApiClient
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
    val stepDescription: String = "Preparando motor de inteligência artificial vocal",
    val songTitle: String = "",
    val artist: String = "",
    val isError: Boolean = false,
    val errorMessage: String = "",
    val errorSuggestion: String = "",
    val canRetryOnDevice: Boolean = true
)

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class ShareFile(val file: File, val mimeType: String, val title: String) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PraiseDatabase.getInstance(application)
    private val repository = PraiseRepository(application, database.praiseDao())

    val audioPlayer = MultiTrackAudioPlayer(application, viewModelScope)
    val previewPlayer = PreviewAudioPlayer(application, viewModelScope)

    val projects: StateFlow<List<PraiseProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedProjectWithStems = MutableStateFlow<ProjectWithStems?>(null)
    val selectedProjectWithStems: StateFlow<ProjectWithStems?> = _selectedProjectWithStems.asStateFlow()

    private val _processingState = MutableStateFlow(ProcessingUiState())
    val processingState: StateFlow<ProcessingUiState> = _processingState.asStateFlow()

    private val _audioLoadState = MutableStateFlow<AudioLoadState>(AudioLoadState.Idle)
    val audioLoadState: StateFlow<AudioLoadState> = _audioLoadState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Configuração do motor: true = Motor Integrado no Celular (recomendado e infalível em celulares físicos)
    val useOnDeviceSeparation = MutableStateFlow(true)

    // Configurable Backend API URL (Opcional, para quem rodar o container Demucs na rede)
    val backendApiUrl = MutableStateFlow("http://10.0.2.2:8001")
    val apiKey = MutableStateFlow("")
    val preferredFormat = MutableStateFlow("WAV (Fidelidade Máxima)")
    val backendStatus = MutableStateFlow("Motor no Celular Ativo")

    private var processingJob: Job? = null
    private var audioLoadJob: Job? = null

    // Cache para permitir retentativa no celular
    private var lastLoadedFile: File? = null
    private var lastTitle: String = ""
    private var lastArtist: String = ""
    private var lastKey: String = "G"
    private var lastNotes: String = ""
    private var lastModelName: String = ""
    private var lastAudioFormat: String = ""

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            autoDetectBackend()
        }
    }

    private suspend fun autoDetectBackend() {
        val active = SeparationApiClient.discoverActiveServer(backendApiUrl.value)
        if (active != null) {
            backendApiUrl.value = active
            backendStatus.value = "Servidor Remoto Conectado ($active)"
            useOnDeviceSeparation.value = false
        } else {
            backendStatus.value = "Motor Nativo no Celular Ativo (Sem dependência de servidor)"
            useOnDeviceSeparation.value = true
        }
    }

    fun navigateTo(screen: Screen) {
        if (screen != Screen.RESULT_STUDIO) {
            audioPlayer.pause()
        }
        if (screen != Screen.NEW_PROJECT) {
            previewPlayer.pause()
        }
        _currentScreen.value = screen
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            backendStatus.value = "Testando conexão com ${backendApiUrl.value}..."
            val result = SeparationApiClient.checkHealth(backendApiUrl.value)
            result.fold(
                onSuccess = { health ->
                    backendStatus.value = "Conectado (${health.service ?: "Demucs AI"}, Porta ${health.port})"
                    useOnDeviceSeparation.value = false
                    emitToast("Servidor Demucs conectado com sucesso!")
                },
                onFailure = { error ->
                    val discovered = SeparationApiClient.discoverActiveServer(backendApiUrl.value)
                    if (discovered != null) {
                        backendApiUrl.value = discovered
                        backendStatus.value = "Conectado ($discovered)"
                        useOnDeviceSeparation.value = false
                        emitToast("Servidor Demucs localizado em $discovered!")
                    } else {
                        backendStatus.value = "Servidor inacessível. Usando Motor no Celular."
                        useOnDeviceSeparation.value = true
                        emitToast("Servidor não encontrado. O app utilizará o motor integrado no celular.")
                    }
                }
            )
        }
    }

    fun setEngineMode(onDevice: Boolean) {
        useOnDeviceSeparation.value = onDevice
        if (onDevice) {
            emitToast("Modo 'No Celular' ativado: funciona sem internet ou servidor!")
        } else {
            emitToast("Modo 'Servidor Demucs' ativado: requer backend na rede.")
        }
    }

    fun loadAudioFromUri(uri: Uri) {
        audioLoadJob?.cancel()
        previewPlayer.release()
        _audioLoadState.value = AudioLoadState.Loading(0.05f, "Acessando áudio no dispositivo...")

        audioLoadJob = viewModelScope.launch {
            val result = AudioFileManager.copyAndValidateAudio(
                context = getApplication(),
                uri = uri,
                onProgress = { fraction, msg ->
                    _audioLoadState.value = AudioLoadState.Loading(fraction, msg)
                }
            )

            result.fold(
                onSuccess = { info ->
                    _audioLoadState.value = AudioLoadState.Success(info)
                    previewPlayer.loadAudio(info.file)
                    val keyMsg = info.detectedKey?.let { " • Tom Cifra Club: ${it.rootKey}" } ?: ""
                    emitToast("Música carregada com sucesso!$keyMsg")
                },
                onFailure = { error ->
                    _audioLoadState.value = AudioLoadState.Error(
                        errorMessage = error.localizedMessage ?: "Erro ao carregar áudio",
                        suggestion = "Verifique se o arquivo está acessível e nos formatos MP3, WAV, M4A, AAC ou FLAC."
                    )
                }
            )
        }
    }

    fun resetAudioLoad() {
        audioLoadJob?.cancel()
        previewPlayer.release()
        _audioLoadState.value = AudioLoadState.Idle
    }

    fun openProjectInStudio(projectId: Long) {
        previewPlayer.pause()
        viewModelScope.launch {
            repository.getProjectWithStems(projectId).collect { projectWithStems ->
                if (projectWithStems != null && projectWithStems.stems.isNotEmpty()) {
                    _selectedProjectWithStems.value = projectWithStems
                    audioPlayer.loadTracks(projectWithStems.stems)
                    _currentScreen.value = Screen.RESULT_STUDIO
                } else if (projectWithStems != null && projectWithStems.stems.isEmpty()) {
                    emitToast("Faixas não encontradas para este projeto")
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
        audioFormat: String,
        loadedAudioFile: File?,
        durationSeconds: Int
    ) {
        if (title.isBlank()) {
            emitToast("Por favor, informe o nome do louvor")
            return
        }

        if (loadedAudioFile == null || !loadedAudioFile.exists()) {
            emitToast("Por favor, envie ou selecione uma música antes de separar as vozes")
            return
        }

        lastLoadedFile = loadedAudioFile
        lastTitle = title
        lastArtist = artist
        lastKey = musicalKey
        lastNotes = notes
        lastModelName = modelName
        lastAudioFormat = audioFormat

        previewPlayer.pause()

        val isDeviceMode = useOnDeviceSeparation.value
        _processingState.value = ProcessingUiState(
            progress = 0.05f,
            currentStep = 1,
            stepTitle = if (isDeviceMode) "Iniciando Motor no Celular" else "Verificando Servidor Demucs",
            stepDescription = if (isDeviceMode) "Decodificando áudio no próprio dispositivo..." else "Conectando ao backend em ${backendApiUrl.value}...",
            songTitle = title,
            artist = artist.ifBlank { "Ministério de Louvor" },
            isError = false,
            canRetryOnDevice = true
        )
        _currentScreen.value = Screen.PROCESSING

        processingJob = viewModelScope.launch {
            try {
                val newProjectId = repository.separateAudioSmart(
                    preferOnDevice = useOnDeviceSeparation.value,
                    backendUrl = backendApiUrl.value,
                    sourceAudioFile = loadedAudioFile,
                    title = title,
                    artist = artist.ifBlank { "Ministério de Louvor" },
                    musicalKey = musicalKey,
                    notes = notes,
                    modelName = modelName,
                    audioFormat = audioFormat,
                    onProgress = { fraction, description ->
                        val step = when {
                            fraction < 0.40f -> 1
                            fraction < 0.70f -> 2
                            fraction < 0.95f -> 3
                            else -> 4
                        }
                        val titleStep = when (step) {
                            1 -> "Decodificando Louvor"
                            2 -> "Isolando Instrumental e Voz"
                            3 -> "Separando Soprano, Contralto e Tenor"
                            else -> "Finalizando Faixas no Estúdio"
                        }
                        _processingState.value = _processingState.value.copy(
                            progress = fraction,
                            currentStep = step,
                            stepTitle = titleStep,
                            stepDescription = description
                        )
                    }
                )

                _processingState.value = _processingState.value.copy(
                    progress = 1.0f,
                    currentStep = 4,
                    stepTitle = "Separação Concluída com Sucesso!",
                    stepDescription = "Todas as faixas geradas e sincronizadas no Estúdio"
                )

                delay(600)
                openProjectInStudio(newProjectId)
            } catch (e: Exception) {
                _processingState.value = _processingState.value.copy(
                    isError = true,
                    errorMessage = e.localizedMessage ?: "Erro durante o processamento do louvor",
                    errorSuggestion = "Você pode separar diretamente no seu celular sem precisar de servidor.",
                    canRetryOnDevice = true
                )
            }
        }
    }

    fun retrySeparationOnDevice() {
        val file = lastLoadedFile
        if (file != null && file.exists()) {
            useOnDeviceSeparation.value = true
            startSeparation(
                title = lastTitle,
                artist = lastArtist,
                musicalKey = lastKey,
                notes = lastNotes,
                modelName = "IA Vocal Integrada no Celular",
                audioFormat = lastAudioFormat,
                loadedAudioFile = file,
                durationSeconds = 180
            )
        } else {
            _currentScreen.value = Screen.NEW_PROJECT
            emitToast("Por favor, selecione o arquivo novamente")
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
            if (file.exists() && file.length() > 0) {
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
        previewPlayer.release()
    }
}
