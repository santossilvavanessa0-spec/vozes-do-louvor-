package com.example.data.repository

import android.content.Context
import com.example.audio.WavAudioGenerator
import com.example.data.local.PraiseDao
import com.example.data.model.PraiseProject
import com.example.data.model.ProjectWithStems
import com.example.data.model.StemTrack
import com.example.data.model.StemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PraiseRepository(
    private val context: Context,
    private val praiseDao: PraiseDao
) {

    val allProjects: Flow<List<PraiseProject>> = praiseDao.getAllProjects()

    fun getProjectWithStems(projectId: Long): Flow<ProjectWithStems?> =
        praiseDao.getProjectWithStems(projectId)

    suspend fun createProjectWithStems(
        title: String,
        artist: String,
        musicalKey: String,
        notes: String,
        modelName: String,
        audioFormat: String
    ): Long = withContext(Dispatchers.IO) {
        val project = PraiseProject(
            title = title,
            artist = artist,
            musicalKey = musicalKey,
            notes = notes,
            durationSeconds = 18,
            selectedModel = modelName,
            audioFormat = audioFormat,
            isSample = false
        )

        val projectId = praiseDao.insertProject(project)

        // Generate the real audio stems for this project
        val stemFiles = WavAudioGenerator.generateStemsForProject(
            context = context,
            projectId = projectId,
            songTitle = title,
            musicalKey = musicalKey
        )

        val stems = listOf(
            StemTrack(
                projectId = projectId,
                type = StemType.SOPRANO,
                title = "Soprano",
                description = "Voz feminina aguda (C4 a C6)",
                audioFilePath = stemFiles[StemType.SOPRANO]?.absolutePath ?: "",
                frequencyRange = "260 Hz - 1050 Hz"
            ),
            StemTrack(
                projectId = projectId,
                type = StemType.CONTRALTO,
                title = "Contralto (Alto)",
                description = "Voz feminina média/grave (F3 a F5)",
                audioFilePath = stemFiles[StemType.CONTRALTO]?.absolutePath ?: "",
                frequencyRange = "175 Hz - 700 Hz"
            ),
            StemTrack(
                projectId = projectId,
                type = StemType.TENOR,
                title = "Tenor",
                description = "Voz masculina aguda (C3 a C5)",
                audioFilePath = stemFiles[StemType.TENOR]?.absolutePath ?: "",
                frequencyRange = "130 Hz - 500 Hz"
            ),
            StemTrack(
                projectId = projectId,
                type = StemType.BAIXO,
                title = "Baixo (Bass)",
                description = "Voz masculina grave (E2 a E4)",
                audioFilePath = stemFiles[StemType.BAIXO]?.absolutePath ?: "",
                frequencyRange = "80 Hz - 350 Hz"
            ),
            StemTrack(
                projectId = projectId,
                type = StemType.INSTRUMENTAL,
                title = "Instrumental",
                description = "Acompanhamento sem vozes (Piano, Pad, Acústico)",
                audioFilePath = stemFiles[StemType.INSTRUMENTAL]?.absolutePath ?: "",
                frequencyRange = "Full Spectrum"
            ),
            StemTrack(
                projectId = projectId,
                type = StemType.VOCAL_FULL,
                title = "Vozes Completas",
                description = "Coral original completo isolado sem instrumentos",
                audioFilePath = stemFiles[StemType.VOCAL_FULL]?.absolutePath ?: "",
                frequencyRange = "Full Vocal"
            )
        )

        praiseDao.insertStems(stems)
        projectId
    }

    suspend fun createZipExport(project: PraiseProject, stems: List<StemTrack>): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val zipFile = File(exportDir, "${safeTitle}_Vozes_do_Louvor_Stems.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            stems.forEach { stem ->
                val audioFile = File(stem.audioFilePath)
                if (audioFile.exists()) {
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
            // Seed a starter sample praise so user can test immediate playback
            createProjectWithStems(
                title = "Bondade de Deus",
                artist = "Isaías Saad / Bethel Music",
                musicalKey = "G",
                notes = "Ensaio do coral: Entrada em uníssono nos versos. No refrão, sopranos cantam a melodia principal, contraltos e tenores sustentam harmonia em terças e baixos na tônica.",
                modelName = "Demucs v4 Hybrid AI (Naipes Vocais)",
                audioFormat = "WAV (High Definition)"
            )
        }
    }
}
