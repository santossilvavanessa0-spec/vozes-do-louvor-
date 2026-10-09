package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.data.local.PraiseDao
import com.example.data.model.PraiseProject
import com.example.data.model.ProjectWithStems
import com.example.data.model.StemTrack
import com.example.data.model.StemType
import com.example.network.SeparationApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SeparationServerException(message: String) : Exception(message)

class PraiseRepository(
    private val context: Context,
    private val praiseDao: PraiseDao
) {

    val allProjects: Flow<List<PraiseProject>> = praiseDao.getAllProjects()

    fun getProjectWithStems(projectId: Long): Flow<ProjectWithStems?> =
        praiseDao.getProjectWithStems(projectId)

    suspend fun separateWithBackend(
        backendUrl: String,
        sourceAudioFile: File,
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        modelName: String,
        audioFormat: String
    ): Long = withContext(Dispatchers.IO) {
        // 1. Check health of the backend server first
        val healthResult = SeparationApiClient.checkHealth(backendUrl)
        if (healthResult.isFailure) {
            throw SeparationServerException(
                "Servidor Demucs não encontrado em '$backendUrl'. " +
                "Inicie o backend com 'docker compose up' ou configure o IP correto em Configurações."
            )
        }

        // 2. Call separation API
        val sepResult = SeparationApiClient.separateAudio(backendUrl, sourceAudioFile, modelName)
        if (sepResult.isFailure) {
            throw SeparationServerException(
                "Falha ao processar com modelo Demucs: ${sepResult.exceptionOrNull()?.localizedMessage}"
            )
        }

        val sepResponse = sepResult.getOrThrow()
        val durationSec = sepResponse.durationSeconds.toInt().coerceAtLeast(10)

        // 3. Create project in DB
        val project = PraiseProject(
            title = title,
            artist = artist,
            musicalKey = musicalKey,
            notes = notes,
            durationSeconds = durationSec,
            originalFileName = sourceAudioFile.name,
            selectedModel = modelName,
            audioFormat = audioFormat,
            isSample = false
        )
        val projectId = praiseDao.insertProject(project)

        val projectDir = File(context.filesDir, "projects/$projectId")
        projectDir.mkdirs()

        // 4. Download and validate real stem files from server
        val cleanBaseUrl = if (backendUrl.endsWith("/")) backendUrl.dropLast(1) else backendUrl
        val stemConfigs = listOf(
            Triple(StemType.SOPRANO, "Soprano", sepResponse.stems.soprano),
            Triple(StemType.CONTRALTO, "Contralto (Alto)", sepResponse.stems.contralto),
            Triple(StemType.TENOR, "Tenor", sepResponse.stems.tenor),
            Triple(StemType.BAIXO, "Baixo (Bass)", sepResponse.stems.baixo),
            Triple(StemType.INSTRUMENTAL, "Instrumental", sepResponse.stems.instrumental),
            Triple(StemType.VOCAL_FULL, "Vozes Completas", sepResponse.stems.vocals)
        )

        val stemsToInsert = mutableListOf<StemTrack>()

        for ((type, label, relativeUrl) in stemConfigs) {
            if (relativeUrl.isNullOrBlank()) continue
            val fullUrl = if (relativeUrl.startsWith("http")) relativeUrl else "$cleanBaseUrl$relativeUrl"
            val destFile = File(projectDir, "${type.name.lowercase()}.wav")

            val dlResult = SeparationApiClient.downloadStem(fullUrl, destFile)
            if (dlResult.isFailure || !destFile.exists() || destFile.length() < 1024) {
                throw SeparationServerException("Erro ao baixar faixa separada de $label do servidor.")
            }

            // Validate audio decodability
            validateAudioIntegrity(destFile)

            val desc = when (type) {
                StemType.SOPRANO -> "Voz feminina aguda isolada (C4 a C6)"
                StemType.CONTRALTO -> "Voz feminina média/grave isolada (F3 a F5)"
                StemType.TENOR -> "Voz masculina aguda isolada (C3 a C5)"
                StemType.BAIXO -> "Voz masculina grave isolada (E2 a E4)"
                StemType.INSTRUMENTAL -> "Acompanhamento musical sem vozes"
                StemType.VOCAL_FULL -> "Vocal original isolado completo"
            }

            val freq = when (type) {
                StemType.SOPRANO -> "260 Hz - 1050 Hz"
                StemType.CONTRALTO -> "175 Hz - 700 Hz"
                StemType.TENOR -> "130 Hz - 500 Hz"
                StemType.BAIXO -> "80 Hz - 350 Hz"
                StemType.INSTRUMENTAL -> "Full Spectrum"
                StemType.VOCAL_FULL -> "Full Vocal"
            }

            stemsToInsert.add(
                StemTrack(
                    projectId = projectId,
                    type = type,
                    title = label,
                    description = desc,
                    audioFilePath = destFile.absolutePath,
                    frequencyRange = freq
                )
            )
        }

        if (stemsToInsert.isEmpty()) {
            praiseDao.deleteProject(projectId)
            throw SeparationServerException("Nenhuma faixa de áudio válida foi gerada pelo servidor.")
        }

        praiseDao.insertStems(stemsToInsert)
        projectId
    }

    private fun validateAudioIntegrity(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (dur == null || dur.toLongOrNull() ?: 0L <= 0) {
                throw IllegalStateException("Arquivo de áudio inválido ou sem duração detectável: ${file.name}")
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun createZipExport(project: PraiseProject, stems: List<StemTrack>): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val zipFile = File(exportDir, "${safeTitle}_Vozes_do_Louvor_Stems.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            stems.forEach { stem ->
                val audioFile = File(stem.audioFilePath)
                if (audioFile.exists() && audioFile.length() > 0) {
                    val entryName = "${safeTitle}_${stem.title.replace(" ", "_")}.wav"
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(audioFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        zipFile
    }

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        praiseDao.deleteProject(projectId)
        val dir = File(context.filesDir, "projects/$projectId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        // Only seeds initial sample metadata if DB is empty
        val count = praiseDao.getProjectCount()
        if (count == 0) {
            val starterProject = PraiseProject(
                title = "Bondade de Deus",
                artist = "Isaías Saad",
                musicalKey = "G",
                notes = "Ensaio coral: Sopranos entram no refrão em C5. Contraltos e tenores sustentam terças abaixo.",
                durationSeconds = 180,
                originalFileName = "bondade_de_deus.mp3",
                selectedModel = "Demucs v4 Hybrid AI",
                audioFormat = "WAV (Alta Fidelidade)",
                isSample = true
            )
            praiseDao.insertProject(starterProject)
        }
    }
}
