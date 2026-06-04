package com.example.ankits

import kotlin.math.*

class PitchDetector(private val sampleRate: Int = 44100) {

    private val minFreq = 60.0
    private val maxFreq = 2000.0

    var referencePitch: Float = 440f

    data class PitchResult(
        val frequency: Float,
        val noteName: String,
        val octave: Int,
        val cents: Float
    ) {
        val noteFull: String get() = "$noteName$octave"
        val isInTune: Boolean get() = abs(cents) <= 5f
    }

    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun detect(buffer: ShortArray): PitchResult? {
        val n = buffer.size

        var energy = 0f
        for (s in buffer) {
            energy += s.toFloat() * s.toFloat()
        }

        val rms = sqrt(energy / n)
        if (rms < 500f) return null

        val minLag = maxOf(1, (sampleRate / maxFreq).toInt())
        val maxLag = minOf(n / 2, (sampleRate / minFreq).toInt())
        if (minLag >= maxLag) return null

        var bestLag = minLag
        var bestVal = Float.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until n - lag) {
                sum += buffer[i].toFloat() * buffer[i + lag].toFloat()
            }
            if (sum > bestVal) {
                bestVal = sum
                bestLag = lag
            }
        }

        val confidence = bestVal / energy
        if (confidence < 0.3f) return null

        val prev = if (bestLag > minLag) acfAt(buffer, n, bestLag - 1) else bestVal
        val next = if (bestLag < maxLag) acfAt(buffer, n, bestLag + 1) else bestVal
        val denom = 2f * (prev + next - 2f * bestVal)
        val delta = if (abs(denom) > 1e-9f) (prev - next) / denom else 0f
        val refinedLag = bestLag.toFloat() + delta

        val frequency = sampleRate.toFloat() / refinedLag
        if (frequency < minFreq || frequency > maxFreq) return null

        val midiNote = 12f * log2(frequency / referencePitch) + 69f
        val roundedNote = round(midiNote).toInt()
        val cents = 100f * (midiNote - roundedNote)
        val noteIndex = ((roundedNote % 12) + 12) % 12
        val octave = roundedNote / 12 - 1

        return PitchResult(
            frequency = frequency,
            noteName = noteNames[noteIndex],
            octave = octave,
            cents = cents
        )
    }

    private fun acfAt(buffer: ShortArray, n: Int, lag: Int): Float {
        var sum = 0f
        for (i in 0 until n - lag) {
            sum += buffer[i].toFloat() * buffer[i + lag].toFloat()
        }
        return sum
    }
}
