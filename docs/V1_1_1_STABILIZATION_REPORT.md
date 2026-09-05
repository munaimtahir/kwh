# KWH v1.1.1 stabilization report

## A. Files changed

Paths below are relative to the repository root.

| File | Purpose |
|---|---|
| `app/build.gradle.kts` | Set version name 1.1.1 and code 4; preserve dependencies, namespace and application ID. |
| `app/src/main/java/com/example/kwh/di/DatabaseModule.kt` | Register the full existing migration chain in production. |
| `app/src/main/java/com/example/kwh/billing/BillingCycleCalculator.kt` | Independently resolve the next month's intended anchor. |
| `app/src/main/java/com/example/kwh/repository/MeterRepository.kt` | Prefer carry-forward in both calculation paths; route ordinary submissions through atomic validation. |
| `app/src/main/java/com/example/kwh/data/MeterDao.kt` | Transactional chronological-neighbor, date duplicate and timestamp checks before insertion. |
| `app/src/main/java/com/example/kwh/ui/home/HomeViewModel.kt` | Reject non-finite input, guard repeated saves and return save results to the dialog. |
| `app/src/main/java/com/example/kwh/MainActivity.kt` | Keep failed entries editable, show warnings with Cancel / Correct and prevent repeated saves. |
| `app/src/main/res/values/strings.xml` | Add the Correct action label. |
| `app/src/main/java/com/example/kwh/ui/history/HistoryViewModel.kt` | Correct CSV v2 quoting, escaped-quote parsing and multiline records; prevent header text from becoming meter metadata. |
| `app/src/main/java/com/example/kwh/reminders/MeterReminderWorker.kt` | Handle notification permission rejection so reminder rescheduling continues; resolve lint error. |
| `app/src/main/java/com/example/kwh/reminders/SwitchReminderWorker.kt` | Handle notification permission rejection so daily checks continue; resolve lint error. |
| `app/src/test/java/com/example/kwh/billing/DefaultBillingCycleCalculatorTest.kt` | Correct old defect-encoding expectation; test every date across normal/leap years and rollover. |
| `app/src/test/java/com/example/kwh/data/MeterDatabaseMigrationTest.kt` | Exercise production-provider v1/v2/v3 upgrades and fresh v4 persistence/reopen. |
| `app/src/test/java/com/example/kwh/data/ReadingSubmissionTest.kt` | Verify concurrent duplicate rejection against real Room. |
| `app/src/test/java/com/example/kwh/repository/MeterRepositoryCycleStatsTest.kt` | Update carry-forward regression expectation. |
| `app/src/test/java/com/example/kwh/repository/StabilizationTest.kt` | Carry-forward, fallback, backdating/deletion, validation and configured threshold boundaries. |
| `app/src/test/java/com/example/kwh/repository/MeterRepositoryEnvironmentTest.kt` | Three-meter sibling selection with carry-forward and exact switch outcome assertions. |
| `app/src/test/java/com/example/kwh/ui/history/HistoryViewModelTest.kt` | CSV v2 export/import round trip with quoted notes, settings, environment and activation records. |
| `app/src/test/java/com/example/kwh/ui/home/HomeViewModelTest.kt` | Warning/correction and rapid-save regressions. |
| `docs/discovery/KWH_DEVELOPMENT_CONTEXT.md` | Append stabilization baseline while preserving historical findings. |
| `docs/V1_1_1_STABILIZATION_REPORT.md` | Record scope, verification, data safety and release limitations. |

## B. Bugs fixed

| Defect | Original behavior / root cause | Correction | Regression evidence |
|---|---|---|---|
| Migration registration | Production registered only 1→2, so Room could not find a full upgrade chain. | Register all three existing migrations. | Open populated v1/v2/v3 databases through the actual provider and verify final schema/data; fresh v4 reopen includes switch outcomes. |
| Short months | Adding a month to an already-clipped start propagated February's day into March. | Resolve each end month from the requested anchor. | Anchors 1/29/30/31 checked daily from December 2022 through January 2025 for containment, exact anchors and adjacency. |
| Single reading / omitted carry-forward | First in-cycle reading took priority over the pre-cycle baseline. | Prefer latest pre-cycle reading in both repository paths, otherwise earliest in-cycle. | 14,000→14,125 = 125; multiple readings, no carry, backdated insertion and baseline/latest deletion; reactive/one-shot equality. |
| Lower / inconsistent readings | Ordinary insertion had no chronological checks. | Reject values below previous or above next chronological reading; show actionable warning without discarding input. | Neighbor, earliest-entry, correction and non-finite input tests. |
| Duplicate submissions | Neither saving guards nor atomic duplicate checks existed. | Transactional same-meter/date/value and timestamp checks plus UI/ViewModel guards. | Ten concurrent real Room submissions leave one row; repeated ViewModel saves and date/timestamp cases. |
| CSV safety | Unquoted text shifted columns, doubled quotes were discarded, and header text could fabricate an environment for an ungrouped meter. | Escape text fields, parse doubled quotes/multiline records, and accept metadata only from valid reading rows; no schema change. | Grouped and ungrouped round trips with comma-containing environment, quotes/newline in notes, thresholds, anchor, buffer and activation history. |
| Notification permission rejection | Both workers could throw before rescheduling when notification permission was rejected. | Catch `SecurityException` around posting, preserving the rest of the worker flow. | Full lint validation and existing reminder/snooze suite; device permission-revocation smoke test remains unverified. |

