#!/usr/bin/env bash
# Real-radio smoke test: needs a PHYSICAL device with Bluetooth on, unlocked, and BLE devices in range.
# Usage: ANDROID_SERIAL=<serial> scripts/e2e-real.sh      Exit 0 = pass. Artifacts: build/agent/shots/real-*.png
set -euo pipefail
. "$(dirname "$0")/lib.sh"
S="$REPO_ROOT/scripts"
[ -n "${ANDROID_SERIAL:-}" ] || { echo "set ANDROID_SERIAL to the physical device (adb devices)" >&2; exit 2; }
case "$ANDROID_SERIAL" in emulator-*) echo "this test needs a physical device" >&2; exit 2 ;; esac
resolve_serial
echo "real-radio target: $ANDROID_SERIAL"
if adb shell dumpsys window | grep -q 'mDreamingLockscreen=true'; then
  echo "device is locked: unlock it and re-run" >&2; exit 1
fi
[ "$(adb shell settings get global bluetooth_on | tr -d '\r')" = 1 ] || { echo "Bluetooth is off on the device" >&2; exit 1; }

fails=0
check() { local d="$1"; shift; if "$@" >/dev/null 2>&1; then echo "PASS  $d"; else echo "FAIL  $d"; fails=$((fails + 1)); fi; }
rows() { "$S/ui.sh" dump | grep -c 'id=device_row_' || true; }
wait_rows() { local deadline=$((SECONDS + $2)); while [ $SECONDS -lt $deadline ]; do [ "$(rows)" -ge "$1" ] && return 0; sleep 1; done; return 1; }
button_text() { "$S/ui.sh" dump | grep -A1 'id=scan_button' | grep -q "text='$1'"; }

"$S/app.sh" install
"$S/app.sh" clear >/dev/null
"$S/app.sh" grant >/dev/null
"$S/app.sh" launch >/dev/null     # no override: real scanner

check "list screen visible"         "$S/ui.sh" wait device_list_screen 15
check "no debug label (real mode)"  bash -c "! '$S/ui.sh' find debug_label"
check "ready to scan"               button_text "Start scan"
"$S/ui.sh" tap scan_button >/dev/null
check "scanning indicator shown"    "$S/ui.sh" wait scan_indicator 5
check "at least 1 real device within 20s" wait_rows 1 20
sleep 5
echo "devices listed after 25s: $(rows)"
"$S/shot.sh" real-list >/dev/null
check "no scan failure banner"      bash -c "! '$S/ui.sh' find failure_banner"
first=$("$S/ui.sh" dump | grep -m1 -oE 'id=device_row_[0-9A-F:]+' | sed 's/^id=//' || true)
if [ -n "$first" ]; then
  "$S/ui.sh" tap "$first" >/dev/null
  check "detail screen opens"       "$S/ui.sh" wait detail_screen 10
  check "address is a MAC"          bash -c "'$S/ui.sh' find detail_address | grep -qE \"text='([0-9A-F]{2}:){5}[0-9A-F]{2}'\""
  check "rssi is plausible"         bash -c "'$S/ui.sh' find detail_rssi | grep -qE \"text='-[0-9]+ dBm'\""
  "$S/shot.sh" real-detail >/dev/null
  adb shell input keyevent KEYCODE_BACK
  check "back to list, still scanning" bash -c "'$S/ui.sh' wait device_list 5 && '$S/ui.sh' dump | grep -A1 'id=scan_button' | grep -q \"text='Stop scan'\""
fi
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "stop works"                  button_text "Start scan"
check "devices kept after stop"     wait_rows 1 3
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
check "relaunch works"              "$S/ui.sh" wait device_list_screen 10
check "no crash in logs"            bash -c "! '$S/logs.sh' | grep -qE 'FATAL EXCEPTION|E/AndroidRuntime'"
echo "--- scan log:"; "$S/logs.sh" | grep 'BtScan/Scan' | tail -5
[ "$fails" -eq 0 ] && echo "REAL SMOKE OK" || { echo "REAL SMOKE FAILED ($fails)"; exit 1; }
