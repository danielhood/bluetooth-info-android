# Roadmap

Each milestone ends with a green `scripts/verify.sh` (from M1 on), a commit, and a short note in this file marking it done. Milestones are sequenced so the **agent harness exists before the app does**: from M1 on, every later change is verified by the tooling rather than by trust.

Status key: [ ] not started, [~] in progress, [x] done.

## M0. Environment bootstrap [x]

Goal: the machine can build and run Android apps headlessly.

- [x] **Human:** approve downloads; run `sdkmanager --licenses`.
- [x] **Human:** enable USB debugging on the Pixel 9a and accept the host key prompt. Device: Pixel 9a, serial `5A151JEBF21180`, Android 17 (API 37), BLE supported.
- [x] Install JDK 21 (apt), Android cmdline-tools 22.0, platform-tools 37.0.1, emulator 37.2.13, platform 36, build-tools 36.1.0, system images `android-36;google_apis` and `android-34;aosp_atd` (about 14 GB in `~/Android/Sdk`).
- [x] Create the AVD (`btscan-api36`, pixel_6 profile, 3 GB RAM).
- [x] Write `scripts/env.sh`, `scripts/env-check.sh`, `scripts/emu.sh`.

Findings:
- Headless boot takes about 30 s; screenshot and `uiautomator dump` work.
- The emulator image reports `android.hardware.bluetooth` and `bluetooth_le`, which is encouraging for spike S1.
- `adb shell uiautomator dump /dev/tty` prints nothing useful; use `adb exec-out` (M1 `ui.sh` must handle this).
- The Pixel runs API 37, newer than the installed platform 36. M1 decides `compileSdk`/`targetSdk` (36 vs 37) and installs `platforms;android-37.x` if needed. Scripts must honour `ANDROID_SERIAL` because the phone and emulator can be attached together.
- `sdkmanager` is flagged deprecated in favour of Google's Android CLI (`android sdk`); evaluate in S2.

Exit: `env-check.sh` all green (done); `emu.sh start && emu.sh wait` boots headless (done); Pixel 9a visible to adb (done).

## M1. Scaffold and harness [x]

Goal: an empty-but-real app plus the full verify loop.

- [x] Gradle project: wrapper (Gradle 9.9.0, checksum pinned), `libs.versions.toml` with latest-stable versions, single `:app`.
- [x] Manifest with the permissions from D9; `debug`/`release` source sets with `DebugOverrides`.
- [x] Placeholder `DeviceListScreen` with test tags; `AppRoot` sets `testTagsAsResourceId`.
- [x] Spotless (ktlint), detekt, lint, warnings-as-errors; unit tests, Roborazzi screenshot tests, one Managed-Device instrumented test.
- [x] `gw.sh`, `app.sh`, `ui.sh`, `shot.sh`, `logs.sh`, `verify.sh`, `e2e-smoke.sh`, `check-release.py`.
- [x] `AGENTS.md`, `CLAUDE.md`, `.gitignore`, `.editorconfig`.

Result: `scripts/verify.sh --full` passes (about 2 min cold, about 1 min warm). Smoke passes on the emulator.

