package com.kiddo.antitheft

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.OutputStream
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var vlcVideoLayout: VLCVideoLayout
    private lateinit var alertSwitch: Switch
    private lateinit var preferences: SharedPreferences
    private val esp32Ip = "192.168.244.145"
    private val esp32Port = 8088
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vlcVideoLayout = findViewById(R.id.vlcVideoLayout)
        alertSwitch = findViewById(R.id.alertSwitch)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        libVLC = LibVLC(this)
        mediaPlayer = MediaPlayer(libVLC)

        val server = "http://192.168.244.38:5000/video"
        val media = Media(libVLC, Uri.parse(server))
        mediaPlayer.media = media

        val isAlertEnabled = preferences.getBoolean("alertEnabled", false)
        alertSwitch.isChecked = isAlertEnabled
        updateSwitchText(isAlertEnabled)

        alertSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSwitchText(isChecked)
            preferences.edit().putBoolean("alertEnabled", isChecked).apply()
            if (isChecked) {
                sendSignal("c")
                Toast.makeText(this, "Alert enabled!", Toast.LENGTH_SHORT).show()
            } else {
                sendSignal("d")
                Toast.makeText(this, "Alert disabled!", Toast.LENGTH_SHORT).show()
            }
        }

        val viewImagesButton = findViewById<Button>(R.id.viewImagesButton)
        viewImagesButton.setOnClickListener {
            val intent = Intent(this, ImagesActivity::class.java)
            startActivity(intent)
        }

        StartService()
    }

    private fun sendSignal(signal: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(esp32Ip, esp32Port)
                val outputStream: OutputStream = socket.getOutputStream()
                outputStream.write(signal.toByteArray())
                outputStream.flush()
                outputStream.close()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateSwitchText(isChecked: Boolean) {
        alertSwitch.text = if (isChecked) "On" else "Off"
    }

    private fun StartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startSocketService()
        }
    }

    private fun startSocketService() {
        val intent = Intent(this, ReceiveSignal::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSocketService()
                } else {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (vlcVideoLayout != null && mediaPlayer != null) {
            try {
                mediaPlayer.attachViews(vlcVideoLayout, null, false, false)
                mediaPlayer.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer.detachViews()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            mediaPlayer.release()
        }
        if (libVLC != null) {
            libVLC.release()
        }
    }
}
