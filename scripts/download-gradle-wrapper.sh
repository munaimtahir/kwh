#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"
WRAPPER_JAR="$ROOT_DIR/gradle/wrapper/gradle-wrapper.jar"
PROPERTIES_FILE="$ROOT_DIR/gradle/wrapper/gradle-wrapper.properties"

if [[ -f "$WRAPPER_JAR" ]]; then
  echo "Gradle wrapper JAR already present at $WRAPPER_JAR"
  exit 0
fi

if [[ ! -f "$PROPERTIES_FILE" ]]; then
  echo "Cannot locate gradle-wrapper.properties at $PROPERTIES_FILE" >&2
  exit 1
fi

DISTRIBUTION_URL=$(grep -E '^distributionUrl=' "$PROPERTIES_FILE" | cut -d'=' -f2-)
if [[ -z "$DISTRIBUTION_URL" ]]; then
  echo "Unable to determine distributionUrl from $PROPERTIES_FILE" >&2
  exit 1
fi

DISTRIBUTION_FILE="$(basename "$DISTRIBUTION_URL")"
GRADLE_VERSION="${DISTRIBUTION_FILE#gradle-}"
GRADLE_VERSION="${GRADLE_VERSION%-bin.zip}"
GRADLE_VERSION="${GRADLE_VERSION%-all.zip}"

if [[ -z "$GRADLE_VERSION" ]]; then
  echo "Failed to parse Gradle version from distributionUrl=$DISTRIBUTION_URL" >&2
  exit 1
fi

WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-wrapper.jar"

TMP_FILE=$(mktemp)
trap 'rm -f "$TMP_FILE"' EXIT

if command -v curl >/dev/null 2>&1; then
  curl -fsSL "$WRAPPER_URL" -o "$TMP_FILE"
elif command -v wget >/dev/null 2>&1; then
  wget -q "$WRAPPER_URL" -O "$TMP_FILE"
else
  echo "Neither curl nor wget is available to download $WRAPPER_URL" >&2
  exit 1
fi

install -m 0644 "$TMP_FILE" "$WRAPPER_JAR"
echo "Downloaded Gradle wrapper JAR $WRAPPER_URL"
