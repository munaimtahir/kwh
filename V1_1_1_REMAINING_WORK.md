# KWH v1.1.1 — remaining work and second-machine handoff

## Current status

The stabilization implementation is on `main`. Release status is **NOT READY** until device validation and production signing are completed.

Completed implementation:

- Production registration of Room migrations 1→2→3→4; schema remains v4.
- Independently clamped monthly billing anchors, including February and leap years.
- Carry-forward consumption in reactive and one-shot repository calculations.
- Chronological-neighbor validation, editable Cancel / Correct warnings, transactional duplicate protection, and repeated-save guards.
- CSV v2 escaping, multiline/escaped-quote parsing, and protection against header text becoming environment metadata.
- Notification permission rejection handling in both reminder workers.
- Version name 1.1.1, version code 4; application ID and namespace unchanged.

First-machine validation passed: debug build, minified/shrunk release build, instrumentation-test APK compilation, **71 unit tests**, and lint (**0 errors, 45 warnings**). Tests include 6 migration/persistence tests, 8 billing tests, 21 repository tests, 10 switch-advisor tests and 4 CSV/history tests. These counts are subsets of the 71 total, not additional totals.

Unfinished: connected/UI execution (no connected device; installed emulator cannot boot without KVM) and production signing (no production keystore configured). The first-machine release APK was verified as **Android Debug signed** and must not be used as a production update.

## 1. Get the exact source and read the baseline

On a clean second-machine checkout:

```bash
git clone git@github.com:munaimtahir/kwh.git
cd kwh
git switch main
git pull --ff-only origin main
git status --short
git rev-parse HEAD
```

If the checkout already exists, inspect its worktree before pulling; preserve local work and do not reset it.

Read:

1. This document.
2. `docs/V1_1_1_STABILIZATION_REPORT.md` (file inventory, fixes, evidence and limitations).
3. `docs/discovery/KWH_DEVELOPMENT_CONTEXT.md`, especially the appended stabilization section.
4. The discovery technical-debt, forecasting-baseline, data-model, architecture and feature-matrix documents.

`docs/KWH_CONTEXT_PACK/` is the historical discovery bundle. Its older defect descriptions are not instructions to undo the fixes. The appended section in `docs/discovery/KWH_DEVELOPMENT_CONTEXT.md` describes the updated implementation.

Build artifacts, local SDK configuration, keystores and `/tmp` logs from the first machine are not transferred through Git. Regenerate evidence on this machine. Some older setup documentation references SDK 34; current `app/build.gradle.kts` specifies compile/target SDK 36 and JVM 17. Use the checked-in Gradle wrapper and existing dependency versions.

## 2. Establish device testing

- Configure JDK 17 and Android SDK 36; set the local SDK path through ignored `local.properties` or the normal SDK environment configuration.
- Connect an authorized Android test device or start a working emulator. Confirm it appears as `device`, not `unauthorized` or `offline`, in `adb devices`.
- Use a dedicated test device/profile with synthetic data. Standard debug test APKs have the production application ID and must not displace a real user's production installation.
- Prefer coverage of the supported older Android range (minSdk 24) and Android 33+ notification permissions where practical. Record actual API levels tested rather than implying all versions were exercised.

Run sequentially, preserving command output and exit status:

```bash
./gradlew clean
./gradlew assembleDebug testDebugUnitTest lint assembleDebugAndroidTest --console=plain
./gradlew connectedDebugAndroidTest --console=plain
```

Inspect `app/build/reports/tests/testDebugUnitTest/`, `app/build/test-results/testDebugUnitTest/`, `app/build/reports/lint-results-debug.html` and generated connected-test reports. The first-machine baseline is 71 passing unit tests. If adding tests, report the new count and why it changed.

## 3. Finish UI and regression verification

The existing connected suite is limited to home-screen tests. Compilation alone does not prove the new reading-dialog behavior. Add focused instrumented tests where practical and record manual evidence for the remaining checks:

- Valid reading saves once and closes the dialog. Rapid double-taps leave one record.
- A lower reading shows previous/new values, preserves the typed entry, and provides Cancel / Correct. Correcting it permits saving; cancelling adds nothing.
- Backdated entries are checked against both chronological neighbors, including an entry before the first stored reading. Same-date/value duplicates are blocked; equal values on different dates remain permitted.
- February 26 = 14,000 and March 10 = 14,125, anchor 1: current-cycle usage is 125. Backdating and deleting baseline/latest entries update visible stats; Undo restores the intended record. Use controlled test clocks/fixtures where required rather than changing production calculations.
- Anchor 29/30/31 month transitions agree with the deterministic billing tests. Dashboard, detail and history agree on cycle consumption and forecasts.
- Multiple meters, environments, activation tracking, least-used sibling recommendations, switch date/urgency, safety buffer, calibration and outcome logging remain functional.
- Reading reminders, switch alerts and snooze work with permission granted. Denial/revocation does not crash workers or interrupt subsequent scheduling. Record the actual device/API and checks performed.
- CSV v2 round trips preserve supported metadata, readings and activation history for grouped and ungrouped meters, including commas, quotes and multiline notes.
- Historical reading navigation and dark-mode persistence remain operational.

