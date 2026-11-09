#!/usr/bin/env bash
# App logs from the target device: BtScan/* tags plus crashes. Usage: logs.sh [--follow] [--clear]
set -euo pipefail
. "$(dirname "$0")/lib.sh"
resolve_serial
pat='BtScan/|E/AndroidRuntime|FATAL EXCEPTION|ANR in'
case "${1:-}" in
  --follow) adb logcat -v time | grep --line-buffered -E "$pat" ;;
  --clear)  adb logcat -c ;;
  *)        adb logcat -d -v time | grep -E "$pat" || true ;;
esac
