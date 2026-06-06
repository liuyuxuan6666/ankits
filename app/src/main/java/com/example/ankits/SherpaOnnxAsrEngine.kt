package com.example.ankits

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.*

class SherpaOnnxAsrEngine : AsrEngine {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var listening = false
    @Volatile private var cancelled = false
    private var initialized = false

    override fun init(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (initialized) {
            handler.post { onReady() }
            return
        }

        Thread({
            try {
                val modelDir = copyModelIfNeeded(context)

                val featConfig = FeatureConfig.builder()
                    .setSampleRate(16000)
                    .setFeatureDim(80)
                    .build()

                val modelConfig = OnlineModelConfig.builder()
                    .setZipformer2Ctc(
                        OnlineZipformer2CtcModelConfig.builder()
                            .setModel("$modelDir/model.int8.onnx")
                            .build()
                    )
                    .setTokens("$modelDir/tokens.txt")
                    .build()

                val endpointConfig = EndpointConfig.builder()
                    .setRule1(EndpointRule.builder()
                        .setMustContainNonSilence(false)
                        .setMinTrailingSilence(1.2f)
                        .setMinUtteranceLength(1.0f)
                        .build())
                    .setRule2(EndpointRule.builder()
                        .setMustContainNonSilence(false)
                        .setMinTrailingSilence(0.5f)
                        .setMinUtteranceLength(0.0f)
                        .build())
                    .setRule3(EndpointRule.builder()
                        .setMustContainNonSilence(false)
                        .setMinTrailingSilence(0.0f)
                        .setMinUtteranceLength(20.0f)
                        .build())
                    .build()

                val config = OnlineRecognizerConfig.builder()
                    .setFeatureConfig(featConfig)
                    .setOnlineModelConfig(modelConfig)
                    .setEndpointConfig(endpointConfig)
                    .setEnableEndpoint(true)
                    .setDecodingMethod("greedy_search")
                    .build()

                recognizer = OnlineRecognizer(config)
                initialized = true
                handler.post { onReady() }
            } catch (e: UnsatisfiedLinkError) {
                handler.post { onError("本地识别引擎加载失败: ${e.message}") }
            } catch (e: Exception) {
                handler.post { onError("本地识别初始化失败: ${e.message}") }
            }
        }, "SherpaOnnxAsrInit").apply { start() }
    }

    private fun copyModelIfNeeded(context: Context): String {
        val destDir = context.filesDir.absolutePath + "/asr"
        val marker = java.io.File("$destDir/.copied")
        if (!marker.exists()) {
            copyAssetDir(context, "asr", destDir)
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

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEndOfSpeech: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!initialized || recognizer == null) {
            onError("本地识别引擎未就绪")
            return
        }

        stopListening()
        listening = true
        cancelled = false

        val rec = recognizer!!
        val s = rec.createStream()
        stream = s
        var lastPartial = ""

        recordThread = Thread({
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate * 2)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val chunkSize = sampleRate / 10 // 100ms
                val shorts = ShortArray(chunkSize)

                while (!cancelled) {
                    val n = recorder.read(shorts, 0, chunkSize)
                    if (n <= 0) continue

                    val floats = FloatArray(n) { shorts[it] / 32768.0f }
                    s.acceptWaveform(floats, sampleRate)

                    while (rec.isReady(s)) {
                        rec.decode(s)
                    }

                    val text = rec.getResult(s).text
                    if (text.isNotBlank() && text != lastPartial) {
                        lastPartial = text
                        handler.post { onPartial(text) }
                    }

                    if (rec.isEndpoint(s)) {
                        if (lastPartial.isNotBlank()) {
                            val finalText = lastPartial
                            lastPartial = ""
                            handler.post { onFinal(finalText) }
                        }
                        handler.post { onEndOfSpeech() }
                        rec.reset(s)
                    }
                }
            } catch (e: Exception) {
                handler.post { onError("录音错误: ${e.message}") }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                try { recorder.release() } catch (_: Exception) {}
                if (audioRecord === recorder) audioRecord = null
            }
        }, "SherpaOnnxAsrRecord").apply { start() }
    }

    override fun stopListening() {
        cancelled = true
        listening = false

        audioRecord?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        audioRecord = null

        recordThread?.join(500)
        recordThread = null

        stream?.let {
            try { it.inputFinished() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        stream = null
    }

    override fun shutdown() {
        stopListening()
        recognizer?.release()
        recognizer = null
        initialized = false
    }

    override fun isListening(): Boolean = listening
}
