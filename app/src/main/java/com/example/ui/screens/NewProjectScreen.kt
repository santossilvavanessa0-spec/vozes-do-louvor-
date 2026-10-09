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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGoldDark
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.StemBaixo
import com.example.ui.theme.StemContralto
import com.example.ui.theme.StemInstrumental
import com.example.ui.theme.StemSoprano
import com.example.ui.theme.StemTenor
import com.example.ui.theme.StemVocalFull
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

data class PraisePreset(
    val title: String,
    val artist: String,
    val key: String,
    val notes: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProjectScreen(
    onStartSeparation: (
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        modelName: String,
        audioFormat: String
    ) -> Unit
) {
    var songTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf("G") }
    var rehearsalNotes by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }

    // Models & format
    var selectedModel by remember { mutableStateOf("Demucs v4 Hybrid AI (Naipes Vocais)") }
    var selectedFormat by remember { mutableStateOf("WAV (Alta Fidelidade)") }

    // Stems selection
    var includeSoprano by remember { mutableStateOf(true) }
    var includeContralto by remember { mutableStateOf(true) }
    var includeTenor by remember { mutableStateOf(true) }
    var includeBaixo by remember { mutableStateOf(true) }
    var includeInstrumental by remember { mutableStateOf(true) }
    var includeFullVocal by remember { mutableStateOf(true) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAudioUri = uri
            selectedFileName = uri.lastPathSegment ?: "audio_louvor.mp3"
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
                    text = "Novo Projeto de Louvor",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Preencha os dados e envie o áudio para separação dos naipes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Section: File Upload Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audioPickerLauncher.launch("audio/*") }
                    .testTag("upload_audio_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        if (selectedFileName != null) listOf(AccentGold, PrimaryPurple)
                        else listOf(DarkSurfaceBorder, DarkSurfaceElevated)
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedFileName != null) AccentGold.copy(alpha = 0.2f)
                                else PrimaryPurple.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedFileName != null) Icons.Default.Audiotrack else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (selectedFileName != null) AccentGold else PrimaryPurpleLight,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedFileName != null) {
                        Text(
                            text = "Arquivo selecionado:",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGold
                        )
                        Text(
                            text = selectedFileName ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toque para trocar o arquivo",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    } else {
                        Text(
                            text = "Toque para enviar o áudio do louvor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Formatos aceitos: MP3, WAV, M4A ou FLAC",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tamanho recomendado: até 50 MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Section: Presets (Quick Rehearsal Testing)
        item {
            Column {
                Text(
                    text = "Ou escolha um louvor para teste imediato:",
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
                                    selectedFileName = "${preset.title.replace(" ", "_")}.mp3"
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
            Button(
                onClick = {
                    val finalTitle = songTitle.ifBlank { "Louvor Sem Título" }
                    val finalArtist = artistName.ifBlank { "Ministério de Louvor" }
                    onStartSeparation(
                        finalTitle,
                        finalArtist,
                        selectedKey,
                        rehearsalNotes,
                        selectedModel,
                        selectedFormat
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
    color: androidx.compose.ui.graphics.Color,
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
