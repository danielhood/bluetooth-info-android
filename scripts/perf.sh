#!/usr/bin/env bash
# Frame-rendering check with 200 simulated devices updating (fake scenario `many`), debug build, no radio needed.
# Usage: [ANDROID_SERIAL=...] scripts/perf.sh [seconds=12]
# Prints frame statistics from `dumpsys gfxinfo` while the list scrolls. An emulator renders in software, so use a
# physical device for numbers that mean something. Exit 1 on an ANR/crash or if more than 30% of frames are janky.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
S="$REPO_ROOT/scripts"
resolve_serial
DURATION="${1:-12}"
echo "perf target: $ANDROID_SERIAL (200 devices, scrolling for ${DURATION}s)"
if adb shell dumpsys window | grep -q 'mDreamingLockscreen=true'; then echo "device is locked" >&2; exit 1; fi
"$S/app.sh" install >/dev/null 2>&1
"$S/app.sh" clear >/dev/null; "$S/app.sh" grant >/dev/null
"$S/app.sh" launch --scenario many >/dev/null
"$S/ui.sh" wait scan_button 15 >/dev/null
"$S/ui.sh" tap scan_button >/dev/null
sleep 6
echo "devices listed: $("$S/ui.sh" find device_count | grep -oE "text='[^']*'")"
size=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${size%x*}; H=${size#*x}
x=$((W / 2)); top=$((H * 3 / 10)); bottom=$((H * 8 / 10))
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
end=$((SECONDS + DURATION))
while [ $SECONDS -lt $end ]; do
  adb shell input swipe "$x" "$bottom" "$x" "$top" 350
  adb shell input swipe "$x" "$top" "$x" "$bottom" 350
done
stats=$(adb shell dumpsys gfxinfo "$PKG")
echo "$stats" | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile' | sed 's/^ *//'
total=$(echo "$stats" | grep -oE 'Total frames rendered: [0-9]+' | grep -oE '[0-9]+$' || echo 0)
janky=$(echo "$stats" | grep -oE 'Janky frames: [0-9]+' | head -1 | grep -oE '[0-9]+$' || echo 0)
echo "devices still listed: $("$S/ui.sh" find device_count | grep -oE "text='[^']*'")"
anr=$("$S/logs.sh" | grep -cE 'ANR in|FATAL EXCEPTION' || true)
mem=$(adb shell dumpsys meminfo "$PKG" | grep -m1 'TOTAL PSS' | sed 's/^ *//' | cut -c1-60)
echo "memory: $mem"
"$S/shot.sh" perf-many >/dev/null
[ "$anr" -eq 0 ] || { echo "PERF FAILED: ANR or crash in logs" >&2; exit 1; }
if [ "$total" -gt 0 ] && [ $((janky * 100 / total)) -gt 30 ]; then echo "PERF FAILED: $janky of $total frames janky (>30%)" >&2; exit 1; fi
echo "PERF OK (janky ${janky}/${total})"
