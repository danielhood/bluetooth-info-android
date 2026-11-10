# Tier 3: GATT Inspection

**Status:** Plan. **Permission:** `BLUETOOTH_CONNECT` (from Tier 2a). **Contacts the device:** yes, one device at a time, only after an explicit user action and confirmation. **Pairing:** never; **writing:** never (one narrow, defined exception for enabling notifications, see 3.4).

This tier **reverses the Phase 1 rule "never connect"**. It is only started after the policy changes in [policy-and-guardrails.md](policy-and-guardrails.md) are in place and the owner has confirmed decision D-P2-3.

## 1. Goal

For one device the user selects, connect over GATT, discover its attribute table, and read the standard, widely implemented information it exposes without pairing: who made it, which model and firmware, its battery level, its advertised appearance. Optionally, show live values from standard notification services. The result is a read-only "Device information" screen.

## 2. Scope

### 2.1 Version 1 (read-only, allowlisted)

| ID | Pri | Capability | Detail |
|---|---|---|---|
| T3-G1 | M | **Connect and discover** | LE transport, no auto-connect, 10 s connect timeout, 10 s service discovery timeout. Result: the full list of services, characteristics and descriptors with UUIDs, friendly names where known, and property flags (read, write, write-no-response, notify, indicate). |
| T3-G2 | M | **Generic Access** (`0x1800`) | Device name (`0x2A00`), appearance (`0x2A01`, shown as a category via a curated lookup), preferred connection parameters (`0x2A04`). |
| T3-G3 | M | **Device Information** (`0x180A`) | Manufacturer name, model number, serial number, hardware revision, firmware revision, software revision (`0x2A29`, `0x2A24`, `0x2A25`, `0x2A27`, `0x2A26`, `0x2A28`), System ID (`0x2A23`), PnP ID (`0x2A50`: vendor id source, vendor id, product id, version; vendor looked up in the company table), regulatory certification data (`0x2A2A`, shown as hex). |
| T3-G4 | M | **Battery Service** (`0x180F`) | Battery level (`0x2A19`), percent. |
| T3-G5 | M | **Failure transparency** | Every operation reports success, failure with Android's status code and plain-language reason, or "requires pairing; skipped". A partial result is shown, never hidden. |
| T3-G6 | M | **Always disconnect** | On leaving the screen, on app background, on Bluetooth turning off, on overall timeout (30 s), on user cancel. `BluetoothGatt.close()` is always called. |
| T3-G7 | S | **Read-only escape hatch for unknown characteristics: none in v1.** | Unknown services are listed with their properties but their values are not read (see 2.3). |

### 2.2 Version 2 (live values, still read-only)

| ID | Pri | Capability | Detail |
|---|---|---|---|
| T3-N1 | S | **Notifications for standard services** | Battery level notify; Heart Rate Measurement (`0x2A37`, flags, 8/16-bit value, optional contact and energy fields); Environmental Sensing (temperature `0x2A6E`, humidity `0x2A6F`, pressure `0x2A6D`) for devices that allow unauthenticated notification. Shown as a live panel with a stop button; auto-stop after 60 s. |
| T3-N2 | C | **Connection details** | Negotiated MTU, PHY, connection interval as reported by callbacks; on-demand RSSI. |

### 2.3 Explicit exclusions

- **No writes to characteristics.** Notification enablement writes the standard Client Characteristic Configuration descriptor (`0x2902`) only, through one function, and nothing else.
- **No reading of unknown or vendor characteristics.** They may have side effects or leak sensitive data and cannot be assumed safe. An "advanced: read all readable characteristics" mode is deliberately not part of v1 (decision D-P2-4); revisit only after v1 is verified and the owner agrees.
- **No pairing, bonding, or security level changes.** No `createBond`, no reflection or hidden API.
- **No connection to more than one device at a time**, no reconnect loops, no background operation, no persistence, no uploading.

### 2.4 User flow and consent

