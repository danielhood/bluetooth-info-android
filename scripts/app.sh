#!/usr/bin/env bash
# App control on the target device. Usage:
#   app.sh install [--no-build] | launch [--scenario NAME] [--scanner fake|real] | stop | clear | grant | revoke | state
# Target: $ANDROID_SERIAL, else the emulator if attached, else the single attached device.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
resolve_serial; echo "target: $ANDROID_SERIAL" >&2
APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$REPO_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED_RELEASE_APK="$OUT/app-release-debugsigned.apk"

# Local-only signing of the release build with the standard Android debug key, so a minified build can be run.
sign_release() {
  "$ANDROID_HOME/build-tools/36.1.0/apksigner" sign --ks "$HOME/.android/debug.keystore" \
    --ks-pass pass:android --key-pass pass:android --out "$SIGNED_RELEASE_APK" "$RELEASE_APK"
}

perms() { # runtime permissions the scan needs on this device (the app does not assert neverForLocation, so location too)
  local sdk; sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
  if [ "$sdk" -ge 31 ]; then
    echo android.permission.BLUETOOTH_SCAN android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION
  else
    echo android.permission.ACCESS_FINE_LOCATION
  fi
}

cmd="${1:-}"; shift || true
case "$cmd" in
  install)
    # BTSCAN_VARIANT=release installs the minified release build, signed locally with the debug key.
    if [ "${BTSCAN_VARIANT:-debug}" = release ]; then
      [ "${1:-}" = "--no-build" ] || "$REPO_ROOT/scripts/gw.sh" -q assembleRelease
      sign_release && adb install -r "$SIGNED_RELEASE_APK"
    else
      [ "${1:-}" = "--no-build" ] || "$REPO_ROOT/scripts/gw.sh" -q assembleDebug
      adb install -r -t "$APK"
    fi ;;
  launch)
    scenario=""; scanner=""
    while [ $# -gt 0 ]; do case "$1" in
      --scenario) scenario="$2"; shift 2 ;; --scanner) scanner="$2"; shift 2 ;; *) echo "unknown arg $1" >&2; exit 2 ;; esac; done
    [ -n "$scenario" ] && [ -z "$scanner" ] && scanner=fake
    extras=()
    [ -n "$scanner" ] && extras+=(--es scanner "$scanner")
    [ -n "$scenario" ] && extras+=(--es scenario "$scenario")
    adb logcat -c
    adb shell am force-stop "$PKG"
    adb shell am start -W -n "$PKG/.MainActivity" "${extras[@]}" | grep -E 'Status|Error|Warning' || true ;;
  stop)  adb shell am force-stop "$PKG" ;;
  clear) adb shell pm clear "$PKG" ;;
  grant) for p in $(perms); do adb shell pm grant "$PKG" "$p" && echo "granted $p"; done ;;
  revoke) for p in $(perms); do adb shell pm revoke "$PKG" "$p" && echo "revoked $p"; done ;;
  state)
    echo "pid: $(adb shell pidof "$PKG" | tr -d '\r' || true)"
    adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|ResumedActivity' || true
    for p in $(perms); do echo "permission $p: $(adb shell dumpsys package "$PKG" | grep -m1 "$p:" | sed 's/^ *//')"; done ;;
  *) echo "usage: $0 install|launch|stop|clear|grant|revoke|state" >&2; exit 2 ;;
esac
