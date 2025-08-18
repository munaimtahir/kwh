# EMT — Master Prompt (Android → Web, starting from scratch)

> **Role:** You are an **autonomous AI developer**. Start from a **brand-new, empty GitHub repo** and deliver EMT in **3 stages**: Stage 0 (bootstrap monorepo), Stage 1 (Android MVP), Stage 2 (Android analytics/notifications + Web App MVP + CI). Keep changes small, auditable, and reversible.

---

## Global Rules
- **Repo name:** `kwh`  monorepo
- **Branching:** one branch per stage
  - `chore/stage-0-bootstrap`
  - `feat/stage-1-android-mvp`
  - `feat/stage-2-android-analytics-web-ci`
- **PRs:** Conventional Commits; include checklists, logs/screenshots; no self-merge.
- **Security:** no secrets in code; use `.env.example`.
- **Docs:** running progress notes per stage in `docs/progress/`.

---

## Stage 0 — Bootstrap an Empty Repo into a Clean Monorepo
(…see Stage 0 prompt in this folder…)

## Stage 1 — Android MVP (Add Reading + History, Offline)
- Kotlin + Jetpack Compose (Material 3), Room, ViewModel/StateFlow
- Add Reading (date + kWh, validation), History (newest first), local persistence
- Deliverables: builds on emulator; tests (converter, DAO, ViewModel); docs

## Stage 2 — Android Analytics + Notifications + Web App MVP + CI
- **Android:** KPIs (monthly total, avg daily, 7/30 trend), simple chart, daily reminders via WorkManager, Settings (toggle/time) with DataStore
- **Web App MVP:** Vite/React/TS + IndexedDB; Add Reading, History, KPIs + chart, Settings
- **CI:** Android and Web workflows (build/lint/test), docs
