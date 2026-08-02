# QA Checklist
- [x] Install/launch on API 26, 30, 34. (Pixel 6 / Pixel 4a / Nexus 5X emulator)
- [x] First-run empty state shows.
- [x] Meter create → reading add → latest visible.
- [x] Reminder fires at next scheduled time; snooze action works (M4).
- [x] CSV export opens share sheet; file readable (M3).

## Environment Switching (M6–M10) — API 35 (Pixel 5 / Android_15_Test emulator)
- [x] Create environment, assign two meters, group renders on Home with "Ungrouped" bucket unaffected.
- [x] "Set as active meter" (Meter Settings) opens an activation row; Home shows the "Active" chip; sibling shows "Set as active" link.
- [x] Switch recommendation banner appears on Home once usage nears/exceeds the lowest threshold, with correct OVERDUE styling; absent when projected usage wouldn't cross before cycle end (verified both cases).
- [x] "Mark switched" closes the outgoing meter's activation, activates the target, logs a `SwitchOutcomeEntity`, and populates the "Switch accuracy" indicator (days late/early, units margin, sample size) — safety buffer correctly unchanged below the 3-outcome calibration minimum.
- [x] Meter Settings: environment create/assign/unassign, safety-buffer field, activation history list — all render and persist correctly; app data survives an `adb install -r` reinstall.
- [x] History screen (readings, CSV export trigger) and Settings screen (dark theme, snooze) unaffected by the feature.
- [x] Full logcat scan across the session: no exceptions/fatals for `com.example.kwh` (one benign `HWUI` software-renderer format warning, expected on the swiftshader emulator).
- [x] `./gradlew connectedAndroidTest` green (2/2) after fixing a pre-existing, unrelated stale `HomeScreenTest` that referenced a long-removed `HomeScreen` API (predated this initiative; blocked instrumented tests entirely until fixed).
