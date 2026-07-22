package com.sauso.nightlight

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

/**
 * JS-facing plugin. From the web app:
 *
 *   Capacitor.Plugins.BackgroundAudio.start({ label: "Nursery" })
 *   Capacitor.Plugins.BackgroundAudio.stop()
 *   Capacitor.Plugins.BackgroundAudio.addListener("stopped", ...)
 *
 * start() while already running just retitles the notification.
 */
@CapacitorPlugin(
    name = "BackgroundAudio",
    permissions = [
        Permission(
            strings = [Manifest.permission.POST_NOTIFICATIONS],
            alias = "notifications"
        )
    ]
)
class BackgroundAudioPlugin : Plugin() {

    companion object {
        // The service needs a way to reach whichever plugin instance is live so
        // "Stop" on the notification can be reflected in the web UI.
        @Volatile
        private var instance: BackgroundAudioPlugin? = null

        fun notifyStopped() {
            instance?.notifyListeners("stopped", JSObject())
        }
    }

    override fun load() {
        instance = this
    }

    override fun handleOnDestroy() {
        if (instance === this) instance = null
        super.handleOnDestroy()
    }

    @PluginMethod
    fun start(call: PluginCall) {
        // Android 13+ needs runtime consent to show the foreground-service
        // notification. The service starts either way (denying just hides the
        // notification into the FGS task manager), but asking once is polite
        // and gives the person the Stop button.
        if (Build.VERSION.SDK_INT >= 33 &&
            getPermissionState("notifications") != com.getcapacitor.PermissionState.GRANTED &&
            !call.getBoolean("skipPermissionPrompt", false)!!
        ) {
            requestPermissionForAlias("notifications", call, "onNotificationPermission")
            return
        }
        startService(call)
    }

    @PermissionCallback
    private fun onNotificationPermission(call: PluginCall) {
        // Granted or denied, we still start - background audio works regardless.
        startService(call)
    }

    private fun startService(call: PluginCall) {
        val label = call.getString("label") ?: "camera"
        val intent = Intent(context, AudioService::class.java).apply {
            action = AudioService.ACTION_START
            putExtra(AudioService.EXTRA_LABEL, label)
        }
        ContextCompat.startForegroundService(context, intent)
        call.resolve()
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        context.stopService(Intent(context, AudioService::class.java))
        call.resolve()
    }
}
