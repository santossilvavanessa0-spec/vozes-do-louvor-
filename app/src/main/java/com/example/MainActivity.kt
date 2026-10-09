package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppTopBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyPraisesScreen
import com.example.ui.screens.NewProjectScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ResultStudioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen
import com.example.viewmodel.UiEvent

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val projects by viewModel.projects.collectAsStateWithLifecycle()
                val selectedProjectWithStems by viewModel.selectedProjectWithStems.collectAsStateWithLifecycle()
                val playerState by viewModel.audioPlayer.uiState.collectAsStateWithLifecycle()
                val previewPlayerState by viewModel.previewPlayer.state.collectAsStateWithLifecycle()
                val processingState by viewModel.processingState.collectAsStateWithLifecycle()
                val audioLoadState by viewModel.audioLoadState.collectAsStateWithLifecycle()

                val backendUrl by viewModel.backendApiUrl.collectAsStateWithLifecycle()
                val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
                val preferredFormat by viewModel.preferredFormat.collectAsStateWithLifecycle()
                val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()

                // Listen to UI Events (Toast & Share)
                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is UiEvent.ShareFile -> {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        this@MainActivity,
                                        "${applicationContext.packageName}.fileprovider",
                                        event.file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = event.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(shareIntent, event.title))
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Erro ao compartilhar arquivo: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }

                // BackHandler implementations
                BackHandler(enabled = currentScreen != Screen.HOME) {
                    when (currentScreen) {
                        Screen.NEW_PROJECT -> viewModel.navigateTo(Screen.HOME)
                        Screen.PROCESSING -> viewModel.cancelProcessing()
                        Screen.RESULT_STUDIO -> viewModel.navigateTo(Screen.MY_PRAISES)
                        Screen.MY_PRAISES -> viewModel.navigateTo(Screen.HOME)
                        Screen.SETTINGS -> viewModel.navigateTo(Screen.HOME)
                        Screen.HOME -> Unit
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    topBar = {
                        val showBack = currentScreen != Screen.HOME && currentScreen != Screen.PROCESSING
                        val title = when (currentScreen) {
                            Screen.HOME -> "Vozes do Louvor AI"
                            Screen.NEW_PROJECT -> "Novo Louvor"
                            Screen.PROCESSING -> "Processando com Demucs"
                            Screen.RESULT_STUDIO -> "Estúdio de Ensaio"
                            Screen.MY_PRAISES -> "Meus Louvores"
                            Screen.SETTINGS -> "Configurações"
                        }
                        AppTopBar(
                            title = title,
                            showBackButton = showBack,
                            onBackClick = {
                                when (currentScreen) {
                                    Screen.RESULT_STUDIO -> viewModel.navigateTo(Screen.MY_PRAISES)
                                    else -> viewModel.navigateTo(Screen.HOME)
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (currentScreen != Screen.PROCESSING && currentScreen != Screen.RESULT_STUDIO) {
                            AppBottomNav(
                                currentScreen = currentScreen,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                        }
                    },
                    containerColor = DarkBackground
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            Screen.HOME -> {
                                HomeScreen(
                                    recentProjects = projects,
                                    onNavigate = { viewModel.navigateTo(it) },
                                    onOpenProject = { viewModel.openProjectInStudio(it) }
                                )
                            }
                            Screen.NEW_PROJECT -> {
                                NewProjectScreen(
                                    audioLoadState = audioLoadState,
                                    previewPlayer = viewModel.previewPlayer,
                                    previewPlayerState = previewPlayerState,
                                    onSelectAudioUri = { viewModel.loadAudioFromUri(it) },
                                    onResetAudioLoad = { viewModel.resetAudioLoad() },
                                    onSelectSample = { title, artist, key, notes ->
                                        // When selecting sample, inform user
                                        viewModel.resetAudioLoad()
                                    },
                                    onStartSeparation = { title, artist, key, notes, model, format, file, duration ->
                                        viewModel.startSeparation(
                                            title = title,
                                            artist = artist,
                                            musicalKey = key,
                                            notes = notes,
                                            modelName = model,
                                            audioFormat = format,
                                            loadedAudioFile = file,
                                            durationSeconds = duration
                                        )
                                    }
                                )
                            }
                            Screen.PROCESSING -> {
                                ProcessingScreen(
                                    state = processingState,
                                    onCancel = { viewModel.cancelProcessing() },
                                    onNavigateToSettings = { viewModel.navigateTo(Screen.SETTINGS) }
                                )
                            }
                            Screen.RESULT_STUDIO -> {
                                selectedProjectWithStems?.let { projectWithStems ->
                                    ResultStudioScreen(
                                        projectWithStems = projectWithStems,
                                        player = viewModel.audioPlayer,
                                        playerState = playerState,
                                        onDownloadSingleStem = { stem, title ->
                                            viewModel.exportSingleStem(stem, title)
                                        },
                                        onDownloadAllZip = { viewModel.exportAllStemsZip() },
                                        onBackToLibrary = { viewModel.navigateTo(Screen.MY_PRAISES) }
                                    )
                                } ?: run {
                                    viewModel.navigateTo(Screen.MY_PRAISES)
                                }
                            }
                            Screen.MY_PRAISES -> {
                                MyPraisesScreen(
                                    projects = projects,
                                    onOpenProject = { viewModel.openProjectInStudio(it) },
                                    onDeleteProject = { viewModel.deleteProject(it) },
                                    onNavigateToNew = { viewModel.navigateTo(Screen.NEW_PROJECT) }
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    backendUrl = backendUrl,
                                    apiKey = apiKey,
                                    preferredFormat = preferredFormat,
                                    backendStatus = backendStatus,
                                    onUpdateBackendUrl = { viewModel.backendApiUrl.value = it },
                                    onUpdateApiKey = { viewModel.apiKey.value = it },
                                    onUpdatePreferredFormat = { viewModel.preferredFormat.value = it },
                                    onTestBackend = { viewModel.testBackendConnection() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
