# Nightlight: iOS build (no Mac, GitHub Actions) — Scope Document

Status: **Not started.** Planning document only. Confirm repo structure and the
external facts below before starting — Apple's tooling and GitHub's runner terms
change over time.

---

## The two money/access facts up front

1. **GitHub macOS runners are free here.** `nightlight-mobile` is a public repo,
   and GitHub Actions standard runners — including macOS — are free for public
   repos (no per-minute charge). Only the larger/XL runner classes cost money;
   the standard `macos-*` images are what we need and they're free. There's a
   concurrency cap (a handful of simultaneous macOS jobs), not a minute budget.

2. **An Apple Developer Program membership ($99/year) is unavoidable to get the
   app onto a real iPhone.** There is no free path to a device install without a
   Mac:
   - Free Apple ID + Xcode can sideload for 7 days — but that needs a Mac we
     don't have.
   - Installing any IPA on a device requires a signing certificate + provisioning
     profile, which require the paid program.
   - **TestFlight** (the realistic distribution channel for a no-Mac setup) also
     requires the paid program + App Store Connect.

   **BUT** the paid account is only needed for the *last* phase. Everything up to
   "runs in the iOS Simulator with the plugins working" can be done **free, with
   no account**, because Simulator builds don't require signing. So defer the $99
   purchase until the app actually works in the Simulator.

---

## Honest technical risks (flag before committing effort)

These are the reasons an iOS port is more than "flip a switch," independent of
cost:

1. **Background audio is architecturally different on iOS.** Android used a
   foreground service + wake lock (`AudioService.kt`). iOS has no equivalent.
   The iOS mechanism is: declare `UIBackgroundModes: [audio]` in Info.plist and
   keep an `AVAudioSession` (category `.playback`) actively playing. As long as
   audio genuinely plays, the app stays alive backgrounded. WKWebView media can
   drive that session, so it's feasible — but it's stricter than Android (if audio
   stalls, iOS suspends the app) and WebRTC-audio-in-WKWebView background is less
   proven than HLS. **This is the biggest feasibility unknown and must be
   prototyped on a real device before calling the port "done"** (Phase 3).

2. **WebRTC ("Low latency" mode) in WKWebView is historically flaky.** Native
   Safari supports WebRTC; WKWebView (what Capacitor uses) has been limited and
   quirky across iOS versions. **HLS ("Compatibility" mode) is rock-solid on iOS**
   (native playback). Plan: verify WebRTC in WKWebView early; if it doesn't behave,
   iOS simply defaults to HLS — the app still works, just without sub-second
   latency. Not a blocker, but set expectations.

3. **No-Mac development friction.** `npx cap add ios` and `pod install` are
   macOS-only, and editing the Xcode project (Info.plist, entitlements, adding
   Swift plugin files) normally means Xcode. Without a Mac, all of that happens by
   editing project files directly and validating via CI runs — a slow feedback
   loop (minutes per iteration vs. seconds locally). It's doable (this whole plan
   assumes it) but the iteration cost is real.
   - **Optional mitigation:** rent a cloud Mac (e.g. MacinCloud) for a few hours
     to do the one-time fiddly bits — initial scaffold, Xcode capability config —
     then hand ongoing builds to CI. Cheaper in time than fighting `.pbxproj` by
     hand. Not required, just an option.

---

## Phased plan

### Phase 0 — Prerequisites (free, no account)
- Repo already public → free macOS CI confirmed.
- Decide: pure no-Mac (edit project files + CI only) vs. a few hours of cloud Mac
  for setup. Recommend cloud Mac for Phase 1's scaffold if the `.pbxproj` editing
  proves painful.
- **Do NOT buy the Apple account yet** — not needed until Phase 3.

### Phase 1 — iOS scaffold + Simulator build in CI (free, no account)
Goal: prove the web app loads in an iOS WKWebView.
- A macOS CI job runs `npm ci` → `npx cap add ios` (generates `ios/`) →
  commits the generated `ios/` back to the repo (Capacitor projects commit the
  native folder; `Pods/` stays gitignored and is restored by `pod install` in CI).
