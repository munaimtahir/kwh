#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# 1) Python (orchestrator) — only if folder exists and has requirements
if [ -d "services/orchestrator" ]; then
  python3 -m venv services/orchestrator/.venv || true
  services/orchestrator/.venv/bin/pip install --upgrade pip || true
  if [ -f "services/orchestrator/requirements.txt" ]; then
    services/orchestrator/.venv/bin/pip install -r services/orchestrator/requirements.txt || true
  fi
fi

# 2) Web — only if package.json exists
if [ -d "web/emt-frontend" ] && [ -f "web/emt-frontend/package.json" ]; then
  (cd web/emt-frontend && npm ci || true)
fi

# 3) Android — ensure gradle wrapper is executable if present
if [ -f "apps/mobile-android/gradlew" ]; then
  chmod +x apps/mobile-android/gradlew
fi

echo "ok" > .setup_ok

# Optional: run very fast smoke
bash scripts/smoke.sh || true
