#!/usr/bin/env bash
# Screenshot the target device to build/agent/shots/<name>.png (default name: timestamp). Prints the path.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
resolve_serial
f="$OUT/shots/${1:-$(date +%H%M%S)}.png"
adb exec-out screencap -p > "$f"
echo "$f"