## C. Data safety

- Schema remains v4. No entity or schema changes.
- Existing migration SQL is unchanged; only production registration changed.
- No destructive migration fallback, table drop, data reset or historical rewrite was introduced.
- Existing readings, environments, activation histories and switch outcomes remain preserved. Test deletion/recreation operates only on isolated test databases and fake data.
- CSV schema remains v2. Import and Undo retain the historical restore path, including legacy anomalies. CSV v2 imports into an existing selected meter and does not encode a complete database backup or switch outcomes.

## D. Test results

Commands:

```text
./gradlew clean assembleDebug testDebugUnitTest lint assembleRelease
./gradlew testDebugUnitTest --tests com.example.kwh.ui.history.HistoryViewModelTest
./gradlew assembleDebug testDebugUnitTest lint assembleRelease assembleDebugAndroidTest connectedDebugAndroidTest --continue
./gradlew assembleDebug testDebugUnitTest lint assembleRelease assembleDebugAndroidTest --continue
```

The initial clean succeeded. An initial build overlapped the addition of a string resource and required a fresh invocation. The first complete unit run found the CSV escaping defect (69 passed, 1 failed); after correction, the targeted History suite passed (3/3), then the full suite passed (70/70). Lint subsequently identified two existing notification-permission errors. After adding rejection handling, the full suite and lint passed again. A final CSV header guard and ungrouped round-trip regression expanded the suite to 71 passing tests. Obsolete release invocations were stopped before rerunning against the final source.

| Validation | Result |
|---|---|
| Clean | PASS |
| Debug build | PASS — `app/build/outputs/apk/debug/app-debug.apk` |
| Release build | PASS — minification/resource shrinking included; `app/build/outputs/apk/release/app-release.apk` (debug-signed under existing fallback) |
| Unit tests | PASS — 71 tests, 0 failures, 0 errors, 0 skipped |
| Migration tests | PASS — 6 tests, including production v1/v2/v3→v4 and fresh-v4 reopen |
| Billing tests | PASS — 8 tests, including daily continuity across normal/leap years |
| Repository tests | PASS — 21 tests across cycle stats, stabilization and environments |
| Switch advisor | PASS — 10 tests; calibration and outcome assertions also pass in repository tests |
| Reading insertion concurrency | PASS — real Room transaction test; 10 concurrent submissions leave 1 row |
| CSV | PASS — 4 History tests, including grouped and ungrouped v2 round trips |
| ViewModel reading warnings / double-save | PASS — Home suite, 7 tests |
| Instrumentation APK | PASS — `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Device/UI execution | BLOCKED — connected task failed without an available device; API 36 emulator cannot start without KVM support |
| Lint | PASS — 0 errors, 45 warnings; existing dependency/API/resource warnings remain |

Machine-readable unit results: `app/build/test-results/testDebugUnitTest/`. HTML reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. Final-source validation log: `/tmp/kwh-verified-validation.log`. Earlier session logs: `/tmp/kwh-initial-validation.log`, `/tmp/kwh-validation.log`, `/tmp/kwh-csv-retest.log`, `/tmp/kwh-final-validation.log`, `/tmp/kwh-release-validation.log`, `/tmp/kwh-emulator.log`.

## E. Remaining known issues

- Device/UI execution requires a working Android device or emulator. The installed API 36 emulator cannot start on this host because KVM CPU support is unavailable.
- No production keystore configuration exists in this checkout. The existing release configuration falls back to debug signing; a compiled release artifact is not a production-signed update.
- Previously stored or imported inconsistent cumulative readings are preserved. This sprint prevents invalid ordinary insertion; it does not implement reset/replacement normalization.
- CSV v2's pre-existing scope does not include switch outcomes or full meter recreation. The round trip verifies its supported selected-meter metadata/readings/activations contract.
- Existing AGP 8.4.0 / compileSdk 36 compatibility warning remains; dependencies were not upgraded.

## F. Release readiness

NOT READY — all local build, unit, migration, repository, CSV and lint checks passed. Final Gradle invocation completed successfully (149 actionable tasks). Version metadata confirms 1.1.1 / code 4 and application ID `com.vexel.kwh`. Production signing and device/UI verification remain outstanding; do not distribute the debug-signed release APK as an update to production users.
