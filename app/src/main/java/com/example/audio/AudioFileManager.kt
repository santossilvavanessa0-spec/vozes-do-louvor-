package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

data class LoadedAudioInfo(
    val file: File,
    val fileName: String,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val durationMs: Long,
    val formattedDuration: String,
    val mimeType: String?,
    val detectedTitle: String?,
    val detectedArtist: String?,
    val detectedKey: KeyDetectionResult? = null
)

sealed class AudioLoadState {
    object Idle : AudioLoadState()
    data class Loading(val progress: Float, val statusMessage: String) : AudioLoadState()
    data class Success(val audioInfo: LoadedAudioInfo) : AudioLoadState()
    data class Error(val errorMessage: String, val suggestion: String) : AudioLoadState()
}

object AudioFileManager {

    private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "flac", "ogg")

    suspend fun copyAndValidateAudio(
        context: Context,
        uri: Uri,
        onProgress: (Float, String) -> Unit
    ): Result<LoadedAudioInfo> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f, "Acessando arquivo no dispositivo...")

            // 1. Query metadata from ContentResolver
            var displayName = "louvor_audio.mp3"
            var estimatedSize: Long = -1

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex != -1) {
                        estimatedSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Fallback for display name if query returned null
            if (displayName.isBlank() || displayName == "louvor_audio.mp3") {
                uri.lastPathSegment?.let { segment ->
                    val clean = segment.substringAfterLast("/")
                    if (clean.isNotBlank()) displayName = clean
                }
            }

            // Check file extension
            val extension = displayName.substringAfterLast(".", "").lowercase(Locale.ROOT)
            val mimeType = context.contentResolver.getType(uri)

            val isExtensionValid = extension in SUPPORTED_EXTENSIONS
            val isMimeValid = mimeType != null && (
                mimeType.startsWith("audio/") ||
                mimeType == "application/ogg" ||
                mimeType == "application/x-flac"
            )

            if (!isExtensionValid && !isMimeValid) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Formato não suportado. Por favor, selecione um arquivo MP3, WAV, M4A, AAC ou FLAC."
                    )
                )
            }

            onProgress(0.15f, "Carregando fluxo de áudio...")

            // 2. Open InputStream and copy into cache
            val targetDir = File(context.cacheDir, "imported_audios")
            targetDir.mkdirs()

            // Ensure unique sanitized filename
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(targetDir, "${System.currentTimeMillis()}_$safeName")

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Não foi possível ler o arquivo. Verifique se o arquivo está disponível.")
                )

            var bytesCopied: Long = 0
            val buffer = ByteArray(16384)

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead

                        if (estimatedSize > 0) {
                            val fraction = (bytesCopied.toFloat() / estimatedSize).coerceIn(0.15f, 0.85f)
                            onProgress(fraction, "Carregando áudio (${formatBytes(bytesCopied)})...")
                        } else {
                            onProgress(0.5f, "Carregando áudio (${formatBytes(bytesCopied)})...")
                        }
                    }
                    output.flush()
                }
            }

            onProgress(0.88f, "Validando integridade do áudio...")

            // 3. Size validation
            if (bytesCopied < 1024) {
                targetFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("O arquivo selecionado está vazio ou corrompido (tamanho inferior a 1 KB).")
                )
            }

            if (bytesCopied > 100 * 1024 * 1024) { // 100 MB limit
                targetFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("O arquivo é muito grande (${formatBytes(bytesCopied)}). O limite máximo suportado é 100 MB.")
                )
            }

            // 4. Decode audio metadata & test integrity
            var durationMs: Long = 0
            var detectedTitle: String? = null
            var detectedArtist: String? = null

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(targetFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L

                detectedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                detectedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)

                if (durationMs <= 0) {
                    targetFile.delete()
                    return@withContext Result.failure(
                        IllegalStateException("Não foi possível decodificar a faixa de áudio. O arquivo pode estar corrompido ou protegido por DRM.")
                    )
                }
            } catch (e: Exception) {
                targetFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Falha ao analisar a estrutura de áudio: ${e.localizedMessage}")
                )
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            onProgress(0.94f, "Identificando tom da música automaticamente...")
            val detectedKey = MusicalKeyDetector.detectKey(targetFile, displayName)

            onProgress(1.0f, "Música carregada com sucesso!")

            val info = LoadedAudioInfo(
                file = targetFile,
                fileName = displayName,
                fileSizeBytes = bytesCopied,
                formattedSize = formatBytes(bytesCopied),
                durationMs = durationMs,
                formattedDuration = formatTime(durationMs),
                mimeType = mimeType ?: "audio/$extension",
                detectedTitle = detectedTitle?.takeIf { it.isNotBlank() },
                detectedArtist = detectedArtist?.takeIf { it.isNotBlank() },
                detectedKey = detectedKey
            )

            Result.success(info)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }
}
