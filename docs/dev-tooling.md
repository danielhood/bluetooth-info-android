# Development Tooling and Agent Harness

The objective: an agent can go from a clean checkout to a built, installed, running, inspected and verified app **using only shell commands**, deterministically, with output small enough to read.

## 1. Toolchain to install

Nothing is installed yet (see README findings). `sudo` needs a password on this machine, so the JDK is installed by the owner with `apt` (one command, below) and everything else goes into user space. Downloads and SDK licence acceptance are **human-approved steps** (M0).

| Component | Choice | Location |
|---|---|---|
| JDK | **OpenJDK 21 headless** from apt: `sudo apt install openjdk-21-jdk-headless`. 21 is an LTS, above the AGP minimum (17), and matches what current Android Studio bundles. Not 25: too new for the Gradle/Kotlin/AGP combination to be safe. | `/usr/lib/jvm/java-21-openjdk-amd64`, `JAVA_HOME` set by `scripts/env.sh` |
| Gradle | Via the committed **wrapper only**; no system Gradle | repo |
| Android command-line tools | Latest from developer.android.com | `~/Android/Sdk/cmdline-tools/latest` |
| SDK packages | `platform-tools`, `emulator`, `platforms;android-<compileSdk>`, `build-tools;<ver>`, one system image (`system-images;android-34;google_apis;x86_64` or newer), and the ATD image for the Managed Device | `~/Android/Sdk` |
| KVM | Already accessible to the user | `/dev/kvm` |
| Helpers | `python3` (stdlib only), `jq`, `make`: already present | system |

`ANDROID_HOME` and `JAVA_HOME` are exported by a committed `scripts/env.sh` that every other script sources, so an agent never depends on shell profile state. `scripts/env-check.sh` prints a pass/fail table of all of the above and the exact fix command for each failure.

