# Developer TODO (cut & paste into issues)

- [ ] Room: bump DB version; add `billing_anchor_day` and `thresholds_csv` to `MeterEntity`.
- [ ] Room migration with defaults (anchor=1, thresholds="200,300").
- [ ] Dao: add queries for baseline/latest inside cycle.
- [ ] Repository: add `getCycleStats(meterId)` and Flow that emits `CycleStats` for list.
- [ ] UI Home: show cycle window, used & projected units; entry point to Settings.
- [ ] UI History: mini chart over current cycle, baseline marker.
- [ ] Settings screen (per meter): anchor day selector; thresholds editor; default reminder=7.
- [ ] WorkManager: extend to compute `CycleStats` and send threshold/reminder notifications.
- [ ] CSV import/export: include new fields (anchor, thresholds).
- [ ] Tests: calculator, migration, repo, worker.
- [ ] Docs: README update with new screenshots.