1. Detail screen of a **connectable** device shows **"Inspect device…"** (disabled with an explanation when the device is not connectable).
2. A confirmation dialog states, with the device's name/address: that the app will connect and read standard information; that the device owner may be interrupted (some devices allow only one connection); that the user should only do this for devices they own or have permission to inspect; and that some devices may ask to pair, which the user can decline.
3. Confirmation starts the inspection on a dedicated **Inspect screen** (its own navigation destination, so leaving it cancels and disconnects).
4. The screen shows live progress (Connecting, Discovering, Reading 3 of 9), then results, with a "Copy as text" action for the user's own use.
5. First use per process also shows a short one-time explanation. Consent is **not persisted** across app restarts (decision D-P2-6).

## 3. Design

### 3.1 Domain

```
domain/gatt/
  GattInspector.kt          interface GattInspector { fun inspect(address: String, plan: ReadPlan): Flow<GattSessionState> }
                            // Cold flow; collecting starts the session, cancelling disconnects and closes.
  GattSessionState.kt       sealed: Connecting, Discovering, Reading(done, total),
                            Finished(GattResult), Failed(GattFailure)
  GattResult.kt             services: List<GattService>, summary: DeviceInfoSummary
  GattService/GattCharacteristic/GattDescriptor   uuid, friendlyName?, properties, value: ReadOutcome?
  ReadOutcome.kt            sealed: Value(Bytes), Skipped(reason), Failed(status, reason), RequiresPairing
  DeviceInfoSummary.kt      deviceName, appearance, manufacturer, model, serial, hw/fw/sw revision, systemId, pnpId, battery
  GattFailure.kt            enum Kind { PERMISSION_MISSING, NOT_CONNECTABLE, CONNECT_FAILED, TIMEOUT, DISCONNECTED,
                                     SERVICE_DISCOVERY_FAILED, BONDING_REQUESTED, BLUETOOTH_OFF }  + platform status
  ReadPlan.kt               the explicit allowlist (service uuid -> characteristic uuids) the session may read
  parsers/                  DeviceInformation, Appearance, Battery, HeartRate, EnvironmentalSensing, Ppcp, PnpId (pure)
  lookup/                   GattUuids (services/characteristics/descriptors), AppearanceCategories (curated)
```

- **`ReadPlan` is the safety valve.** The adapter reads only characteristics the plan names. The default plan is the allowlist in 2.1 and cannot be extended from the UI in v1. A test asserts the default plan contains no write-capable operation and only the listed UUIDs.
- Parsers are pure functions of `Bytes`, with golden vectors from the Bluetooth SIG specifications (string characteristics are UTF-8, battery is one byte 0 to 100, PnP ID is 7 bytes, Heart Rate Measurement has a flags-dependent layout).
- State machine and timeouts live in the domain-level session logic (injected clock/dispatcher), not inside the Android adapter, so failure paths are testable on virtual time.

### 3.2 Data layer

`AndroidGattInspector` in `data/ble/android` (the only file allowed to call `connectGatt`, enforced by the architecture test). It wraps `BluetoothGattCallback` in a `callbackFlow`, with these known Android realities handled explicitly:

- **Serialise operations.** Android allows one outstanding GATT operation; a small operation queue issues the next read only after the previous callback. All callbacks arrive on binder threads and are posted into a channel.
- **Callback signature differences.** API 33 introduced new `onCharacteristicRead(gatt, characteristic, value, status)`; older versions use the deprecated overload and `characteristic.value`. Implement both, version-gated, covered by Robolectric at SDK 30 and current.
- **Status 133 and friends.** Retry the connect once on a generic failure (status 133), then report. Always call `gatt.close()` in a `finally`/`awaitClose`, even after errors, to avoid exhausting the platform's limited GATT clients.
- **Stop the BLE scan first.** The ViewModel stops scanning before connecting (scanning during connect reduces success rates); the app does not resume automatically.
- **Address validity.** Resolvable private addresses rotate; if the device's address changed since it was listed, the connect fails and the screen says "the device may have changed its address; scan again".
- **Bond watch.** Register for `ACTION_BOND_STATE_CHANGED` filtered to the device; if `BOND_BONDING` appears, abort the session, disconnect, and show `BONDING_REQUESTED` with guidance ("the device asked to pair; decline the system prompt").
- **Permission and adapter loss mid-session.** `SecurityException` maps to `PERMISSION_MISSING`; the adapter monitor turning off ends the session with `BLUETOOTH_OFF`.
- **No logging of values or addresses in release builds** (policy doc); debug builds log state transitions only.

