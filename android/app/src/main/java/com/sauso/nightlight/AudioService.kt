package com.sauso.nightlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the app process (and therefore the WebView's
 * audio stream) alive while the screen is off or Nightlight is in the background.
 *
 * It doesn't play any audio itself - the WebRTC/HLS audio keeps playing inside
 * the WebView. This service's job is to hold a partial wake lock + wifi lock and
 * show the persistent notification Android requires, so the OS doesn't freeze
 * or kill the process to save battery.
 */
class AudioService : Service() {

    companion object {
        const val CHANNEL_ID = "nightlight_listening"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.sauso.nightlight.action.START"
        const val ACTION_STOP = "com.sauso.nightlight.action.STOP"
        const val EXTRA_LABEL = "label"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Tapped "Stop" on the notification: tell the web side so the
                // tiles can flip themselves back from Background to On.
                BackgroundAudioPlugin.notifyStopped()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val label = intent?.getStringExtra(EXTRA_LABEL) ?: "camera"
                startInForeground(label)
                acquireLocks()
            }
        }
        // If Android does kill us, don't resurrect a zombie service with no
        // WebView to keep alive - the person will just reopen the app.
        return START_NOT_STICKY
    }

    private fun startInForeground(label: String) {
        createChannel()

        // Tapping the notification body brings the app back to the front.
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nightlight")
            .setContentText("Listening to $label")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Calling startForeground again while already running just updates the
        // notification - that's how the label refreshes when cameras join/leave.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background listening",
            // LOW = no sound, no heads-up popup; just quietly present in the shade.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while Nightlight keeps listening with the screen off"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nightlight::Listening").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // Deliberately NOT WIFI_MODE_FULL_LOW_LATENCY: that mode is documented to
            // only be active while the acquiring app is foregrounded with the screen
            // on - i.e. it silently does nothing in exactly the situation this
            // service exists for (screen off, listening in the background), letting
            // wifi power-saving starve the WebRTC connection until it dropped.
            // HIGH_PERF is deprecated but remains the only mode that holds wifi
            // awake from the background.
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Nightlight::Wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        super.onDestroy()
    }
}
