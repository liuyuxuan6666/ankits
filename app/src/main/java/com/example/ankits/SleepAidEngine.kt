package com.example.ankits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File

class SleepAidEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHUNK_SIZE = 2048
        private const val BUFFER_MULTIPLIER = 3
        private const val MAX_WAV_DATA_SIZE = 50 * 1024 * 1024 // 50MB PCM data (~10 min)
    }

    enum class SoundType {
        WHITE_NOISE, PINK_NOISE, BROWN_NOISE,
        RAIN, OCEAN, FAN, SINGING_BOWL
    }

    inner class SoundChannel(val type: SoundType) {
        @Volatile var enabled: Boolean = false
        @Volatile var volume: Float = 0.5f
        @Volatile var fileBuffer: FloatArray = FloatArray(0)
        @Volatile var readIndex: Int = 0
    }

    val channels: List<SoundChannel> = SoundType.entries.map { SoundChannel(it) }

    private var audioTrack: AudioTrack? = null
    private var renderThread: Thread? = null

    @Volatile var masterVolume: Float = 1.0f
    @Volatile var fadeProgress: Float = -1f
    @Volatile var isPlaying: Boolean = false
        private set

    var onPlaybackStopped: (() -> Unit)? = null

    fun getChannel(type: SoundType): SoundChannel = channels.first { it.type == type }

    fun loadFile(type: SoundType, path: String): Boolean {
        val channel = getChannel(type)
        val samples = parseWavFile(path)
        if (samples.isEmpty()) return false
        channel.fileBuffer = samples
        channel.readIndex = 0
        return true
    }

    private fun parseWavFile(path: String): FloatArray {
        val bytes = try {
            File(path).readBytes()
        } catch (_: Exception) {
            return FloatArray(0)
        }
        if (bytes.size < 44) return FloatArray(0)
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()) return FloatArray(0)
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()) return FloatArray(0)

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = (bytes[offset + 4].toInt() and 0xFF) or
                    ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 7].toInt() and 0xFF) shl 24)

            if (chunkId == "data") {
                // Guard against OOM: reject files larger than 50MB of PCM data
                if (chunkSize > MAX_WAV_DATA_SIZE) return FloatArray(0)

                val sampleCount = chunkSize / 2
                val samples = FloatArray(sampleCount)
                var dataOffset = offset + 8
                for (i in 0 until sampleCount) {
                    if (dataOffset + 1 >= bytes.size) break
                    val lo = bytes[dataOffset].toInt() and 0xFF
                    val hi = bytes[dataOffset + 1].toInt() and 0xFF
                    val sample = (lo or (hi shl 8)).toShort().toFloat() / Short.MAX_VALUE
                    samples[i] = sample.coerceIn(-1f, 1f)
                    dataOffset += 2
                }
                return samples
            }
            offset += 8 + chunkSize
            if (chunkSize % 2 != 0) offset++
        }
        return FloatArray(0)
    }

    private fun initAudioTrack(): Boolean {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) return true

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, CHUNK_SIZE * BUFFER_MULTIPLIER * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        return audioTrack?.state == AudioTrack.STATE_INITIALIZED
    }

    fun start(): Boolean {
        if (isPlaying) return true
        if (!initAudioTrack()) return false

        isPlaying = true
        masterVolume = 1.0f
        fadeProgress = -1f

        audioTrack?.play()

        renderThread = Thread {
            renderLoop()
        }.apply {
            name = "SleepAidRender"
            priority = Thread.MAX_PRIORITY
            start()
        }

        return true
    }

    fun stop() {
        isPlaying = false
        try {
            renderThread?.join(500)
        } catch (_: InterruptedException) {}
        renderThread = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    fun startFadeOut() {
        fadeProgress = 0f
    }

    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun renderLoop() {
        val mixBuf = FloatArray(CHUNK_SIZE)
        val chanBuf = FloatArray(CHUNK_SIZE)

        while (isPlaying) {
            mixBuf.fill(0f)

            for (ch in channels) {
                if (!ch.enabled) continue
                fillChannelBuffer(ch, chanBuf)
                val vol = ch.volume
                for (i in 0 until CHUNK_SIZE) {
                    mixBuf[i] += chanBuf[i] * vol
                }
            }

            val gain = computeGain()
            if (gain <= 0f && fadeProgress >= 1f) {
                isPlaying = false
                break
            }

            val output = ShortArray(CHUNK_SIZE)
            for (i in 0 until CHUNK_SIZE) {
                val s = (mixBuf[i] * gain).coerceIn(-1f, 1f)
                output[i] = (s * Short.MAX_VALUE).toInt().toShort()
            }

            audioTrack?.write(output, 0, output.size)
        }

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
        onPlaybackStopped?.invoke()
    }

    private fun computeGain(): Float {
        val fp = fadeProgress
        return if (fp < 0f) masterVolume else masterVolume * (1f - fp.coerceIn(0f, 1f))
    }

    private fun fillChannelBuffer(ch: SoundChannel, buf: FloatArray) {
        val fb = ch.fileBuffer
        if (fb.isEmpty()) {
            buf.fill(0f)
            return
        }
        val fbLen = fb.size
        var ri = ch.readIndex
        for (i in buf.indices) {
            buf[i] = fb[ri]
            ri++
            if (ri >= fbLen) ri = 0
        }
        ch.readIndex = ri
    }
}
