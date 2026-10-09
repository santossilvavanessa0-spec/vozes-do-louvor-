package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioLoadState
import com.example.audio.LoadedAudioInfo
import com.example.audio.PreviewAudioPlayer
import com.example.audio.PreviewPlayerState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGoldDark
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.StemBaixo
import com.example.ui.theme.StemContralto
import com.example.ui.theme.StemInstrumental
import com.example.ui.theme.StemSoprano
import com.example.ui.theme.StemTenor
import com.example.ui.theme.StemVocalFull
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.Locale

data class PraisePreset(
    val title: String,
    val artist: String,
    val key: String,
    val notes: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProjectScreen(
    audioLoadState: AudioLoadState,
    previewPlayer: PreviewAudioPlayer,
    previewPlayerState: PreviewPlayerState,
    onSelectAudioUri: (Uri) -> Unit,
    onResetAudioLoad: () -> Unit,
    onSelectSample: (title: String, artist: String, key: String, notes: String) -> Unit,
    onStartSeparation: (
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        modelName: String,
        audioFormat: String,
        loadedFile: File?,
        durationSeconds: Int
    ) -> Unit
) {
    var songTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf("G") }
    var rehearsalNotes by remember { mutableStateOf("") }

    // Stems selection
    var includeSoprano by remember { mutableStateOf(true) }
    var includeContralto by remember { mutableStateOf(true) }
    var includeTenor by remember { mutableStateOf(true) }
    var includeBaixo by remember { mutableStateOf(true) }
    var includeInstrumental by remember { mutableStateOf(true) }
    var includeFullVocal by remember { mutableStateOf(true) }

    // Launcher for selecting audio from device / Google Drive
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectAudioUri(uri)
        }
    }

    // When audio is successfully loaded, auto-fill ID3 metadata if fields are empty
    LaunchedEffect(audioLoadState) {
        if (audioLoadState is AudioLoadState.Success) {
            val info = audioLoadState.audioInfo
            if (songTitle.isBlank() && !info.detectedTitle.isNullOrBlank()) {
                songTitle = info.detectedTitle
            } else if (songTitle.isBlank()) {
                songTitle = info.fileName.substringBeforeLast(".").replace("_", " ")
            }
            if (artistName.isBlank() && !info.detectedArtist.isNullOrBlank()) {
                artistName = info.detectedArtist
            }
        }
    }

    val keysList = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    val presets = listOf(
        PraisePreset(
            title = "Bondade de Deus",
            artist = "Isaías Saad",
            key = "G",
            notes = "Coral: Sopranos entram no refrão em C5. Contraltos sustentam terças abaixo."
        ),
        PraisePreset(
            title = "Porque Ele Vive",
            artist = "Harpa Cristã / Tradicional",
            key = "A",
            notes = "Arranjo vocal clássico a 4 vozes para ensaio geral."
        ),
        PraisePreset(
            title = "A Bênção (The Blessing)",
            artist = "Gabriel Guedes & Julia Vitória",
            key = "B",
            notes = "Dinâmica de adoração: crescendo da ponte com tenores e baixos marcando."
        )
    )

    fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("new_project_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Carregar Louvor para Separação",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Envie uma música do seu celular ou do Google Drive para separar as vozes do coro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Section: Audio Upload States Card
        item {
            when (audioLoadState) {
                is AudioLoadState.Idle -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_selection_idle_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceElevated))
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Selecione o Áudio do Louvor",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Formatos suportados: MP3, WAV, M4A, AAC ou FLAC",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Button 1: Enviar música do celular
                            Button(
                                onClick = {
                                    audioPickerLauncher.launch(
                                        arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/m4a", "audio/aac", "audio/flac")
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("upload_from_device_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGold,
                                    contentColor = DarkBackground
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enviar música do celular",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Button 2: Selecionar do Google Drive / Arquivos
                            OutlinedButton(
                                onClick = {
                                    audioPickerLauncher.launch(
                                        arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/m4a", "audio/aac", "audio/flac")
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("select_from_drive_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPurpleLight)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = PrimaryPurpleLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Selecionar do Google Drive / Arquivos",
                                    color = PrimaryPurpleLight,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                is AudioLoadState.Loading -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_loading_progress_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(PrimaryPurple, AccentGold))
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = { audioLoadState.progress },
                                color = AccentGold,
                                trackColor = DarkSurfaceBorder,
                                modifier = Modifier.size(52.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Carregando e validando áudio...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = audioLoadState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentGold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            LinearProgressIndicator(
                                progress = { audioLoadState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = AccentGold,
                                trackColor = DarkSurface
                            )
                        }
                    }
                }

                is AudioLoadState.Success -> {
                    val info = audioLoadState.audioInfo
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_loaded_success_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(SuccessGreen, AccentGold))
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SuccessGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = info.fileName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Tamanho: ${info.formattedSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                            Text(text = "•", color = TextMuted)
                                            Text(
                                                text = "Duração: ${info.formattedDuration}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AccentGold
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = onResetAudioLoad,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Trocar música",
                                        tint = TextMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Integrated Pre-Listening Audio Player
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Pré-escuta do Louvor",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AccentGold
                                        )
                                        Text(
                                            text = "${formatMs(previewPlayerState.currentPositionMs)} / ${formatMs(previewPlayerState.durationMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Slider(
                                        value = previewPlayerState.currentPositionMs.toFloat(),
                                        onValueChange = { previewPlayer.seekTo(it.toInt()) },
                                        valueRange = 0f..previewPlayerState.durationMs.toFloat().coerceAtLeast(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AccentGold,
                                            activeTrackColor = AccentGold,
                                            inactiveTrackColor = DarkSurfaceBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { previewPlayer.togglePlayPause() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentGold,
                                                contentColor = DarkBackground
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (previewPlayerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (previewPlayerState.isPlaying) "Pausar" else "Ouvir",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (previewPlayerState.isPlaying) "Pausar" else "Ouvir Áudio",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is AudioLoadState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_load_error_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(ErrorRed, DarkSurfaceBorder))
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Não foi possível carregar o arquivo",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ErrorRed
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = audioLoadState.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = audioLoadState.suggestion,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onResetAudioLoad,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = DarkBackground)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tentar Novamente", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Section: Presets (Quick Rehearsal Testing)
        item {
            Column {
                Text(
                    text = "Ou teste com um louvor de demonstração:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = AccentGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    songTitle = preset.title
                                    artistName = preset.artist
                                    selectedKey = preset.key
                                    rehearsalNotes = preset.notes
                                    onSelectSample(preset.title, preset.artist, preset.key, preset.notes)
                                }
                                .testTag("preset_${preset.title.take(5)}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (songTitle == preset.title) DarkSurfaceElevated else DarkSurface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(
                                    if (songTitle == preset.title) listOf(AccentGold, PrimaryPurple)
                                    else listOf(DarkSurfaceBorder, DarkSurfaceBorder)
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (songTitle == preset.title) AccentGold else TextPrimary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tom ${preset.key}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Praise Info Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceBorder))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Identificação do Louvor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = songTitle,
                        onValueChange = { songTitle = it },
                        label = { Text("Nome do Louvor *") },
                        placeholder = { Text("Ex: Bondade de Deus, Porque Ele Vive...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_song_title"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = DarkSurfaceBorder,
                            focusedLabelColor = AccentGold,
                            unfocusedLabelColor = TextMuted
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = artistName,
                        onValueChange = { artistName = it },
                        label = { Text("Cantor / Ministério de Louvor") },
                        placeholder = { Text("Ex: Isaías Saad, Coral Kemuel, Fernandinho...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_artist_name"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = DarkSurfaceBorder,
                            focusedLabelColor = AccentGold,
                            unfocusedLabelColor = TextMuted
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tom Original da Música",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        keysList.forEach { key ->
                            FilterChip(
                                selected = selectedKey == key,
                                onClick = { selectedKey = key },
                                label = { Text(key) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold,
                                    selectedLabelColor = DarkBackground,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = rehearsalNotes,
                        onValueChange = { rehearsalNotes = it },
                        label = { Text("Observações para o Ensaio (Opcional)") },
                        placeholder = { Text("Ex: Entrada suave no verso 1; coro divide em 4 vozes na ponte...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_rehearsal_notes"),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = DarkSurfaceBorder,
                            focusedLabelColor = AccentGold,
                            unfocusedLabelColor = TextMuted
                        )
                    )
                }
            }
        }

        // Section: Vocal Stems to generate
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceBorder))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Faixas para Separação",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StemCheckboxItem(
                        title = "Soprano",
                        desc = "Voz feminina aguda (C4 a C6)",
                        color = StemSoprano,
                        checked = includeSoprano,
                        onCheckedChange = { includeSoprano = it }
                    )
                    StemCheckboxItem(
                        title = "Contralto (Alto)",
                        desc = "Voz feminina grave/média (F3 a F5)",
                        color = StemContralto,
                        checked = includeContralto,
                        onCheckedChange = { includeContralto = it }
                    )
                    StemCheckboxItem(
                        title = "Tenor",
                        desc = "Voz masculina aguda (C3 a C5)",
                        color = StemTenor,
                        checked = includeTenor,
                        onCheckedChange = { includeTenor = it }
                    )
                    StemCheckboxItem(
                        title = "Baixo (Bass)",
                        desc = "Voz masculina grave (E2 a E4)",
                        color = StemBaixo,
                        checked = includeBaixo,
                        onCheckedChange = { includeBaixo = it }
                    )
                    StemCheckboxItem(
                        title = "Instrumental",
                        desc = "Acompanhamento sem os vocais",
                        color = StemInstrumental,
                        checked = includeInstrumental,
                        onCheckedChange = { includeInstrumental = it }
                    )
                    StemCheckboxItem(
                        title = "Vozes Completas",
                        desc = "Vocal original completo sem instrumentos",
                        color = StemVocalFull,
                        checked = includeFullVocal,
                        onCheckedChange = { includeFullVocal = it }
                    )
                }
            }
        }

        // Section: Technical explanation / Disclaimer as required by prompt
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceBorder))
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Transparência Tecnológica na Separação",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AccentGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A IA utiliza análise formântica espectral combinada com modelos de separação de fontes (Demucs). Em louvores gravados ao vivo com coro misto, frequências podem se sobrepor; o sistema equaliza e isola o centro tonal de cada naipe vocal para garantir clareza no aprendizado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Section: Submit Action Button
        item {
            val loadedFile = (audioLoadState as? AudioLoadState.Success)?.audioInfo?.file
            val durationSec = ((audioLoadState as? AudioLoadState.Success)?.audioInfo?.durationMs?.div(1000)?.toInt()) ?: 18

            Button(
                onClick = {
                    val finalTitle = songTitle.ifBlank { "Louvor Sem Título" }
                    val finalArtist = artistName.ifBlank { "Ministério de Louvor" }
                    onStartSeparation(
                        finalTitle,
                        finalArtist,
                        selectedKey,
                        rehearsalNotes,
                        "Demucs v4 Hybrid AI (Naipes Vocais)",
                        "WAV (Alta Fidelidade)",
                        loadedFile,
                        durationSec
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("submit_separate_voices_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    contentColor = DarkBackground
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Separar Vozes com IA",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StemCheckboxItem(
    title: String,
    desc: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = color,
                uncheckedColor = DarkSurfaceBorder,
                checkmarkColor = DarkBackground
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}
