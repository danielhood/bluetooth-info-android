# Decisions

Format: **decision**, then *why it suits agentic coding*, then rejected alternatives. IDs are stable so other docs and commits can cite them.

Version numbers are intentionally **not** fixed here (see `gradle/libs.versions.toml`; pinned values are also recorded in roadmap.md under M1). Milestone M1 resolves the latest stable versions at scaffold time and records them in `gradle/libs.versions.toml`, which becomes the single source of truth. Research during planning suggests the AGP 9.x line is current; AGP 9 changes how Kotlin is wired into the build, so M1 must confirm that every plugin below (Spotless, detekt, Roborazzi) supports the chosen AGP before pinning.

---

## D1. Language: Kotlin

Kotlin 2.x with coroutines and Flow.

*Agent fit:* the dominant language in current Android documentation and samples, so models produce idiomatic code with few corrections. Strong static typing gives the compiler a large share of the "is this right?" feedback loop.

Rejected: Java (less idiomatic output for modern Android APIs), Flutter/React Native/KMP (extra toolchains and a bridge layer for BLE add failure modes and obscure the native Bluetooth APIs this POC exists to exercise).

## D2. UI: Jetpack Compose + Material 3

*Agent fit:*
- Declarative UI is plain Kotlin: no XML/layout inflation, no view IDs, no binding generation.
- `Modifier.testTag` plus `testTagsAsResourceId` exposes stable identifiers to `uiautomator`, so an agent can address elements without pixel coordinates.
- Composables are testable on the JVM with Robolectric and screenshot-testable without an emulator (D13).

Rejected: XML views (more files per screen, more cross-file consistency to maintain).

## D3. Build: Gradle Kotlin DSL, version catalog, wrapper, single module

- `./gradlew` wrapper committed; JDK pinned through toolchain config.
- All versions in `gradle/libs.versions.toml`.
- One Gradle module `:app`. Layering is by package and enforced by tests (D4), not by modules.

*Agent fit:* a single module keeps the build graph trivial and the blast radius of a bad edit small. The version catalog gives one file to inspect or fix when a dependency breaks. Kotlin DSL is checked by the compiler and IDE-style tooling.

Rejected: multi-module (real benefits only at scale; cost is more `build.gradle.kts` files and more ways to misconfigure), Groovy DSL (no type checking), Bazel/Buck (heavy, poor model familiarity).

## D4. Architecture: unidirectional data flow, three layers, ports and adapters for Bluetooth

```
UI (Compose)  ->  ViewModel (state + events)  ->  Domain/Data (repository, ports)  ->  Android BLE adapter
```

Details in [architecture.md](architecture.md). Key rules:

- The Android Bluetooth classes appear in **exactly one package** (`data.ble.android`).
- Everything above that package is plain Kotlin that runs on the JVM.
- Layer rules are enforced by an architecture test (`ArchitectureTest`, plain import scanning; Konsist was rejected as an unnecessary dependency coupled to the Kotlin version), so an agent that violates them gets a failing test rather than a code review comment.

*Agent fit:* tight, explicit boundaries are the best defence against agent drift. A fixed recipe ("new screen = Screen + ViewModel + UiState + route") makes every addition look like the last one.

Rejected: MVI frameworks (Orbit/MVIKotlin: extra abstraction to learn), Clean Architecture with use-case classes per action (ceremony outweighs benefit at this size).

## D5. Dependency injection: manual (`AppContainer`)

An `AppContainer` created in the `Application` class holds singletons; ViewModels are built with `viewModelFactory { initializer { ... } }`.

*Agent fit:* no annotation processor or KSP step, so no generated-code errors, no separate failure mode in the build, faster builds, and the whole object graph is readable in one file. A debug override hook (D11) is trivial to add.

Rejected: Hilt (the standard choice at scale, but adds a plugin, KSP/kapt, generated components and error messages that are hard for agents to act on), Koin (runtime resolution failures instead of compile-time ones). Revisit if the graph exceeds roughly ten injected types.

## D6. Navigation: Navigation Compose with type-safe routes

Two routes: `DeviceList` and `DeviceDetail(address: String)`, defined as `@Serializable` types.

*Agent fit:* routes are compile-checked types, not strings, so a wrong argument name is a compile error.

Note on "secondary window": interpreted as a **secondary screen** in the same activity, not a second OS window. Confirmed by the single-activity norm; flag if multi-window was actually intended.

## D7. Bluetooth scope: BLE scanning only, no connections

Uses `BluetoothLeScanner`. Classic discovery (`BluetoothAdapter.startDiscovery`) is out of scope but the `BleScanner` port is named and shaped so a sibling `ClassicScanner` could be added.

