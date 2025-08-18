# kwh — Electricity Meter Tracker (Monorepo Bootstrap)

This repo is prepped to start EMT in three stages (Android → Web). Use the prompts in `docs/roadmap/` to guide autonomous agents.

## Layout
```
apps/
  mobile-android/        # Android app (to be created in Stage 1)
services/
  orchestrator/          # Optional backend/automation (future)
web/
  emt-frontend/          # Web app (Stage 2)
docs/
  roadmap/               # Master prompts
  progress/              # Stage logs
scripts/
  smoke.sh               # Fast post-setup sanity checks
setup.sh                 # Idempotent environment setup
agents.md                # Runner hints for automated agents
```

## Quick start
The platform will clone to `/app`. Do **not** re-clone in scripts.

Run setup (non-interactive):
```bash
bash ./setup.sh
```

Run a very fast smoke check:
```bash
bash ./scripts/smoke.sh
```

Then hand the relevant prompt from `docs/roadmap/` to your agent and work via PRs.