**Human-only steps (M0):** approve the downloads; run `sdkmanager --licenses` (licence acceptance is the user's decision); optionally plug in a physical phone and accept the USB-debugging prompt.

## 2. Repository layout for tooling

```
AGENTS.md                      agent instructions (CLAUDE.md contains "@AGENTS.md")
gradle/libs.versions.toml      all versions
scripts/
  env.sh                       exports JAVA_HOME, ANDROID_HOME, PATH (sourced, not run)
  env-check.sh                 diagnose toolchain
  emu.sh start|stop|status|wait   headless AVD lifecycle
  app.sh install|launch|stop|clear|grant|revoke   app control, launch takes --scenario
  ui.sh dump|find|tap|back|text   UI inspection and interaction
  shot.sh [name]               screenshot to build/agent/shots/<name>.png
  logs.sh [--follow]           logcat filtered to the app process and BtScan/ tags
  e2e-smoke.sh                 black-box scenario run via the scripts above
  verify.sh [--quick|--full]   THE gate
build/agent/                   artifacts written by scripts (git-ignored)
docs/screens/                  committed reference screenshots (from Roborazzi)
```

All scripts: `set -euo pipefail`, no prompts, nonzero exit on failure, concise output (one line per fact, or JSON with `--json`), honour `ANDROID_SERIAL` so the same scripts target the emulator or a physical phone.

## 3. The agent loop: see and drive the running app

### Seeing

| Need | Tool | Notes |
|---|---|---|
| Structured UI state | `ui.sh dump` wraps `adb exec-out uiautomator dump /dev/tty` and a small Python (stdlib) filter | Emits one line per node: `resource-id`, `text`, `content-desc`, `bounds`, `clickable`. Compose tags appear as `resource-id` because the root sets `testTagsAsResourceId`. Roughly 1 to 3 KB for these screens. |
| Visual state | `shot.sh` wraps `adb exec-out screencap -p` | Agent reads the PNG with its image-capable file tool. Use for layout/visual checks; use `dump` for assertions. |
| Behaviour | `logs.sh` | Filters `logcat` by app PID plus `BtScan/*` tags; fatal exceptions always shown. |
| App-internal state (debug) | `adb shell am broadcast -a <pkg>.DUMP_STATE` then `logs.sh` | One JSON line of `DeviceListUiState` under tag `BtScan/State`. Optional (M6). |

### Driving

| Action | Command |
|---|---|
| Install and start in a scenario | `app.sh install && app.sh launch --scenario default` |
| Tap an element by test tag | `ui.sh tap scan_button` (resolves the tag to bounds from a fresh dump, then `adb shell input tap`) |
| Enter permission states | `app.sh grant` / `app.sh revoke` (`pm grant`/`pm revoke` for the Bluetooth permission set) |
| Reset | `app.sh clear` (`pm clear`) |
| Back / home / background | `ui.sh back`, `adb shell input keyevent KEYCODE_HOME` |
| Toggle emulator system state | `adb shell cmd bluetooth_manager enable/disable` where the emulator image supports it |

### Known gotchas (also recorded in AGENTS.md)

- `uiautomator dump` can fail with "could not get idle state" while an infinite animation runs. Mitigations: the scanning indicator is static when animator scale is 0 (`scripts/emu.sh` sets all animation scales to 0 on boot); `ui.sh` retries 3 times, then retries with `--compressed`.
- Always wait for `sys.boot_completed` before installing (`emu.sh wait`).
- First Gradle build downloads dependencies and is slow; subsequent runs use the daemon and cache. Never kill the daemon mid-run.
- The permission dialog is a system window; `uiautomator dump` sees it, and `ui.sh tap` works on its buttons by text.
- Emulator has no real BLE radio unless spike S1 succeeds. A "no devices" result on the emulator in **real** mode is expected, not a bug.

### Optional: MCP servers

Evaluated in spike S2, not part of the baseline. Candidate classes: community Android MCP servers wrapping `adb`/uiautomator (for example CursorTouch's Android-MCP) and Google's Android CLI if it proves useful. Adopt only if they beat the scripts on reliability and token cost, run them against the emulator only, and review their source first, since they execute arbitrary UI actions.

## 4. Verification pyramid

`scripts/verify.sh` runs these in order and stops at the first failure with the failing task's tail output.

| # | Stage | Command underneath | Typical time (warm) | In `--quick` | In `--full` |
|---|---|---|---|---|---|
| 1 | Format check | `./gradlew spotlessCheck` | seconds | yes | yes |
| 2 | Static analysis | `./gradlew detekt lintDebug` | ~30 s | yes | yes |
| 3 | Compile with warnings as errors | `./gradlew assembleDebug` | ~30 s | yes | yes |
| 4 | JVM unit + architecture tests | `./gradlew testDebugUnitTest` | seconds | yes | yes |
| 5 | JVM screenshot tests (Roborazzi) | `./gradlew verifyRoborazziDebug` | ~30 s | yes | yes |
| 6 | Release build + fake-absent check | `./gradlew assembleRelease` + `unzip`/`grep` on dex | ~1 min | no | yes |
| 7 | Instrumented tests on Managed Device | `./gradlew <device>DebugAndroidTest` | minutes | no | yes |
| 8 | Black-box smoke on emulator | `scripts/e2e-smoke.sh` | ~1 to 2 min | no | yes |

Auto-fix helpers: `./gradlew spotlessApply`; `./gradlew recordRoborazziDebug` to update reference screenshots (a human or the agent must *look at* the diff images before committing them).

Test conventions:
- Names say behaviour (`stale device is removed after 30 seconds`).
- No `Thread.sleep`; virtual time or `waitUntil` with a timeout.
- Hand-written fakes, no mocking library.
- Every F-/acceptance item in [functional-spec.md](functional-spec.md) maps to at least one test, referenced in a comment (`// F-L11`).

## 5. Contents of AGENTS.md (written in M1)

1. One-paragraph project summary and pointer to `docs/`.
2. **Commands:** the table of scripts above and the exact `verify.sh` invocation.
3. **Definition of done:** `verify.sh --quick` passes for any change; `--full` passes for anything touching UI, manifest, data/ble, or build files; UI changes include a screenshot the agent has looked at.
4. **Layer rules** from [architecture.md](architecture.md) (these are enforced by tests, but stating them avoids a failed round trip).
5. **Recipes:** add a screen, add a fake scenario, add a parsed AD type, add a dependency (edit `libs.versions.toml` only).
6. **Prohibitions:** no `connectGatt`; no `BLUETOOTH_CONNECT`; no network; no new dependency without a stated reason in the commit; no `@Suppress` or lint baseline additions to silence a finding; no editing generated or build output.
7. **Gotchas** list from section 3.
8. **Commit style:** small commits, imperative subject, one logical change, `verify.sh --quick` green before each.

## 6. Why these tools over alternatives

- **Scripts over a GUI-driving "computer use" approach:** `adb`+`uiautomator` returns exact text and bounds, is headless, and is cheap in tokens. Screenshot-and-click is slower and less reliable; kept as a fallback for visual checks only.
- **Robolectric/Roborazzi over emulator-only UI tests:** feedback in seconds instead of minutes, and no dependence on emulator boot health during the edit loop.
- **Managed Device over a hand-started AVD for tests:** the device definition is in code, so a clean checkout reproduces it.
