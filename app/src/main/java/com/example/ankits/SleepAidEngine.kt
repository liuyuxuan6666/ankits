package com.example.ankits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Random

class SleepAidEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHUNK_SIZE = 2048
        private const val BUFFER_MULTIPLIER = 3
    }

    enum class SoundType {
        WHITE_NOISE, PINK_NOISE, BROWN_NOISE,
        RAIN, OCEAN, FAN, SINGING_BOWL
    }

    inner class SoundChannel(val type: SoundType) {
        @Volatile var enabled: Boolean = false
        @Volatile var volume: Float = 0.5f
        val random = Random()

        // Pink noise state
        val pinkValues = FloatArray(7) { random.nextFloat() * 2f - 1f }
        var pinkCounter = 0

        // Brown noise state
        var brownPrev = 0.0

        // Rain state
        var rainPhase = 0.0

        // Ocean state
        var oceanPhase = 0.0
        var oceanLp = 0.0

        // Fan state
        var fanLp = 0.0
        var fanBp = 0.0

        // Singing bowl state
        var bowlTime = 0.0
        var bowlPhase = DoubleArray(8) { 0.0 }
        var bowlSampleCount = 0
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
        when (ch.type) {
            SoundType.WHITE_NOISE -> fillWhiteNoise(ch, buf)
            SoundType.PINK_NOISE -> fillPinkNoise(ch, buf)
            SoundType.BROWN_NOISE -> fillBrownNoise(ch, buf)
            SoundType.RAIN -> fillRain(ch, buf)
            SoundType.OCEAN -> fillOcean(ch, buf)
            SoundType.FAN -> fillFan(ch, buf)
            SoundType.SINGING_BOWL -> fillSingingBowl(ch, buf)
        }
    }

    private fun fillWhiteNoise(ch: SoundChannel, buf: FloatArray) {
        for (i in buf.indices) {
            buf[i] = ch.random.nextFloat() * 2f - 1f
        }
    }

    private fun fillPinkNoise(ch: SoundChannel, buf: FloatArray) {
        for (i in buf.indices) {
            ch.pinkCounter++
            var sum = 0f
            var mask = 1
            for (j in 0..6) {
                if (ch.pinkCounter and mask != 0) {
                    ch.pinkValues[j] = ch.random.nextFloat() * 2f - 1f
                }
                sum += ch.pinkValues[j]
                mask = mask shl 1
            }
            buf[i] = sum / 7f
        }
    }

    private fun fillBrownNoise(ch: SoundChannel, buf: FloatArray) {
        for (i in buf.indices) {
            val white = ch.random.nextFloat() * 2f - 1f
            ch.brownPrev += white * 0.02
            if (ch.brownPrev > 1.0) ch.brownPrev = 1.0
            if (ch.brownPrev < -1.0) ch.brownPrev = -1.0
            buf[i] = ch.brownPrev.toFloat()
        }
    }

    private fun fillRain(ch: SoundChannel, buf: FloatArray) {
        for (i in buf.indices) {
            ch.pinkCounter++
            var sum = 0f
            var mask = 1
            for (j in 0..6) {
                if (ch.pinkCounter and mask != 0) {
                    ch.pinkValues[j] = ch.random.nextFloat() * 2f - 1f
                }
                sum += ch.pinkValues[j]
                mask = mask shl 1
            }
            val pink = sum / 7f
            val mod = 0.6f + 0.4f * sin(ch.rainPhase).toFloat()
            ch.rainPhase += 0.012
            if (ch.rainPhase > 2.0 * PI) ch.rainPhase -= 2.0 * PI
            buf[i] = pink * mod
        }
    }

    private fun fillOcean(ch: SoundChannel, buf: FloatArray) {
        for (i in buf.indices) {
            val white = ch.random.nextFloat() * 2f - 1f
            ch.brownPrev += white * 0.015
            if (ch.brownPrev > 1.0) ch.brownPrev = 1.0
            if (ch.brownPrev < -1.0) ch.brownPrev = -1.0
            val brown = ch.brownPrev

            ch.oceanLp += (brown - ch.oceanLp) * 0.002
            val swell = 0.55f + 0.45f * sin(ch.oceanPhase).toFloat()
            ch.oceanPhase += 0.00008
            if (ch.oceanPhase > 2.0 * PI) ch.oceanPhase -= 2.0 * PI

            buf[i] = (ch.oceanLp * swell).toFloat()
        }
    }

    private fun fillFan(ch: SoundChannel, buf: FloatArray) {
        for (i in buf.indices) {
            val white = ch.random.nextFloat() * 2f - 1f
            ch.fanLp += (white - ch.fanLp) * 0.08
            ch.fanBp += (ch.fanLp - ch.fanBp) * 0.08
            buf[i] = ((ch.fanLp - ch.fanBp) * 4.0).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private fun fillSingingBowl(ch: SoundChannel, buf: FloatArray) {
        val ratios = doubleArrayOf(1.0, 2.01, 3.0, 4.03, 5.01, 6.02, 7.0, 8.04)
        val amps = doubleArrayOf(1.0, 0.6, 0.4, 0.3, 0.25, 0.18, 0.12, 0.08)
        val decays = doubleArrayOf(0.8, 1.2, 1.8, 2.5, 3.3, 4.2, 5.0, 6.0)
        val baseFreq = 136.0

        for (i in buf.indices) {
            if (ch.bowlSampleCount > 120000) {
                ch.bowlTime = 0.0
                for (j in ch.bowlPhase.indices) {
                    ch.bowlPhase[j] = ch.random.nextDouble() * 2.0 * PI
                }
                ch.bowlSampleCount = 0
            }

            var sample = 0.0
            for (j in ratios.indices) {
                val freq = baseFreq * ratios[j]
                val env = amps[j] * exp(-ch.bowlTime * decays[j])
                sample += env * sin(ch.bowlPhase[j])
                ch.bowlPhase[j] += 2.0 * PI * freq / SAMPLE_RATE
                if (ch.bowlPhase[j] > 2.0 * PI) ch.bowlPhase[j] -= 2.0 * PI
            }

            ch.bowlTime += 1.0 / SAMPLE_RATE
            ch.bowlSampleCount++
            buf[i] = (sample * 0.3).coerceIn(-1.0, 1.0).toFloat()
        }
    }
}
