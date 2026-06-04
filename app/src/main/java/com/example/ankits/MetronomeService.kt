package com.example.ankits

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MetronomeService : Service() {

    val engine = MetronomeEngine()
    private val binder = LocalBinder()

    var onBeatCallback: ((Int) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): MetronomeService = this@MetronomeService
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "节拍器播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "节拍器后台播放通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    fun startPlayback(onBeat: (Int) -> Unit) {
        engine.initAudioTrack()
        onBeatCallback = onBeat
        engine.start { beat ->
            onBeatCallback?.invoke(beat)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun stopPlayback() {
        engine.stop()
        onBeatCallback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun updateBpm(newBpm: Int) {
        engine.setTempo(newBpm)
        updateNotification()
    }

    fun updateTimeSignature(beats: Int, unit: Int) {
        engine.setTimeSignature(beats, unit)
        updateNotification()
    }

    fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MetronomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("电子节拍器运行中")
            .setContentText("BPM: ${engine.bpm}  |  ${engine.beatsPerBar}/${engine.beatUnit} 拍")
            .setSmallIcon(R.drawable.ic_metronome)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "metronome_playback"
    }
}
