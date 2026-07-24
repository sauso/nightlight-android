# Nightlight: Rename `nightlight-android` to `nightlight-mobile` — Scope Document

Status: **Not started.** Planning document only, to hand to Claude Code when
work begins.

## Why

Capacitor projects are designed to hold multiple native platforms as sibling
folders (`android/`, `ios/`) under one shared web layer and
`capacitor.config.json` - not as separate repos per platform. Ahead of
starting iOS development, the repo name should stop implying
Android-specific scope. This is a small, low-risk, mostly-mechanical task -
not a restructure of the project itself, just a rename plus reference
cleanup.

## What this is NOT

- Not a fork. The existing git history, issues, and everything else stays
  exactly as-is - a rename, not a new repo.
- Not a restructure of the code. `android/` stays exactly where it is;
  `ios/` gets added alongside it later (separate, future work) via
  `npx cap add ios`, not as part of this task.

---

## Steps

**1. Rename on GitHub** (the authoritative rename - everything else here is
cleanup around it):
```bash
gh repo rename nightlight-mobile
```
Or via GitHub web UI: repo -> Settings -> Repository name.

GitHub automatically redirects the old URL (`.../nightlight-android`) to the
new one for git operations and web links, indefinitely - this means the
rename is not an emergency-fix-everything-immediately situation, but
references should still be cleaned up for clarity.

**2. Update the local clone's remote:**
```bash
git remote set-url origin https://github.com/sauso/nightlight-mobile.git
```
(or the SSH form, matching whichever this local machine is already using)

**3. Rename the local folder to match** (optional but recommended for
consistency - this is a separate manual step from the GitHub rename, doesn't
happen automatically):
- Rename `nightlight-android/` to `nightlight-mobile/` on disk
- Update the VS Code workspace file (`.code-workspace`) if one exists, since
  it references the folder by path
- Update the workspace-level `CLAUDE.md` (if set up per the earlier VS Code
  multi-root discussion) - it references `nightlight-android/` by name and
  should be updated to match

**4. Search for and update stale references to the old name:**
```bash
grep -rn "nightlight-android" .
```
Likely spots given the project's history this session:
- `README.md`
- Repo-level `CLAUDE.md` (if `/init` was run and it mentions the repo by
  name anywhere)
- Any GitHub Actions workflow files - comments, job names, or artifact
  names that reference "android" specifically should be reviewed once iOS
  jobs are added alongside them (this task doesn't need to add iOS CI jobs
  yet, just make sure nothing about naming actively implies Android-only)
- `package.json`'s `name` field, if it was set to something
  android-specific
- `capacitor.config.json`'s `appName`/`appId` - these should NOT change
  (`appId` especially is permanent once published anywhere) - just confirm
  they were never accidentally Android-specific to begin with

**5. Confirm nothing broke:**
- `git pull` / `git push` still work against the renamed remote
- CI (if any exists yet) still triggers correctly
- The app still builds (`npx cap sync android && cd android &&
  .\gradlew assembleRelease`) - this task shouldn't change any actual
  Android code, so this is a sanity check, not expected to surface issues

---

## Explicitly out of scope for this task

- Adding `ios/` via `npx cap add ios` - separate, later work
- Any GitHub Actions changes beyond fixing naming references - the actual
  iOS build/sign/TestFlight pipeline is its own task
- Changing `appId` (`com.sauso.nightlight`) - this is permanent once
  published anywhere (Play Store, TestFlight) and has no reason to change
  just because the repo/folder name changed
