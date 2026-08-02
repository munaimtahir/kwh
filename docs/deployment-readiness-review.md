# Deployment Readiness Review

_Last updated: 2026-07-30_

## Play Store submission blockers — resolved
- `applicationId` changed from `com.example.kwh` (Play Console hard-rejects this namespace) to `com.vexel.kwh`.
- Release builds now sign with a real keystore (`keystore.properties`, gitignored) instead of the debug key. See README "Release builds" section.
- `compileSdk`/`targetSdk` bumped to 36 (Android 16) for Play's target-API policy window.
- Edge-to-edge enabled (`enableEdgeToEdge()`); verified safe since all screens use Material3 `Scaffold`.
- R8 minification + resource shrinking enabled for release, with explicit keep rules for WorkManager workers and Room; verified end-to-end on a real device (see `docs/QA-Checklist.md`).

## Still outside code scope (Play Console listing side, not this repo)
- A publicly hosted Privacy Policy URL for the Console listing (README has a privacy note, but Console requires a live URL — e.g. GitHub Pages).
- Store listing assets: screenshots, feature graphic, short/full description, Data Safety form (should be straightforward — the app collects nothing and has no network access).
- First upload should enroll in **Play App Signing** so Google can assist with upload-key recovery if `release-keystore.jks` is ever lost.

## Historical note
Earlier revisions of this doc flagged that the Android SDK wasn't provisioned in the working
environment, blocking local Gradle builds. That's no longer the case — the SDK, an AVD, and a full
build/test/instrumented-test/manual-QA loop are all working (see `docs/QA-Checklist.md`).
