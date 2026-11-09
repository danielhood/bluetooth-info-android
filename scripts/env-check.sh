#!/usr/bin/env bash
# Diagnose the toolchain. Prints PASS/FAIL per item with a fix hint. Exit 1 if any FAIL.
set -uo pipefail
. "$(dirname "$0")/env.sh"
fail=0
ok()  { printf 'PASS  %-22s %s\n' "$1" "$2"; }
bad() { printf 'FAIL  %-22s %s\n      fix: %s\n' "$1" "$2" "$3"; fail=1; }

if command -v javac >/dev/null; then
  v=$(javac -version 2>&1 | awk '{print $2}'); [ "${v%%.*}" -ge 17 ] && ok jdk "javac $v ($JAVA_HOME)" || bad jdk "javac $v < 17" "sudo apt install openjdk-21-jdk-headless"
else bad jdk "javac not found" "sudo apt install openjdk-21-jdk-headless"; fi

chk() { # name path hint
  [ -e "$2" ] && ok "$1" "$2" || bad "$1" "missing $2" "$3"; }
chk cmdline-tools "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "see docs/dev-tooling.md section 1"
chk platform-tools "$ANDROID_HOME/platform-tools/adb" "sdkmanager platform-tools"
chk emulator "$ANDROID_HOME/emulator/emulator" "sdkmanager emulator"
chk platform-37 "$ANDROID_HOME/platforms/android-37.0" "sdkmanager 'platforms;android-37.0'"
chk build-tools "$ANDROID_HOME/build-tools/36.1.0" "sdkmanager 'build-tools;36.1.0'"
chk sysimg-36 "$ANDROID_HOME/system-images/android-36/google_apis/x86_64" "sdkmanager 'system-images;android-36;google_apis;x86_64'"
chk sysimg-atd-34 "$ANDROID_HOME/system-images/android-34/aosp_atd/x86_64" "sdkmanager 'system-images;android-34;aosp_atd;x86_64'"

[ -r /dev/kvm ] && [ -w /dev/kvm ] && ok kvm "/dev/kvm rw" || bad kvm "/dev/kvm not accessible" "add user to kvm group"
for t in python3 jq; do command -v $t >/dev/null && ok $t "$(command -v $t)" || bad $t missing "sudo apt install $t"; done

if command -v avdmanager >/dev/null && avdmanager list avd 2>/dev/null | grep -q "Name: $BTSCAN_AVD"; then ok avd "$BTSCAN_AVD"
else bad avd "AVD $BTSCAN_AVD not found" "scripts/emu.sh create"; fi

if command -v adb >/dev/null; then
  echo "INFO  adb devices:"; adb devices | tail -n +2 | sed '/^$/d;s/^/      /'
fi
exit $fail
