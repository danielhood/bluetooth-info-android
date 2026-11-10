# Phase 2: Learning More From Neighbouring Bluetooth Devices

**Status:** Plan (2026-11-09). Nothing here is built. Phase 1 (M0 to M6, see [../roadmap.md](../roadmap.md)) is complete.

Phase 1 scans for BLE advertisements and shows what they carry. Phase 2 asks how much more information can legitimately be obtained from nearby devices, in three tiers of increasing reach and increasing responsibility. Pairing is out of scope for all tiers.

| Tier | Plan | New permission | Contacts the device? | Reverses a Phase 1 rule? |
|---|---|---|---|---|
| 1 | [Advertisement decoding](tier1-advertisement-decoding.md): beacons, sensors, vendor hints, radio metadata, rough distance | None | No (passive) | No |
| 2 | [System device info and classic discovery](tier2-system-info-and-classic.md): the OS's own cached view of a device, optional classic discovery | `BLUETOOTH_CONNECT` | 2a: no. 2b/2c: classic inquiry and an SDP query | Partly: permission rule, BLE-only decision (D7) |
| 3 | [GATT inspection](tier3-gatt-inspection.md): connect, list services, read standard information, optional live notifications | `BLUETOOTH_CONNECT` | **Yes** (explicit user action) | Yes: "never connect" |

Cross-cutting rules for all tiers are in [policy-and-guardrails.md](policy-and-guardrails.md). Read that before starting Tier 2 or 3.

## What the tiers add, in one sentence each

- **Tier 1:** decode what devices already broadcast (iBeacon, Eddystone, Ruuvi and BTHome sensors, Apple/Google/Microsoft frames, PHY and advertising interval) and show it with honest confidence labels.
- **Tier 2:** show what Android already knows about a device (system name, alias, bond state, device class, cached services) and optionally discover classic Bluetooth devices.
- **Tier 3:** on the user's explicit request, connect to one device, discover its full attribute table, read its standard information (manufacturer, model, serial, firmware, battery) and optionally stream live values.

## Sequencing

```
                     ┌──────────────────────────────┐
                     │ X1 Test-peripheral tooling   │  (shared, early)
                     │ X2 Policy + guardrails in    │
                     │    code (rules and tests)    │
                     └──────────────┬───────────────┘
                                    │ unblocks
   Tier 1 ───────────────► Tier 2a ─┴─► Tier 2b/2c ──► Tier 3
   (no permission)         (CONNECT,      (classic,       (connect,
                            cached info)   SDP)            GATT)
```

Recommended order and reasoning:

1. **X2 policy and guardrails first (small).** Update the architecture tests, manifest test and `AGENTS.md` so each later tier changes a rule deliberately rather than quietly. Details in the policy doc.
2. **X1 test-peripheral tooling in parallel with Tier 1.** It is what makes radio-level tests automatable (see below).
3. **Tier 1.** Independent of everything else, no new permission, highest value for least risk. Do this first.
4. **Tier 2a** introduces `BLUETOOTH_CONNECT` and its progressive-permission UX, which Tier 3 reuses.
5. **Tier 2b/2c** only after the owner confirms the classic-discovery scope change (decision D-P2-2 below).
6. **Tier 3** last: it needs the permission plumbing from 2a, the policy from X2, and the test peripheral from X1.

What can be done by parallel agent sessions without conflicts: X1 (scripts and tooling only) alongside Tier 1's pure-domain work (decoders and their tests). Everything that edits the detail screen or `BleDevice` is sequential.

## Shared work

### X1. Test-peripheral tooling (the biggest enabler)

Phase 1's weakest point was that real advertisements were only ever seen on one phone from whatever happened to be nearby. Tiers 1 and 3 need *known* advertisers and a *known* GATT server. Options, in order of preference:

| Option | What it gives | Cost and risk |
|---|---|---|
| **A. Host BlueZ controller as a peripheral.** This machine has a USB Bluetooth controller (`hci0`). BlueZ can broadcast arbitrary advertisements (iBeacon, Eddystone, manufacturer data) and host a GATT server (Device Information, Battery, custom services, optionally protected characteristics). The Pixel 9a then scans a deterministic, physically real device. | Real radio, real Android stack, fully scripted, free. | Needs BlueZ advertising/GATT tooling (for example `bluetoothctl`'s advertise menu, or a small Python GATT server using the `bluez` D-Bus API). Controller capabilities must be checked (`btmgmt info`). Phone must be unlocked and nearby. |
| **B. Google Bumble over the emulator's netsim.** Bumble is a Python Bluetooth stack that can act as a virtual peripheral and connect to the Android emulator's virtual radio. This would let the **emulator** run Tier 1 and Tier 3 end to end headlessly, which would resolve Phase 1's spike S1 limitation. | Fully headless and CI-friendly, no phone. | Third-party Python code (pin a version, install in a venv, review it). Netsim gRPC integration is the uncertain part and must be proven in a time-boxed spike. |
| **C. Dedicated dev board** (for example an nRF52 DK) running a stock GATT/beacon sample. | Deterministic, battery-powered, independent of the host. | Hardware purchase; not needed if A works. |
| **D. A second phone** running a peripheral simulator app. | No new hardware. | Manual, not scriptable, third-party app on a personal device. |

Spike **X1-S1** (time-box 3 h): prove option A for advertising (iBeacon + manufacturer data visible on the Pixel through the app) and for a minimal GATT server (Device Information with known strings, Battery 87%). Spike **X1-S2** (time-box 3 h): attempt option B against the emulator. Outcome decides which scripts are written: `scripts/peripheral.sh start|stop <profile>` for A, and/or a netsim script for B. Profiles correspond one-to-one with the fake-scanner scenarios so JVM and radio tests assert the same values.

### X2. Policy and guardrails

Described in [policy-and-guardrails.md](policy-and-guardrails.md): consent model, forbidden operations (writes, pairing), data handling, what the architecture tests must enforce at each tier.

### X3. Roadmap, AGENTS.md and gate updates

Each tier adds to `verify.sh` (new scenarios in the smoke test, new golden-vector tests), to `AGENTS.md` (changed rules, new scenario names) and to `docs/roadmap.md` (or a `docs/phase2/roadmap` section in this folder as the tiers progress). Real-device checks stay as separate scripts outside the default gate, as in Phase 1.

## Decisions to confirm (with recommended defaults)

| ID | Question | Recommended default |
|---|---|---|
| D-P2-1 | Proceed in the order X2, (X1 ∥ Tier 1), Tier 2a, Tier 2b/2c, Tier 3? | Yes |
| D-P2-2 | Tier 2b: reopen the "BLE only" decision (D7) to add classic Bluetooth discovery? | Yes, behind a separate source filter and a time-boxed 12 s inquiry |
| D-P2-3 | Tier 3: allow connecting at all in this app, under the policy doc? | Yes, with per-device confirmation, read-only, allowlisted characteristics, own-devices notice |
| D-P2-4 | Tier 3: allow "read every readable characteristic" (advanced mode)? | No for v1; revisit after v1 is verified |
| D-P2-5 | Test peripheral: spend effort on option B (Bumble/netsim) or stop at option A? | Try B in a time-boxed spike; keep A regardless |
| D-P2-6 | Should the app ever persist anything (device history, consent) across runs? | No (keeps decision D12); consent is per process |
| D-P2-7 | Minimum Android version for Tier 2/3 features | Same as app (26); features degrade gracefully where an API is newer |

## Risks across the phase

| Risk | Impact | Mitigation |
|---|---|---|
| Android initiates **bonding automatically** when a GATT read needs encryption | A pairing dialog appears (out of scope) | Allowlist standard characteristics, watch the bond-state broadcast and abort, warn in the consent text, test on a protected characteristic (Tier 3 spike T3-S2) |
| Scope creep from "inspect" to "control" | Safety and policy breach | Architecture test forbidding characteristic writes; descriptor writes only for notification enablement |
| Privacy exposure from serial numbers, MACs, locations | Harm to people near the user | Policy doc: no persistence, no network, no logging of identifiers in release, per-device confirmation |
| Radio tests flaky or hardware-dependent | Unreliable gate | Keep deterministic fake-based tests in the default gate; radio tests as named scripts; X1 tooling reduces dependence on whatever is nearby |
| Decoders claim more than is known | Misinformation (for example "this is an AirTag") | Confidence labels and wording rules in Tier 1; vendor formats that are reverse-engineered are labelled as hints |
| Android API differences across 26 to 37 | Crashes on some versions | Version-gated calls, Robolectric tests at SDK 30 and current, emulator tests, document untested versions |

## Out of scope for all of Phase 2

Pairing or bonding, writing to characteristics, firmware updates, pairing-protected data, MAC spoofing or tracking people, any network upload, device history or persistence, background or foreground-service scanning, controlling devices.
