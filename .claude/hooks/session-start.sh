#!/bin/bash
# Prepares a fresh Claude Code on the web container to build and test this Android app:
# installs the Android SDK (fresh containers don't have one) and points Gradle at it.
# Build secrets (FINANCE_APP_ANTHROPIC_API_KEY, ENABLE_BANKING_PRIVATE_KEY, ANDROID_KEYSTORE_B64,
# ANDROID_KEYSTORE_PASSWORD) come from the cloud environment's variables — never from here.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK_ROOT=/opt/android-sdk
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "$SDK_ROOT/cmdline-tools"
  tmp=$(mktemp -d)
  curl -sSL -o "$tmp/cmdtools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o "$tmp/cmdtools.zip" -d "$tmp"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

if [ ! -d "$SDK_ROOT/platforms/android-34" ] || [ ! -d "$SDK_ROOT/build-tools/34.0.0" ]; then
  yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$SDK_ROOT" "platforms;android-34" "build-tools;34.0.0" "platform-tools" >/dev/null
fi

# local.properties is gitignored; it only tells Gradle where the SDK is.
PROPS="$CLAUDE_PROJECT_DIR/local.properties"
if ! grep -qs '^sdk.dir=' "$PROPS"; then
  echo "sdk.dir=$SDK_ROOT" >> "$PROPS"
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$SDK_ROOT" >> "$CLAUDE_ENV_FILE"
fi

# Download Gradle and the project's plugins/dependencies now, so the cached container starts warm.
cd "$CLAUDE_PROJECT_DIR"
./gradlew --quiet help >/dev/null
