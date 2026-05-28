package com.ipn.filemanager.bluetooth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ipn.filemanager.R

/**
 * Servicio en segundo plano para transferencias Bluetooth.
 * Muestra notificación persistente durante la transferencia.
 */
class BluetoothTransferService : Service() {

    companion object {
        const val CHANNEL_ID = "bt_transfer_channel"
        const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("FileManager IPN")
            .setContentText("Transferencia Bluetooth en curso...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Transferencias Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notificaciones de transferencia de archivos vía Bluetooth" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