### 3.3 Fake GATT for the debug build and JVM tests

`FakeGattInspector` plays scripted sessions from `GattScenarios`, selected through the existing `DebugOverrides` seam (`--es gatt <name>`):

| Scenario | Behaviour |
|---|---|
| `full-info` | Complete Device Information, Battery 87%, appearance "heart rate sensor", several unknown vendor services listed but not read |
| `battery-only` | Only Battery and Generic Access |
| `needs-pairing` | A Device Information read fails with insufficient authentication; the session reports "requires pairing; skipped" for that item and continues |
| `bonding-requested` | Simulates `BOND_BONDING` mid-session; expects abort |
| `connect-fails` | Status 133 twice |
| `timeout` | Discovery never completes |
| `disconnect-midway` | Link drops after two reads |
| `notify-heart-rate`, `notify-env` | Live values for v2 |

### 3.4 The one defined "write"

Enabling notifications requires writing the CCCD descriptor with a standard two-byte value. It is implemented in a single function `enableNotifications(characteristic)` which writes only descriptor `0x2902` and only for characteristics in the notification allowlist. The architecture test enforces: no `writeCharacteristic` anywhere; `writeDescriptor` appears only in that function's file; no `createBond`, `setPin`, `setPairingConfirmation`, reflection on `BluetoothDevice`.

### 3.5 UI

New destination `InspectDestination(address)` and screen `DeviceInspectScreen`:

- Confirmation dialog (tag `inspect_confirm_dialog`, buttons `inspect_confirm`, `inspect_cancel`).
- Progress states, then sections: Summary (manufacturer, model, serial, revisions, battery, appearance, PnP), Attribute table (expandable services with characteristic properties and read outcomes), Notifications panel (v2), error banner with the reason and a Retry.
- Tags: `inspect_button`, `inspect_progress`, `inspect_summary_<key>`, `inspect_service_<uuid>`, `inspect_error`, `inspect_retry`, `inspect_copy`, `inspect_notify_toggle`.
- Serial numbers and addresses are shown on screen (the user asked for them) but **never logged** and never leave the app except through the explicit Copy action.

## 4. Testing and verification

| Layer | What |
|---|---|
| JVM unit | Parser golden vectors; the `ReadPlan` allowlist test; session state machine on virtual time for every scenario (timeouts, retries, partial results, abort on bonding, disconnect midway, cancellation closes the fake and "disconnects"); ViewModel states; "never more than one session". |
| Robolectric | `AndroidGattInspector` callback handling with shadows: operation serialisation, both callback signatures (SDK 30 and current), status mapping, `close()` on every exit path. |
| Architecture tests | `connectGatt` only in `AndroidGattInspector.kt`; no writes except the notification helper; no pairing calls; no `android.bluetooth` outside `data/ble/android`. |
| Screenshot | Confirmation dialog, progress, full summary, partial with "requires pairing", failure banners, notification panel (light and dark). |
| Whole-app JVM | List to detail to Inspect with the fake: consent required, results shown, back disconnects (the fake records a disconnect). |
| Emulator smoke | `--es gatt full-info` flow through the UI by tags; asserts the fake recorded exactly one session and one disconnect. |
| **Real radio, deterministic** | X1 option A: the host BlueZ controller runs a GATT server with a known Device Information profile (fixed strings, battery 87%), plus a **protected** characteristic that requires encryption. Script `scripts/e2e-gatt.sh` runs on the Pixel and asserts the exact values, the "requires pairing; skipped" handling, and what Android does on the protected read (spike T3-S2). X1 option B (Bumble over netsim) would let the emulator do the same headlessly. |
| Real radio, own devices | The owner's own BLE gadget (fitness band, sensor) inspected on the Pixel; review of the result for accuracy and of the consent wording. Never against devices the owner does not control. |
| Safety checks | A test peripheral that counts writes: after any session, its write counter must be zero except the CCCD write in the notification scenarios. |

