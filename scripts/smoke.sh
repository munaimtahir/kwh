#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Python quick check
if [ -d "services/orchestrator" ]; then
  if [ -x "services/orchestrator/.venv/bin/python" ]; then
    services/orchestrator/.venv/bin/python -c "import sys; print('python-ok', sys.version.split()[0])"
  else
    echo "python-skip"
  fi
fi

# Web quick check
if [ -d "web/emt-frontend" ] && [ -f "web/emt-frontend/package.json" ]; then
  node -v >/dev/null 2>&1 && echo "node-ok" || echo "node-missing"
else
  echo "web-skip"
fi

# Android quick check
[ -f "apps/mobile-android/gradlew" ] && echo "android-gradle-ok" || echo "android-skip"

echo "smoke-ok"
