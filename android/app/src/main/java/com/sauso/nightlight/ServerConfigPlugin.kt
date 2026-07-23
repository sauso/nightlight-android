package com.sauso.nightlight

import android.content.Context
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.net.HttpURLConnection
import java.net.URL

/**
 * Which Nightlight server this app talks to, chosen at runtime rather than baked
 * into the APK (the same first-run flow as the Home Assistant app).
 *
 * From the web side (both the bundled setup/error pages and the remote app):
 *
 *   Capacitor.Plugins.ServerConfig.get()            -> { url: string | null }
 *   Capacitor.Plugins.ServerConfig.save({ url })    -> validates + persists, rejects if unreachable
 *   Capacitor.Plugins.ServerConfig.clear()
 *   Capacitor.Plugins.ServerConfig.restart()        -> rebuilds the activity/bridge so the
 *                                                      current saved choice takes effect
 *
 * MainActivity reads the saved value in onCreate and points the Capacitor bridge at
 * it; no saved value means the bundled first-run setup page (www/) loads instead.
 */
@CapacitorPlugin(name = "ServerConfig")
class ServerConfigPlugin : Plugin() {

    companion object {
        private const val PREFS = "nightlight_server"
        private const val KEY_URL = "server_url"

        fun getSavedUrl(context: Context): String? {
            val url = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, null)
            return if (url.isNullOrBlank()) null else url
        }
    }

    @PluginMethod
    fun get(call: PluginCall) {
        val ret = JSObject()
        ret.put("url", getSavedUrl(context))
        call.resolve(ret)
    }

    // Confirms the address actually hosts a reachable Nightlight server (its
    // unauthenticated /api/health endpoint) before persisting anything - a typo'd
    // address that saved anyway would boot the app onto a dead URL every launch,
    // with only the error page as a way back.
    //
    // Scheme handling: an explicit https:// or http:// is respected as typed. A bare
    // address (domain, IP, IP:port) tries https first, then falls back to http -
    // self-hosted servers are commonly exposed plain-http on the LAN with no reverse
    // proxy in front (e.g. 192.168.1.50:4000), and the wrong-scheme attempt fails
    // fast (TLS handshake against a plaintext port doesn't sit out the timeout).
    @PluginMethod
    fun save(call: PluginCall) {
        val raw = call.getString("url")?.trim().orEmpty()
        if (raw.isEmpty()) {
            call.reject("Enter your server address")
            return
        }
        val bare = raw.trimEnd('/')
        val candidates = if (bare.contains("://")) {
            if (!bare.startsWith("https://") && !bare.startsWith("http://")) {
                call.reject("The address must start with https:// or http://")
                return
            }
            listOf(bare)
        } else {
            listOf("https://$bare", "http://$bare")
        }

        // Plain thread rather than blocking a Capacitor thread: HttpURLConnection
        // can sit the full timeout on an unreachable host.
        Thread {
            for (url in candidates) {
                try {
                    val conn = URL("$url/api/health").openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (code == 200 && body.contains("\"ok\"")) {
                        context
                            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_URL, url)
                            .apply()
                        val ret = JSObject()
                        ret.put("url", url)
                        call.resolve(ret)
                        return@Thread
                    }
                } catch (e: Exception) {
                    // Fall through to the next candidate scheme, if any.
                }
            }
            call.reject("Couldn't reach a Nightlight server at that address")
        }.start()
    }

    @PluginMethod
    fun clear(call: PluginCall) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_URL).apply()
        call.resolve()
    }

    // Recreating the activity rebuilds the Capacitor bridge from scratch, which is
    // what re-runs MainActivity's saved-server check - the supported way to change
    // a bridge's server URL, which is otherwise fixed for the bridge's lifetime.
    @PluginMethod
    fun restart(call: PluginCall) {
        call.resolve()
        activity?.runOnUiThread { activity.recreate() }
    }
}
