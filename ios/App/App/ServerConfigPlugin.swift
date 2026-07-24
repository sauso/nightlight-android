import Foundation
import Capacitor
import UIKit

/// iOS counterpart of `ServerConfigPlugin.kt`. Which Nightlight server the app talks
/// to is chosen at runtime (the Home Assistant-style first-run flow), not baked in.
///
/// From the web side (same plugin + method names as Android, so `nativeBridge.js`
/// works unchanged):
///
///   Capacitor.Plugins.ServerConfig.get()          -> { url: string | null }
///   Capacitor.Plugins.ServerConfig.list()         -> { servers: string[], active: string | null }
///   Capacitor.Plugins.ServerConfig.save({ url })  -> validates + persists, rejects if unreachable
///   Capacitor.Plugins.ServerConfig.forget({ url })
///   Capacitor.Plugins.ServerConfig.clear()
///   Capacitor.Plugins.ServerConfig.restart()      -> reloads the bridge so the current choice takes effect
///
/// The launch-time "load the saved server" wiring lives in the bridge view controller
/// (added separately); this plugin owns the stored value it reads.

/// Persisted server selection, shared between the plugin and the bridge view controller.
enum ServerConfigStore {
    private static let defaults = UserDefaults.standard
    private static let keyURL = "nightlight_server_url"
    private static let keyKnown = "nightlight_known_servers"

    static var savedURL: String? {
        get {
            let value = defaults.string(forKey: keyURL)
            return (value?.isEmpty ?? true) ? nil : value
        }
        set {
            if let value = newValue, !value.isEmpty {
                defaults.set(value, forKey: keyURL)
            } else {
                defaults.removeObject(forKey: keyURL)
            }
        }
    }

    /// Every server that has ever validated, most-recently-used first.
    static var knownServers: [String] {
        get { defaults.stringArray(forKey: keyKnown) ?? [] }
        set { defaults.set(newValue, forKey: keyKnown) }
    }

    static func remember(_ url: String) {
        var list = knownServers
        list.removeAll { $0 == url }
        list.insert(url, at: 0)
        knownServers = list
    }

    static func forget(_ url: String) {
        var list = knownServers
        list.removeAll { $0 == url }
        knownServers = list
    }
}

@objc(ServerConfigPlugin)
public class ServerConfigPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "ServerConfigPlugin"
    public let jsName = "ServerConfig"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "get", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "list", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "save", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "forget", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clear", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "restart", returnType: CAPPluginReturnPromise),
    ]

    @objc func get(_ call: CAPPluginCall) {
        call.resolve(["url": ServerConfigStore.savedURL ?? NSNull()])
    }

    @objc func list(_ call: CAPPluginCall) {
        call.resolve([
            "servers": ServerConfigStore.knownServers,
            "active": ServerConfigStore.savedURL ?? NSNull(),
        ])
    }

    @objc func forget(_ call: CAPPluginCall) {
        if let url = call.getString("url") {
            ServerConfigStore.forget(url)
        }
        call.resolve()
    }

    // Clears only the ACTIVE choice, deliberately keeping the remembered list -
    // "Change server" should land on a screen offering your other servers.
    @objc func clear(_ call: CAPPluginCall) {
        ServerConfigStore.savedURL = nil
        call.resolve()
    }

    // Confirms the address actually hosts a reachable Nightlight server (its
    // unauthenticated /api/health endpoint) before persisting. Scheme handling
    // mirrors Android: an explicit scheme is respected; a bare address tries https
    // first then http (self-hosted LAN servers are commonly plain-http).
    @objc func save(_ call: CAPPluginCall) {
        let raw = (call.getString("url") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.isEmpty {
            call.reject("Enter your server address")
            return
        }
        var bare = raw
        while bare.hasSuffix("/") { bare.removeLast() }

        let candidates: [String]
        if bare.contains("://") {
            guard bare.hasPrefix("https://") || bare.hasPrefix("http://") else {
                call.reject("The address must start with https:// or http://")
                return
            }
            candidates = [bare]
        } else {
            candidates = ["https://\(bare)", "http://\(bare)"]
        }

        validate(candidates, index: 0, call: call)
    }

    private func validate(_ candidates: [String], index: Int, call: CAPPluginCall) {
        if index >= candidates.count {
            call.reject("Couldn't reach a Nightlight server at that address")
            return
        }
        let base = candidates[index]
        guard let url = URL(string: "\(base)/api/health") else {
            validate(candidates, index: index + 1, call: call)
            return
        }
        var request = URLRequest(url: url, timeoutInterval: 5)
        request.httpMethod = "GET"
        URLSession.shared.dataTask(with: request) { [weak self] data, response, _ in
            let ok = (response as? HTTPURLResponse)?.statusCode == 200
                && (data.flatMap { String(data: $0, encoding: .utf8) }?.contains("\"ok\"") ?? false)
            if ok {
                ServerConfigStore.savedURL = base
                ServerConfigStore.remember(base)
                call.resolve(["url": base])
            } else {
                // Try the next candidate scheme, if any.
                self?.validate(candidates, index: index + 1, call: call)
            }
        }.resume()
    }

    // Reloads the app so the current saved choice takes effect - the iOS equivalent
    // of Android recreating the activity. Rebuilding the root view controller re-runs
    // the bridge's server-URL selection (added with the view-controller wiring).
    @objc func restart(_ call: CAPPluginCall) {
        call.resolve()
        DispatchQueue.main.async { [weak self] in
            guard let window = self?.bridge?.viewController?.view.window else { return }
            let storyboard = UIStoryboard(name: "Main", bundle: nil)
            window.rootViewController = storyboard.instantiateInitialViewController()
        }
    }
}
