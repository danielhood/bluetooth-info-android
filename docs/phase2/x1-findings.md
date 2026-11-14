# X1 findings: test peripheral spike (host BlueZ to the Pixel)

**Date:** 2026-11-10. **Status:** spike X1-S1 done (option A of [README](README.md#x1-test-peripheral-tooling-the-biggest-enabler) works). X1-S2 (Bumble over the emulator's radio) not started.

## Result in one paragraph

This machine's Bluetooth controller (BlueZ 5.85, peripheral role, 20 advertising instances, extended advertising on 1M/2M/Coded) can advertise any of the Tier 1 payloads, and the Pixel 9a (Android 17) receives them through the real scanner. Ruuvi, AltBeacon, BTHome (plain and encrypted) decoded **with exactly the expected values**. iBeacon, Eddystone (UID, URL, TLM) and extended (2M) advertisements were **not delivered** while the app declares `neverForLocation`, and **were delivered and decoded correctly once that flag was removed** (control experiment, not committed). The spike also found and fixed a real app bug (below).

## What was built

- `scripts/peripheral/profiles.json`: advertisement profiles (hex, same bytes as the fake scenarios) and `scripts/peripheral/peripheral.py`: registers them with BlueZ over D-Bus. Needs only the system packages `python3-dbus` and `python3-gi`; no root, nothing installed or reconfigured; advertising only (no connections, no pairing).
- `scripts/peripheral.sh` with `list`, `start`, `stop`, `status` and `probe <profile>` (launches the app on the phone, scans, advertises, prints what the app decoded). The script was confirmed end to end on 2026-11-14: it installs and launches the app, scans, advertises, finds the row by the host's address, prints the decoded and Radio lines (scrolling for the lower ones) and stops advertising on exit.
- `PeripheralProfilesTest` fails if a profile that mirrors a fake scenario differs from it by a single byte (proved by a deliberate edit; `profiles.json` is declared as a Gradle test input so edits re-run it).

## Real-radio results (app with `neverForLocation`, as shipped)

| Profile | Delivered? | Decoded on the phone |
|---|---|---|
| `ruuvi`, `ruuvi-100ms` | yes | 24.3 C, 53.49 %, 1000.44 hPa, 0.004/-0.004/1.036 G, 2977 mV, 4 dBm, counter 66, seq 205 |
| `altbeacon` | yes | UUID 2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6, major 1, minor 2 |
| `bthome` | yes | 97 %, 25.06 C, 50.55 %, 1008.83 hPa |
| `bthome-encrypted` | yes | flagged encrypted, no values |
| `ibeacon` | **no** | |
| `eddystone-uid`, `-url`, `-tlm` | **no** | |
| `ruuvi-2m` (extended advertising, 2M) | **no** | |

Control: same build with `neverForLocation` removed and `ACCESS_FINE_LOCATION` granted (manifest restored afterwards, nothing committed): iBeacon (UUID, major 1, minor 2), Eddystone UID (namespace, instance), URL (`https://example.com/`), TLM (3000 mV, 24.5 C) and `ruuvi-2m` (75 packets) were all delivered and decoded exactly. nRF Connect (which has location permission) also hears the host at about -35 dBm; its interval readout for `ruuvi-2m` (104 ms) matches the configured 100 ms.

So on Android 17, `neverForLocation` hides **iBeacon, Eddystone and extended advertising** from the app. It does not hide AltBeacon, manufacturer data in general (Ruuvi), service data in general (BTHome), Fast Pair or Apple frames (real neighbours show Apple and Fast Pair chips). Consequences if the flag stays: the iBeacon, Eddystone and distance features can only be seen through the fake scanner, and the Radio section can never show 2M or Coded PHY, a set id or a periodic interval for real devices.

## Decision D-P2-8 (decided 2026-11-14: option B, remove `neverForLocation`)

| Option | Effect |
|---|---|
| **A. Keep `neverForLocation`** | One permission ("Nearby devices"), simplest consent story. iBeacon, Eddystone and extended advertising invisible on real devices. |
| **B. Remove it** | Android 12+ also needs the Location permission (and Location services on) to deliver scan results; the manifest allowlist test and the policy doc change together; the permission screen explains both. Everything Tier 1 decodes becomes visible. The app still stores and sends nothing. |
| C. Build flavour with and without | Both worlds, more build and test surface than a POC needs. |

Recommended and chosen: B, because beacons and radio metadata are most of Tier 1's value and the app never persists or transmits scan data. Implemented the same day: manifest, permission checker (Location requested with Nearby devices), Location-off state on every Android version, wording, guardrail test, smoke script and docs.

## After the decision (2026-11-14)

With `neverForLocation` removed, every profile above, including iBeacon, Eddystone and the extended 2M advertisement, is received and decoded exactly on the Pixel. The full results table is in [tier1-advertisement-decoding.md](tier1-advertisement-decoding.md#t1-m5-results-pixel-9a-android-17-2026-11-14-shipped-build-without-neverforlocation).

## Bug found by the spike (fixed)

The device list is sorted by signal and keyed by address. A keyed lazy list keeps the row that was first in view when an item is inserted above it, so a **new strongest device appeared above the visible area and could not be seen without scrolling up**. The strongest device is usually the nearest one. Phase 1's runs missed it because neighbours were seen in roughly stable order. Fixed in `DeviceListScreen` (stay at the top when the list was at the top; a list the user scrolled down is left alone) with two regression tests, the first of which failed before the fix.

## Things learned about the setup and the harness

- BlueZ `Type: peripheral` advertises from the controller's **public address**, so the row is found by address; `broadcast` instances use a **random address per registration**. Default interval is about 1.28 s; the `interval_ms` property (MinInterval/MaxInterval) works.
- Row names are not exposed in the `uiautomator` dump (rows merge their text), only the row's address id and the chip node. Scripts must match addresses, and the list only contains rows on screen (the fix above matters here too).
- The phone locks itself after a few minutes; long probes need it awake.
- nRF Connect (installed on the phone) is a good independent ground truth when the app and the host disagree.
- Unexplained, seen twice: the app's list dropped to 0 or 2 devices while the platform's scan statistics showed results still arriving. Not reproduced after the fix; revisit during T1-M5.
- `PHY_LE_ALL_SUPPORTED` and `setNumOfMatches(MAX)` (nRF Connect's settings) were tried on the scanner; neither changed the outcome and neither was kept. Whether Coded PHY needs `PHY_LE_ALL_SUPPORTED` is untested.

## Effect on the plan

- T1-M5 can now be run: real-radio validation of Ruuvi, AltBeacon and BTHome is **done here**; iBeacon, Eddystone and the Radio section wait for decision D-P2-8.
- Tier 3 (GATT inspection) will use the same host controller as a GATT server (spike X1 for GATT not yet run).
