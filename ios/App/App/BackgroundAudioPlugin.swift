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

    // Called both when the first camera enters background mode and on every membership
    // change; activating an already-active session is a harmless no-op, matching the
    // Android "start while running just updates the notification" behavior.
    @objc func start(_ call: CAPPluginCall) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
            call.resolve()
        } catch {
            call.reject("Could not activate the audio session: \(error.localizedDescription)")
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        // notifyOthersOnDeactivation lets other apps' audio (music, etc.) resume.
        // A deactivation failure is non-fatal - resolve either way so the web side's
        // state stays consistent.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        call.resolve()
    }
}
