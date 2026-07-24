import UIKit
import Capacitor

/// Root bridge view controller. Chooses which server to load at launch — the iOS
/// counterpart of Android's MainActivity reading the saved URL in onCreate:
///   - a saved server  -> point the bridge at it (loads the remote web UI)
///   - no saved server -> leave the descriptor on the bundled web assets, so the
///     first-run setup page (public/index.html) loads instead.
///
/// `ServerConfigPlugin.restart()` rebuilds this controller so a newly saved choice
/// takes effect. `errorPath` (error.html) comes from capacitor.config.json and covers
/// the case where a saved server is unreachable at launch.
class MainViewController: CAPBridgeViewController {
    // Local app-target plugins aren't auto-discovered on iOS (unlike npm-package
    // plugins, and unlike Android's @CapacitorPlugin annotation) - they must be
    // registered explicitly here, or Capacitor.Plugins.* is undefined in the WebView.
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(ServerConfigPlugin())
        bridge?.registerPluginInstance(BackgroundAudioPlugin())
    }

    override func instanceDescriptor() -> InstanceDescriptor {
        let descriptor = super.instanceDescriptor()
        // serverURL is a String on iOS (the raw address), not a URL.
        if let saved = ServerConfigStore.savedURL {
            descriptor.serverURL = saved
        }
        return descriptor
    }
}
