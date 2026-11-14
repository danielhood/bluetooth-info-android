# Functional Specification

Scope: scan for nearby BLE devices, list them, show basic details for one. No connecting, no persistence, no background operation.

IDs (`F-…`) are for citing in tests and commits. Priority: **M** must, **S** should, **C** could.

## Screens

### 1. Device List (start screen)

| ID | Pri | Requirement |
|---|---|---|
| F-L1 | M | Top bar with title "BT Scan". |
| F-L2 | M | A primary **Start scan / Stop scan** button (single button, label toggles). |
| F-L3 | M | Rows show: name (or "Unnamed device" in muted style), address, RSSI in dBm with a 4-level signal indicator, and a "connectable" marker if applicable. |
| F-L4 | M | Rows are ordered by RSSI descending, ties by address, updating at most every 500 ms while scanning. Rows are keyed by address so scroll position and focus survive reordering. |
| F-L5 | M | Tapping a row opens the Device Detail screen for that address. |
| F-L6 | M | A counter shows "N devices". |
| F-L7 | M | While scanning, a visible scanning indicator is shown (non-animating when system animations are disabled, see tooling doc). |
| F-L8 | M | Devices stay listed after Stop. A **Clear** action empties the list and returns to the initial hint ("Tap Start scan..."). |
| F-L9 | S | "Hide unnamed devices" switch (default off). |
| F-L10 | M | Empty states: before first scan, "Tap Start scan to look for nearby devices"; scanning with no results yet, "Searching…"; stopped with zero results, "No devices found". |
| F-L11 | M | Devices unseen for 30 s while scanning are removed. |
| F-L12 | M | Scanning stops automatically when the app is backgrounded (state becomes Idle with devices retained). |

### 2. Device Detail (secondary screen)

Reached by tapping a row; system back or top-bar up arrow returns to the list with scan state unchanged.

| ID | Pri | Field | Source |
|---|---|---|---|
| F-D1 | M | Name (or "Unnamed device") | Local-name AD structure |
| F-D2 | M | Address | `ScanResult.device.address`; note shown that it may be a randomised private address |
| F-D3 | M | Signal: current RSSI (dBm), live | Latest advertisement |
| F-D4 | M | Connectable: Yes / No | `ScanResult.isConnectable` |
| F-D5 | M | TX power (dBm) or "Not advertised" | TX-power AD structure |
| F-D6 | M | First seen / last seen ("12 s ago") and advertisement count | Repository |
| F-D7 | M | Service UUIDs: full list; 16-bit standard UUIDs also show a friendly name when known (for example Heart Rate, Battery) | Parser + small lookup table |
| F-D8 | M | Manufacturer data: company ID (hex) with company name when known, payload as hex | Parser + small lookup table |
| F-D9 | S | Service data entries (UUID + hex) | Parser |
| F-D10 | S | Advertising flags decoded (LE General Discoverable, BR/EDR not supported, …) | Parser |
| F-D11 | S | Raw advertisement bytes as hex, with a copy action | Repository |
| F-D12 | M | **Out of range state:** if the device is no longer in the repository, keep showing the last known data (if the screen was already open) under a banner "Out of range, last seen N s ago"; if it was never known (for example after process restore) show "Device not found" with a back action. | Repository |

Not shown (would require connecting or `BLUETOOTH_CONNECT`): bond state, GATT services, device class, alias.

## Environment and permission states

The list screen derives one status from permission, adapter and scan state. Each non-normal state replaces the list area with an explanation and one clear action. The Start button is disabled or re-labelled accordingly.

| State | Trigger | UI | Action |
|---|---|---|---|
| Permission needed | Required runtime permission not granted | Explanation of why Nearby devices and Location (API 31+) or Location (API 26 to 30) are needed | **Grant permission** triggers the system dialog |
| Permission permanently denied | Denied and the OS will not prompt again | Explanation that it must be changed in Settings | **Open app settings** |
| Bluetooth off | Adapter state off | "Bluetooth is turned off" | **Turn on** opens the system Bluetooth settings/enable intent |
| Location off | Location services disabled (all API levels) | "Location must be on for scanning. Android counts Bluetooth scan results as location data." | **Open location settings** |
| Unsupported | No BLE hardware / no adapter | "This device does not support Bluetooth LE" | None |
| Failed | Scanner reports an error | Plain-language message with the platform code; scan-too-frequent says "Wait a few seconds and try again" | **Retry** |

The permission-needed state is shown as soon as the list screen loads (not only after tapping Start), with Start disabled. The environment is re-evaluated on every resume (the user may have changed settings) and when the adapter state broadcast changes. Granting a permission or turning Bluetooth on never auto-starts a scan; the user taps Start.