Why: most nearby devices advertise via BLE; advertisement data gives rich details (name, RSSI, TX power, service UUIDs, manufacturer data) without ever connecting. See open question 1 in the README.

**Update 2026-11-14 (D-P2-2): classic discovery was added (Tier 2b), still no connections.** The owner approved it on the condition that it is gated by the UI: a classic inquiry (`BluetoothAdapter.startDiscovery`, about 12 s, can briefly disturb audio on a connected headset) only ever starts from an explicit tap on "Scan classic devices", confirmed in a notice once per process. It never starts on launch, on a permission grant, on resume, when a BLE scan starts or when Bluetooth returns; it is a separate button, not part of Start/Stop; and `ArchitectureTest` confines the platform calls to `AndroidClassicScanner.kt` and the single start call to the list ViewModel's user-intent path. The two scans never run at the same time (starting one stops the other and says so). Still out of scope: connections and pairing.

**Update 2026-11-17 (Tier 2c): a classic service lookup was added.** `fetchUuidsWithSdp` queries one device for the profiles it supports; unlike everything before it, it **contacts the device** (a short classic session; the host saw the phone connect and leave within seconds). It follows the same UI-gate rule, stricter: only from an explicit tap on that device's detail screen, after a dialog naming the device, **every time** (no remembered consent), offered only for classic or dual-mode devices once system information is readable, 10 s limit, cancelled when the screen or app is left. `ArchitectureTest` confines the platform call to `AndroidProfileLookup.kt` and the port call to `DeviceDetailViewModel`. Android may start pairing by itself when a classic query needs it (observed once, refused by the test host); the app never calls a pairing function and the dialog says so.

## D8. SDK levels

| Setting | Value | Reason |
|---|---|---|
| `minSdk` | 26 | Android 8.0 covers essentially all active devices; `ScanResult.isConnectable()` needs API 26; avoids pre-8 background quirks |
| `compileSdk` / `targetSdk` | latest stable at M1 | Permission behaviour is target-dependent; pin and record |

## D9. Permissions: minimal set, no `BLUETOOTH_CONNECT`

| API level | Manifest permissions | Notes |
|---|---|---|
| 31+ | `BLUETOOTH_SCAN` (no `neverForLocation`, see the update below), `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` | Two runtime prompts: "Nearby devices" and Location (Android 12+ ignores a fine-location request unless coarse is requested with it). Location *services* must be on. |
| 26 to 30 | `BLUETOOTH`, `BLUETOOTH_ADMIN` (both `maxSdkVersion="30"`), `ACCESS_FINE_LOCATION` | Location *services* must also be switched on or scans silently return nothing |

Plus `<uses-feature android:name="android.hardware.bluetooth_le" android:required="false"/>` so the app installs on emulators and degrades to an "unsupported" state rather than being filtered out.

The device name is read from the advertisement bytes (AD types 0x08/0x09), **not** from `BluetoothDevice.getName()`, which needs `BLUETOOTH_CONNECT` on API 31+. This keeps the permission prompt to one item and matches the "never connect" requirement.

Original trade-off: `neverForLocation` makes the OS filter out some beacon-style advertisements. Acceptable for this POC; documented in the spec.

**Update 2026-11-14 (D-P2-8): `neverForLocation` was dropped.** The real-radio spike ([phase2/x1-findings.md](phase2/x1-findings.md)) showed that on Android 17 it hides iBeacon, Eddystone and extended advertising (and with them 2M/Coded PHY, set id and periodic interval) from the app, which are most of what Tier 1 decodes. The price: the scan now needs Location permission (precise) and Location services on, on every Android version, and the permission explanation says so. The app still never uses, stores or sends a location; scan data is not persisted or transmitted (policy doc). `ArchitectureTest` fails if the flag is added back without updating this record.

## D10. Parse raw advertisement bytes with our own pure-Kotlin parser

The real scanner passes `ScanResult.scanRecord.bytes` untouched into a `RawAdvertisement`. A pure-Kotlin `AdvertisementParser` decodes AD structures (flags, local name, TX power, service UUIDs 16/32/128-bit, service data, manufacturer data).

*Agent fit:* this is the single most important testability decision.
- The parser is fully JVM-testable with golden byte vectors (no Robolectric, no emulator).
- The **fake scanner emits raw bytes too**, so fake and real paths exercise identical parsing and UI code. The only untested-on-emulator code is the thin `ScanResult` to `RawAdvertisement` mapper.

Rejected: using `ScanRecord` getters (platform class, awkward to construct in tests, and the fake could not feed it).

## D11. Fake scanner for emulator and tests, in the `debug` source set

`FakeBleScanner` plays back named, scripted **scenarios** (devices appear, drift in RSSI, disappear). Scenario and mode are chosen at launch by an intent extra, which is honoured **only in debug builds**:

