# Nightlight: Rename `nightlight-android` to `nightlight-mobile` — Scope Document

Status: **Not started.** Planning document only, to hand to Claude Code when
work begins.

> **Revised 2026-07-24** after verifying every reference against the live repos.
> Key correction: this is **not** a single-repo task. The rename touches the
> `nightlight` repo too — including a user-facing link whose change deploys to
> production — so it is not purely mechanical. See "Cross-repo changes" below.

## Why

Capacitor projects are designed to hold multiple native platforms as sibling
folders (`android/`, `ios/`) under one shared web layer and
`capacitor.config.json` - not as separate repos per platform. Ahead of
starting iOS development, the repo name should stop implying
Android-specific scope. Still low-risk and mostly mechanical - just a rename
plus reference cleanup - but it spans both repos, and one edit is a released
change.

## What this is NOT

- Not a fork. The existing git history, issues, and everything else stays
  exactly as-is - a rename, not a new repo.
- Not a restructure of the code. `android/` stays exactly where it is;
  `ios/` gets added alongside it later (separate, future work) via
  `npx cap add ios`, not as part of this task.
- Not an `appId`/`appName` change. Confirmed `com.sauso.nightlight` /
  `Nightlight` - neither is Android-specific, and `appId` is permanent once
  published anywhere. Leave both untouched.

---

## Step 1 — Rename on GitHub (the authoritative rename)

**Use the GitHub web UI:** repo → Settings → Repository name → `nightlight-mobile`.

> The `gh` CLI is **not installed** on this machine (verified: `gh: command
> not found`), so `gh repo rename` is not available unless gh is installed
> first. The web UI is the path of least resistance.

GitHub automatically redirects the old URL (`.../nightlight-android`) to the
new one for git operations and web links, indefinitely. So nothing breaks the
moment the name changes - every reference below keeps working via redirect,
making this a cleanup-for-clarity task, not an emergency.

## Step 2 — Update the local clone's remote

```bash
git remote set-url origin https://github.com/sauso/nightlight-mobile.git
```
(Verified: the remote is currently the HTTPS form
`https://github.com/sauso/nightlight-android.git`, so use the HTTPS URL above.)

## Step 3 — Rename the local folder to match

- Rename `nightlight-android/` → `nightlight-mobile/` on disk.
- **The VS Code workspace file needs no change.** Verified:
  `nightlight-project.code-workspace` is a single-root workspace using
  `"path": "."` - it points at the parent folder and never names the subfolder,
  so a folder rename is invisible to it. (The original plan's step to edit it
  was wrong.)

---

## Step 4 — Reference cleanup, `nightlight-mobile` repo

Verified inventory (not guesses). Only these files in this repo contain the
string `nightlight-android`:

- **`package.json`** — `"name": "nightlight-android"` → `nightlight-mobile`.
- **`package-lock.json`** — same `"name"` field (top-level and the root
  `packages.""` entry) → `nightlight-mobile`. (The lock file was missing from
  the original plan.) Regenerating with `npm install` after editing
  package.json is the clean way to keep them in sync.
- **`CHANGELOG.md`** — the link-reference definitions at the bottom
  (`github.com/sauso/nightlight-android/compare/...` and `/releases/tag/...`)
  → point at `nightlight-mobile`. Historical prose entries can stay as a
  record; only the active compare/tag URLs matter.
- **`planning/rename-to-nightlight-mobile-scope.md`** — this document's own
  self-references update naturally as part of the work.

Verified **non-issues** in this repo (the original plan flagged these; none
apply):

- No `nightlight-android` reference in `README.md`.
- No repo-level `CLAUDE.md` exists here.
- No `.github/workflows/` exist here - there is no CI in this repo to update.

---

## Step 5 — Cross-repo changes, `nightlight` repo (NEW — the plan missed this)

The web app repo hardcodes references to this repo. These do **not** get fixed
by the GitHub rename/redirect at the source level - the strings live in code.

- **`frontend/src/pages/About.jsx`** — the "GitHub — Android app" link is
  hardcoded to `https://github.com/sauso/nightlight-android`. This is
  **user-facing**, and editing it **deploys to production**. Per the repo's
  "if it deploys, it gets released" rule, this means:
  - a `nightlight` changelog entry + version bump (patch) + tag + the normal
    build/deploy-to-Unraid cycle.
  So the rename is not zero-release: it carries one small `nightlight` release.
  (The link keeps working via GitHub redirect until then, so there's no rush.)
- **`frontend/src/lib/nativeBridge.js`** — a code comment mentions
  `ServerConfigPlugin.kt in nightlight-android`. Cosmetic; update in the same
  commit as About.jsx.
- **`CHANGELOG.md`** (nightlight) — prose entries reference the android repo by
  name in pairing notes. Historical record; leave as-is.
- **Workspace-level `CLAUDE.md`** (`nightlight-project/CLAUDE.md`) — names
  `nightlight-android/` in three places (the repo list, the native-code path,
  and the versioning instructions). Update all three to `nightlight-mobile/`.

---

## Suggested order of work when starting

1. GitHub web-UI rename (Step 1) - authoritative, everything else is cleanup
   behind the redirect.
2. Local remote + folder rename (Steps 2-3).
3. `nightlight-mobile` repo cleanup (Step 4): package.json + regenerate lock,
   changelog URLs. Sanity build: `npx cap sync android && cd android &&
   ./gradlew assembleRelease` - no Android code changes, so this is a smoke
   test, not expected to surface anything.
4. `nightlight` repo cleanup (Step 5): About.jsx + nativeBridge comment as one
   commit, cut a patch release (changelog/version/tag), deploy to Unraid.
5. Workspace `CLAUDE.md` update.

## Explicitly out of scope

- Adding `ios/` via `npx cap add ios` - separate, later work.
- Any CI/build-pipeline changes - none exist in this repo yet; the iOS
  build/sign/TestFlight pipeline is its own task.
- Changing `appId` (`com.sauso.nightlight`), `appName`, or the native package
  folder `com/sauso/nightlight/` - all permanent/correct, none Android-specific.
