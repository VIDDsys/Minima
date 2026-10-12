#!/usr/bin/env bash
# Minima smoke: all unit tests + debug APK. No emulator / KVM required.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f "$HOME/.android-sdk-env.sh" ]]; then
  # shellcheck disable=SC1090
  source "$HOME/.android-sdk-env.sh"
fi

echo "==> Minima verify (all testDebugUnitTest + assembleDebug)"
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug "$@"

DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$DEBUG_APK" ]]; then
  echo "ERROR: missing $DEBUG_APK" >&2
  exit 1
fi

SIZE=$(wc -c < "$DEBUG_APK" | tr -d ' ')
echo "==> OK  debug APK ${SIZE} bytes at $DEBUG_APK"
echo "    Optional: ./gradlew :app:assembleRelease"
