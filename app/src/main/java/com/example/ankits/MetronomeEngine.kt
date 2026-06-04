package com.example.ankits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

class MetronomeEngine {

    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100
    private val clickDurationMs = 30

    private var accentedSamples: ShortArray = ShortArray(0)
    private var unaccentedSamples: ShortArray = ShortArray(0)

    var bpm: Int = 120
        private set
    var beatsPerBar: Int = 4
        private set
    var beatUnit: Int = 4
        private set
    var currentBeat: Int = 0
        private set

    var accentFreq: Double = 880.0
        set(value) {
            field = value
            accentedSamples = generateClick(value, 0.8)
        }
    var unaccentFreq: Double = 440.0
        set(value) {
            field = value
            unaccentedSamples = generateClick(value, 0.5)
        }

    private var playing = false
    val isPlaying: Boolean get() = playing
    private var onBeat: ((beat: Int) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var nextBeatTime = 0L
    private var scheduleCount = 0

    init {
        accentedSamples = generateClick(accentFreq, 0.8)
        unaccentedSamples = generateClick(unaccentFreq, 0.5)
    }

    fun initAudioTrack(): Boolean {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) return true

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(minBufSize, sampleRate * 2 * clickDurationMs / 1000)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        return audioTrack?.state == AudioTrack.STATE_INITIALIZED
    }

    private fun generateClick(freq: Double, amplitude: Double): ShortArray {
        val numSamples = (sampleRate * clickDurationMs / 1000).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = 1.0 - i.toDouble() / (numSamples - 1).coerceAtLeast(1)
            val value = (amplitude * envelope * sin(2.0 * PI * freq * t) * Short.MAX_VALUE).toInt()
            samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    fun start(onBeat: (beat: Int) -> Unit) {
        if (playing) return
        if (!initAudioTrack()) return

        this.onBeat = onBeat
        playing = true
        currentBeat = 0
        nextBeatTime = 0L
        scheduleCount = 0

        audioTrack?.play()
        scheduleNextBeat()
    }

    fun stop() {
        playing = false
        handler.removeCallbacksAndMessages(null)
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    fun setTempo(newBpm: Int) {
        bpm = newBpm.coerceIn(20, 300)
        if (playing) {
            nextBeatTime = 0L
            scheduleCount = 0
            handler.removeCallbacksAndMessages(null)
            scheduleNextBeat()
        }
    }

    fun setTimeSignature(beats: Int, unit: Int) {
        beatsPerBar = beats.coerceIn(1, 12)
        beatUnit = unit
        if (!playing) {
            currentBeat = 0
        }
    }

    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun scheduleNextBeat() {
        if (!playing) return

        val intervalMs = (60000.0 / bpm).toLong()
        val now = SystemClock.elapsedRealtime()

        if (nextBeatTime == 0L) {
            nextBeatTime = now
        }

        // Determine which sample to play for this beat
        val beatIndex = scheduleCount % beatsPerBar
        val samples = if (beatIndex == 0) accentedSamples else unaccentedSamples
        audioTrack?.write(samples, 0, samples.size)

        onBeat?.invoke(beatIndex)
        currentBeat = beatIndex
        scheduleCount++

        nextBeatTime += intervalMs

        // Drift compensation: if we're more than 1 interval behind, reset
        var delay = nextBeatTime - SystemClock.elapsedRealtime()
        if (delay < -intervalMs) {
            nextBeatTime = SystemClock.elapsedRealtime() + intervalMs
            delay = intervalMs
        }
        if (delay < 0) delay = 0

        handler.postDelayed({ scheduleNextBeat() }, delay)
    }
}
