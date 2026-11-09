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

## M1. Scaffold and harness [ ]

Goal: an empty-but-real app plus the full verify loop.

- [ ] Gradle project: wrapper, `libs.versions.toml` with resolved latest-stable versions (check AGP/Spotless/detekt/Roborazzi compatibility, record choices), single `:app`.
- [ ] Manifest with the permissions from D9, `debug`/`release` source sets with `DebugOverrides`.
- [ ] A placeholder `DeviceListScreen` ("Hello") with a test tag and root `testTagsAsResourceId`.
- [ ] Spotless, detekt, lint, warnings-as-errors configured; one trivial unit test, one Roborazzi test, one Managed-Device instrumented test.
- [ ] `app.sh`, `ui.sh`, `shot.sh`, `logs.sh`, `verify.sh`, `e2e-smoke.sh` (smoke: launch, dump, assert the tag is present, screenshot).
- [ ] `AGENTS.md`, `CLAUDE.md`, `.gitignore`.

Exit: from a clean clone, `verify.sh --full` passes; an agent can launch the app on the emulator, dump the UI and see the placeholder tag, and take a screenshot.

## M2. Domain and data with fakes [ ]

- [ ] Models, ports, `Clock`.
- [ ] `AdvertisementParser` with golden-vector tests (acceptance 8).
- [ ] `DefaultDeviceRepository`: merge, staleness, throttle, ordering, failure (acceptance 5), all with virtual time.
- [ ] `FakeBleScanner` + scenarios (emits raw bytes) in `src/debug`; `AppContainer` wiring through `DebugOverrides`.
- [ ] Architecture tests.

Exit: verify `--quick` green; no UI beyond placeholder; repository demonstrably correct under every fake scenario in unit tests.

## M3. List screen and environment states [ ]

- [ ] `DeviceListViewModel` + `DeviceListUiState`; all `ListStatus` states.
- [ ] Compose list, Start/Stop/Clear, empty states, hide-unnamed, counter, signal indicator.
- [ ] Permission request flow, adapter monitor, resume re-evaluation (using the fake adapter monitor for now; the real ones land in M5).
- [ ] ViewModel tests with Turbine; Roborazzi screenshots for each status; reference images committed to `docs/screens/` after an agent has reviewed them.

Exit: acceptance 1, 6, 7 pass (the E2E part with the fake scanner on the emulator); agent has *looked at* the screenshots of every state.

## M4. Detail screen and navigation [ ]

- [ ] Routes, `AppNavHost`, `DeviceDetailViewModel`, detail UI for all F-D fields, lookup tables (company IDs, service UUIDs: small curated subset).
- [ ] Out-of-range / not-found states.
- [ ] Compose navigation tests; screenshots for loaded, sparse-fields, out-of-range, not-found.

Exit: acceptance 2, 3, 4, 5 pass on JVM and emulator.

## M5. Real Bluetooth [ ]

- [ ] `AndroidBleScanner` (`callbackFlow`, `SCAN_MODE_LOW_LATENCY`, error code mapping), `AndroidAdapterStateMonitor` (state broadcast + location state for API ≤ 30), `AndroidPermissionChecker`.
- [ ] Wire real ports as the default in `AppContainer`.
- [ ] Background-stop behaviour (acceptance 9).
- [ ] **Spike S1** (time-boxed, see below).
- [ ] **Manual:** real-device run (acceptance 10).

Exit: on a physical phone the app lists real nearby devices and detail looks right; `verify.sh --full` still green.

## M6. Hardening and wrap-up [ ]

- [ ] `many` scenario performance check (N-1); fix jank if present.
- [ ] Rotation, background, Bluetooth-toggle mid-scan (N-2).
- [ ] Accessibility pass (N-3) and dark theme screenshots (N-4).
- [ ] `assembleRelease` with minification; fake-absent check (acceptance 11).
- [ ] Optional debug state-dump receiver.
- [ ] **Spike S2** (MCP evaluation) write-up.
- [ ] Retrospective in `docs/retrospective.md`: what the agent did unaided, where a human had to step in, which tooling earned its keep.

Exit: all acceptance criteria met or explicitly listed as known gaps.

## Spikes

| ID | Question | Time-box | Method | Decision it feeds |
|---|---|---|---|---|
| S1 | Can the emulator's virtual Bluetooth radio (netsim, which advertises built-in virtual BLE beacons on emulator 33.1.14+) let the **real** `AndroidBleScanner` find devices in a headless emulator? | 2 h | Start the AVD with netsim, run the real-mode app, check the list; try `emulator -netsim-args` options | If yes: add a real-radio E2E test to `--full` and reduce reliance on a physical phone. If no: physical phone stays the only real-radio check. Note: netsim cannot be bridged to a host USB radio at the same time. |
| S2 | Does an Android MCP server or Google's Android CLI beat the `scripts/` harness on reliability and token cost? | 2 h | Same three tasks via each route (launch + assert a tag, grant permission via dialog, capture screenshot); compare steps, tokens, flakiness | Adopt, or document and drop |

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