Fix only stabilization regressions exposed by these checks. Investigate failures, rerun affected tests, then rerun the broader suite after the source is stable. Do not upgrade unrelated dependencies or suppress lint errors to claim success.

## 4. Configure and verify production signing

Obtain the existing release signing material through the owner's secure mechanism. Do not generate a replacement key and assume it can update installed users. If the material is absent, report that external blocker explicitly; never print passwords or private key material.

The existing build reads ignored root-level `keystore.properties` with these property names:

```properties
storeFile=<path to the authorized existing keystore>
storePassword=<provided securely>
keyAlias=<existing alias>
keyPassword=<provided securely>
```

Ensure both credentials and the actual keystore path are excluded from Git. Never force-add secrets. Keep version 1.1.1 / code 4 unless verified distribution history shows a version-code conflict requiring a documented release decision.

With signing configured, build:

```bash
./gradlew assembleRelease --console=plain
```

If the delivery channel requires an Android App Bundle, also run `./gradlew bundleRelease --console=plain`.

Use the installed SDK's `apksigner verify --print-certs` on `app/build/outputs/apk/release/app-release.apk`. Verify its certificate against the authorized previous release certificate; merely seeing a non-debug certificate is insufficient. For Play-managed signing, distinguish the upload certificate from the certificate on delivered APKs and verify through the established delivery process. Record non-secret certificate fingerprints, application ID, version name/code, artifact SHA-256 and build result.

## 5. Verify a data-preserving update

- On a dedicated test installation signed compatibly with the candidate, prepare an older supported build/database fixture containing meters, readings, settings and, where supported, environments, activations and switch outcomes.
- Inventory records and relationships before updating. Install the matching signed candidate as an update **without uninstalling, clearing app data or resetting the database**.
- Verify startup, record IDs/counts/values, settings, relationships, activation history and switch outcomes after the update and after reopening the app.
- Unit migration coverage already exercises production-provider v1/v2/v3→v4 and fresh-v4 persistence. Record which real-device upgrade path(s) you additionally exercised; do not represent unit coverage as on-device coverage.
- A signature mismatch is a blocker to investigate, not a reason to uninstall. CSV v2 is not a full database backup: it omits switch outcomes and full meter recreation.

## 6. Close the release gate and hand off evidence

- Rerun builds, unit tests, lint and connected tests after any final fixes.
- Update `docs/V1_1_1_STABILIZATION_REPORT.md`, the stabilization section of `docs/discovery/KWH_DEVELOPMENT_CONTEXT.md`, and this document with second-machine results. Preserve historical findings and first-machine evidence.
- Include tested commit SHA, device/API details, actual counts/results, upgrade evidence, remaining warnings, certificate verification and artifact locations/checksums. Keep secret credentials and user database contents out of documentation and commits.
- Mark **READY FOR v1.1.1 RELEASE** only after compatible production signing, successful on-device/UI verification and the data-preserving update checks. Otherwise retain **NOT READY** and identify exact blockers.
- Report any code changes and their verification. Release publishing/uploading is a separate action; preparing and validating the release does not itself authorize distribution.

## Copyable prompt for the second-machine agent

```text
Continue the existing KWH Android v1.1.1 stabilization sprint from current main in munaimtahir/kwh. Read V1_1_1_REMAINING_WORK.md in the project root first, then docs/V1_1_1_STABILIZATION_REPORT.md and the authoritative docs/discovery baseline, including the appended v1.1.1 section.

The implementation is already present. The first machine passed 71 unit tests, debug and minified release builds, instrumentation APK compilation, and lint with 0 errors / 45 warnings. Its remaining blockers were no usable Android device/emulator and no production signing configuration. Its release APK was debug-signed. Build artifacts and /tmp logs are not in Git.

Execute the remaining-work document on this machine: run connected/UI tests, add focused regression coverage for the reading dialog where practical, complete the listed smoke checks, fix any stabilization failures and retest, securely configure the existing authorized signing key, verify certificate compatibility and version metadata, build the release artifact, and prove a data-preserving update on a dedicated test installation. Update the report and root checklist with actual evidence and a truthful READY / NOT READY decision.

Preserve all historical data and the canonical repository/domain calculation paths. Do not introduce destructive migration, uninstall/clear a user's app to bypass a signing issue, change the schema or CSV version unnecessarily, upgrade unrelated dependencies, or add roadmap features. Never commit signing credentials, keystores or user databases. Do not publish a release as part of this task. Work autonomously through ordinary fixes and retests; stop only for a genuine external blocker such as unavailable authorized signing material or device access, and identify it precisely.
```