- Add iOS config mirroring the Android decisions:
  - `Info.plist`: `UIBackgroundModes = [audio]` (for later), and App Transport
    Security settings to permit the user's chosen server — including plain-http
    LAN servers, mirroring the Android `usesCleartextTraffic` decision
    (`NSAppTransportSecurity` → `NSAllowsArbitraryLoads` or scoped exceptions).
  - Bundle the same `www/` setup + error pages.
- Build for the Simulator (`xcodebuild -scheme App -sdk iphonesimulator`, **no
  signing**). Optionally boot the Simulator in CI and screenshot to confirm the
  first-run setup screen renders.
- Deliverable: green CI job producing a Simulator build; the web UI loads on iOS.

### Phase 2 — Port the native plugins to Swift (free, Simulator-tested)
- **`ServerConfigPlugin.swift`** — port of the Kotlin plugin: `UserDefaults` for
  the saved + known-servers list, `URLSession` health check against `/api/health`
  (https-first, http fallback), reconfigure the Capacitor bridge's server URL,
  and reload. (Bridge server-URL reconfiguration differs from Android — research
  the Capacitor iOS API; may require a bridge reload rather than activity
  recreate.)
- **Background audio (Swift)** — no foreground service; instead a plugin that
  configures/activates `AVAudioSession(.playback)` and relies on the `audio`
  background mode. The Android notification's Stop button maps to iOS Control
  Center / Now Playing via `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`
  (optional polish, not required for MVP).
- Test each plugin in the Simulator. **Caveat:** true background-audio behavior
  can only be validated on a real device (Simulator background handling differs),
  so final sign-off waits for Phase 3.
- The JS bridge (`nativeBridge.js` in the *nightlight* repo) already calls these
  plugins by name; the Swift plugins must expose the **same** plugin + method
  names so the existing web code works unchanged on both platforms.

### Phase 3 — Signing, TestFlight, real device ($99 account required)
- Enrol in the Apple Developer Program ($99/yr).
- In the Apple portal / App Store Connect: register App ID `com.sauso.nightlight`,
  create the app record.
- Generate an **App Store Connect API key** (`.p8` + key id + issuer id) — this is
  what lets a no-Mac CI pipeline manage signing without manual cert wrangling.
- GitHub secrets: the API key (base64 `.p8`, key id, issuer id) and team id.
- macOS CI job using **Fastlane**: `match`/`sigh` to obtain signing assets via the
  API key, build a **signed IPA**, and `upload_to_testflight`.
- Install the **TestFlight** app on the iPhone, install the build, and **validate
  background audio + WebRTC-vs-HLS for real** — the moment of truth for risk #1
  and #2 above.
- Distribution stays TestFlight (no Mac needed for the ongoing pipeline). Public
  App Store release is a separate, later decision (review process, screenshots,
  privacy nutrition labels, etc.).

---

## What's free vs. what needs the $99 account

| Work | Cost |
| --- | --- |
| Scaffold `ios/`, Simulator builds in CI | Free (public repo macOS runners) |
| Swift plugin ports, Simulator testing | Free |
| Signed IPA, real-device install, TestFlight | Needs Apple Developer Program ($99/yr) |

So the account purchase can be deferred until the app demonstrably works in the
Simulator — de-risking the spend.

---

## Suggested order of work when starting
1. Phase 1 scaffold job — get `ios/` generated and a Simulator build green first;
   this also surfaces any WKWebView-loading issues immediately.
2. Port `ServerConfigPlugin` to Swift (simpler of the two, no OS-behavior risk) —
   proves the plugin-porting pattern end to end in the Simulator.
3. Port background audio to Swift — the harder one; get it structurally in place,
   knowing final validation needs a device.
4. Only then buy the Apple account and do Phase 3 (signing + TestFlight), which is
   also where background audio gets its real-device test.

## Explicitly out of scope
- Public App Store submission (separate task: review, metadata, privacy labels).
- Renaming/changing `appId` (`com.sauso.nightlight`) — stays as-is for both
  platforms.
- Any change to the web app itself — the iOS shell loads the same live web UI as
  Android; the two custom plugins are the only native surface to reimplement.
