#!/usr/bin/env bash
# Black-box smoke test of the installed debug APK on the target device (emulator by default).
# Defaults to our headless emulator (started if needed). Exit 0 = all checks passed.
# Artifacts: build/agent/shots/smoke-*.png. Covers roadmap acceptance 1, 6, 7 (device side) and 9.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
S="$REPO_ROOT/scripts"

# Default target is our own emulator (started if needed); set ANDROID_SERIAL to smoke-test another device.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  "$S/emu.sh" start; "$S/emu.sh" wait
  export ANDROID_SERIAL="$BTSCAN_EMU_SERIAL"
fi
resolve_serial
echo "smoke target: $ANDROID_SERIAL"
if adb shell dumpsys window | grep -q 'mDreamingLockscreen=true'; then
  echo "device $ANDROID_SERIAL is locked: unlock the screen (and keep it awake) and re-run" >&2; exit 1
fi

fails=0
check() { # description, command...
  local d="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "PASS  $d"; else echo "FAIL  $d"; fails=$((fails + 1)); fi
}
absent() { ! "$S/ui.sh" find "$1"; }
rows() { "$S/ui.sh" dump | grep -c 'id=device_row_' || true; }
wait_rows() { # min count, timeout seconds
  local deadline=$((SECONDS + $2))
  while [ $SECONDS -lt $deadline ]; do [ "$(rows)" -ge "$1" ] && return 0; sleep 1; done; return 1
}
scan_enabled() { "$S/ui.sh" find scan_button | grep -q 'enabled=true'; }
scan_disabled() { "$S/ui.sh" find scan_button | grep -q 'enabled=false'; }
button_text() { "$S/ui.sh" dump | grep -A1 'id=scan_button' | grep -q "text='$1'"; }
sorted_by_signal() { # rssi values in dump order must be non-increasing
  "$S/ui.sh" dump | grep -oE -- "text='-[0-9]+ dBm'" | tr -dc '0-9\n-' |
    python3 -I -c 'import sys; v=[int(x) for x in sys.stdin.read().split()]; sys.exit(0 if len(v)>1 and v==sorted(v, reverse=True) else 1)'
}
count_text() { "$S/ui.sh" find device_count | grep -q "text='$1'"; }
tap_allow() { "$S/ui.sh" tap permission_allow_button || "$S/ui.sh" tap Allow; }

"$S/app.sh" install
"$S/app.sh" clear >/dev/null

echo "-- launch with fake scanner override"
"$S/app.sh" launch --scenario default >/dev/null
check "screen visible"              "$S/ui.sh" wait device_list_screen 20
check "scan button present"         "$S/ui.sh" find scan_button
check "debug label shows override"  "$S/ui.sh" find "scanner=fake scenario=default"
check "app logged the override"     bash -c "'$S/logs.sh' | grep -q 'launch debugConfig=scanner=fake scenario=default'"
"$S/shot.sh" smoke-fake >/dev/null

echo "-- permission flow (acceptance 6): runtime permission is not granted after pm clear"
check "permission needed is shown"  "$S/ui.sh" wait status_message 10
check "message names the permission" "$S/ui.sh" find "permission"
check "start is disabled"           scan_disabled
"$S/shot.sh" smoke-permission-needed >/dev/null
"$S/ui.sh" tap status_action_button >/dev/null || true
check "system dialog appears"       "$S/ui.sh" wait permission_allow_button 10
"$S/shot.sh" smoke-permission-dialog >/dev/null
tap_allow >/dev/null || true
check "ready after granting"        "$S/ui.sh" wait empty_message 10
check "granting did not auto-start" button_text "Start scan"
check "start is enabled"            scan_enabled

echo "-- scan to list (acceptance 1)"
"$S/ui.sh" tap scan_button >/dev/null
check "scanning indicator shown"    "$S/ui.sh" wait scan_indicator 5
check "5 devices within 4s"         wait_rows 5 4
check "list sorted by signal"       sorted_by_signal
check "counter matches"             bash -c "'$S/ui.sh' dump | grep -q \"id=device_count text='[56] devices'\""
check "6th device arrives by 6s"    wait_rows 6 3
"$S/shot.sh" smoke-scanning >/dev/null
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "stop keeps the devices"      bash -c "[ \"\$($S/ui.sh dump | grep -c 'id=device_row_')\" -ge 5 ]"
check "button back to start"        button_text "Start scan"
"$S/ui.sh" tap clear_button >/dev/null
check "clear returns to the initial hint" "$S/ui.sh" wait "Tap Start scan" 5
check "counter shows 0"             count_text "0 devices"

echo "-- background stops the scan (acceptance 9)"
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "scanning again"              wait_rows 1 5
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
check "back on the list"            "$S/ui.sh" wait device_list_screen 10
check "scan stopped while away"     button_text "Start scan"
check "devices were kept"           wait_rows 1 5

echo "-- environment states (acceptance 7)"
"$S/app.sh" launch --scenario bluetooth-off >/dev/null
check "bluetooth off message"       "$S/ui.sh" wait "Bluetooth is turned off" 10
check "start disabled when off"     scan_disabled
"$S/shot.sh" smoke-bluetooth-off >/dev/null
"$S/app.sh" launch --scenario unsupported >/dev/null
check "unsupported message"         "$S/ui.sh" wait "does not support Bluetooth LE" 10
"$S/app.sh" launch --scenario scan-fails >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
"$S/ui.sh" tap scan_button >/dev/null
check "failure banner after 2s"     "$S/ui.sh" wait failure_banner 8
check "retry offered"               "$S/ui.sh" find retry_button
"$S/shot.sh" smoke-failed >/dev/null

echo "-- launch without overrides"
"$S/app.sh" launch >/dev/null
check "screen visible"              "$S/ui.sh" wait device_list_screen 20
check "no debug label"              absent debug_label
check "app logged no override"      bash -c "'$S/logs.sh' | grep -q 'launch debugConfig=none'"

check "no crash in logs"            bash -c "! '$S/logs.sh' | grep -qE 'FATAL EXCEPTION|E/AndroidRuntime'"

[ "$fails" -eq 0 ] && echo "SMOKE OK" || { echo "SMOKE FAILED ($fails)"; exit 1; }
