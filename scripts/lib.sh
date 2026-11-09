# Source me from other scripts. Provides toolchain env, repo paths and target-device resolution.
. "$(dirname "${BASH_SOURCE[0]}")/env.sh"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG=dev.danielhood.btscan
OUT="$REPO_ROOT/build/agent"
mkdir -p "$OUT/shots"

# Pick the target device: $ANDROID_SERIAL if set, else the emulator if attached, else the only attached device.
resolve_serial() {
  [ -n "${ANDROID_SERIAL:-}" ] && return 0
  local devs; devs=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
  if grep -qx "$BTSCAN_EMU_SERIAL" <<<"$devs"; then export ANDROID_SERIAL="$BTSCAN_EMU_SERIAL"
  elif [ "$(grep -c . <<<"$devs")" = 1 ]; then export ANDROID_SERIAL="$devs"
  else echo "no unique target device (attached: ${devs:-none}); start the emulator or set ANDROID_SERIAL" >&2; return 1; fi
}
