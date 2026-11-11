#!/usr/bin/env bash
# Host-side test peripheral: advertise known payloads from this machine's BlueZ controller so a real phone can scan
# them (docs/phase2/README.md X1). Advertising only: it never accepts connections and never pairs.
#   peripheral.sh list                     profile names (scripts/peripheral/profiles.json)
#   peripheral.sh start <profile> [...]    advertise in the background until `stop`
#   peripheral.sh stop | status
#   ANDROID_SERIAL=<phone> peripheral.sh probe <profile> [seconds]
#       launch the app on the phone with the REAL scanner, scan, advertise the profile and print what the app decoded
#       for the host's row. Exit 0 = the row appeared. The phone must be unlocked and in range (it is, by USB).
# Needs: bluetoothd running, python3-dbus and python3-gi (system packages), no root. Profiles use type "peripheral",
# which advertises from the controller's PUBLIC address, so the row is found by address.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
S="$REPO_ROOT/scripts"
PY="$S/peripheral/peripheral.py"
PIDFILE="$OUT/peripheral.pid"
LOG="$OUT/peripheral.log"

host_address() {
  busctl get-property org.bluez /org/bluez/hci0 org.bluez.Adapter1 Address | sed -E 's/^s "(.*)"$/\1/'
}

stop_adv() {
  if [ -f "$PIDFILE" ]; then kill "$(cat "$PIDFILE")" 2>/dev/null || true; rm -f "$PIDFILE"; sleep 1.5; fi
}

start_adv() {
  stop_adv
  python3 "$PY" run "$@" >"$LOG" 2>&1 &
  echo $! >"$PIDFILE"
  for _ in $(seq 1 20); do
    grep -q '^advertising:' "$LOG" && return 0
    grep -q 'failed' "$LOG" && { cat "$LOG" >&2; return 1; }
    sleep 0.5
  done
  echo "advertising did not start; log:" >&2; cat "$LOG" >&2; return 1
}

probe() {
  local profile="$1" max="${2:-40}" addr
  [ -n "${ANDROID_SERIAL:-}" ] || { echo "set ANDROID_SERIAL to the physical phone" >&2; exit 2; }
  case "$ANDROID_SERIAL" in emulator-*) echo "needs a physical phone (the emulator has no radio)" >&2; exit 2 ;; esac
  addr=$(host_address)
  if adb shell dumpsys window | grep -q 'mDreamingLockscreen=true'; then echo "phone is locked: unlock it and re-run" >&2; exit 1; fi
  trap stop_adv EXIT
  "$S/app.sh" install >/dev/null; "$S/app.sh" clear >/dev/null; "$S/app.sh" grant >/dev/null
  "$S/app.sh" launch >/dev/null
  "$S/ui.sh" wait device_list_screen 15 >/dev/null 2>&1 || {
    echo "the app did not come up: the phone probably locked itself (unlock it and keep it awake) or the install failed" >&2; exit 1; }
  sleep 1.6; "$S/ui.sh" tap scan_button >/dev/null; sleep 4   # scan first, so the host is a NEW device (the list must show it)
  start_adv "$profile"
  local start=$SECONDS found=no
  while [ $((SECONDS - start)) -lt "$max" ]; do
    if "$S/ui.sh" dump | grep -q "device_row_$addr"; then found=yes; break; fi
  done
  if [ $found = no ]; then
    echo "NOT SEEN: profile '$profile' (host $addr) did not appear within ${max}s." >&2
    echo "If other profiles work, the platform may be filtering this format: iBeacon, Eddystone and extended advertising are hidden from apps declaring neverForLocation (docs/phase2/x1-findings.md)." >&2
    exit 1
  fi
  "$S/ui.sh" tap "device_row_$addr" >/dev/null; "$S/ui.sh" wait detail_screen 8 >/dev/null
  echo "SEEN: profile '$profile' as $addr after $((SECONDS - start))s"
  "$S/ui.sh" dump | grep -E 'detail_decoded_|decoded_|detail_radio_|detail_ad_count' | sed -E "s/ desc=.*//; s/ bounds=.*//"
}

case "${1:-}" in
  list) python3 "$PY" list ;;
  start) shift; [ $# -ge 1 ] || { echo "usage: $0 start <profile> [...]" >&2; exit 2; }; start_adv "$@"; echo "advertising from $(host_address): $*" ;;
  stop) stop_adv; echo stopped ;;
  status) if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then tail -1 "$LOG"; else echo "not advertising"; fi ;;
  probe) shift; [ $# -ge 1 ] || { echo "usage: $0 probe <profile> [seconds]" >&2; exit 2; }; probe "$@" ;;
  *) sed -n 2,13p "$0"; exit 2 ;;
esac
