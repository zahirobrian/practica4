package com.ipn.btbrowser.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Servicio en primer plano para mantener el servidor BT activo. */
class ProxyService : Service() {
    companion object { const val CHANNEL_ID = "bt_proxy_channel" }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Proxy BT", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BT Browser IPN — Servidor activo")
            .setContentText("Compartiendo Internet via Bluetooth")
            .setOngoing(true).build()
        startForeground(2001, notif)
        return START_NOT_STICKY
    }
}