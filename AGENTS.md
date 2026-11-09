# AGENTS.md

Android proof-of-concept (Kotlin, Jetpack Compose): scan nearby **BLE** devices, list them, show details of one on a second screen. Never connects to a device. Built mainly by AI agents; the point is to make that reliable.

Plan and rationale live in `docs/` (start with `docs/README.md`, then `decisions.md`, `architecture.md`, `functional-spec.md`, `roadmap.md`). If code and docs disagree, fix whichever is wrong in the same commit. Current status: see `docs/roadmap.md`.

## Commands (always from the repo root)

| Need | Command |
|---|---|
| **The gate** (fast: format, detekt, lint, compile, unit + screenshot tests) | `scripts/verify.sh --quick` |
| Full gate (adds release/leak check, Managed-Device tests, emulator smoke) | `scripts/verify.sh --full` |
| Run any Gradle task | `scripts/gw.sh <tasks>` (sets JDK/SDK; do not call `./gradlew` from a bare shell) |
| Auto-fix formatting | `scripts/gw.sh spotlessApply` |
| Update reference screenshots in `docs/screens/` | `scripts/gw.sh recordRoborazziDebug` then **look at the images** before committing |
| Diagnose toolchain | `scripts/env-check.sh` |
| Emulator (headless) | `scripts/emu.sh start && scripts/emu.sh wait` / `stop` / `status` |
| Install / launch / stop / clear app | `scripts/app.sh install`, `launch [--scenario NAME]`, `stop`, `clear` |
| Grant / revoke the Bluetooth runtime permission | `scripts/app.sh grant` / `revoke` |
| See the screen as structured text | `scripts/ui.sh dump` (also `find`, `tap`, `wait`, `back`, `text`) |
| See the screen as an image | `scripts/shot.sh [name]` then read the PNG in `build/agent/shots/` |
| App logs and crashes | `scripts/logs.sh` (`--follow`, `--clear`) |
| Black-box smoke test (fake scanner, plus real scanner start/stop) | `scripts/e2e-smoke.sh` |
| Real-radio smoke on the phone | `ANDROID_SERIAL=<serial> scripts/e2e-real.sh` |

Target device: `$ANDROID_SERIAL` if set; otherwise the emulator if attached; otherwise the single attached device. The physical phone (Pixel 9a) is for real-radio checks: `ANDROID_SERIAL=<serial> scripts/app.sh install`.

## Definition of done

- Any change: `scripts/verify.sh --quick` passes.
- Anything touching UI, the manifest, `data/ble`, build files or scripts: `scripts/verify.sh --full` passes.
- UI changes: you have actually looked at a screenshot (`shot.sh` or the Roborazzi PNG) and the `ui.sh dump`.
- Docs/roadmap checkboxes updated in the same commit. Small commits, imperative subject, one logical change.

## Architecture rules (summary; details in `docs/architecture.md`)

- Layers: `ui -> domain <- data`. `ui` must not import `data`; `domain` imports no `android.*`; `android.bluetooth.*` only under `data/ble/android`.
- ViewModels take no `Context`. Time and dispatchers are injected, never called directly.
- Manual DI in `AppContainer` only. Fake/debug code lives in `app/src/debug`; `DebugOverrides` is the only seam. Release must never contain it (`verify.sh --full` checks).
- Fake scanner scenarios: `app/src/debug/.../data/ble/fake/Scenarios.kt` (select with `app.sh launch --scenario NAME`; names listed in `docs/functional-spec.md`). To add one, add a `Scenario`, register it in `all`, and add a test.
- Architecture rules are enforced by `ArchitectureTest` (import scanning); a failing rule names the offending file and import.
- New screen recipe: `Screen.kt` + `ViewModel.kt` + `UiState.kt` + route + tags in `ui/common/TestTags.kt` + tests.
- Every interactive/important composable gets a `Modifier.testTag` from `TestTags` (tags show up as `resource-id` in `ui.sh dump`). The container for a screen also needs a `contentDescription` or uiautomator will not expose it.

## Do not

- Call `connectGatt`, request `BLUETOOTH_CONNECT`, or make network calls.
- Add a dependency without adding it to `gradle/libs.versions.toml` and stating why in the commit. No hardcoded versions elsewhere.
- Silence a finding with `@Suppress`, a lint baseline, or by loosening config to make a check pass, unless the commit message explains why it is a false positive.
- Edit generated files or anything under `build/`.
- Use `Thread.sleep` in tests (virtual time or `waitUntil`) or a mocking library (hand-written fakes).
- Enable the Gradle build cache. It served stale resource outputs once; it is off on purpose.

## Gotchas

- First Gradle run is slow (dependency download); later runs use the daemon.
- `uiautomator dump` can fail with "could not get idle state" if something animates forever. `emu.sh wait` disables system animations; `ui.sh` retries. Avoid infinite animations in the UI.
- `adb shell uiautomator dump /dev/tty` prints nothing useful; `ui.sh` dumps to a file and reads it with `exec-out`.
- The emulator has no real BLE radio. Real-mode scans there find nothing; use fake scenarios (`app.sh launch --scenario default`) or the physical phone.
- Robolectric needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` (already set in `app/build.gradle.kts`).
- Kotlin warnings are errors. Compose test APIs: use the `v2` packages (`androidx.compose.ui.test.junit4.v2`).
- compileSdk/targetSdk are 37 because current Compose/lifecycle require it; Robolectric is pinned to sdk 36 in `app/src/test/resources/robolectric.properties`.
- Unit tests with coroutines: `advanceTimeBy(n)` does not run work scheduled exactly at `n`; call `runCurrent()` after it.
- A scan without `--scenario` uses the real scanner. The emulator has a virtual Bluetooth adapter but no devices, so real mode shows an empty list there. Real-radio checks need the physical phone: `ANDROID_SERIAL=<serial> scripts/e2e-real.sh` and `scripts/real-device-tests.sh` (phone unlocked, Bluetooth on, devices nearby). Their screenshots show nearby devices; they stay in the git-ignored `build/agent/`.
- Memory: `--full` runs Gradle plus up to two emulators; close browsers first on this 16 GB machine. Gradle/Kotlin heaps are capped in `gradle.properties` and `verify.sh` stops the Gradle daemon before the smoke stage.
- `verify.sh --full` stops the exploration emulator (to free resources for the Managed Device) and restarts it for the smoke test.
