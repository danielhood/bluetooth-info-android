#!/usr/bin/env bash
# Runs the MINIFIED release build (signed locally with the debug key) on the emulator (default) or ANDROID_SERIAL.
# Checks R8 did not break startup, navigation graph creation, permissions or the real scan path, and that the
# fake scanner cannot be switched on in release. Exit 0 = pass. Artifacts: build/agent/shots/release-*.png
set -euo pipefail
. "$(dirname "$0")/lib.sh"
S="$REPO_ROOT/scripts"
if [ -z "${ANDROID_SERIAL:-}" ]; then "$S/emu.sh" start; "$S/emu.sh" wait; export ANDROID_SERIAL="$BTSCAN_EMU_SERIAL"; fi
resolve_serial
echo "release smoke target: $ANDROID_SERIAL"
if adb shell dumpsys window | grep -q 'mDreamingLockscreen=true'; then echo "device is locked" >&2; exit 1; fi

fails=0
check() { local d="$1"; shift; if "$@" >/dev/null 2>&1; then echo "PASS  $d"; else echo "FAIL  $d"; fails=$((fails + 1)); fi; }
rows() { "$S/ui.sh" dump | grep -c 'id=device_row_' || true; }
button_text() { "$S/ui.sh" dump | grep -A1 'id=scan_button' | grep -q "text='$1'"; }

BTSCAN_VARIANT=release "$S/app.sh" install
check "installed app is not debuggable" bash -c "! adb shell dumpsys package $PKG | grep -q 'DEBUGGABLE'"
"$S/app.sh" clear >/dev/null
"$S/app.sh" grant >/dev/null

echo "-- launch with a fake-scanner request: must be ignored in release"
"$S/app.sh" launch --scenario default >/dev/null
check "list screen visible"         "$S/ui.sh" wait device_list_screen 15
check "no debug label"              bash -c "! '$S/ui.sh' find debug_label"
check "start enabled"               bash -c "'$S/ui.sh' find scan_button | grep -q 'enabled=true'"
"$S/ui.sh" tap scan_button >/dev/null
check "scanning (real scanner)"     "$S/ui.sh" wait scan_indicator 5
sleep 6
check "no fake devices appeared"    bash -c "[ \"\$($S/ui.sh dump | grep -c 'id=device_row_')\" -eq 0 ] || [ -n \"\${ANDROID_SERIAL##emulator-*}\" ]"
check "no failure banner"           bash -c "! '$S/ui.sh' find failure_banner"
check "platform scan started"       bash -c "'$S/logs.sh' | grep -q 'BtScan/Scan.*scan started'"
"$S/shot.sh" release-scanning >/dev/null
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "stop works"                  button_text "Start scan"

echo "-- device with real advertisers: open a detail screen (navigation graph under R8)"
if [ -n "${ANDROID_SERIAL##emulator-*}" ]; then
  sleep 1.5   # the Start/Stop button ignores taps within 1 s of the previous one
  "$S/ui.sh" tap scan_button >/dev/null
  for _ in $(seq 1 20); do [ "$(rows)" -ge 1 ] && break; sleep 1; done
  first=$("$S/ui.sh" dump | grep -m1 -oE 'id=device_row_[0-9A-F:]+' | sed 's/^id=//' || true)
  check "a real device is listed"   test -n "$first"
  if [ -n "$first" ]; then
    "$S/ui.sh" tap "$first" >/dev/null
    check "detail opens in release"   "$S/ui.sh" wait detail_screen 10
    check "detail shows an address"   bash -c "'$S/ui.sh' find detail_address | grep -qE \"text='([0-9A-F]{2}:){5}[0-9A-F]{2}'\""
    adb shell input keyevent KEYCODE_BACK
    check "back returns to list"      "$S/ui.sh" wait device_list 5
  fi
  "$S/ui.sh" tap scan_button >/dev/null || true
else
  echo "SKIP  detail navigation (emulator has no BLE devices; run on a physical device)"
fi

check "no crash in logs"            bash -c "! '$S/logs.sh' | grep -qE 'FATAL EXCEPTION|E/AndroidRuntime'"
# Put the debug build back so later debug-only tooling works.
BTSCAN_VARIANT=debug "$S/app.sh" install --no-build >/dev/null 2>&1 || true
[ "$fails" -eq 0 ] && echo "RELEASE SMOKE OK" || { echo "RELEASE SMOKE FAILED ($fails)"; exit 1; }
