#!/usr/bin/env bash
# THE gate. Usage: verify.sh [--quick|--full]   (default: --quick)
#   quick: format, detekt + lint, compile (warnings as errors), JVM unit tests, screenshot verification
#   full:  quick + release build/debug-leak check + instrumented tests (Gradle Managed Device) + emulator smoke test
# On failure prints the tail of the failing stage and the path to its full log; exit 1.
set -uo pipefail
. "$(dirname "$0")/lib.sh"
mode="${1:---quick}"
case "$mode" in --quick|--full) ;; *) echo "usage: $0 [--quick|--full]" >&2; exit 2 ;; esac
LOGDIR="$OUT/verify"; mkdir -p "$LOGDIR"
GW="$REPO_ROOT/scripts/gw.sh"

stage() { # name, command...
  local name="$1"; shift
  local log="$LOGDIR/${name// /_}.log" start=$SECONDS
  printf '%-34s' "[$name]"
  if "$@" >"$log" 2>&1; then
    printf 'ok (%ss)\n' $((SECONDS - start))
  else
    printf 'FAILED (%ss)\n' $((SECONDS - start))
    echo "---- last 40 lines of $log"; tail -40 "$log"; echo "---- full log: $log"
    exit 1
  fi
}

release_check() {
  "$GW" -q assembleRelease &&
  python3 -I "$REPO_ROOT/scripts/check-release.py" \
    "$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk" \
    "$REPO_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
}

# Free emulator RAM/ports for the Managed Device; the exploration emulator is restarted by the smoke test.
managed_device_tests() {
  "$REPO_ROOT/scripts/emu.sh" stop || true
  "$GW" -q pixel2api34DebugAndroidTest
}

stage "format (spotless)"            "$GW" -q spotlessCheck
stage "static analysis (detekt+lint)" "$GW" -q detekt lintDebug
stage "compile (warnings as errors)"  "$GW" -q assembleDebug
stage "jvm unit tests"               "$GW" -q testDebugUnitTest
stage "screenshot tests (verify)"    "$GW" -q verifyRoborazziDebug

if [ "$mode" = "--full" ]; then
  stage "release build + leak check"  release_check
  stage "instrumented (managed dev)"  managed_device_tests
  stage "emulator smoke"              "$REPO_ROOT/scripts/e2e-smoke.sh"
fi
echo "VERIFY OK ($mode)"