Pinned versions (as resolved 2026-11-08): AGP 9.5.1, Kotlin 2.4.21, Gradle 9.9.0, Compose BOM 2026.09.00, Roborazzi 1.78.0, Robolectric 4.17, Spotless 8.11.0 (ktlint 1.8.0), detekt 1.23.8. `compileSdk`/`targetSdk` 37 (the Pixel's API level; the newest Compose/lifecycle libraries require compileSdk 37). Robolectric runs at sdk 36.

Findings and decisions made during M1:
- **detekt 1.23.8** works with Kotlin 2.4 only because its own classpath is pinned to Kotlin 2.0.21 (root `build.gradle.kts`). Revisit when detekt 2.0 is stable.
- **Gradle build cache is off.** It served a stale resource-compile result after a folder rename; a tiny project gains nothing from it.
- **Robolectric** on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`.
- **Compose test rules:** use the `v2` packages; the v1 ones are deprecated and warnings are errors.
- **uiautomator** exposes a Compose container only if it has semantics beyond `testTag` (we add a `contentDescription` to the screen container).
- `verify.sh --full` stops the exploration emulator before the Managed Device test and the smoke test restarts it.
- The smoke test passes on both the emulator and the physical Pixel 9a (API 37; run by the owner with the phone unlocked on 2026-11-08). The script refuses to run on a locked device and says so.

Exit: met. From a clean clone, `verify.sh --full` passes; an agent can launch the app on the emulator, dump the UI and see the placeholder tags, and take a screenshot.

## M2. Domain and data with fakes [x]

- [x] Models (`Bytes`, `RawAdvertisement`, `Advertisement`, `BleDevice`, `AdapterState`, `PermissionState`, `ScanFailure`), ports (`BleScanner`, `AdapterStateMonitor`, `PermissionChecker`, `Clock`).
- [x] `AdvertisementParser` with 19 golden-vector tests (acceptance 8).
- [x] `DefaultDeviceRepository`: merge, 30 s staleness, 500 ms throttle, ordering, failure, restart safety; 15 virtual-time tests (acceptance 5).
- [x] `FakeBleScanner` + all eight scenarios + `AdBuilder` in `src/debug`; `AppContainer` wiring through `DebugOverrides.scannerFor`.
- [x] Architecture tests (import scanning, see decisions D4).

Result: 69 JVM unit tests; `verify.sh --full` passes. No UI beyond the M1 placeholder, by design.

Decisions and findings made during M2:
- **Architecture tests use plain import scanning** instead of Konsist: no dependency, no Kotlin-version coupling, and the negative case was checked (a forbidden import fails the test). The rules cover domain/ui/data layering, `android.bluetooth` confinement, ViewModels, fakes confined to `src/debug`, and "never connect".
- **Clock port** returns `java.time.Instant` (available on minSdk 26 without desugaring); scanners stamp advertisements with it.
- **`ScanFailure`** is a single exception class with a `Kind` enum and optional platform code; the repository catches only `ScanFailure`. Platform exceptions (for example `SecurityException`) must be mapped to it by the real scanner in M5.
- **Repository scan generation counter:** a cancelled scan's cleanup must not flip `scanning` to false after a newer scan has started; covered by a test.
- **Until M5** the "real" scanner slot is `UnavailableBleScanner`, which fails with `FEATURE_UNSUPPORTED`. Launching without a debug override and starting a scan therefore reports a failure; this is expected.
- **Compose-free wiring check:** `AppContainerTest` proves the launch config selects the fake scanner and scenario.
- Test pitfall: `advanceTimeBy(n)` does not run work scheduled exactly at `n`; tests use an `advance` helper that also calls `runCurrent()`.
- `emu.sh` hardening found by full runs: `stop` now waits for the emulator to be gone, and `wait` wakes the screen and dismisses the keyguard (a fresh emulator could look "locked" and fail the smoke test).

Exit: met. Repository correct under every fake scenario in unit tests.

## M3. List screen and environment states [x]

- [x] `DeviceListViewModel` + `DeviceListUiState`; all `ListStatus` states derived by a pure function (`deriveStatus`).
- [x] Compose list: Start/Stop, Clear, hide-unnamed, counter, static scanning indicator, signal bars, empty states, failure banner with Retry.
- [x] Permission request flow (real `AndroidPermissionChecker`; BLUETOOTH_SCAN on API 31+, fine location below), adapter monitor port with fake and placeholder implementations, resume re-evaluation, stop on background.
- [x] 20 ViewModel tests, 19 Compose behaviour tests, 14 screenshot references in `docs/screens/` (every status, light and dark).
- [x] Instrumented tests on the Managed Device (scenario `default` lists devices) and the extended emulator smoke test.

Result: 118 JVM unit tests, 2 instrumented tests; `verify.sh --full` passes. Acceptance criteria covered: 1 (scan to list; ViewModel, instrumented and smoke), 6 (permission paths; smoke runs the real system dialog, permanent denial is unit-tested), 7 (adapter paths; screenshots and smoke), 9 (background stop; smoke).

Decisions and findings made during M3:
- **Permission-needed is shown as soon as the screen loads**, not only after tapping Start. Start is disabled in that state and the explanation carries the Grant button. This satisfies acceptance 6 ("Start leads to the permission-needed state") more directly. Permanent denial is detected from the result of the request (`shouldShowRequestPermissionRationale` false after a denial) and held in the ViewModel.
- **Clear returns the list to the initial hint** ("Tap Start scan..."), not "No devices found"; the latter appears only after a scan that found nothing.
- **Failure** is a banner above the retained list (Retry re-runs the scan), while permission, Bluetooth-off, location-off and unsupported replace the list area.
- **Scanning indicator is static** (a dot plus text): no infinite animation, so uiautomator can always reach an idle state.
- **"Turn on Bluetooth" opens the system Bluetooth settings** instead of using `BluetoothAdapter.ACTION_REQUEST_ENABLE`, which would break the rule that `android.bluetooth` stays in `data/ble/android`.
- Until M5, real mode uses `UnavailableBleScanner` and `PlaceholderAdapterStateMonitor` (always on).
- **Memory:** a full run was killed by the OS (Claude desktop process) while Gradle (3 GB), the Kotlin daemon and two emulators were up beside a loaded desktop. Mitigations: Gradle heap 2 GB, Kotlin daemon 1 GB, 4 workers, exploration emulator 2 GB, and `verify.sh` stops the Gradle daemon before the smoke stage. Peak system use in a full run was about 10.5 GB of 14.8 GB. Close heavy applications (browsers) before `--full`.
- **emu.sh** now identifies its own AVD (a Gradle Managed Device can grab port 5554) and waits for the port to free before starting.

Exit: met.

## M4. Detail screen and navigation [x]

- [x] Type-safe routes (`ListDestination`, `DetailDestination(address)`), `AppRoot` with `NavHost`; list ViewModel scoped to its back-stack entry, detail ViewModel keyed by address.
- [x] `DeviceDetailViewModel` (last-known data retained, out-of-range flag, once-a-second age ticker only while observed) and `DeviceDetailContent`: name, address (with the private-address note), live signal, connectable, TX power or "Not advertised", first/last seen, advertisement count, service UUIDs with friendly names, manufacturer data with company names, service data, decoded flags, raw bytes with Copy.
- [x] Curated lookup tables (`CompanyIds`, `ServiceUuids`, `AdFlag`) with tests.
- [x] Out-of-range and not-found states.
- [x] 7 detail ViewModel tests, 9 detail Compose tests, 4 full-app navigation tests (real time, fake scanner), 5 detail screenshot references, 1 more instrumented test, smoke test extended with the detail flow.

Result: 148 JVM unit tests and 3 instrumented tests; `verify.sh --full` passes. Acceptance criteria covered: 2 (list to detail), 3 (live RSSI: navigation test and smoke), 4 (back keeps list and scanning: navigation test and smoke, both top-bar and system back), 5 (stale removal and out-of-range banner: repository and detail ViewModel tests on virtual time).

Decisions and findings made during M4:
- **Bug found by the navigation tests:** stop-on-background was wired to the navigation entry's lifecycle, which reaches STOP whenever the detail screen opens, so opening a device stopped the scan. It now observes the activity's lifecycle. Covered by the back/live-RSSI navigation tests.
- **Merged semantics on detail containers** (`mergedTestTag`): service, manufacturer, service-data, flags and banner containers read as one node, so `ui.sh dump` shows their text as one line. List rows are deliberately not merged (the smoke test reads their child texts).
- **Fake RSSI drift sped up** (period about 4.4 s, amplitude 4 dB) so that "RSSI changes within 3 s" is observable.
- **Detail age ticker uses `WhileSubscribed`**, so it does not run while the screen is not visible and does not keep test schedulers busy.
- `androidx.compose.material:material-icons-core` was added for the back arrow (not pulled in by material3).
- **Pixel 9a (API 37) smoke test passes** for the whole M3/M4 flow with the fake scanner (all 44 checks, run 2026-11-09), including the real permission dialog, list, detail, back and background stop. Real Bluetooth is still M5.
- **Not yet exercised:** the minified release build compiles (leak check passes) but has not been run, so R8 behaviour of type-safe navigation is unverified. Scheduled for M6.
- Memory: this full run started with only about 2.6 GB available (other applications); it completed, but close heavy apps first.

Exit: met.

## M5. Real Bluetooth [x]

- [x] `AndroidBleScanner` (`callbackFlow`, low-latency, extended advertisements included, error-code mapping, `SecurityException` mapped to `PERMISSION_MISSING`), `AndroidAdapterStateMonitor` (state and location broadcasts; location only matters on Android 11 and below), `AndroidPermissionChecker` (from M3).
- [x] Real ports are the defaults in `AppContainer`; the debug overrides still replace them when a scenario is requested. The M2/M3 placeholders are deleted.
- [x] Background-stop (acceptance 9) was done in M3/M4 and re-verified with the real scanner.
- [x] **Spike S1** (below).
- [x] **Real-device run on the Pixel 9a** (below).

Result: 158 JVM unit tests (11 new: error mapping, adapter on/off/unsupported, location on Android 11 under Robolectric sdk 30, permission checker on Android 12+ and 11); `verify.sh --full` passes, including a new smoke section that starts and stops the real scanner on the emulator.

Real-device results (Pixel 9a, Android 17 / API 37, 2026-11-09):
- `scripts/e2e-real.sh`: 15/15 checks. A real scan listed 8 devices within 25 s, sorted by signal, with real names (one with an emoji) and connectable flags; the detail screen showed a valid MAC address and plausible signal; back kept the scan running; stop and relaunch worked; no crashes.
- `scripts/real-device-tests.sh` (instrumented, physical device only): 2/2, real advertisements arrive and parse without error; the adapter monitor reports on.
- Acceptance 10 (manual real hardware) is met for Android 12+. The Android 8 to 11 location path is covered by Robolectric (sdk 30) only; no device on that range was available.

Spike S1 (emulator Bluetooth): the Android 36 emulator image exposes a virtual Bluetooth adapter (netsim). The real scanner starts and stops cleanly on it and reports an empty list, which the smoke test now asserts. Injecting virtual beacons would need the netsim command-line or gRPC client, which is not shipped in this SDK (only `netsimd`); not pursued. Decision: the physical phone remains the only real-advertisement check, and the emulator covers the real code path without devices.

Decisions and findings made during M5:
- Scan settings use `setLegacy(false)` so extended advertisements are included; devices that only advertise legacy are unaffected.
- The advertisement channel buffers 512 entries and drops on overflow; at the rates seen (tens of devices) nothing is lost, and the repository throttles what reaches the UI.
- The architecture rule "nothing connects" scans source text for the permission name, so even a comment containing it fails the check; comments say "connect permission" instead.
- Real-radio checks are separate scripts (`e2e-real.sh`, `real-device-tests.sh`) and are not part of `verify.sh --full`, because they need an unlocked physical phone with Bluetooth devices in range.

Exit: met.

## M6. Hardening and wrap-up [x]

- [x] 200-device performance check (N-1): `scripts/perf.sh`.
- [x] Rotation, background, Bluetooth toggled mid-scan (N-2): two real bugs found and fixed (below).
- [x] Accessibility pass (N-3): labelled switch row, automated touch-target (48 dp) and "every interactive element is labelled" checks on the list and detail screens.
- [x] Dark theme screenshots (N-4): list, permission-needed, failed and detail states.
- [x] Minified release build installed and run (`scripts/release-smoke.sh`, now a `--full` stage); also run on the Pixel with real devices, including opening a detail screen.
- [x] Manifest check: no INTERNET and no connect permission (N-5, test).
- [ ] ~~Optional debug state-dump receiver~~ not built: `ui.sh dump` plus the logs already give ground truth, and nothing needed more.
- [x] **Spike S2** (below).
- [x] Retrospective: [retrospective.md](retrospective.md).

Result: 171 JVM unit tests, 3 Managed-Device tests, 2 on-device tests; `verify.sh --full` passes.

Measurements (fake scenario `many`, 200 devices updating, list scrolled for 12 s):
- Pixel 9a: 636 frames, 3.9% janky, median frame 7 ms, 95th percentile 27 ms, no ANR; memory about 150 MB PSS.
- Emulator (software rendering, so not representative): 691 frames, 6.1% janky, median 24 ms.

Bugs found and fixed in M6:
- **Scan kept running behind "Bluetooth is turned off".** When Bluetooth (or location, on Android 11 and below) is lost, the platform silently ends the scan; the app still believed it was scanning and showed a phantom "Scanning" after Bluetooth returned. The ViewModel now stops the scan when the adapter leaves ON. Unit tests plus a smoke step that toggles Bluetooth on the emulator.
- **Rotation stopped the scan.** Stop-on-background fired on the configuration-change stop. It now ignores stops where `isChangingConfigurations` is true. Found by an activity-recreation test that now guards it (list and detail both survive rotation).

Spike S2 result: Google's own Android CLI (the `android` binary in cmdline-tools) is capable: `layout --flat` returns JSON of the UI, `screen capture [--annotate]` screenshots, plus `input`, `run`, `emulator`, `docs`, `preview` (renders Compose previews to PNG). Measured against `ui.sh dump`: both about 2 s per call, JSON about 2.7 KB versus 1.3 KB of text lines. Costs: the first call silently downloads an embedded installation, usage metrics are on unless `--no-metrics` is passed, and `layout` installs an instrumentation server on the device. Decision: not adopted as the baseline. The scripts have no dependencies, understand Compose test tags directly, and are already wired into the gates. The CLI is a reasonable complement (annotated screenshots, `preview`), always with `--no-metrics` and only on the emulator. Third-party MCP servers were not installed or evaluated: they run arbitrary UI actions with no benefit over the scripts shown here, and are supply-chain risk.

Exit: met. All acceptance criteria are met; known gaps: the Android 8 to 11 location path is verified only under Robolectric (no device on that range), and text contrast is not checked automatically (Material 3 defaults are used).

## What comes next

Phase 2 (three tiers of learning more from neighbouring devices) is planned; **X2 (policy guardrail tests) is done**, the tiers are not started: see [phase2/README.md](phase2/README.md).

## Spikes

| ID | Question | Time-box | Method | Decision it feeds |
|---|---|---|---|---|
| S1 (done: adapter yes, beacon injection no; see M5) | Can the emulator's virtual Bluetooth radio (netsim, which advertises built-in virtual BLE beacons on emulator 33.1.14+) let the **real** `AndroidBleScanner` find devices in a headless emulator? | 2 h | Start the AVD with netsim, run the real-mode app, check the list; try `emulator -netsim-args` options | If yes: add a real-radio E2E test to `--full` and reduce reliance on a physical phone. If no: physical phone stays the only real-radio check. Note: netsim cannot be bridged to a host USB radio at the same time. |
| S2 (done: see M6) | Does an Android MCP server or Google's Android CLI beat the `scripts/` harness on reliability and token cost? | 2 h | Same three tasks via each route (launch + assert a tag, grant permission via dialog, capture screenshot); compare steps, tokens, flakiness | Adopt, or document and drop |

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Plugin/AGP incompatibility at M1 | Medium | Slows M1 | Pin to newest mutually compatible set; keep fallback tools (Paparazzi) |
| Emulator unstable headless | Low-Med | Blocks E2E | `emu.sh` health check and auto-restart; JVM tests carry most of the load |
| Pixel 9a (API 31+) cannot exercise the API 26 to 30 location-permission path | Certain | That path validated only with the fake scanner on an older emulator image | Spike S1; document as a known gap |
| `uiautomator` idle-state hangs | Medium | Flaky inspection | Mitigations in dev-tooling.md section 3 |
| Agent drifts from conventions | Medium | Inconsistent code | Architecture tests, detekt, `AGENTS.md`, small commits gated by `verify.sh` |
| `neverForLocation` hides some beacons | Low | Missing devices in real scans | Documented trade-off; revisit if it matters |
| Scope creep (classic BT, connect, history) | Medium | Dilutes POC | Out of scope list below |

## Out of scope

Connecting or pairing; GATT; classic Bluetooth discovery; background or foreground-service scanning; persistence or history; export/share; filters beyond hide-unnamed; tablets/foldable layouts; localisation (English only); Play Store readiness; CI service (the local `verify.sh` is the CI).

## Suggested agent working method

1. Read `AGENTS.md` and the relevant doc section.
2. Make the smallest change that advances the current milestone item.
3. Run `verify.sh --quick`; fix until green.
4. For UI work: run the screenshot or emulator loop and *look* at the result.
5. Run `verify.sh --full` before closing a milestone.
6. Commit; tick the checkbox in this file in the same commit.
