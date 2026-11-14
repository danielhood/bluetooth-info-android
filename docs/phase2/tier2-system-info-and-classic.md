# Tier 2: System Device Info and Classic Discovery

**Status:** 2a implemented (T2-M1 and T2-M2, 2026-11-14); T2-M0 spikes S1 and S2 observed on the emulator only, phone and T2-M5 pending; 2b, 2c not started. **New permission:** `BLUETOOTH_CONNECT` (Android 12+; nothing new on 11 and below). **Pairing:** never. **Connecting:** none in 2a; a brief classic inquiry in 2b; an SDP query in 2c.

## 1. Goal

Show what Android itself knows about a device, which the advertisement alone does not carry, and optionally widen discovery to classic Bluetooth. This tier is where the app first asks for `BLUETOOTH_CONNECT`, so it also builds the **progressive permission** plumbing that Tier 3 reuses.

It splits into three increments with separate gates because their risk differs:

| Increment | Contents | Contacts the device? | Gate |
|---|---|---|---|
| **2a** | Cached system info for devices the app already lists | No | Default |
| **2b** | Classic Bluetooth discovery, merged into the list as a second source | Classic inquiry (the device may answer; no connection) | Owner decision D-P2-2 (reopens decision D7, BLE-only) |
| **2c** | Classic service lookup (SDP) for one device on request | Yes, a short classic ACL session to ask what profiles it supports | Explicit per-device user action under the policy doc |

## 2. Scope

### 2.1 Increment 2a: system information

Data per device, all obtained from the OS without connecting (via `BluetoothAdapter.getRemoteDevice(address)`):

| ID | Pri | Field | Android source | Value to the user |
|---|---|---|---|---|
| T2-S1 | M | **System name** | `BluetoothDevice.getName()` | Often present when the advertisement has no name (the OS may have learned it earlier). Shown with "from system" attribution and not replacing the advertised name when both exist and differ. |
| T2-S2 | M | **Alias** | `BluetoothDevice.getAlias()` | A name the user chose in system settings for a known device |
| T2-S3 | M | **Bond state** | `getBondState()` | "Not paired / paired / pairing". **Read-only display.** The app never initiates or influences bonding. |
| T2-S4 | S | **Device type** | `getType()` | Classic, LE, dual-mode, or unknown |
| T2-S5 | S | **Bluetooth class** | `getBluetoothClass()` | Major class (phone, computer, audio/video, peripheral, wearable, health, toy, and so on) and the device sub-class, from a curated lookup table. Often absent for LE-only devices, shown as "not available". |
| T2-S6 | S | **Address type** | `getAddressType()` (API 35+) | Public/random/anonymous/unknown. Also answers Tier 1's T1-A1 if its spike finds the call needs CONNECT. Hidden on older Android versions. |
| T2-S7 | C | **Cached service UUIDs** | `getUuids()` | What the OS last learned about services; may be stale or empty. Clearly labelled "cached". |

Behaviours:

- **Progressive permission.** Scanning keeps working with only `BLUETOOTH_SCAN`. The detail screen shows a "System information" section with a button **"Allow system information"** when `BLUETOOTH_CONNECT` is missing. Granting it populates the section; denying leaves the rest of the screen unchanged and shows an explanation. Permanent denial offers app settings (same pattern as Phase 1).
- **No side effects.** Calls used in 2a must not cause radio traffic. Spike T2-S1 verifies this (logcat and, with X1 tooling, the peripheral side).
- **No identifiers logged** beyond debug builds (policy doc).
- **Android 11 and below:** the legacy `BLUETOOTH` permission already covers these calls; the section is available without a new prompt.

### 2.2 Increment 2b: classic discovery (needs owner decision D-P2-2)

| ID | Pri | Requirement |
|---|---|---|
| T2-C1 | M | A **"Scan classic devices (about 12 s)"** action next to Start/Stop. It runs `startDiscovery()` once and ends by itself; it is not a continuous scan. |
| T2-C2 | M | Results are merged into the same list as a second **source**. Each device shows its transport: BLE, Classic, or Both (same address seen by both). |
| T2-C3 | M | Classic rows show name (if reported), address, RSSI (as reported in the discovery broadcast), bond state, device class. They have no advertisement bytes, so decoded-data sections are simply absent. |
| T2-C4 | S | A source filter: All, BLE, Classic. |
| T2-C5 | M | Classic discovery stops if the app is backgrounded, when Bluetooth turns off, and before any Tier 3 connection. |
| T2-C6 | M | Both scans never run simultaneously (they compete for the radio and degrade each other, and discovery harms active audio streaming). Starting one stops the other with a visible note. |

### 2.3 Increment 2c: classic service lookup (SDP)

| ID | Pri | Requirement |
|---|---|---|
| T2-L1 | C | On a classic or dual-mode device's detail screen, **"Look up supported profiles"** runs `fetchUuidsWithSdp()` and shows the returned profile UUIDs with friendly names (A2DP, HFP, HID, PBAP, and so on; curated lookup). |
| T2-L2 | C | This contacts the device (a short classic session). It requires the same per-device confirmation as Tier 3 and the same "own devices" notice, because it is the first operation that talks to someone else's hardware. |
| T2-L3 | C | Timeout 10 s, one query at a time, cancelled when leaving the screen. |

