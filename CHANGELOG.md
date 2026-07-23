# Changelog

All notable changes to the Nightlight Android app are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning
follows [Semantic Versioning](https://semver.org/) and matches `versionName` in
`android/app/build.gradle` (whose `versionCode` increments on every release). While on
0.x: minor bumps for new features, patch bumps for fixes. History before 0.1.0 exists
only as git history — 0.1.0 is the first tracked release, not the first release.

## [Unreleased]

## [0.1.0] - 2026-07-23

### Added
- First-launch server setup (like the Home Assistant app): the APK no longer has a
  server address baked in. A bundled setup screen asks for it once, verifies a
  Nightlight server actually answers there before saving, and every later launch
  connects straight to it.
- "Can't reach your server" screen when the saved server is unreachable at launch,
  with retry and switch-server options — replaces the dead WebView error page.
- `ServerConfig` Capacitor plugin (get/save/clear/restart) backing both of the above
  and the web app's new "Change server" menu item (nightlight 0.1.0).

### Changed
- Cleartext (http://) traffic disabled in the WebView — server addresses must be
  HTTPS.

### Notes
- Updating from a pre-0.1.0 install shows the setup screen once (the previously
  hardcoded address is not migrated).

[Unreleased]: https://github.com/sauso/nightlight-android/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/sauso/nightlight-android/releases/tag/v0.1.0
