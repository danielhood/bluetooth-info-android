#!/usr/bin/env bash
# Runs the real-radio instrumented tests on a physical device: ANDROID_SERIAL=<serial> scripts/real-device-tests.sh
set -euo pipefail
. "$(dirname "$0")/lib.sh"
[ -n "${ANDROID_SERIAL:-}" ] || { echo "set ANDROID_SERIAL to the physical device (adb devices)" >&2; exit 2; }
exec "$REPO_ROOT/scripts/gw.sh" connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.danielhood.btscan.RealScannerOnDeviceTest
