package com.example.ankits

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.*

class SherpaOnnxTtsEngine : TtsEngine {

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var isSpeaking = false
    @Volatile private var cancelled = false
    private var initialized = false
    @Volatile private var currentSpeed: Float = 1.0f

    private var onDone: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun init(
        context: Context,
        onReady: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        this.onDone = onDone
        this.onError = onError

        if (initialized) {
            handler.post { onReady() }
            return
        }

        Thread({
            try {
                val modelDir = copyModelIfNeeded(context)

                val vitsConfig = OfflineTtsVitsModelConfig.builder()
                    .setModel("$modelDir/zh_CN-huayan-medium.onnx")
                    .setTokens("$modelDir/tokens.txt")
                    .setDataDir("$modelDir/espeak-ng-data")
                    .build()

                val modelConfig = OfflineTtsModelConfig.builder()
                    .setVits(vitsConfig)
                    .setNumThreads(1)
                    .setDebug(false)
                    .build()

                val config = OfflineTtsConfig.builder()
                    .setModel(modelConfig)
                    .build()

                tts = OfflineTts(config)
                initialized = true
                handler.post { onReady() }
            } catch (e: UnsatisfiedLinkError) {
                handler.post { onError("本地TTS引擎加载失败: ${e.message}") }
            } catch (e: Exception) {
                handler.post { onError("本地TTS初始化失败: ${e.message}") }
            }
        }, "SherpaOnnxInit").apply { start() }
    }

    private fun copyModelIfNeeded(context: Context): String {
        val destDir = context.filesDir.absolutePath + "/tts"
        val marker = java.io.File("$destDir/.copied")
        if (!marker.exists()) {
            copyAssetDir(context, "tts", destDir)
            java.io.File(destDir).mkdirs()
            marker.createNewFile()
        }
        return destDir
    }

    private fun copyAssetDir(context: Context, path: String, destPath: String) {
        java.io.File(destPath).mkdirs()

        val assets = context.assets.list(path) ?: return
        for (asset in assets) {
            val assetPath = "$path/$asset"
            val destFile = "$destPath/$asset"
            val subAssets = context.assets.list(assetPath)
            if (subAssets != null && subAssets.isNotEmpty()) {
                copyAssetDir(context, assetPath, destFile)
            } else {
                try {
                    context.assets.open(assetPath).use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output, 8192)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun speak(text: String) {
        if (!initialized || tts == null) {
            onError?.invoke("本地TTS引擎未就绪")
            return
        }

        stop()
        isSpeaking = true
        cancelled = false

        playbackThread = Thread({
            try {
                val genConfig = GenerationConfig()
                genConfig.setSpeed(currentSpeed)

                val audio = tts!!.generateWithConfigAndCallback(
                    text, genConfig,
                    OfflineTtsCallback { if (cancelled) 0 else 1 }
                )

                if (cancelled || audio.samples.isEmpty()) {
                    handler.post {
                        isSpeaking = false
                        onDone?.invoke()
                    }
                    return@Thread
                }

                playAudioSamples(audio)
            } catch (e: Exception) {
                handler.post {
                    isSpeaking = false
                    onError?.invoke("播放失败: ${e.message}")
                }
            }
        }, "SherpaOnnxPlay").apply { start() }
    }

    private fun playAudioSamples(audio: GeneratedAudio) {
        try {
            val sampleRate = audio.sampleRate
            val samples = audio.samples

            val bufLen = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
            )

            val attr = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(sampleRate)
                .build()

            val track = AudioTrack(
                attr, format,
                bufLen.coerceAtLeast(samples.size * java.lang.Float.BYTES),
                AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack = track

            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()

            val durationMs = (samples.size.toLong() * 1000L) / sampleRate
            var elapsed = 0L
            val step = 100L
            while (!cancelled && elapsed < durationMs) {
                Thread.sleep(step)
                elapsed += step
            }

            track.stop()
            track.release()
            audioTrack = null

            handler.post {
                isSpeaking = false
                onDone?.invoke()
            }
        } catch (e: Exception) {
            handler.post {
                isSpeaking = false
                onError?.invoke("音频播放失败: ${e.message}")
            }
        }
    }

    override fun stop() {
        cancelled = true
        audioTrack?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        audioTrack = null
        playbackThread?.join(500)
        playbackThread = null
        isSpeaking = false
    }

    override fun shutdown() {
        stop()
        tts?.release()
        tts = null
        initialized = false
    }

    override fun isSpeaking(): Boolean = isSpeaking

    override fun setPitch(pitch: Float) {}

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
    }
}
