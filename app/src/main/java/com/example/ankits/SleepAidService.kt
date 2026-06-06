package com.example.ankits

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.os.Build
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class SleepAidService : Service() {

    val engine = SleepAidEngine()
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    private var mediaPlayer: MediaPlayer? = null
    private var importedAudioPaths: List<String> = emptyList()

    var isPlaying = false
        private set
    var currentTrackIndex = -1
    var shuffleEnabled = false

    var timerDurationMs = 0L
    var timerRemainingMs = 0L
    var isFadingOut = false
    var fadeOutEnabled = true

    private var timerTickRunnable: Runnable? = null
    private var fadeOutRunnable: Runnable? = null

    private lateinit var mediaSession: MediaSession

    var onStateChanged: (() -> Unit)? = null
    var onTimerTick: ((Long) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): SleepAidService = this@SleepAidService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        ensureSoundFilesExist()
        loadAllSoundFiles()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> togglePlayback()
            ACTION_SKIP_NEXT -> skipToNext()
            ACTION_SKIP_PREV -> skipToPrevious()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.release()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        mediaSession.isActive = false
        mediaSession.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // --- Notification channel ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "助眠播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "小助眠后台播放通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // --- MediaSession ---

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "SleepAidService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!isPlaying) resumePlayback()
                }
                override fun onPause() {
                    if (isPlaying) togglePlayback()
                }
                override fun onStop() {
                    stopPlayback()
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

    // --- Sound files ---

    private fun ensureSoundFilesExist() {
        val dir = File(filesDir, SOUNDS_DIR)
        dir.mkdirs()
        for (type in SleepAidEngine.SoundType.entries) {
            val file = File(dir, "${type.name.lowercase()}.wav")
            if (!file.exists()) {
                generateSilentWavFile(file)
            }
        }
    }

    private fun loadAllSoundFiles() {
        val dir = File(filesDir, SOUNDS_DIR)
        for (type in SleepAidEngine.SoundType.entries) {
            val file = File(dir, "${type.name.lowercase()}.wav")
            if (file.exists()) {
                engine.loadFile(type, file.absolutePath)
            }
        }
    }

    private fun generateSilentWavFile(file: File) {
        val sampleRate = 44100
        val durationSeconds = 3
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2
        val riffSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(riffSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }
        file.outputStream().use { it.write(buffer.array()) }
    }

    // --- Imported audio sync ---

    fun syncImportedAudio(paths: List<String>, currentIndex: Int, shuffle: Boolean) {
        importedAudioPaths = paths
        currentTrackIndex = if (currentIndex < paths.size) currentIndex else -1
        shuffleEnabled = shuffle
    }

    // --- Playback ---

    fun startPlayback(): Boolean {
        val hasBuiltin = engine.channels.any { it.enabled }
        val hasImported = currentTrackIndex in importedAudioPaths.indices

        if (!hasBuiltin && !hasImported) return false

        if (hasBuiltin) {
            if (!engine.start()) return false
        }

        if (hasImported) {
            playImportedAudio(currentTrackIndex)
        }

        if (timerDurationMs > 0) startTimer(timerDurationMs)

        isPlaying = true
        startForeground(NOTIFICATION_ID, buildNotification())
        updateMediaSessionState(true)
        onStateChanged?.invoke()
        return true
    }

    fun stopPlayback() {
        engine.stop()
        stopImportedAudio()
        cancelTimer()

        isPlaying = false
        updateMediaSessionState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        onStateChanged?.invoke()
        stopSelf()
    }

    fun togglePlayback() {
        if (isPlaying) {
            engine.stop()
            try { mediaPlayer?.pause() } catch (_: Exception) {}
            isPlaying = false
            updateMediaSessionState(false)
            updateNotification()
        } else {
            val hasBuiltin = engine.channels.any { it.enabled }
            val hasImported = currentTrackIndex in importedAudioPaths.indices

            if (hasBuiltin) engine.start()
            if (hasImported && mediaPlayer != null) {
                try { mediaPlayer?.start() } catch (_: Exception) {}
            }

            isPlaying = true
            updateMediaSessionState(true)
            updateNotification()
        }
        onStateChanged?.invoke()
    }

    private fun resumePlayback() {
        if (isPlaying) return
        togglePlayback()
    }

    private fun updateMediaSessionState(playing: Boolean) {
        val state = if (playing)
            android.media.session.PlaybackState.STATE_PLAYING
        else
            android.media.session.PlaybackState.STATE_PAUSED
        mediaSession.setPlaybackState(
            android.media.session.PlaybackState.Builder()
                .setState(state, android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY or
                            android.media.session.PlaybackState.ACTION_PAUSE or
                            android.media.session.PlaybackState.ACTION_STOP
                )
                .build()
        )
    }

    // --- Imported audio ---

    fun playImportedAudio(index: Int) {
        stopImportedAudio()
        if (index !in importedAudioPaths.indices) return

        currentTrackIndex = index
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(importedAudioPaths[index])
            setOnPreparedListener { it.start() }
            setOnCompletionListener { advanceToNextTrack() }
            setOnErrorListener { _, _, _ -> advanceToNextTrack(); true }
            prepareAsync()
        }
    }

    fun skipToNext() {
        if (importedAudioPaths.size <= 1) return
        val next = if (shuffleEnabled) {
            var r = random.nextInt(importedAudioPaths.size)
            while (r == currentTrackIndex && importedAudioPaths.size > 1) r = random.nextInt(importedAudioPaths.size)
            r
        } else {
            (currentTrackIndex + 1) % importedAudioPaths.size
        }
        currentTrackIndex = next
        playImportedAudio(next)
        updateNotification()
        onStateChanged?.invoke()
    }

    fun skipToPrevious() {
        if (importedAudioPaths.size <= 1) return
        val prev = if (shuffleEnabled) {
            var r = random.nextInt(importedAudioPaths.size)
            while (r == currentTrackIndex && importedAudioPaths.size > 1) r = random.nextInt(importedAudioPaths.size)
            r
        } else {
            (currentTrackIndex - 1 + importedAudioPaths.size) % importedAudioPaths.size
        }
        currentTrackIndex = prev
        playImportedAudio(prev)
        updateNotification()
        onStateChanged?.invoke()
    }

    fun stopImportedAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun advanceToNextTrack() {
        if (importedAudioPaths.isEmpty() || importedAudioPaths.size == 1) return

        val next = if (shuffleEnabled) {
            if (importedAudioPaths.size == 1) 0
            else {
                var r = random.nextInt(importedAudioPaths.size)
                while (r == currentTrackIndex && importedAudioPaths.size > 1) {
                    r = random.nextInt(importedAudioPaths.size)
                }
                r
            }
        } else {
            (currentTrackIndex + 1) % importedAudioPaths.size
        }

        currentTrackIndex = next
        playImportedAudio(next)
    }

    // --- Timer ---

    fun setTimer(durationMs: Long, fadeOut: Boolean) {
        timerDurationMs = durationMs
        fadeOutEnabled = fadeOut
    }

    fun clearTimer() {
        timerDurationMs = 0
    }

    private fun startTimer(durationMs: Long) {
        timerRemainingMs = durationMs
        isFadingOut = false
        onTimerTick?.invoke(timerRemainingMs)
        updateNotification()

        timerTickRunnable?.let { handler.removeCallbacks(it) }
        timerTickRunnable = object : Runnable {
            override fun run() {
                timerRemainingMs -= 1000
                onTimerTick?.invoke(timerRemainingMs)
                updateNotification()

                if (timerRemainingMs <= 0) {
                    stopPlayback()
                    return
                }

                if (fadeOutEnabled && !isFadingOut && timerRemainingMs <= 30000) {
                    beginFadeOut(timerRemainingMs)
                }

                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerTickRunnable!!, 1000)
    }

    private fun beginFadeOut(durationMs: Long) {
        isFadingOut = true
        engine.startFadeOut()

        val steps = (durationMs / 50).coerceIn(1, 600)
        val stepMs = durationMs / steps
        val increment = 1f / steps

        fadeOutRunnable?.let { handler.removeCallbacks(it) }
        var step = 0
        fadeOutRunnable = object : Runnable {
            override fun run() {
                step++
                engine.fadeProgress = (increment * step).coerceAtMost(1f)
                val mpVol = (1f - engine.fadeProgress).coerceAtLeast(0f)
                try { mediaPlayer?.setVolume(mpVol, mpVol) } catch (_: Exception) {}
                if (step < steps && engine.isPlaying) {
                    handler.postDelayed(this, stepMs)
                }
            }
        }
        handler.postDelayed(fadeOutRunnable!!, stepMs)
    }

    private fun cancelTimer() {
        timerTickRunnable?.let { handler.removeCallbacks(it) }
        timerTickRunnable = null
        fadeOutRunnable?.let { handler.removeCallbacks(it) }
        fadeOutRunnable = null
        isFadingOut = false
        timerRemainingMs = 0L
        onTimerTick?.invoke(timerRemainingMs)
        updateNotification()
    }

    // --- Notification ---

    fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, SleepAidActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, SleepAidService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this, 1, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 1, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val playPauseIcon = if (isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play

        val playPauseLabel = if (isPlaying)
            getString(R.string.sleep_aid_notification_pause)
        else
            getString(R.string.sleep_aid_notification_play)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Notification.Builder(this, CHANNEL_ID)
                .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
        } else {
            Notification.Builder(this)
        }

        val nb = builder
            .setContentTitle(getString(R.string.sleep_aid_notification_title))
            .setContentText(buildNotificationText())
            .setSmallIcon(R.drawable.ic_sleep_aid)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // Add skip buttons when there are multiple imported tracks
        if (importedAudioPaths.size > 1) {
            val prevIntent = createActionIntent(ACTION_SKIP_PREV)
            val nextIntent = createActionIntent(ACTION_SKIP_NEXT)
            nb.addAction(android.R.drawable.ic_media_previous, getString(R.string.sleep_aid_notification_prev), prevIntent)
            nb.addAction(playPauseIcon, playPauseLabel, togglePendingIntent)
            nb.addAction(android.R.drawable.ic_media_next, getString(R.string.sleep_aid_notification_next), nextIntent)
        } else {
            nb.addAction(playPauseIcon, playPauseLabel, togglePendingIntent)
        }

        return nb.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, SleepAidService::class.java).apply {
            this.action = action
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun buildNotificationText(): String {
        return if (timerRemainingMs > 0) {
            val totalSec = (timerRemainingMs / 1000).toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            getString(R.string.sleep_aid_notification_text_with_timer, min, sec)
        } else {
            getString(R.string.sleep_aid_notification_text)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "sleep_aid_playback"
        const val ACTION_TOGGLE = "com.example.ankits.sleepaid.TOGGLE"
        const val ACTION_SKIP_NEXT = "com.example.ankits.sleepaid.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.example.ankits.sleepaid.SKIP_PREV"
        const val SOUNDS_DIR = "sleep_aid_sounds"
    }
}
