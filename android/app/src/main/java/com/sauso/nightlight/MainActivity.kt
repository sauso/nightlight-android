package com.sauso.nightlight

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be registered before super.onCreate so the bridge picks it up.
        registerPlugin(BackgroundAudioPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