## 5. Milestones and exit criteria

| ID | Work | Exit |
|---|---|---|
| T3-M0 | Policy implemented (rules and tests), Tier 2a permission flow merged; **spike T3-S1** (can the host BlueZ controller serve the profiles and is the Pixel able to connect), **spike T3-S2** (what Android does when a read needs encryption: auto-bond prompt or plain failure) | Findings recorded; consent text adjusted to match |
| T3-M1 | Domain models, parsers and lookups with vectors; `ReadPlan`; session state machine; `FakeGattInspector` and scenarios | AC T3-1, T3-2 |
| T3-M2 | Inspect screen, confirmation flow, ViewModel, tags, screenshots, whole-app and emulator smoke with the fake | AC T3-3, T3-4 |
| T3-M3 | `AndroidGattInspector` with Robolectric tests; architecture rule updates | AC T3-5 |
| T3-M4 | Real-device validation with the X1 profile and own devices; `e2e-gatt.sh`; docs, AGENTS.md | AC T3-6, T3-7 |
| T3-M5 (v2, optional) | Notification support and live panel (`CCCD` helper, parsers, scenarios, tests) | AC T3-8 |

## 6. Acceptance criteria

1. **T3-1:** every parser reproduces its specification vectors and rejects malformed input without throwing; the default `ReadPlan` contains exactly the allowlisted characteristics.
2. **T3-2:** each fake scenario ends in the documented state; partial results are shown; every session ends with exactly one disconnect/close, including on cancellation and on every failure.
3. **T3-3:** the Inspect screen cannot start without the confirmation dialog; cancelling does nothing; confirmation starts one session.
4. **T3-4:** leaving the screen, backgrounding the app, or turning Bluetooth off during a session disconnects it and returns the UI to a sane state.
5. **T3-5:** the architecture tests pass with the rules in the policy doc; a deliberate violation (adding a characteristic write) fails the gate.
6. **T3-6:** against the X1 test peripheral the Pixel shows exactly the configured manufacturer, model, serial, revisions and battery level, and the protected characteristic is reported as "requires pairing; skipped" with no write counted on the peripheral.
7. **T3-7:** on an owner-controlled real device the result is accurate; any system pairing prompt that appears is declinable and the app ends in a clean "bonding requested" state.
8. **T3-8 (v2):** live heart-rate/battery/environmental values update and stop on the stop button, on 60 s, on leaving, and on disconnect; the only write is the CCCD.

## 7. Risks and open questions

| Risk or question | Plan |
|---|---|
| **Android auto-initiates bonding** when a read needs encryption | Allowlist limits exposure, bond watch aborts, consent text warns; spike T3-S2 establishes exact behaviour and the copy is adjusted |
| Connecting interrupts the owner's use of a one-connection peripheral | Consent text; short sessions; strict timeouts; always disconnect |
| Flaky GATT behaviour across vendors (status 133, stale caches) | One retry, clear error, no loops; hardware tests on at least two device types |
| Reading serial numbers of others' devices is a privacy issue | Policy: own-devices notice, per-device confirmation, no logging, no persistence, no upload; owner approval of the wording before release |
| Notification parsers wrong for edge flags | Vectors from the specifications, including all flag combinations for Heart Rate |
| Test peripheral limitations (BlueZ cannot reproduce a vendor quirk) | Combine with real own-device tests; record gaps |
| minSdk 26 API differences | Version-gated overloads; Robolectric at SDK 30 and current; note any untested version |

Out of scope for Tier 3: pairing or bonding, writing characteristics, firmware or configuration changes, reading unknown vendor data, multiple simultaneous connections, background inspection, saving or exporting results to storage or network (the Copy action is a user-initiated clipboard copy only).
