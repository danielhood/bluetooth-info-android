#!/usr/bin/env bash
# Headless emulator lifecycle. Usage: emu.sh create|start|wait|stop|status
set -euo pipefail
. "$(dirname "$0")/env.sh"
SER="$BTSCAN_EMU_SERIAL"
LOG="${TMPDIR:-/tmp}/btscan-emulator.log"
IMG="system-images;android-36;google_apis;x86_64"

running() { adb devices | grep -q "^$SER[[:space:]]*device"; }

case "${1:-status}" in
  create)
    avdmanager list avd 2>/dev/null | grep -q "Name: $BTSCAN_AVD" && { echo "AVD $BTSCAN_AVD exists"; exit 0; }
    echo no | avdmanager create avd -n "$BTSCAN_AVD" -k "$IMG" -d pixel_6 >/dev/null
    cfg="$HOME/.android/avd/$BTSCAN_AVD.avd/config.ini"
    sed -i '/^hw.ramSize=/d;/^hw.keyboard=/d;/^disk.dataPartition.size=/d' "$cfg"
    printf 'hw.ramSize=3072\nhw.keyboard=yes\ndisk.dataPartition.size=6G\n' >> "$cfg"
    echo "created $BTSCAN_AVD" ;;
  start)
    if running; then echo "already running ($SER)"; exit 0; fi
    nohup emulator -avd "$BTSCAN_AVD" -no-window -no-audio -no-snapshot -no-boot-anim \
      -gpu swiftshader_indirect -port "${SER##*-}" >"$LOG" 2>&1 &
    echo "starting $BTSCAN_AVD (log: $LOG); run: scripts/emu.sh wait" ;;
  wait)
    timeout 240 adb -s "$SER" wait-for-device
    for _ in $(seq 1 120); do
      [ "$(adb -s "$SER" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 2
    done
    [ "$(adb -s "$SER" shell getprop sys.boot_completed | tr -d '\r')" = 1 ] || { echo "boot timeout; see $LOG" >&2; exit 1; }
    for k in window_animation_scale transition_animation_scale animator_duration_scale; do
      adb -s "$SER" shell settings put global $k 0
    done
    # The emulator has no PIN: wake it, dismiss the keyguard and keep the screen on so UI checks work.
    adb -s "$SER" shell input keyevent KEYCODE_WAKEUP
    adb -s "$SER" shell wm dismiss-keyguard
    adb -s "$SER" shell svc power stayon true
    for _ in $(seq 1 20); do
      adb -s "$SER" shell dumpsys window | grep -q 'mDreamingLockscreen=true' || break; sleep 1
      adb -s "$SER" shell wm dismiss-keyguard
    done
    echo "ready: $SER API $(adb -s "$SER" shell getprop ro.build.version.sdk | tr -d '\r')" ;;
  stop)
    if running; then
      adb -s "$SER" emu kill >/dev/null || true
      # Wait for the emulator to be really gone, so an immediate `start` cannot race a dying instance.
      for _ in $(seq 1 30); do
        running || pgrep -f "avd $BTSCAN_AVD" >/dev/null || break
        sleep 1
      done
      echo "stopped"
    else echo "not running"; fi ;;
  status)
    running && echo "running ($SER)" || echo "stopped" ;;
  *) echo "usage: $0 create|start|wait|stop|status" >&2; exit 2 ;;
esac
