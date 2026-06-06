package com.example.ankits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
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

    @Volatile private var playing = false
    val isPlaying: Boolean get() = playing
    private var onBeat: ((beat: Int) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var audioThread: Thread? = null

    @Volatile private var volBpm: Int = 120
    @Volatile private var volBeatsPerBar: Int = 4

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

        // Keep buffer minimal so write() blocks close to actual playback position
        val bufferSize = maxOf(minBufSize, sampleRate)

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
        volBpm = bpm
        volBeatsPerBar = beatsPerBar
        playing = true
        currentBeat = 0

        audioTrack?.play()

        audioThread = Thread {
            val track = audioTrack ?: return@Thread
            var beatCount = 0

            while (playing) {
                val currentBpm = volBpm
                val currentBeatsPerBar = volBeatsPerBar
                val beatIndex = beatCount % currentBeatsPerBar
                val click = if (beatIndex == 0) accentedSamples else unaccentedSamples

                // Write the click audio
                val written = writeAll(track, click)
                if (written < 0) break

                // Notify main thread
                val beat = beatIndex
                handler.post {
                    if (playing) {
                        onBeat?.invoke(beat)
                        currentBeat = beat
                    }
                }
                beatCount++

                // Write silence for the rest of the interval, paced by audio clock
                val intervalMs = (60000.0 / currentBpm).toLong()
                val totalSamples = (sampleRate * intervalMs / 1000).toInt()
                val silenceSamples = totalSamples - click.size

                if (silenceSamples > 0) {
                    // Write silence in small chunks to stay responsive to stop/BPM changes
                    val chunkSize = sampleRate / 10 // ~100ms chunks
                    val silenceChunk = ShortArray(chunkSize)
                    var remaining = silenceSamples
                    while (remaining > 0 && playing) {
                        val toWrite = minOf(remaining, chunkSize)
                        val w = track.write(silenceChunk, 0, toWrite)
                        if (w < 0) break
                        remaining -= w
                    }
                }
            }
        }.apply {
            name = "MetronomeAudio"
            isDaemon = true
            start()
        }
    }

    private fun writeAll(track: AudioTrack, samples: ShortArray): Int {
        var offset = 0
        while (offset < samples.size) {
            val w = track.write(samples, offset, samples.size - offset)
            if (w < 0) return w
            offset += w
        }
        return offset
    }

    fun stop() {
        playing = false
        // Unblock audio thread if it's stuck in write()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
        audioThread?.join(200)
        audioThread = null
    }

    fun setTempo(newBpm: Int) {
        bpm = newBpm.coerceIn(20, 300)
        volBpm = bpm
    }

    fun setTimeSignature(beats: Int, unit: Int) {
        beatsPerBar = beats.coerceIn(1, 12)
        beatUnit = unit
        volBeatsPerBar = beatsPerBar
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
}
