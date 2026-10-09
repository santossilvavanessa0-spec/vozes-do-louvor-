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
    val stepDescription: String = "Preparando conexão com o servidor de IA",
    val songTitle: String = "",
    val artist: String = "",
    val isError: Boolean = false,
    val errorMessage: String = "",
    val errorSuggestion: String = ""
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

    // Configurable Backend API URL (Default: http://10.0.2.2:8001 for emulator, http://localhost:8001 for local)
    val backendApiUrl = MutableStateFlow("http://10.0.2.2:8001")
    val apiKey = MutableStateFlow("")
    val preferredFormat = MutableStateFlow("WAV (Fidelidade Máxima)")
    val backendStatus = MutableStateFlow("Verificando servidor...")

    private var processingJob: Job? = null
    private var audioLoadJob: Job? = null

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
            backendStatus.value = "Conectado ($active)"
        } else {
            backendStatus.value = "Inacessível em ${backendApiUrl.value}"
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
                    emitToast("Servidor Demucs conectado com sucesso!")
                },
                onFailure = { error ->
                    val discovered = SeparationApiClient.discoverActiveServer(backendApiUrl.value)
                    if (discovered != null) {
                        backendApiUrl.value = discovered
                        backendStatus.value = "Conectado ($discovered)"
                        emitToast("Servidor Demucs localizado em $discovered!")
                    } else {
                        backendStatus.value = "Inacessível (${error.localizedMessage})"
                        emitToast("Não foi possível conectar: ${error.localizedMessage}")
                    }
                }
            )
        }
    }

    fun loadAudioFromUri(uri: Uri) {
        audioLoadJob?.cancel()
        previewPlayer.release()
        _audioLoadState.value = AudioLoadState.Loading(0.05f, "Iniciando leitura do arquivo...")

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
                    emitToast("Música carregada: ${info.fileName}")
                },
                onFailure = { error ->
                    _audioLoadState.value = AudioLoadState.Error(
                        errorMessage = error.localizedMessage ?: "Erro desconhecido ao carregar áudio",
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

        previewPlayer.pause()

        _processingState.value = ProcessingUiState(
            progress = 0.05f,
            currentStep = 1,
            stepTitle = "Verificando Servidor Demucs",
            stepDescription = "Conectando ao backend em ${backendApiUrl.value}...",
            songTitle = title,
            artist = artist.ifBlank { "Ministério de Louvor" },
            isError = false
        )
        _currentScreen.value = Screen.PROCESSING

        processingJob = viewModelScope.launch {
            try {
                // Check if configured server is active, or discover available port
                var targetUrl = backendApiUrl.value
                val healthCheck = SeparationApiClient.checkHealth(targetUrl)
                if (healthCheck.isFailure) {
                    val discovered = SeparationApiClient.discoverActiveServer(targetUrl)
                    if (discovered != null) {
                        targetUrl = discovered
                        backendApiUrl.value = discovered
                        backendStatus.value = "Conectado ($discovered)"
                    } else {
                        throw SeparationServerException(
                            "Servidor Demucs não encontrado em '${targetUrl}'. " +
                            "Certifique-se de que o backend está ativo ou configure o IP correto em Ajustes."
                        )
                    }
                }

                // Step 1: Upload
                _processingState.value = _processingState.value.copy(
                    progress = 0.15f,
                    currentStep = 1,
                    stepTitle = "Enviando Áudio para o Servidor",
                    stepDescription = "Fazendo upload de '${loadedAudioFile.name}' (${AudioFileManager.formatBytes(loadedAudioFile.length())})..."
                )

                // Step 2: Demucs Processing
                _processingState.value = _processingState.value.copy(
                    progress = 0.40f,
                    currentStep = 2,
                    stepTitle = "Demucs v4: Isolando Fontes Musicais",
                    stepDescription = "Separando instrumental da faixa vocal original..."
                )

                // Step 3: Vocal Tessituras
                _processingState.value = _processingState.value.copy(
                    progress = 0.70f,
                    currentStep = 3,
                    stepTitle = "IA Coral: Decomposição de Naipes",
                    stepDescription = "Isolando Soprano (agudo), Contralto (médio) e Tenor (masculino)..."
                )

                val newProjectId = repository.separateWithBackend(
                    backendUrl = targetUrl,
                    sourceAudioFile = loadedAudioFile,
                    title = title,
                    artist = artist.ifBlank { "Ministério de Louvor" },
                    musicalKey = musicalKey,
                    notes = notes,
                    modelName = modelName,
                    audioFormat = audioFormat
                )

                _processingState.value = _processingState.value.copy(
                    progress = 1.0f,
                    currentStep = 4,
                    stepTitle = "Separação Concluída com Sucesso!",
                    stepDescription = "Faixas reais baixadas e sincronizadas no Estúdio"
                )

                delay(600)
                openProjectInStudio(newProjectId)
            } catch (e: SeparationServerException) {
                _processingState.value = _processingState.value.copy(
                    isError = true,
                    errorMessage = e.message ?: "Falha na comunicação com o servidor de IA",
                    errorSuggestion = "Certifique-se de que o backend está executando. Se estiver usando emulador, use http://10.0.2.2:8001. Em celular físico, use o IP local do seu computador na mesma rede Wi-Fi."
                )
            } catch (e: Exception) {
                _processingState.value = _processingState.value.copy(
                    isError = true,
                    errorMessage = e.localizedMessage ?: "Erro inesperado durante a separação",
                    errorSuggestion = "Verifique o arquivo original e tente novamente."
                )
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