The Start/Stop button is debounced (about 1 s) because Android throttles repeated scan starts (about 5 per 30 s window).

## Non-functional requirements

| ID | Requirement |
|---|---|
| N-1 | List stays responsive with 200 simulated devices updating (fake scenario `many`): no dropped-frame jank visible in a manual check, no ANR. |
| N-2 | No crash on rotation, backgrounding, or Bluetooth toggled mid-scan. |
| N-3 | Accessibility: every interactive element has a content description or visible text; touch targets 48 dp; contrast per Material 3 defaults. |
| N-4 | Light and dark themes supported (system-driven); no dynamic colour requirement. |
| N-5 | The app never calls `connectGatt`, requests `BLUETOOTH_CONNECT`, or makes any network call. |
| N-6 | Release builds contain no fake scanner and cannot be put in fake mode. |

## Fake scenarios (debug builds)

Selected with `--es scenario <name>`; each is deterministic (seeded) so screenshots and assertions are repeatable.

| Scenario | Behaviour | Used to test |
|---|---|---|
| `default` | 6 devices: 3 named (one with service UUIDs, one with manufacturer data), 3 unnamed; RSSI drifts; one appears at t=3 s; one vanishes at t=40 s | Happy path, ordering, staleness |
| `empty` | Scan succeeds, no devices | F-L10 |
| `many` | 200 devices, continuous updates | N-1 |
| `sparse-fields` | Devices with only a name, or only manufacturer data, or malformed AD structures | Parser robustness, F-D5 "Not advertised" |
| `beacons` | An iBeacon, an Eddystone device rotating UID/URL/TLM frames, an Eddystone-EID and an AltBeacon (exact bytes in `BeaconFrames.kt`) | Tier 1 decoders, frames accumulating on one device |
| `sensors` | Ruuvi format 5 (the documentation example), BTHome plain, BTHome encrypted (exact bytes in `SensorFrames.kt`) | Tier 1 sensor decoders, encrypted handling |
| `vendors` | Apple Find My / AirDrop / Proximity Pairing / unnamed message, Fast Pair (both modes), Tile- and SmartTag-style identifiers, a plain device (exact bytes in `VendorFrames.kt`; payloads are filler) | Tier 1 vendor hints and wording rules |
| `radio` | Extended advertising with 2M PHY, set id and periodic interval; coded PHY without TX power; truncated report; slow legacy device; fast calibrated iBeacon (intervals 500/500/500/1000/100 ms) | Radio section, omitted unavailable values, interval and distance estimates |
| `bluetooth-off` | Adapter reports Off | Bluetooth-off state |
| `unsupported` | Adapter reports Unsupported | Unsupported state |
| `scan-fails` | Scanner throws `ScanFailure` after 2 s | Failed state and Retry |
| `too-frequent` | Scanner throws "scanning too frequently" on start | Throttle message |

Runtime permission handling is **not** faked: the real Android permission flow runs, and agents drive it with `adb shell pm grant/revoke` or by tapping the dialog.

## Acceptance criteria

Each is checked by an automated test unless marked (manual).

1. **Scan to list.** With scenario `default`, after tapping Start the list shows 5 devices within 2 s and 6 after 3 s, ordered by RSSI. *(emulator E2E, ViewModel test)*
2. **List to detail.** Tapping the first row opens a detail screen whose address matches the row and whose fields F-D1 to F-D8 are present. *(Compose test, E2E)*
3. **Live detail.** On the open detail screen the RSSI value changes at least once within 3 s. *(E2E)*
4. **Back.** Back from detail returns to the list; scroll position and scanning state unchanged. *(Compose test)*
5. **Stale removal.** A device that stops advertising disappears from the list after 30 s of virtual time and detail shows the out-of-range banner. *(repository + ViewModel tests)*
6. **Permission paths.** With permission revoked, Start leads to the permission-needed state; granting and tapping Start scans. Permanently denied shows the Settings action. *(E2E with `pm revoke`)*
7. **Adapter paths.** `bluetooth-off` and `unsupported` scenarios show their states and no Start action. *(Compose screenshot tests)*
8. **Parser.** Golden-vector tests cover: flags, short/complete name, TX power, 16/32/128-bit UUIDs, service data, manufacturer data, truncated and zero-length structures, oversized lengths. *(JVM unit)*
9. **Background stop.** Sending the app to the background stops the scan; resume shows Idle with retained devices. *(E2E)*
10. **Real hardware (manual).** On a physical Android 12+ phone with BLE devices nearby, a real scan lists at least one device and detail shows plausible values. Repeat on an Android 8 to 11 device or emulator if available (location path). *(manual, final milestone)*
11. **Release safety.** `assembleRelease` succeeds; an architecture test and a release-APK string check confirm no fake classes are present. *(CI-style gate)*
