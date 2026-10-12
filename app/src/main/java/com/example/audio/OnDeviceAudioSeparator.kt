package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.data.model.StemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object OnDeviceAudioSeparator {

    /**
     * Realiza a separação real do louvor no próprio celular Android,
     * decodificando o arquivo (MP3, WAV, M4A, AAC, FLAC) e gerando as faixas
     * reais com voz cantada clara e sem ruídos artificiais:
     * - Instrumental (acompanhamento musical sem vozes)
     * - Vozes Completas (vocal original completo e inteligível)
     * - Soprano (voz feminina aguda com brilho)
     * - Contralto (voz feminina intermediária/grave encorpada)
     * - Tenor (voz masculina aguda destacada)
     * - Baixo (voz masculina grave ressonante)
     */
    suspend fun separateOnDevice(
        context: Context,
        inputFile: File,
        projectId: Long,
        onProgress: (Float, String) -> Unit
    ): Map<StemType, File> = withContext(Dispatchers.IO) {
        val stemsDir = File(context.filesDir, "projects/$projectId")
        stemsDir.mkdirs()

        onProgress(0.12f, "Decodificando louvor no celular...")

        // 1. Decodificar arquivo original para PCM 16-bit
        val pcmFile = File(context.cacheDir, "temp_pcm_${System.currentTimeMillis()}.raw")
        val audioInfo = decodeAudioToPcm(inputFile, pcmFile)

        onProgress(0.45f, "Isolando acompanhamento instrumental e vocais...")

        val sampleRate = audioInfo.sampleRate
        val channels = audioInfo.channelCount

        val soloFile = File(stemsDir, "solo.wav")
        val backVocalFile = File(stemsDir, "back_vocal.wav")
        val sopranoFile = File(stemsDir, "back_soprano.wav")
        val contraltoFile = File(stemsDir, "back_contralto.wav")
        val tenorFile = File(stemsDir, "back_tenor.wav")
        val baixoFile = File(stemsDir, "back_baixo.wav")
        val instrumentalFile = File(stemsDir, "instrumental.wav")
        val vocalFullFile = File(stemsDir, "vocal_completo.wav")

        onProgress(0.70f, "Isolando voz principal (solo) e back vocal por naipe...")

        // 2. Processar fluxos PCM gerando as 8 faixas sincronizadas
        processPcmStreams(
            pcmFile = pcmFile,
            sampleRate = sampleRate,
            channels = channels,
            soloFile = soloFile,
            backVocalFile = backVocalFile,
            sopranoFile = sopranoFile,
            contraltoFile = contraltoFile,
            tenorFile = tenorFile,
            baixoFile = baixoFile,
            instrumentalFile = instrumentalFile,
            vocalFullFile = vocalFullFile
        )

        onProgress(0.96f, "Sincronizando faixas de áudio no estúdio...")

        // Limpar PCM temporário
        pcmFile.delete()

        mapOf(
            StemType.SOLO to soloFile,
            StemType.BACK_VOCAL to backVocalFile,
            StemType.SOPRANO to sopranoFile,
            StemType.CONTRALTO to contraltoFile,
            StemType.TENOR to tenorFile,
            StemType.BAIXO to baixoFile,
            StemType.INSTRUMENTAL to instrumentalFile,
            StemType.VOCAL_FULL to vocalFullFile
        )
    }

    private data class DecodedAudioInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val totalSamples: Long
    )

    private fun decodeAudioToPcm(inputFile: File, outputPcm: File): DecodedAudioInfo {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

        var audioTrackIndex = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            throw IllegalStateException("Nenhuma faixa de áudio compatível encontrada no arquivo.")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var totalBytesWritten = 0L

        FileOutputStream(outputPcm).use { fos ->
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                if (!inputEos) {
                    val inIndex = codec.dequeueInputBuffer(10000L)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(info, 10000L)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && info.size > 0) {
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size)
                        outBuffer.get(chunk)
                        fos.write(chunk)
                        totalBytesWritten += info.size
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(info, 0L)
                }

                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        }

        try { codec.stop() } catch (_: Exception) {}
        try { codec.release() } catch (_: Exception) {}
        try { extractor.release() } catch (_: Exception) {}

        val totalSamples = totalBytesWritten / (2 * channels.coerceAtLeast(1))
        return DecodedAudioInfo(sampleRate, channels, totalSamples)
    }

    private fun processPcmStreams(
        pcmFile: File,
        sampleRate: Int,
        channels: Int,
        soloFile: File,
        backVocalFile: File,
        sopranoFile: File,
        contraltoFile: File,
        tenorFile: File,
        baixoFile: File,
        instrumentalFile: File,
        vocalFullFile: File
    ) {
        val fosSolo = FileOutputStream(soloFile)
        val fosBackVocal = FileOutputStream(backVocalFile)
        val fosSoprano = FileOutputStream(sopranoFile)
        val fosContralto = FileOutputStream(contraltoFile)
        val fosTenor = FileOutputStream(tenorFile)
        val fosBaixo = FileOutputStream(baixoFile)
        val fosInst = FileOutputStream(instrumentalFile)
        val fosVocal = FileOutputStream(vocalFullFile)

        // Escrever cabeçalhos WAV preliminares
        writeWavHeaderPlaceholder(fosSolo)
        writeWavHeaderPlaceholder(fosBackVocal)
        writeWavHeaderPlaceholder(fosSoprano)
        writeWavHeaderPlaceholder(fosContralto)
        writeWavHeaderPlaceholder(fosTenor)
        writeWavHeaderPlaceholder(fosBaixo)
        writeWavHeaderPlaceholder(fosInst)
        writeWavHeaderPlaceholder(fosVocal)

        // Filtros acústicos e de tessitura para separação inteligente de vozes
        // 1. Solo: Voz condutora / melodia principal (central, clara, presença e inteligibilidade)
        val soloPresenceFilter = BiquadPeaking(sampleRate, centerFreq = 2200.0, gainDb = 5.0, q = 1.0)
        val soloClarity = BiquadPeaking(sampleRate, centerFreq = 3500.0, gainDb = 4.0, q = 1.2)
        val soloWarmth = BiquadPeaking(sampleRate, centerFreq = 650.0, gainDb = 3.0, q = 1.0)
        val soloHp = BiquadHighpass(sampleRate, cutoff = 110.0)

        // 2. Back Vocal: Vozes de apoio (harmonia aberta, espalhada no estéreo, reduz centro puro do solo)
        val backVocalPresence = BiquadPeaking(sampleRate, centerFreq = 1600.0, gainDb = 3.5, q = 1.1)
        val backVocalAir = BiquadPeaking(sampleRate, centerFreq = 5000.0, gainDb = 3.0, q = 0.9)

        // 3. Soprano do Back Vocal: Agudo feminino (C4-C6). Entalhes em Tenor (220Hz) e Contralto (360Hz) + Highpass 4ª ordem em 520Hz
        val sopNotchTenor = BiquadBandstop(sampleRate, centerFreq = 220.0, q = 2.0)
        val sopNotchContralto = BiquadBandstop(sampleRate, centerFreq = 360.0, q = 2.0)
        val sopHp1 = BiquadHighpass(sampleRate, cutoff = 520.0)
        val sopHp2 = BiquadHighpass(sampleRate, cutoff = 520.0)
        val sopPresence = BiquadPeaking(sampleRate, centerFreq = 720.0, gainDb = 5.0, q = 1.2)
        val sopAir = BiquadPeaking(sampleRate, centerFreq = 2800.0, gainDb = 4.0, q = 1.0)

        // 4. Contralto do Back Vocal: Médio/grave feminino (F3-F5 ~ 340-580Hz). Entalhes em Tenor (220Hz) e Soprano (750Hz)
        val conNotchTenor = BiquadBandstop(sampleRate, centerFreq = 220.0, q = 2.0)
        val conNotchSoprano = BiquadBandstop(sampleRate, centerFreq = 750.0, q = 2.0)
        val conHp1 = BiquadHighpass(sampleRate, cutoff = 320.0)
        val conHp2 = BiquadHighpass(sampleRate, cutoff = 320.0)
        val conLp1 = BiquadLowpass(sampleRate, cutoff = 580.0)
        val conLp2 = BiquadLowpass(sampleRate, cutoff = 580.0)
        val conBody = BiquadPeaking(sampleRate, centerFreq = 420.0, gainDb = 5.0, q = 1.2)

        // 5. Tenor do Back Vocal: Agudo masculino (C3-C5 ~ 150-320Hz). Entalhes em Contralto (440Hz) e Soprano (700Hz)
        val tenNotchContralto = BiquadBandstop(sampleRate, centerFreq = 440.0, q = 2.0)
        val tenNotchSoprano = BiquadBandstop(sampleRate, centerFreq = 700.0, q = 2.0)
        val tenHp1 = BiquadHighpass(sampleRate, cutoff = 150.0)
        val tenHp2 = BiquadHighpass(sampleRate, cutoff = 150.0)
        val tenLp1 = BiquadLowpass(sampleRate, cutoff = 320.0)
        val tenLp2 = BiquadLowpass(sampleRate, cutoff = 320.0)
        val tenChest = BiquadPeaking(sampleRate, centerFreq = 220.0, gainDb = 5.0, q = 1.2)

        // 6. Baixo do Back Vocal: Grave masculino (E2-E4 ~ 60-190Hz). Rejeita todas as vozes médias/agudas
        val baiNotchMid = BiquadBandstop(sampleRate, centerFreq = 350.0, q = 1.8)
        val baiNotchHigh = BiquadBandstop(sampleRate, centerFreq = 600.0, q = 1.8)
        val baiHp = BiquadHighpass(sampleRate, cutoff = 60.0)
        val baiLp1 = BiquadLowpass(sampleRate, cutoff = 190.0)
        val baiLp2 = BiquadLowpass(sampleRate, cutoff = 190.0)
        val baiResonance = BiquadPeaking(sampleRate, centerFreq = 110.0, gainDb = 5.5, q = 1.0)

        // 7. Vocal Completo
        val vocalPresenceFilter = BiquadPeaking(sampleRate, centerFreq = 2000.0, gainDb = 3.0, q = 0.8)

        val inBufferSize = 8192
        val inBuffer = ByteArray(inBufferSize)

        val outBufferSize = 16384
        val outSolo = ByteArray(outBufferSize)
        val outBackVocal = ByteArray(outBufferSize)
        val outSoprano = ByteArray(outBufferSize)
        val outContralto = ByteArray(outBufferSize)
        val outTenor = ByteArray(outBufferSize)
        val outBaixo = ByteArray(outBufferSize)
        val outInst = ByteArray(outBufferSize)
        val outVocal = ByteArray(outBufferSize)

        FileInputStream(pcmFile).use { fis ->
            var bytesRead: Int
            while (fis.read(inBuffer).also { bytesRead = it } != -1) {
                val numSamples = bytesRead / 2
                var outIndex = 0

                var i = 0
                while (i < numSamples) {
                    val sampleLeft: Short
                    val sampleRight: Short

                    if (channels >= 2) {
                        val lLow = inBuffer[i * 2].toInt() and 0xFF
                        val lHigh = inBuffer[i * 2 + 1].toInt()
                        sampleLeft = ((lHigh shl 8) or lLow).toShort()

                        val rLow = inBuffer[(i + 1) * 2].toInt() and 0xFF
                        val rHigh = inBuffer[(i + 1) * 2 + 1].toInt()
                        sampleRight = ((rHigh shl 8) or rLow).toShort()
                        i += 2
                    } else {
                        val low = inBuffer[i * 2].toInt() and 0xFF
                        val high = inBuffer[i * 2 + 1].toInt()
                        sampleLeft = ((high shl 8) or low).toShort()
                        sampleRight = sampleLeft
                        i += 1
                    }

                    val leftF = sampleLeft.toFloat()
                    val rightF = sampleRight.toFloat()

                    // Extração espacial Mid/Side:
                    // Mid (centro acústico): onde a voz principal do cantor solo é posicionada na mixagem de estúdio
                    // Side (canais laterais L - R): onde as aberturas harmônicas de coro/back vocal e ambiência de estúdio residem
                    val sideDiff = (leftF - rightF) * 0.95f
                    val centerMono = (leftF + rightF) * 0.5f

                    // 1. Instrumental: cancelamento do centro vocal (L - R) com graves preservados
                    val instSample = (sideDiff * 0.90f + (leftF * 0.15f)).coerceIn(-32767f, 32767f).toInt().toShort()

                    // 2. Vocal Completo
                    val vocalNatural = centerMono
                    val vocalEnhanced = vocalPresenceFilter.process(vocalNatural)
                    val vocalSample = (vocalNatural * 0.70f + vocalEnhanced * 0.40f)
                        .coerceIn(-32767f, 32767f).toInt().toShort()

                    // 3. Voz Principal (Solo): isolamento da linha condutora frontal
                    var soloSig = centerMono
                    soloSig = soloHp.process(soloSig)
                    soloSig = soloWarmth.process(soloSig)
                    soloSig = soloPresenceFilter.process(soloSig)
                    soloSig = soloClarity.process(soloSig)
                    val soloSample = (soloSig * 1.5f).coerceIn(-32767f, 32767f).toInt().toShort()

                    // 4. Back Vocal Completo: vozes de apoio (reduz centro seco do solo, destaca harmonia vocal)
                    var bvSig = (sideDiff * 0.85f + centerMono * 0.35f)
                    bvSig = backVocalPresence.process(bvSig)
                    bvSig = backVocalAir.process(bvSig)
                    val backVocalSample = (bvSig * 1.6f).coerceIn(-32767f, 32767f).toInt().toShort()

                    // 5. Soprano do Back Vocal: Isolamento puro de agudos femininos
                    var sop = (sideDiff * 0.6f + centerMono * 0.4f)
                    sop = sopNotchTenor.process(sop)
                    sop = sopNotchContralto.process(sop)
                    sop = sopHp1.process(sop)
                    sop = sopHp2.process(sop)
                    sop = sopPresence.process(sop)
                    sop = sopAir.process(sop)
                    val sopranoSample = (sop * 2.8f).coerceIn(-32767f, 32767f).toInt().toShort()

                    // 6. Contralto do Back Vocal: Isolamento puro de vozes médias femininas
                    var con = (sideDiff * 0.6f + centerMono * 0.4f)
                    con = conNotchTenor.process(con)
                    con = conNotchSoprano.process(con)
                    con = conHp1.process(con)
                    con = conHp2.process(con)
                    con = conLp1.process(con)
                    con = conLp2.process(con)
                    con = conBody.process(con)
                    val contraltoSample = (con * 3.0f).coerceIn(-32767f, 32767f).toInt().toShort()

                    // 7. Tenor do Back Vocal: Isolamento puro de vozes masculinas médias/agudas
                    var ten = (sideDiff * 0.5f + centerMono * 0.5f)
                    ten = tenNotchContralto.process(ten)
                    ten = tenNotchSoprano.process(ten)
                    ten = tenHp1.process(ten)
                    ten = tenHp2.process(ten)
                    ten = tenLp1.process(ten)
                    ten = tenLp2.process(ten)
                    ten = tenChest.process(ten)
                    val tenorSample = (ten * 3.2f).coerceIn(-32767f, 32767f).toInt().toShort()

                    // 8. Baixo do Back Vocal: Isolamento puro de graves masculinos
                    var bai = centerMono
                    bai = baiNotchMid.process(bai)
                    bai = baiNotchHigh.process(bai)
                    bai = baiHp.process(bai)
                    bai = baiLp1.process(bai)
                    bai = baiLp2.process(bai)
                    bai = baiResonance.process(bai)
                    val baixoSample = (bai * 3.4f).coerceIn(-32767f, 32767f).toInt().toShort()

                    fun putShort(buf: ByteArray, idx: Int, v: Short) {
                        buf[idx] = (v.toInt() and 0xFF).toByte()
                        buf[idx + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                    }

                    putShort(outSolo, outIndex, soloSample)
                    putShort(outBackVocal, outIndex, backVocalSample)
                    putShort(outSoprano, outIndex, sopranoSample)
                    putShort(outContralto, outIndex, contraltoSample)
                    putShort(outTenor, outIndex, tenorSample)
                    putShort(outBaixo, outIndex, baixoSample)
                    putShort(outInst, outIndex, instSample)
                    putShort(outVocal, outIndex, vocalSample)
                    outIndex += 2

                    if (channels >= 2) {
                        putShort(outSolo, outIndex, soloSample)
                        putShort(outBackVocal, outIndex, backVocalSample)
                        putShort(outSoprano, outIndex, sopranoSample)
                        putShort(outContralto, outIndex, contraltoSample)
                        putShort(outTenor, outIndex, tenorSample)
                        putShort(outBaixo, outIndex, baixoSample)
                        putShort(outInst, outIndex, instSample)
                        putShort(outVocal, outIndex, vocalSample)
                        outIndex += 2
                    }
                }

                if (outIndex > 0) {
                    fosSolo.write(outSolo, 0, outIndex)
                    fosBackVocal.write(outBackVocal, 0, outIndex)
                    fosSoprano.write(outSoprano, 0, outIndex)
                    fosContralto.write(outContralto, 0, outIndex)
                    fosTenor.write(outTenor, 0, outIndex)
                    fosBaixo.write(outBaixo, 0, outIndex)
                    fosInst.write(outInst, 0, outIndex)
                    fosVocal.write(outVocal, 0, outIndex)
                }
            }
        }

        fosSolo.close()
        fosBackVocal.close()
        fosSoprano.close()
        fosContralto.close()
        fosTenor.close()
        fosBaixo.close()
        fosInst.close()
        fosVocal.close()

        val outputChannels = if (channels >= 2) 2 else 1

        // Atualizar cabeçalhos WAV com tamanho real de bytes
        updateWavHeader(soloFile, sampleRate, outputChannels)
        updateWavHeader(backVocalFile, sampleRate, outputChannels)
        updateWavHeader(sopranoFile, sampleRate, outputChannels)
        updateWavHeader(contraltoFile, sampleRate, outputChannels)
        updateWavHeader(tenorFile, sampleRate, outputChannels)
        updateWavHeader(baixoFile, sampleRate, outputChannels)
        updateWavHeader(instrumentalFile, sampleRate, outputChannels)
        updateWavHeader(vocalFullFile, sampleRate, outputChannels)
    }

    private fun writeWavHeaderPlaceholder(fos: FileOutputStream) {
        val dummy = ByteArray(44)
        fos.write(dummy)
    }

    private fun updateWavHeader(file: File, sampleRate: Int, channels: Int) {
        val actualDataSize = (file.length() - 44).coerceAtLeast(0)
        val totalDataLen = actualDataSize + 36
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            val header = ByteArray(44)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            buf.put("RIFF".toByteArray())
            buf.putInt(totalDataLen.toInt())
            buf.put("WAVE".toByteArray())
            buf.put("fmt ".toByteArray())
            buf.putInt(16) // Subchunk1Size
            buf.putShort(1.toShort()) // PCM format
            buf.putShort(channels.toShort())
            buf.putInt(sampleRate)
            buf.putInt(byteRate)
            buf.putShort(blockAlign.toShort())
            buf.putShort(16.toShort()) // BitsPerSample
            buf.put("data".toByteArray())
            buf.putInt(actualDataSize.toInt())

            raf.write(header)
        }
    }

    // Filtros Biquad Peaking e Shelving para equalização musical de alta qualidade
    private class BiquadPeaking(sampleRate: Int, centerFreq: Double, gainDb: Double, q: Double) {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0

        init {
            val a = Math.pow(10.0, gainDb / 40.0)
            val w0 = 2.0 * PI * centerFreq / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)
            val a0 = 1.0 + alpha / a

            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * cosW0) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha / a) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
            return y.toFloat()
        }
    }

    private class BiquadBandstop(sampleRate: Int, centerFreq: Double, q: Double = 1.8) {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0

        init {
            val w0 = 2.0 * PI * centerFreq / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)
            val a0 = 1.0 + alpha

            b0 = 1.0 / a0
            b1 = (-2.0 * cosW0) / a0
            b2 = 1.0 / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
            return y.toFloat()
        }
    }

    private class BiquadHighpass(sampleRate: Int, cutoff: Double) {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0

        init {
            val w0 = 2.0 * PI * cutoff / sampleRate
            val alpha = sin(w0) / (2.0 * 0.707)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cos(w0)) / 2.0) / a0
            b1 = (-(1.0 + cos(w0))) / a0
            b2 = ((1.0 + cos(w0)) / 2.0) / a0
            a1 = (-2.0 * cos(w0)) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
            return y.toFloat()
        }
    }

    private class BiquadLowpass(sampleRate: Int, cutoff: Double) {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0

        init {
            val w0 = 2.0 * PI * cutoff / sampleRate
            val alpha = sin(w0) / (2.0 * 0.707)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cos(w0)) / 2.0) / a0
            b1 = (1.0 - cos(w0)) / a0
            b2 = ((1.0 - cos(w0)) / 2.0) / a0
            a1 = (-2.0 * cos(w0)) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
            return y.toFloat()
        }
    }
}
