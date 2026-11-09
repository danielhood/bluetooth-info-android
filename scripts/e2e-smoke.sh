#!/usr/bin/env bash
# Black-box smoke test of the installed debug APK on the target device (emulator by default).
# Defaults to our headless emulator (started if needed). Exit 0 = all checks passed. Artifacts: build/agent/shots/smoke-*.png
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

"$S/app.sh" install
"$S/app.sh" clear >/dev/null

echo "-- launch with fake scanner override"
"$S/app.sh" launch --scenario default >/dev/null
check "screen visible"              "$S/ui.sh" wait device_list_screen 20
check "scan button present"         "$S/ui.sh" find scan_button
check "debug label shows override"  "$S/ui.sh" find "scanner=fake scenario=default"
check "app logged the override"     bash -c "'$S/logs.sh' | grep -q 'launch debugConfig=scanner=fake scenario=default'"
"$S/shot.sh" smoke-fake >/dev/null

echo "-- launch without overrides"
"$S/app.sh" launch >/dev/null
check "screen visible"              "$S/ui.sh" wait device_list_screen 20
check "no debug label"              absent debug_label
check "app logged no override"      bash -c "'$S/logs.sh' | grep -q 'launch debugConfig=none'"
"$S/shot.sh" smoke-plain >/dev/null

check "no crash in logs"            bash -c "! '$S/logs.sh' | grep -qE 'FATAL EXCEPTION|E/AndroidRuntime'"

[ "$fails" -eq 0 ] && echo "SMOKE OK" || { echo "SMOKE FAILED ($fails)"; exit 1; }
