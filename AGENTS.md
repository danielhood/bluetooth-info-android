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
| Run the minified release build (R8) | `scripts/release-smoke.sh` (part of `--full`; also run it on the phone for the detail flow) |
| Frame check with 200 fake devices | `scripts/perf.sh` (phone for meaningful numbers) |
| Real-radio smoke on the phone | `ANDROID_SERIAL=<serial> scripts/e2e-real.sh` |
| Host as test peripheral (advertise known payloads; `probe` scans them on the phone) | `scripts/peripheral.sh list\|start\|stop\|status`, `ANDROID_SERIAL=<serial> scripts/peripheral.sh probe <profile>` |

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

- Call `connectGatt` or make network calls. Query a device's profiles from anywhere except `DeviceDetailViewModel.confirmProfileLookup` (the per-device UI gate: tap, dialog naming the device, confirm, every time; `fetchUuidsWithSdp` lives only in `AndroidProfileLookup.kt`). Start a classic inquiry from anywhere except the list ViewModel's explicit user-intent path (`startClassicDiscovery` is the UI gate; `startDiscovery`/`cancelDiscovery` live only in `AndroidClassicScanner.kt`). `BLUETOOTH_CONNECT` is only for reading what Android has cached (Tier 2a) and may be named only under `data/ble/android`.
- Log a device address, name, alias, serial, raw bytes or value in `src/main` or `src/release` (`ArchitectureTest` fails on it).
- Write a characteristic, create/remove a bond, or use reflection on Bluetooth classes, in any phase (`ArchitectureTest` fails on the tokens, even in comments). Read `docs/phase2/policy-and-guardrails.md` before touching permissions or device contact.
- Add a manifest permission without updating the exact-set allowlist in `ArchitectureTest` and the policy doc in the same commit.
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
- Scan lifecycle rules (each guarded by a test): the scan stops when the app is backgrounded, but not on rotation (`isChangingConfigurations`) and not when a navigation screen stops; it also stops when the adapter leaves ON. Observe the *activity* lifecycle, never a navigation entry's, for "app in background".
- Google's `android` CLI exists in cmdline-tools. Its first run downloads components and it sends usage metrics unless `--no-metrics` is given; `android layout` installs an instrumentation server on the device. Prefer the `scripts/`; use it only on the emulator.
- The Start/Stop button ignores taps within 1 s of the previous one; scripts must wait about 1.5 s between toggles.
- `verify.sh --full` stops the exploration emulator (to free resources for the Managed Device) and restarts it for the smoke test.
- The phone locks itself after a few minutes; real-radio scripts need it unlocked and awake. Row names are not in the `ui.sh dump` (only addresses and chips), and only rows on screen are listed. See `docs/phase2/x1-findings.md` for what the host peripheral showed, including that `neverForLocation` hides iBeacon, Eddystone and extended advertising.
- Scanning needs **Location permission (precise) and Location services on**, on every Android version: the manifest does not assert `neverForLocation` (decision D-P2-8; `ArchitectureTest` fails if it returns). `app.sh grant` grants all three permissions; `emu.sh wait` switches location on for the emulator. An approximate-only grant is not enough.
- `BluetoothDevice.getAddressType()` is only right on the device object from a `ScanResult`; for one built with `getRemoteDevice(address)` Android answers "public" whatever the truth. Class value 0 from `getBluetoothClass()` means "no class". See `docs/phase2/tier1-advertisement-decoding.md` (T1-A1) and the Tier 2 notes.
- Classic inquiry facts (Pixel, Android 17): it ends itself after about 13 s; name, class and RSSI arrive in the broadcast without the connect permission, pairing state needs it (shown only when readable); a dual-mode device appears once as `BLE + Classic`; two scans never run together. Test target: make the host discoverable with `busctl set-property org.bluez /org/bluez/hci0 org.bluez.Adapter1 Discoverable b true` and set it back to `false` afterwards.
- Profile lookup facts (Pixel, Android 17): it answered in about 5 s, the target saw the phone connect and leave within seconds, and Android attempted to pair on its own during it (a quick BR/EDR bonding attempt the non-pairable test host refused). The app has no pairing call and cannot abort one; the confirmation dialog says some devices may ask to pair. Look for `onBondStateChange` in `adb shell dumpsys bluetooth_manager`. Test target: the host made discoverable (see classic facts above); contact only the owner's devices.