## 3. Design

### 3.1 Domain

```
domain/system/
  SystemDeviceInfo.kt      data class SystemDeviceInfo(name, alias, bondState, type, deviceClass: DeviceClassInfo?, addressType, cachedUuids)
  DeviceClassInfo.kt       data class DeviceClassInfo(major: MajorClass, minor: String?, raw: Int)
  MajorClass.kt            enum + curated lookup from the Bluetooth class-of-device major/minor values
  ProfileUuids.kt          curated classic profile UUID names (2c)
domain/port/
  SystemDeviceInfoSource   suspend fun lookup(address): SystemDeviceInfo?      (2a; no radio traffic)
  ClassicScanner           fun discover(): Flow<ClassicSighting>               (2b; completes by itself after ~12 s)
  ProfileLookup            suspend fun fetchProfiles(address): List<UUID>      (2c; contacts the device)
  PermissionChecker        gains: connectState(): PermissionState and connectPermissions: List<String>
domain/model/
  DeviceSource.kt          enum { BLE, CLASSIC, BOTH }
  ClassicSighting.kt       address, name?, rssi?, deviceClass?, bondState, timestamp
```

- `BleDevice` is generalised carefully: `source: DeviceSource` (default BLE), `rawBytes` already nullable-friendly via `Bytes.EMPTY`. **Do not** add platform types. The repository merges classic sightings by address into the same table; a device seen by both sources becomes `BOTH` and keeps its advertisement bytes.
- `DeviceRepository` gains `startClassicDiscovery()` / `scanningClassic: StateFlow<Boolean>` (2b only). Staleness rules stay the same.
- `SystemDeviceInfoSource` is a port, so the ViewModel and tests never touch Android types. Results are fetched on demand, not stored in the repository (no persistence, decision D12).

### 3.2 Data layer (all `android.bluetooth.*` imports stay under `data/ble/android`)

- `AndroidSystemDeviceInfoSource`: guards each call with the permission check and a `SecurityException` catch; maps `BluetoothClass` int constants to domain `DeviceClassInfo`; version-gates `getAddressType()`.
- `AndroidClassicScanner` (2b): registers for `ACTION_FOUND`, `ACTION_DISCOVERY_FINISHED`; `callbackFlow`; always `cancelDiscovery()` on collection cancel; maps extras (RSSI as `Short.MIN_VALUE` sentinel means absent).
- `AndroidProfileLookup` (2c): `fetchUuidsWithSdp()` plus `ACTION_UUID` broadcast with a timeout.
- Fakes in `src/debug`: `FakeSystemDeviceInfoSource`, `FakeClassicScanner`, extended scenarios.

### 3.3 Permission model

- Manifest: add `BLUETOOTH_CONNECT` (API 31+). It stays **absent from the scan path**: `PermissionChecker.requiredPermissions` for scanning is unchanged.
- `PermissionChecker` gains a second, independent capability: `connectState()`. The list screen never requires it; only Tier 2/3 UI does.
- Whether granting `BLUETOOTH_CONNECT` shows a separate prompt or is granted together with `BLUETOOTH_SCAN` (they share the "Nearby devices" group) differs by Android version; **spike T2-S2** observes this on the Pixel and the emulator and records it. The UX copy handles both outcomes.

### 3.4 UI

- Detail screen: **"System information"** section (2a) with the progressive-permission states: needs permission, loading, loaded, unavailable for this device, permanently denied.
- List screen (2b): a "Classic scan" action and a source filter chip; transport badge in each row.
- Detail (2c): "Look up supported profiles" button with a confirmation dialog.
- Test tags: `system_info_section`, `system_info_allow_button`, `system_name`, `system_alias`, `system_bond_state`, `system_class`, `classic_scan_button`, `source_filter`, `source_badge_<address>`, `profiles_button`, `confirm_dialog_*`.

## 4. Testing and verification

