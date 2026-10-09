package com.example.audio

import android.content.Context
import com.example.data.model.StemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object WavAudioGenerator {

    private const val SAMPLE_RATE = 22050
    private const val DURATION_SECONDS = 18 // Rehearsal loop phrase

    suspend fun generateStemsForProject(
        context: Context,
        projectId: Long,
        songTitle: String,
        musicalKey: String
    ): Map<StemType, File> = withContext(Dispatchers.IO) {
        val stemsDir = File(context.filesDir, "projects/$projectId")
        stemsDir.mkdirs()

        // Base frequency map based on Musical Key
        val baseFreq = when (musicalKey.uppercase().trim()) {
            "C" -> 261.63
            "C#", "DB" -> 277.18
            "D" -> 293.66
            "D#", "EB" -> 311.13
            "E" -> 329.63
            "F" -> 349.23
            "F#", "GB" -> 369.99
            "G" -> 392.00
            "G#", "AB" -> 415.30
            "A" -> 440.00
            "A#", "BB" -> 466.16
            "B" -> 493.88
            else -> 392.00 // Default G
        }

        val result = mutableMapOf<StemType, File>()

        // Generate each stem file
        val sopranoFile = File(stemsDir, "soprano.wav")
        generateWav(sopranoFile, StemType.SOPRANO, baseFreq)
        result[StemType.SOPRANO] = sopranoFile

        val contraltoFile = File(stemsDir, "contralto.wav")
        generateWav(contraltoFile, StemType.CONTRALTO, baseFreq)
        result[StemType.CONTRALTO] = contraltoFile

        val tenorFile = File(stemsDir, "tenor.wav")
        generateWav(tenorFile, StemType.TENOR, baseFreq)
        result[StemType.TENOR] = tenorFile

        val baixoFile = File(stemsDir, "baixo.wav")
        generateWav(baixoFile, StemType.BAIXO, baseFreq)
        result[StemType.BAIXO] = baixoFile

        val instrumentalFile = File(stemsDir, "instrumental.wav")
        generateWav(instrumentalFile, StemType.INSTRUMENTAL, baseFreq)
        result[StemType.INSTRUMENTAL] = instrumentalFile

        val fullVocalFile = File(stemsDir, "vocal_completo.wav")
        generateWav(fullVocalFile, StemType.VOCAL_FULL, baseFreq)
        result[StemType.VOCAL_FULL] = fullVocalFile

        result
    }

    private fun generateWav(file: File, type: StemType, rootFreq: Double) {
        val totalSamples = SAMPLE_RATE * DURATION_SECONDS
        val bytesPerSample = 2 // 16-bit
        val dataSize = totalSamples * bytesPerSample

        FileOutputStream(file).use { fos ->
            // Write temporary header (will update sizes at end)
            writeWavHeader(fos, dataSize, SAMPLE_RATE, 1, 16)

            val buffer = ByteArray(2048)
            var byteIndex = 0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE

                // Musical Rehearsal Chord Progressions: I -> vi -> IV -> V (Praise worship classic progression)
                // Cycle every 4.5 seconds
                val phrase = (t % 4.5) / 4.5
                val step = (t / 4.5).toInt() % 4
                // semitone offset for chords: 0 (I), 9 (vi), 5 (IV), 7 (V)
                val chordOffset = when (step) {
                    0 -> 0.0
                    1 -> -3.0 // vi
                    2 -> 5.0  // IV
                    3 -> 7.0  // V
                    else -> 0.0
                }

                val sampleVal: Short = when (type) {
                    StemType.SOPRANO -> {
                        // High melodic line with gentle vibrato and choir formant
                        val noteFreq = rootFreq * Math.pow(2.0, (chordOffset + 12.0) / 12.0)
                        val vibrato = 1.0 + 0.015 * sin(2.0 * PI * 5.2 * t)
                        val f = noteFreq * vibrato
                        val wave = 0.65 * sin(2.0 * PI * f * t) +
                                   0.25 * sin(4.0 * PI * f * t) +
                                   0.10 * sin(6.0 * PI * f * t)
                        val envelope = smoothEnvelope(phrase)
                        (wave * envelope * 24000).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    StemType.CONTRALTO -> {
                        // Harmony 3rd or 4th below soprano with warm rich timbre
                        val noteFreq = rootFreq * Math.pow(2.0, (chordOffset + 7.0) / 12.0) // 5th/3rd harmony
                        val vibrato = 1.0 + 0.012 * sin(2.0 * PI * 5.0 * t)
                        val f = noteFreq * vibrato
                        val wave = 0.60 * sin(2.0 * PI * f * t) +
                                   0.30 * sin(3.0 * PI * f * t) +
                                   0.10 * sin(5.0 * PI * f * t)
                        val envelope = smoothEnvelope(phrase)
                        (wave * envelope * 22000).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    StemType.TENOR -> {
                        // Male higher octave / harmonic foundation
                        val noteFreq = rootFreq * Math.pow(2.0, (chordOffset) / 12.0)
                        val vibrato = 1.0 + 0.01 * sin(2.0 * PI * 4.8 * t)
                        val f = noteFreq * vibrato
                        val wave = 0.55 * sin(2.0 * PI * f * t) +
                                   0.35 * sin(2.0 * PI * f * 2 * t) +
                                   0.10 * sin(2.0 * PI * f * 3 * t)
                        val envelope = smoothEnvelope(phrase)
                        (wave * envelope * 23000).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    StemType.BAIXO -> {
                        // Deep male foundation (Bass root note octave down)
                        val noteFreq = (rootFreq / 2.0) * Math.pow(2.0, chordOffset / 12.0)
                        val wave = 0.70 * sin(2.0 * PI * noteFreq * t) +
                                   0.25 * sin(4.0 * PI * noteFreq * t) +
                                   0.05 * sin(6.0 * PI * noteFreq * t)
                        val envelope = smoothEnvelope(phrase)
                        (wave * envelope * 25000).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    StemType.INSTRUMENTAL -> {
                        // Praise Piano & Pad chord progression
                        val chordRoot = rootFreq * Math.pow(2.0, chordOffset / 12.0)
                        val chordThird = chordRoot * Math.pow(2.0, (if (step == 1) 3.0 else 4.0) / 12.0)
                        val chordFifth = chordRoot * Math.pow(2.0, 7.0 / 12.0)
                        val bass = (rootFreq / 2.0) * Math.pow(2.0, chordOffset / 12.0)

                        val chord = 0.30 * sin(2.0 * PI * chordRoot * t) +
                                    0.25 * sin(2.0 * PI * chordThird * t) +
                                    0.25 * sin(2.0 * PI * chordFifth * t) +
                                    0.20 * sin(2.0 * PI * bass * t)
                        // Acoustic rhythm strum pulsation
                        val pulse = 0.75 + 0.25 * sin(2.0 * PI * 2.0 * t)
                        (chord * pulse * 20000).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    StemType.VOCAL_FULL -> {
                        // Blend of Soprano, Contralto, Tenor, Baixo
                        val sFreq = rootFreq * Math.pow(2.0, (chordOffset + 12.0) / 12.0)
                        val cFreq = rootFreq * Math.pow(2.0, (chordOffset + 7.0) / 12.0)
                        val tFreq = rootFreq * Math.pow(2.0, (chordOffset) / 12.0)
                        val bFreq = (rootFreq / 2.0) * Math.pow(2.0, chordOffset / 12.0)

                        val blend = 0.30 * sin(2.0 * PI * sFreq * t) +
                                    0.25 * sin(2.0 * PI * cFreq * t) +
                                    0.25 * sin(2.0 * PI * tFreq * t) +
                                    0.20 * sin(2.0 * PI * bFreq * t)
                        val envelope = smoothEnvelope(phrase)
                        (blend * envelope * 24000).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                // Write little-endian 16-bit
                buffer[byteIndex++] = (sampleVal.toInt() and 0xFF).toByte()
                buffer[byteIndex++] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()

                if (byteIndex >= buffer.size) {
                    fos.write(buffer, 0, byteIndex)
                    byteIndex = 0
                }
            }

            if (byteIndex > 0) {
                fos.write(buffer, 0, byteIndex)
            }
        }
    }

    private fun smoothEnvelope(phase: Double): Double {
        // Attack 0.1s, sustain, gentle release at 0.9
        return when {
            phase < 0.1 -> phase / 0.1
            phase > 0.85 -> (1.0 - phase) / 0.15
            else -> 1.0
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // subchunk1size (16 for PCM)
        buf.putShort(1.toShort()) // audio format (1 = PCM)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen)

        out.write(header)
    }
}
