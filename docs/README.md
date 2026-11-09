# BT Scanner POC: Project Plan

**Status:** Plan v1 (2026-11-08). Milestones M0 and M1 are done (toolchain and scaffold); see roadmap.md. `AGENTS.md` at the repo root holds the day-to-day agent instructions; this folder holds the plan and rationale.

## What this is

A proof-of-concept Android app, built almost entirely by AI coding agents, that:

1. Scans for nearby Bluetooth devices and shows them in a live list.
2. Lets the user tap a device to open a secondary screen with its basic details.
3. Does **not** connect to any device.

The app is deliberately small. The real goal of the POC is to find out how reliably an agent can build, run, inspect and fix an Android app **without a human in the loop**. Every decision in this plan is weighted toward that goal: deterministic builds, one-command verification, fast feedback, and the ability for an agent to *see and drive* the running app.

## Documents

| Doc | Contents |
|---|---|
| [decisions.md](decisions.md) | Every framework, language and tooling choice, with rationale, rejected alternatives and open questions. Read this first. |
| [architecture.md](architecture.md) | Layers, module/package layout, data flow, state models, threading, the BLE port/adapter split, build variants. |
| [functional-spec.md](functional-spec.md) | Screens, states, permission and Bluetooth edge cases, fields shown, acceptance criteria. |
| [dev-tooling.md](dev-tooling.md) | Toolchain to install, the agent harness (scripts, emulator, UI inspection), the verification pyramid, `AGENTS.md` contents. |
| [roadmap.md](roadmap.md) | Milestones M0 to M6 with exit criteria, spikes, risks. |

## Summary of key decisions

| Area | Decision |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Build | Gradle (Kotlin DSL, version catalog, wrapper), single `:app` module |
| Architecture | Unidirectional data flow (MVVM), three layers, BLE hidden behind a port interface |
| DI | Manual (`AppContainer`), no Hilt/Dagger |
| Bluetooth | **BLE scanning only**; no connection; no `BLUETOOTH_CONNECT` permission |
| Min SDK | 26 |
| Emulator story | The emulator has no real radio, so a scripted **fake scanner** (debug builds only) feeds the *same* parsing and UI path as the real one |
| Agent eyes and hands | Scripts over `adb` and `uiautomator` with stable Compose test tags, screenshots, filtered logcat |
| Verification | One gate, `scripts/verify.sh`: format, detekt, lint, JVM unit tests, JVM screenshot tests, then emulator tests |

## Owner answers (2026-11-08)

All earlier open questions are resolved:

1. **BLE only:** confirmed. Classic discovery is out of scope; the scanner port leaves room to add it later.
2. **Physical phone:** a Pixel 9a is available for real-radio validation (USB debugging to be enabled in M0). It runs a recent Android (API 31+), so it exercises the `BLUETOOTH_SCAN` path only. The API 26 to 30 location-permission path can be checked only on an emulator image, and only with the fake scanner unless spike S1 succeeds. That gap is accepted and recorded in the roadmap risk register.
3. **Package id and app name:** `dev.danielhood.btscan` / "BT Scan".
4. **Min SDK 26:** confirmed.

## Environment findings (as of 2026-11-08)

Checked on the dev machine before planning:

- Ubuntu 26.04, 16 cores, 14 GB RAM, 245 GB free disk.
- `/dev/kvm` present and readable/writable by the current user, so hardware-accelerated emulation should work.
- **Not installed:** JDK, Gradle, Android SDK, `adb`, emulator. Passwordless `sudo` is not available, so everything is installed in user space (see [dev-tooling.md](dev-tooling.md)).
- Present: `python3`, `jq`, `make`, `node`, `bluetoothctl` (host BlueZ).
- No Android phone was detected over USB.
