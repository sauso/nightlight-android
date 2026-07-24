import Foundation
import Capacitor
import AVFoundation

/// iOS counterpart of `BackgroundAudioPlugin.kt` + `AudioService.kt`. iOS has no
/// foreground-service model; instead, background audio works by putting the app's
/// shared AVAudioSession into the `.playback` category and declaring the `audio`
/// UIBackgroundMode (Info.plist). The WKWebView's stream audio plays through that
/// session, so it keeps going with the screen off / app backgrounded - no wake lock,
/// wifi lock, notification, or battery-optimization exemption needed (all the Android
/// machinery those solved doesn't exist on iOS).
///
/// Same JS contract as Android so nativeBridge.js is unchanged:
///   Capacitor.Plugins.BackgroundAudio.start({ label })
///   Capacitor.Plugins.BackgroundAudio.stop()
///   Capacitor.Plugins.BackgroundAudio.addListener("stopped", ...)
///
/// The "stopped" event (Android fires it when the notification's Stop is tapped) has
/// no equivalent yet on iOS - there's no system Stop affordance here, so listening is
/// ended from the app's own audio button. Control Center / lock-screen controls are a
/// deliberate later addition, best validated on a real device (they can't be in the
/// Simulator).
@objc(BackgroundAudioPlugin)
public class BackgroundAudioPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "BackgroundAudioPlugin"
    public let jsName = "BackgroundAudio"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
    ]

    // The audio session category is set to .playback once at launch (see AppDelegate),
    // so keeping a stream audible in the background is really driven by the web app not
    // muting the <audio> element in Background mode. The native side deliberately does
    // NOT call setActive here: doing so on an already-playing session interrupts the
    // WebView audio (that was the "switching to Background stops it" bug). start/stop
    // just re-assert the category defensively and resolve, keeping the same JS contract
    // as Android.
    @objc func start(_ call: CAPPluginCall) {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        call.resolve()
    }

    @objc func stop(_ call: CAPPluginCall) {
        call.resolve()
    }
}
