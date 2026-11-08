# Source me: `. scripts/env.sh`. Exports toolchain paths so scripts never depend on shell profile state.
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export BTSCAN_AVD="${BTSCAN_AVD:-btscan-api36}"
export BTSCAN_EMU_SERIAL="${BTSCAN_EMU_SERIAL:-emulator-5554}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
