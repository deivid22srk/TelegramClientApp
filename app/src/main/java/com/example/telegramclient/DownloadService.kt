package com.example.telegramclient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 101

        fun start(context: Context, fileName: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("file_name", fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, fileName: String, progress: Int) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("file_name", fileName)
                putExtra("progress", progress)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileName = intent?.getStringExtra("file_name") ?: "Arquivo"
        val progress = intent?.getIntExtra("progress", -1) ?: -1

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baixando: $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (progress >= 0) {
                    setProgress(100, progress, false)
                    setContentText("$progress%")
                } else {
                    setProgress(0, 0, true)
                    setContentText("Preparando...")
                }
            }
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (progress >= 100) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
