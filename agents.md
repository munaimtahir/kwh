# Agent Setup Instructions

- The repo is already cloned to `/app`. Do **not** re-clone.
- Run setup via:
  ```bash
  bash ./setup.sh
  ```
- Success criteria: script exits 0 and writes `.setup_ok`.
- Post-setup quick check:
  ```bash
  bash ./scripts/smoke.sh
  ```
- Avoid interactive commands or heavy downloads.
- Do not install Android SDK here; builds can happen in CI or locally.