| Layer | What |
|---|---|
| JVM unit | Class-of-device lookup (table-driven, including unknown values); `SystemDeviceInfo` mapping with sentinels; repository merge of BLE and classic sightings (`BLE`, `CLASSIC`, `BOTH`), staleness for classic rows; ViewModel permission states (needs permission, granted, denied, permanent) in the same style as `DeviceListViewModelTest`; "never both scans at once" rule. |
| Robolectric | `AndroidSystemDeviceInfoSource` and `AndroidClassicScanner` with shadows at the current SDK and at SDK 30; permission-denied paths return failures, not crashes. |
| Screenshot | System-information states; list with transport badges and source filter (light and dark). |
| Architecture tests | Updated per policy doc: `BLUETOOTH_CONNECT` is now allowed in the manifest **and** in `data/ble/android`; `connectGatt` is still forbidden everywhere in Tier 2 (it only becomes allowed, in one file, in Tier 3). |
| Emulator smoke | Progressive permission flow with `pm revoke`/`pm grant` for CONNECT; fake scenarios `system-info`, `classic-mixed`. |
| Real radio (Pixel) | 2a: a device the phone already knows (user's own earbuds or watch) shows alias, bond state and class; an unknown advertiser shows "not available" gracefully. 2b: classic discovery lists real classic devices nearby; confirm no crash and that stopping works. With X1 option A the host controller can be made discoverable for a deterministic classic target. 2c: only against the user's own device. |

## 5. Milestones and exit criteria

| ID | Work | Exit |
|---|---|---|
| T2-M0 | Policy items for CONNECT (see policy doc); spike T2-S1 (calls cause no traffic) and T2-S2 (permission prompt behaviour) | Findings recorded here |
| T2-M1 | Ports, fakes, `SystemDeviceInfo`, progressive-permission ViewModel and UI, class lookup | AC T2-1, T2-2 |
| T2-M2 | Android implementation of 2a, Robolectric tests, manifest and architecture test updates | AC T2-3 |
| T2-M3 (gate D-P2-2) | Classic discovery: port, repository merge, UI source filter, Android scanner | AC T2-4, T2-5 |
| T2-M4 (optional) | SDP profile lookup with confirmation dialog | AC T2-6 |
| T2-M5 | Real-device validation, docs, AGENTS.md rule updates | AC T2-7 |

### 2a implementation notes (2026-11-14)

- **Built:** `domain/system` (`SystemDeviceInfo`, table-driven `DeviceClassInfo.of`, `ProfileUuids`), the `SystemDeviceInfoSource` port and `PermissionChecker.connectState()/connectPermissions`, `AndroidSystemDeviceInfoSource` (every read inside one `SecurityException` guard; address type gated on API 35, alias on API 30), `FakeSystemDeviceInfoSource` (honours the real permission so the ask and grant flow is identical) and the `system-info` scenario, the progressive-permission ViewModel and a "System information" section on the detail screen, `BLUETOOTH_CONNECT` in the manifest.
- **Guardrails (policy doc):** `BLUETOOTH_CONNECT` may be named only under `data/ble/android`; `fetchUuidsWithSdp`, `startDiscovery`, `cancelDiscovery` and `connectGatt` are forbidden everywhere; a new rule forbids logging identifiers or values in `src/main` and `src/release`. Each rule was shown to fail on a deliberate violation.
- **Spike T2-S2 (emulator, API 36):** with Nearby devices and Location granted for scanning, tapping "Allow system information" shows **no dialog**; Android grants `BLUETOOTH_CONNECT` immediately because it shares the Nearby devices group. The copy was changed to "needs your permission to read it" instead of "one more permission". To confirm on the Pixel (Android 17).
- **Spike T2-S1 (no radio traffic):** the lookups are plain getters over the system's cache (`getName`, `getAlias`, `getBondState`, `getType`, `getBluetoothClass`, `getAddressType`, `getUuids`) and never call anything that connects or queries services; the architecture test enforces the absence of the over-the-air calls. Empirical confirmation with the host peripheral (no connection or pairing appears on its side) is pending a phone.
- **Open for the phone:** what the Pixel reports for an LE-only advertiser and for a bonded device; whether `getAddressType()` needs the connect permission (answers T1-S1); T2-M5 wording review.

## 6. Acceptance criteria

1. **T2-1:** with `BLUETOOTH_CONNECT` revoked, scanning and the list work exactly as in Phase 1, and the detail screen offers "Allow system information"; nothing else changes.
2. **T2-2:** with it granted, the section shows name/alias/bond/type/class/address-type as available and "not available" otherwise; no value is fabricated.
3. **T2-3:** the manifest test passes with `BLUETOOTH_CONNECT` allowed and `INTERNET` still forbidden; `connectGatt` is still absent from the source (architecture test).
4. **T2-4:** a classic scan ends by itself within about 15 s, stops on background and when Bluetooth turns off, and never overlaps a BLE scan.
5. **T2-5:** a device seen by both sources appears once, marked `BOTH`, keeping its advertisement bytes.
6. **T2-6:** the SDP lookup only runs after the per-device confirmation, times out cleanly, and shows friendly profile names.
7. **T2-7:** real-device runs on the Pixel meet the Tier 2 rows of the testing table; owner review of the wording about bond state ("read-only display").

## 7. Risks and open questions

| Risk or question | Plan |
|---|---|
| `getName()` or other calls trigger radio traffic or remote name requests | Spike T2-S1; if they do, move them behind the consent flow |
| Classic discovery disturbs the user's audio | Visible warning on first use; short 12 s inquiry only; never automatic |
| Mixed-transport merge confuses the list model (BLE-centric) | Keep `BleDevice` fields optional where classic has none; add merge tests before UI work |
| API-level differences (class, address type, discovery permission rules differ before API 31) | Version gates plus Robolectric SDK 30 tests; document untested versions |
| Bond state shown can be misread as "app paired this" | Wording and a policy rule: display only, never offer to pair or unpair |

Out of scope for Tier 2: pairing, unpairing, forgetting devices, connecting profiles (A2DP, HFP), reading remote names by actively requesting them beyond what the OS does itself.
