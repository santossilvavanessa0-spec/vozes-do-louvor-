package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.audio.OnDeviceAudioSeparator
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
import kotlin.math.PI
import kotlin.math.sin

class SeparationServerException(message: String) : Exception(message)

class PraiseRepository(
    private val context: Context,
    private val praiseDao: PraiseDao
) {

    val allProjects: Flow<List<PraiseProject>> = praiseDao.getAllProjects()

    fun getProjectWithStems(projectId: Long): Flow<ProjectWithStems?> =
        praiseDao.getProjectWithStems(projectId)

    /**
     * Separação inteligente: Tenta o servidor remoto se estiver configurado e online.
     * Se o servidor não responder ou se o modo celular estiver ativo, processa
     * nativamente no dispositivo sem erros ou dependência de Docker.
     */
    suspend fun separateAudioSmart(
        preferOnDevice: Boolean,
        backendUrl: String,
        sourceAudioFile: File,
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        modelName: String,
        audioFormat: String,
        onProgress: (Float, String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        if (preferOnDevice) {
            return@withContext separateOnDevice(
                sourceAudioFile = sourceAudioFile,
                title = title,
                artist = artist,
                musicalKey = musicalKey,
                notes = notes,
                audioFormat = audioFormat,
                onProgress = onProgress
            )
        }

        // Tentar verificar o servidor remoto rapidamente com timeout curto
        val healthResult = SeparationApiClient.checkHealth(backendUrl)
        if (healthResult.isSuccess) {
            onProgress(0.20f, "Enviando louvor para o servidor Demucs...")
            return@withContext separateWithBackend(
                backendUrl = backendUrl,
                sourceAudioFile = sourceAudioFile,
                title = title,
                artist = artist,
                musicalKey = musicalKey,
                notes = notes,
                modelName = modelName,
                audioFormat = audioFormat
            )
        } else {
            // Servidor indisponível no celular: fallback automático e transparente no dispositivo
            onProgress(0.15f, "Servidor remoto não detectado. Processando diretamente no celular...")
            return@withContext separateOnDevice(
                sourceAudioFile = sourceAudioFile,
                title = title,
                artist = artist,
                musicalKey = musicalKey,
                notes = notes,
                audioFormat = audioFormat,
                onProgress = onProgress
            )
        }
    }

    /**
     * Separação 100% nativa no próprio celular Android.
     */
    suspend fun separateOnDevice(
        sourceAudioFile: File,
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        audioFormat: String,
        onProgress: (Float, String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        // Criar registro preliminar do projeto no banco Room
        val retriever = MediaMetadataRetriever()
        var durationSec = 180
        try {
            retriever.setDataSource(sourceAudioFile.absolutePath)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 180000L
            durationSec = (durMs / 1000).toInt().coerceAtLeast(10)
        } catch (_: Exception) {}
        finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val project = PraiseProject(
            title = title,
            artist = artist,
            musicalKey = musicalKey,
            notes = notes,
            durationSeconds = durationSec,
            originalFileName = sourceAudioFile.name,
            selectedModel = "IA Vocal Integrada no Celular",
            audioFormat = audioFormat,
            isSample = false
        )
        val projectId = praiseDao.insertProject(project)

        try {
            // Processar áudio real com OnDeviceAudioSeparator
            val stemsMap = OnDeviceAudioSeparator.separateOnDevice(
                context = context,
                inputFile = sourceAudioFile,
                projectId = projectId,
                onProgress = onProgress
            )

            val stemConfigs = listOf(
                Triple(StemType.SOPRANO, "Soprano", "Voz feminina aguda isolada (C4 a C6)"),
                Triple(StemType.CONTRALTO, "Contralto (Alto)", "Voz feminina média/grave isolada (F3 a F5)"),
                Triple(StemType.TENOR, "Tenor", "Voz masculina aguda isolada (C3 a C5)"),
                Triple(StemType.BAIXO, "Baixo (Bass)", "Voz masculina grave isolada (E2 a E4)"),
                Triple(StemType.INSTRUMENTAL, "Instrumental", "Acompanhamento musical sem as vozes"),
                Triple(StemType.VOCAL_FULL, "Vozes Completas", "Vocal original isolado completo")
            )

            val stemsToInsert = mutableListOf<StemTrack>()
            for ((type, label, desc) in stemConfigs) {
                val file = stemsMap[type] ?: continue
                if (!file.exists() || file.length() < 1024) continue

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
                        audioFilePath = file.absolutePath,
                        frequencyRange = freq
                    )
                )
            }

            praiseDao.insertStems(stemsToInsert)
            projectId
        } catch (e: Exception) {
            praiseDao.deleteProject(projectId)
            throw e
        }
    }

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
        val healthResult = SeparationApiClient.checkHealth(backendUrl)
        if (healthResult.isFailure) {
            throw SeparationServerException(
                "Servidor Demucs não encontrado em '$backendUrl'. " +
                "Inicie o backend com 'docker compose up' ou utilize o modo 'No Celular' em Ajustes."
            )
        }

        val sepResult = SeparationApiClient.separateAudio(backendUrl, sourceAudioFile, modelName)
        if (sepResult.isFailure) {
            throw SeparationServerException(
                "Falha ao processar com modelo Demucs: ${sepResult.exceptionOrNull()?.localizedMessage}"
            )
        }

        val sepResponse = sepResult.getOrThrow()
        val durationSec = sepResponse.durationSeconds.toInt().coerceAtLeast(10)

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
                throw IllegalStateException("Arquivo de áudio inválido: ${file.name}")
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
        val count = praiseDao.getProjectCount()
        if (count == 0) {
            val starterProject = PraiseProject(
                title = "Bondade de Deus",
                artist = "Isaías Saad",
                musicalKey = "G",
                notes = "Ensaio coral: Sopranos entram no refrão em C5. Contraltos e tenores sustentam terças abaixo.",
                durationSeconds = 180,
                originalFileName = "bondade_de_deus.mp3",
                selectedModel = "IA Vocal Integrada no Celular",
                audioFormat = "WAV (Alta Fidelidade)",
                isSample = true
            )
            val projectId = praiseDao.insertProject(starterProject)

            // Criar arquivos de áudio WAV reais para cada naipe do louvor de exemplo
            val sampleDir = File(context.filesDir, "projects/$projectId")
            sampleDir.mkdirs()

            val stemConfigs = listOf(
                Triple(StemType.SOPRANO, "Soprano", "260 Hz - 1050 Hz"),
                Triple(StemType.CONTRALTO, "Contralto (Alto)", "175 Hz - 700 Hz"),
                Triple(StemType.TENOR, "Tenor", "130 Hz - 500 Hz"),
                Triple(StemType.BAIXO, "Baixo (Bass)", "80 Hz - 350 Hz"),
                Triple(StemType.INSTRUMENTAL, "Instrumental", "Full Spectrum"),
                Triple(StemType.VOCAL_FULL, "Vozes Completas", "Full Vocal")
            )

            val starterStems = mutableListOf<StemTrack>()
            for ((type, label, freq) in stemConfigs) {
                val stemFile = File(sampleDir, "${type.name.lowercase()}.wav")
                generateSampleWav(stemFile, type)

                starterStems.add(
                    StemTrack(
                        projectId = projectId,
                        type = type,
                        title = label,
                        description = "Faixa isolada para ensaio de ${label}",
                        audioFilePath = stemFile.absolutePath,
                        frequencyRange = freq
                    )
                )
            }
            praiseDao.insertStems(starterStems)
        }
    }

    private fun generateSampleWav(file: File, type: StemType) {
        val sampleRate = 44100
        val durationSec = 15 // 15s preview loop
        val totalSamples = sampleRate * durationSec
        val fos = FileOutputStream(file)

        // Write header placeholder
        fos.write(ByteArray(44))

        // Harmonic frequencies for Sol Maior (G major chord progression: G - Em - C - D)
        val (baseFreq, harmonicFreq) = when (type) {
            StemType.SOPRANO -> Pair(392.00, 587.33) // G4, D5 (agudo)
            StemType.CONTRALTO -> Pair(329.63, 440.00) // E4, A4 (médio/grave feminino)
            StemType.TENOR -> Pair(261.63, 392.00) // C4, G4 (masculino agudo)
            StemType.BAIXO -> Pair(98.00, 146.83) // G2, D3 (grave)
            StemType.INSTRUMENTAL -> Pair(196.00, 293.66) // G3, D4 (base)
            StemType.VOCAL_FULL -> Pair(349.23, 523.25) // F4, C5
        }

        val buffer = ByteArray(4096)
        var bufIdx = 0

        for (n in 0 until totalSamples) {
            val t = n.toDouble() / sampleRate
            val vibrato = 1.0 + 0.02 * sin(2.0 * Math.PI * 5.0 * t)
            val envelope = (0.5 + 0.5 * sin(2.0 * Math.PI * 0.25 * t)).coerceIn(0.2, 1.0)

            val sampleVal = ((sin(2.0 * Math.PI * baseFreq * vibrato * t) * 0.6 +
                    sin(2.0 * Math.PI * harmonicFreq * t) * 0.4) * envelope * 16000.0).toInt().toShort()

            buffer[bufIdx] = (sampleVal.toInt() and 0xFF).toByte()
            buffer[bufIdx + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
            bufIdx += 2

            // Stereo channel
            buffer[bufIdx] = (sampleVal.toInt() and 0xFF).toByte()
            buffer[bufIdx + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
            bufIdx += 2

            if (bufIdx >= 4096) {
                fos.write(buffer, 0, bufIdx)
                bufIdx = 0
            }
        }
        if (bufIdx > 0) {
            fos.write(buffer, 0, bufIdx)
        }
        fos.close()

        val actualDataSize = (file.length() - 44).coerceAtLeast(0)
        val totalDataLen = actualDataSize + 36
        val raf = java.io.RandomAccessFile(file, "rw")
        raf.seek(0)
        val header = ByteArray(44)
        val bb = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(totalDataLen.toInt())
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1.toShort())
        bb.putShort(2.toShort()) // stereo
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2 * 2)
        bb.putShort(4.toShort())
        bb.putShort(16.toShort())
        bb.put("data".toByteArray())
        bb.putInt(actualDataSize.toInt())
        raf.write(header)
        raf.close()
    }
}
