package com.kiddo.antitheft

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class ReceiveSignal : Service() {
    private val NOTIFICATION_ID = 1
    private val PORT = 8086
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "ESP32_SIGNAL"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification().build())
        startServer()
    }

    private fun startServer() {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                while (isRunning) {
                    val clientSocket = serverSocket?.accept()
                    clientSocket?.let {
                        handleClient(it)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                var message: String?
                while (reader.readLine().also { message = it } != null) {
                    showNotification()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                clientSocket.close()
            }
        }
    }

    private fun showNotification() {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("WARNING")
                    .setContentText("Sus has been detected")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(System.currentTimeMillis().toInt(), builder.build())
                }
            } catch (e: SecurityException) {
                Log.e("ReceiveSignal", "Notification permission denied.", e)
            }
        } else {
            Log.e("ReceiveSignal", "Notification permission not granted.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notification"
            val descriptionText = "Push notification setting"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val notificationSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(notificationSound, null)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Anti Theft")
            .setContentText("You are being protected")
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