```bash
adb shell am start -n <pkg>/.MainActivity --es scanner fake --es scenario default
```

Mechanism: `src/main` defines `interface DebugOverrides`; `src/debug` provides the real implementation (reads the intent extra, returns fake ports); `src/release` provides a no-op. Release builds cannot reference the fake and cannot be switched into fake mode. Unit tests run against the debug variant (`testDebugUnitTest`), so they see the fake too.

Rejected: product flavors `fake`/`real` (doubles every variant and task name for agents to track), a settings toggle in the UI (needs UI interaction before any test can start), putting the fake in `main` (ships test code).

## D12. No persistence

No database, no DataStore, no history. State lives in memory for the app session. Less to build, nothing to migrate, nothing hidden between runs, which makes every run reproducible.

## D13. Testing stack

| Layer | Tool | Runs on |
|---|---|---|
| Logic (parser, repository, ViewModels) | JUnit 4, kotlinx-coroutines-test, Turbine, hand-written fakes (no mocking framework) | JVM, seconds |
| Compose UI and screenshots | Compose UI test + Robolectric + Roborazzi | JVM, no emulator |
| Instrumented and end-to-end | Compose test rule + AndroidX Test, via Gradle Managed Device | Headless emulator |
| Black-box smoke | `scripts/e2e-smoke.sh` driving the installed APK through `adb`/`uiautomator` | Emulator or device |
| Architecture rules | `ArchitectureTest` (import scanning) | JVM |

*Agent fit:* the JVM screenshot tests produce PNGs an agent can open and *look at* in the same loop as editing code, without booting an emulator. Hand-written fakes avoid mocking-framework magic that agents misuse. Roborazzi compatibility with the chosen AGP/Compose versions must be confirmed in M1; fallback is Paparazzi.

## D14. Static checks

- **Spotless + ktlint** for formatting. `./gradlew spotlessApply` fixes everything automatically, so an agent never argues with the formatter.
- **detekt** for complexity and code-smell rules.
- **Android Lint** with `warningsAsErrors = true` and a committed baseline of zero.
- Kotlin compiler `allWarningsAsErrors = true`.

All four run inside `scripts/verify.sh`.

## D15. How agents see and drive the running app

Baseline (no third-party code): `adb`-based scripts in `scripts/` that wrap `uiautomator dump`, `screencap`, `input`, `am`, `pm grant/revoke` and filtered `logcat`, producing compact, parseable output. Details in [dev-tooling.md](dev-tooling.md).

Optional add-on, evaluated in spike S2 (outcome: not adopted as baseline; Google's `android` CLI is a usable complement with `--no-metrics`, see roadmap M6): a third-party Android MCP server or Google's Android CLI. Search during planning found only community MCP servers (for example CursorTouch's Android-MCP) and mentions of a Google "Android CLI" with agent skills; none verified. They can execute arbitrary UI actions and are third-party code, so the scripts remain the supported path and any MCP is used against the emulator only.

## D16. Emulator: headless AVD for exploration, Gradle Managed Device for tests

- Exploration: a single named AVD (API 34 or newer, x86_64, Google APIs image) launched with `-no-window -no-audio -gpu swiftshader_indirect -no-snapshot` by `scripts/emu.sh`.
- Tests: a Gradle Managed Device (ATD image) declared in `app/build.gradle.kts`, so `./gradlew <device>DebugAndroidTest` creates, boots and tears down its own emulator. Reproducible from a clean checkout.

## D17. Agent instructions live in the repo

`AGENTS.md` at the root (with a `CLAUDE.md` that just imports it) states the commands, the layer rules, the "definition of done" and the gotchas. Contents specified in [dev-tooling.md](dev-tooling.md).

## D18. One gate

`scripts/verify.sh` is the only thing an agent must run to know whether its work is acceptable. `--quick` (static + JVM tests, under a minute once warm) and `--full` (adds emulator tests and smoke). A change is not done until the relevant mode passes.

---

## Risks carried from these decisions

| Risk | Mitigation |
|---|---|
| AGP 9.x plugin compatibility (Spotless, detekt, Roborazzi) | M1 verifies before pinning; fall back to the newest AGP line all plugins support |
| `uiautomator dump` hangs ("could not get idle state") while an infinite animation runs | No infinite animations in the scanning indicator when animations are disabled; `ui.sh` retries and falls back to `--compressed` |
| Emulator cannot scan real BLE | Fake scanner for everything but the thin mapper; spike S1 tries emulator netsim beacons; physical phone for final check |
| Scan start throttling (Android allows about 5 starts per 30 s) | Debounced Start/Stop button; failure mapped to a visible retry-later state |
| License acceptance and large downloads need a human | Called out explicitly as human steps in M0 |
