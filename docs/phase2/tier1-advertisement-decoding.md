# Tier 1: Advertisement Decoding

**Status:** T1-M1 to T1-M5 done (2026-11-10 to 2026-11-14); T1-A1 (address type) was added on 2026-11-14 after the T1-M0 spike was answered; only Swift Pair, the OUI vendor lookup (T1-A2) and the optional list filter (T1-L2) remain. **Permissions:** decision D-P2-8 (2026-11-14) replaced `neverForLocation` with Location permission and Location services (see [x1-findings.md](x1-findings.md)). **Contacts the device:** never.

## 1. Goal

Turn the advertisement bytes the app already captures into meaningful information, and surface radio metadata the platform already provides, without any new permission or any connection. Everything is passive and everything is derived from data the app already receives.

Success looks like: scanning near a Ruuvi tag shows its temperature and humidity; near an iBeacon shows UUID/major/minor; near an Eddystone beacon shows its namespace/URL/telemetry; near an Apple device shows an honest "Apple Continuity: Nearby" hint; and every device shows PHY, advertising interval and a rough distance with clear "estimate" wording.

## 2. Scope

### 2.1 Decoders (the core)

| ID | Pri | Decoder | Source in the advertisement | Output |
|---|---|---|---|---|
| T1-D1 | M | **iBeacon** | Manufacturer data, company `0x004C`, payload `02 15` + 16-byte UUID + major (2) + minor (2) + measured power at 1 m (signed byte) | UUID, major, minor, measured power |
| T1-D2 | M | **Eddystone** | Service data, 16-bit UUID `0xFEAA`; frame type in the first byte | UID (namespace 10 B + instance 6 B, calibrated TX power at 0 m), URL (scheme and expansion codes decoded to a URL string), TLM (battery mV, temperature as 8.8 fixed point, packet count, uptime in 0.1 s units), EID (opaque 8-byte value, shown as "ephemeral ID") |
| T1-D3 | S | **AltBeacon** | Manufacturer data containing beacon code `0xBEAC` | 20-byte ID (UUID + major + minor), reference RSSI |
| T1-D4 | M | **Ruuvi RAWv2 (data format 5)** | Manufacturer data, company `0x0499` | Temperature, humidity, pressure, acceleration x/y/z, battery voltage, TX power, movement counter, sequence number |
| T1-D5 | S | **BTHome v2** | Service data, 16-bit UUID `0xFCD2` | Object list (battery %, temperature, humidity, pressure, illuminance, and the other documented numeric objects); **encrypted** frames are shown as "encrypted, not decodable" |
| T1-D6 | S | **Google Fast Pair** | Service data, 16-bit UUID `0xFE2C` | 3-byte model ID in discoverable mode; otherwise "account-key filter present" |
| T1-D7 | S | **Microsoft Swift Pair** | Manufacturer data, company `0x0006`, beacon id `0x03` | "Swift Pair advertisement" and scenario type |
| T1-D8 | C | **Apple Continuity (type names only)** | Manufacturer data, company `0x004C`, first byte is the message type | Type label (Nearby Info, Nearby Action, Proximity Pairing, Handoff, AirPlay, Find My network, and so on). **No payload decoding.** |
| T1-D9 | C | **Tracker hint** | Combination of Apple Find My type, Fast Pair/tracker service UUIDs (for example the Tile service), Google/Samsung tracker frames | A single cautious hint row: "Looks like a location tracker (unverified)" |

Format authority and wording rules:

- Documented, open formats (iBeacon, Eddystone, AltBeacon, Ruuvi, BTHome, Fast Pair model ID) are decoded in full and tested against vectors taken from their published specifications.
- Formats known only through community reverse engineering (Apple Continuity internals, tracker behaviour) are **hints**: show the type label only, mark it "unofficial", never state a model or owner.
- Anything encrypted or rotating is shown as such. The app must never imply it can identify or follow a person.

### 2.2 Radio metadata

`ScanResult` carries more than the app keeps. Extend `RawAdvertisement` with a `RadioInfo` and show it on the detail screen:

| ID | Pri | Field | Android source (API 26+) | Notes |
|---|---|---|---|---|
| T1-R1 | M | Primary PHY, secondary PHY | `getPrimaryPhy()`, `getSecondaryPhy()` | LE 1M, LE Coded, LE 2M, or none |
| T1-R2 | M | Legacy or extended advertising | `isLegacy()` | |
| T1-R3 | S | Advertising set id | `getAdvertisingSid()` | 255 means not present |
| T1-R4 | S | Platform-reported TX power | `getTxPower()` | 127 means not available |
| T1-R5 | S | Periodic advertising interval | `getPeriodicAdvertisingInterval()` | 0 means none; units of 1.25 ms |
| T1-R6 | S | Data status | `getDataStatus()` | complete or truncated |
| T1-R7 | M | **Average advertising interval** | derived from `firstSeen`, `lastSeen`, `advertisementCount` | Shown as "about N ms between packets (estimate)"; only when at least 5 packets and at least 2 s observed |

### 2.3 Address kind and vendor lookup

| ID | Pri | Item | Plan |
|---|---|---|---|
| T1-A1 | S | **Address type** (public, random, anonymous) | Spike T1-S1: check whether the platform exposes address type without `BLUETOOTH_CONNECT` (an API 35+ call exists; its permission requirement must be verified). If it needs CONNECT, defer to Tier 2a. **Do not** guess from the address bits: a public address can have any top bits, so the heuristic is wrong often enough to mislead. |
| T1-A2 | C | **MAC vendor (OUI)** | Only meaningful for public addresses. Bundle a compact table generated from the public IEEE OUI registry by a script (`scripts/gen-oui.py`), show the vendor only when the address kind is known to be public. Size and licence note recorded in the decision log. |

### 2.4 Derived estimates

| ID | Pri | Item | Rule |
|---|---|---|---|
| T1-E1 | S | **Rough distance** | Only when a calibrated reference power exists (iBeacon measured power at 1 m; Eddystone TX power at 0 m minus 41 dB for 1 m; AltBeacon reference RSSI). Path-loss exponent 2.0. Display bands, not decimals: "about 1 m", "a few metres", "far" with the explicit label "rough estimate". Never shown for devices without calibration data. |

### 2.5 List screen

| ID | Pri | Requirement |
|---|---|---|
| T1-L1 | S | A small **kind chip** per row when a decoder matched (iBeacon, Eddystone, Ruuvi, BTHome, Apple hint, and so on). |
| T1-L2 | C | A filter control "Show only: all / beacons / sensors / hints". Keeps the current "Hide unnamed". |

### 2.6 Detail screen

A new generic **"Decoded data"** section per matched decoder, listing labelled values with units; a visible confidence line ("Open specification" or "Unofficial hint"). Radio metadata and estimates go in a "Radio" section. Existing sections are unchanged.

## 3. Design

### 3.1 Domain

```
domain/decode/
  PayloadDecoder.kt        interface PayloadDecoder { val id: DecoderId; fun decode(ad: Advertisement): Decoded? }
  Decoded.kt               data class Decoded(decoder: DecoderId, confidence: Confidence, fields: List<DecodedField>)
  DecodedField.kt          data class DecodedField(key: FieldKey, value: FieldValue)
  FieldValue.kt            sealed: Text, Number(value: Double, unit: Unit), Hex(Bytes), Bool, Duration(ms)
  Confidence.kt            enum { SPECIFICATION, UNOFFICIAL_HINT, ENCRYPTED }
  Decoders.kt              val all: List<PayloadDecoder>  (order and de-duplication)
  decoders/IBeaconDecoder.kt, EddystoneDecoder.kt, AltBeaconDecoder.kt, RuuviV5Decoder.kt,
           BtHomeDecoder.kt, FastPairDecoder.kt, SwiftPairDecoder.kt, AppleContinuityDecoder.kt, TrackerHint.kt
domain/model/RadioInfo.kt data class RadioInfo(primaryPhy, secondaryPhy, isLegacy, sid, txPower, periodicIntervalUnits, dataStatus)
```

Rules:

- **Pure Kotlin, no `android.*`** (enforced by the existing architecture test).
- A decoder never throws on malformed data: returns `null` (not matched) or a partial result with fewer fields. Golden vectors include truncated and corrupt frames.
- Decoders emit **keys, not strings**. The UI maps `FieldKey` to string resources and formats units. This keeps the domain locale-free and makes adding a decoder a closed recipe: one decoder file, one test file, enum entries, string resources, a fake scenario.
- `Advertisement` gains nothing new; decoders read `manufacturerData`, `serviceData`, `serviceUuids`.
- `BleDevice` gains `decoded: List<Decoded>` and `radio: RadioInfo?`. **Merge rule:** decoded results are keyed by decoder id plus Eddystone frame kind; the latest value wins, so alternating Eddystone UID/URL/TLM frames accumulate rather than overwrite each other.
- `DefaultDeviceRepository.ingest` runs decoders after parsing; cost is bounded (a handful of constant-time checks per advertisement). Verify with the 200-device scenario (target: no measurable change in the perf script).

### 3.2 Data layer

`AndroidBleScanner.toRaw()` fills `RadioInfo` from the `ScanResult` getters. Values the platform reports as "unavailable" (127, 255, 0) are mapped to `null` in the adapter, so the domain never sees sentinels. `RawAdvertisement` gains `radio: RadioInfo?` (null for fakes that do not specify it).

### 3.3 Fake scanner scenarios (debug source set)

Add scenarios, each deterministic and each producing exact bytes from the published specifications so they double as documentation:

| Scenario | Devices |
|---|---|
| `beacons` | iBeacon with known UUID/major/minor/power; Eddystone-UID, -URL and -TLM from one device alternating; AltBeacon; an Eddystone-EID |
| `sensors` | Ruuvi format 5 with the documentation's sample reading; BTHome plain with several objects; BTHome encrypted |
| `vendors` | Apple frames of several types; Fast Pair model ID; Swift Pair; a tracker-like combination; a device with no recognisable data |
| `radio` | Extended advertising with 2M PHY, coded PHY, periodic advertising, advertising set id; legacy for contrast |
| `malformed-decode` | Truncated and corrupt versions of each format |

`FakeDevice` gains an optional `radio` and a per-device packet interval (already supported) so the interval estimate is testable.

### 3.4 UI

- `DeviceRowUi` gets `kindLabel: DecoderId?`. `DeviceDetailUi` gets `decodedSections: List<DecodedSectionUi>` and `radio: RadioUi?`. Mapping lives in the ViewModel layer; formatting in composables via `FieldKey` string resources.
- New test tags: `kind_chip_<address>`, `detail_decoded_<decoderId>`, `detail_radio`, and a field-level tag convention `decoded_<decoderId>_<fieldKey>` for scripts.
- Wording is owned by one string resource file so confidence labels stay consistent.

## 4. Testing and verification

| Layer | What |
|---|---|
| JVM unit | **Golden vectors per decoder**, taken from the published spec examples (iBeacon, Eddystone UID/URL/TLM, Ruuvi's documented RAWv2 sample reading, BTHome documentation examples, AltBeacon spec). Every vector is cited in a comment with its source. Plus truncated, over-long, wrong-length and random-fuzz inputs asserting "never throws". Property test: for any input, `decode` returns either null or fields within documented ranges. |
| JVM unit | Merge rule (alternating Eddystone frames accumulate); interval estimate rules; distance bands and "no calibration, no estimate"; sentinel mapping for radio info. |
| Screenshot | Detail screen per scenario (beacons, sensors, vendors, radio) in light and dark; list rows with kind chips. |
| Whole-app JVM | Scan `beacons` and open the iBeacon device: decoded values present. |
| Emulator smoke | New scenarios launched by name; assert a few field tags via `ui.sh find`. |
| Performance | `perf.sh` before and after; no regression beyond noise with 200 devices. |
| Real radio | With X1 option A (host BlueZ advertising known beacons and a Ruuvi-format payload) the Pixel shows exactly the expected values. Also record what real neighbours produce (Apple frames are common) and confirm nothing mis-decodes or crashes. Script: `scripts/e2e-real.sh` gains a `--expect <profile>` mode. |

Tooling additions: `scripts/gen-oui.py` (only if T1-A2 proceeds); a vectors directory `app/src/test/resources/vectors/` with one small file per format (hex plus source URL) so humans can audit them.

## 5. Milestones and exit criteria

| ID | Work | Exit |
|---|---|---|
| T1-M0 | Spike T1-S1 (address type API and its permission); decide OUI scope; confirm vector sources | Decision recorded here |
| T1-M1 | `RadioInfo`, decoder framework, iBeacon and Eddystone decoders, `beacons` scenario, detail "Decoded data" section | AC T1-1, T1-2 green; `verify.sh --full` passes. **Done.** Notes below. |
| T1-M2 | Ruuvi, BTHome (and AltBeacon), `sensors` scenario | AC T1-3. **Done.** Notes below. |
| T1-M3 | Fast Pair, Swift Pair, Apple type labels, tracker hint, `vendors` scenario, wording review | AC T1-4. **Done except Swift Pair (deferred).** Notes below. |
| T1-M4 | Radio section, interval and distance estimates, list kind chips, `radio` scenario | AC T1-5, T1-6. **Done.** Notes below. |
| T1-M5 | Real-device validation with X1 profiles and with real neighbours; roadmap and AGENTS.md updates | AC T1-7. **Done.** Results below. |

### T1-M1 implementation notes

- Eddystone frame kinds are separate `DecoderId`s (`EDDYSTONE_UID`, `_URL`, `_TLM`, `_EID`), so the merge rule is simply "latest per decoder id" and alternating frames accumulate. `FakeDevice.alternatePayloads` rotates payloads per advertising slot.
- `RadioInfo` is captured and sentinel-mapped (`radioInfoOf`, unit-tested) and stored on `BleDevice.radio`, but not shown yet (the Radio section is T1-M4).
- Test vectors are constructed from the published layouts, not copied from the specs' worked examples (the iBeacon layout is documented by third parties, Apple's text is licensed); the Eddystone URL `http://www.google.com` vector follows the specification's encoding table. A second reading by someone independent is still recommended (risk table below).
- Test peripherals (X1) and spike T1-S1 (address type API permission) are untouched; beacon filtering by `neverForLocation` on real hardware is still unverified until T1-M5.

### T1-M2 implementation notes

- **Vector provenance.** Ruuvi's valid-data vector and decoded values are the official example from docs.ruuvi.com (data format 5); its extreme and invalid vectors are derived from the same field table, not copied. BTHome vectors are the examples printed on bthome.io/format (full example and per-object table). AltBeacon follows github.com/AltBeacon/spec. Details of each source are in the test file comments. A correction made along the way: `0xFFDE` is the largest valid Ruuvi power word (3646 mV, 20 dBm), not an invalid one.
- **BTHome scope.** 13 object ids with verified layouts (packet id, battery, temperature x2 scales, humidity x2, pressure, illuminance, voltage, PM2.5, PM10, CO2, TVOC). Parsing stops at the first unknown or truncated object and keeps what was read, because an unknown object's length cannot be known. Encrypted frames are reported as encrypted with no values. Other documented objects (power, energy, dew point, rotation, binary sensors, events) are not decoded yet.
- **Ruuvi** invalid markers are omitted from the output, never shown as raw sentinels. Pressure and acceleration are converted to hPa and G.
- AltBeacon is in the `beacons` scenario; the 20-byte id is displayed as UUID plus two 16-bit values (convention; the spec only defines a 20-byte id).

### T1-M3 implementation notes

- **Verified before coding.** Google's Fast Pair provider spec (UUID 0xFE2C, discoverable = 3-byte model ID, otherwise account data) and, from the furiousMAC/continuity notes, Apple type bytes 0x05 AirDrop, 0x07 Proximity Pairing and 0x12 Find My were confirmed. Samsung SmartTag UUIDs 0xFD5A (registered) and 0xFD59 (non-registered) come from arXiv 2210.14702, Tile's 0xFEED from arXiv 2510.00350.
- **Swift Pair is deferred.** Microsoft's pages do not state the Microsoft Beacon ID value (0x03 was my assumption) and the payload layouts are only in figures; the page confirms vendor id 0x0006, sub-scenarios 0x00/0x01/0x02 and the reserved RSSI byte 0x80. It can be added once the layout is confirmed against a primary source or a real capture; until then Microsoft data shows only in the existing manufacturer-data list.
- **Not claimed.** Other Apple types (0x03, 0x06, 0x08 to 0x0F ...) were not verified, so they show as "Other message (not named by this app)" with the code. Tile 0xFD84 was not found in sources and is not used.
- **Wording rules, enforced by tests.** Hints use a closed set of labels (`LabelKey`), never free text; `DecodersTest` asserts (in the fuzz loop) that no unofficial hint carries a `Text` value; Apple payload bytes are never read or shown; Fast Pair's model ID is shown as a number and never mapped to a product; the tracker card is titled "Looks like a location tracker (unverified)" and its family label says "resembles", with phrases such as "Tile-style identifier".

### T1-M4 implementation notes

- **Estimates live in the domain** (`domain/estimate/Estimates.kt`, pure, unit-tested): the packet interval is observed time divided by gaps and appears only with at least 5 packets and 2 s observed; the distance band uses free-space path loss (exponent 2) and appears only for devices with a calibrated reference (iBeacon measured power and AltBeacon reference RSSI as 1 m values, Eddystone 0 m power minus 41 dB). Zero, positive or implausible reference values count as uncalibrated. Bands are VERY_CLOSE (under 0.5 m), ABOUT_ONE_METRE (under 2 m), A_FEW_METRES (under 10 m), FAR; never decimals.
- **Wording:** both estimates carry "(estimate)" or "rough estimate" in their labels plus a one-line caveat (missed packets lengthen the interval; walls and bodies change distance a lot).
- **Kind chips:** one chip per row from `List<Decoded>.primaryKind()` with an explicit priority list (a test asserts it covers every decoder). The tracker chip reads "Tracker? (unverified)". T1-L2 (the filter control) is not done; it was a "could".
- **Radio section** shows only what the platform reported; unavailable values are null in the model and not rendered. Android fills radio data from the `ScanResult` getters (unit-tested sentinel mapping); real-hardware values are only seen at T1-M5.
- **Performance (T1-8, emulator):** `perf.sh` with 200 fake devices after T1-M4: 638 frames, 6.74% janky, median 21 ms (Phase 1 baseline 6.1% and 24 ms; software rendering, within noise). The `many` scenario carries no decodable data, so this mostly measures the decoders' short-circuit cost; a Pixel run with real neighbours belongs to T1-M5.
- **Smoke lesson:** rows re-sort as RSSI drifts, so tapping a row by id can open a neighbour. The smoke script now opens devices through `open_device`, which confirms the address and retries; the `radio` scenario also spaces signal levels 12 dB apart.
- The T1-M0 spike (address type API) was answered on 2026-11-14, see the T1-A1 notes below.

### T1-M5 results (Pixel 9a, Android 17, 2026-11-14, shipped build without `neverForLocation`)

Host controller as test peripheral (`scripts/peripheral.sh probe <profile>`), every profile found by the host's address within 7 to 8 s of starting, decoded exactly as specified:

| Profile | What the phone showed |
|---|---|
| `ibeacon` | UUID E2C56DB5-DFFB-48D2-B060-D0F5A71096E0, major 1, minor 2, -59 dBm |
| `eddystone-uid` / `-url` / `-tlm` | namespace and instance; `https://example.com/`; 3000 mV, 24.5 C, 1000 packets, 3600 s |
| `altbeacon` | UUID 2F234454-..., major 1, minor 2, -59 dBm |
| `ruuvi`, `ruuvi-100ms` | the documentation example: 24.3 C, 53.49 %, 1000.44 hPa, 0.004/-0.004/1.036 G, 2977 mV, 4 dBm |
| `bthome` | packet 9, 97 %, 25.06 C, 50.55 %, 1008.83 hPa |
| `bthome-encrypted` | "Encrypted or rotating: not decodable", no values |
| `ruuvi-2m` (extended) | Radio section: primary LE 1M, secondary LE 2M, Extended, set id 0, no TX power or periodic interval (unavailable values omitted) |

- **Interval estimate (T1-R7):** the 100 ms profile reads "about 122 ms" and the extended 100 ms profile "about 120 ms"; default BlueZ advertising (about 1.28 s) reads 1.2 to 1.9 s. As labelled, missed packets make the estimate high.
- **Distance (T1-E1):** calibrated beacons (iBeacon, Eddystone, AltBeacon) next to the phone at -35 dBm read "Very close, under about half a metre"; devices without calibration (Ruuvi, BTHome) show none.
- **Real neighbours** (5 devices in range, 45 s scan): one Apple frame labelled "Other message (not named by this app)" with its code (an unverified type), one Fast Pair frame in not-discoverable mode, the rest plain. No crash, no model or owner claim.
- **Performance on the phone** (`perf.sh`, 200 fake devices, three runs): 3.75 to 6.24 % janky, median 8 ms, 95th percentile 26 to 32 ms; the Phase 1 baseline (3.9 %, 7 ms, 27 ms) is inside the run-to-run spread.
- **Acceptance:** T1-1 to T1-7 met on real hardware. T1-8 changed on purpose: the permission set is no longer "no new permission" because of decision D-P2-8 (Location added); the guardrail test records the new exact set.
- **Not verified on real radio:** the Eddystone frame accumulation (UID, URL and TLM alternating on one device) and the Swift Pair format; the host sends one frame kind per profile.

### T1-A1 and spike T1-S1: address type (2026-11-14, Pixel 9a, Android 17)

- **Result:** `BluetoothDevice.getAddressType()` (Android 15+) needs no `BLUETOOTH_CONNECT`: with the permission revoked it returned a value while `getName`, `getAlias`, `getBondState`, `getType`, `getBluetoothClass` and `getUuids` all threw `SecurityException`. Only Android 17 was tested; the code reads it on Android 15+ and the scan path would need the same check on 15 and 16.
- **Trap:** the value is only meaningful on the device object that came **with a scan result**. For one built from an address alone (`getRemoteDevice`) Android answers "public" whatever the truth: every neighbour showed "Public" that way, while the scan result's own device reported `RANDOM` for the neighbours and `PUBLIC` for the host, matching the address bits. The first Tier 2a version showed the wrong value; it was removed from System information and read from the scan result instead (`RawAdvertisement.addressType`, `BleDevice.addressType`, `data/ble/android/AddressTypeMapping.kt`).
- **UI:** the detail header shows "Address type" (Public, Random, Anonymous, Unknown) when the platform reports one; older Androids show nothing, and the existing "may be a randomised private address" note stays. Verified on the phone: host Public, real neighbours Random.
- Guessing from the address bits was rejected in the plan and remains rejected.

## 6. Acceptance criteria

1. **T1-1:** scenario `beacons` shows the iBeacon's UUID, major, minor and measured power exactly as specified, in the list chip and detail section.
2. **T1-2:** an Eddystone device alternating UID, URL and TLM frames shows all three kinds simultaneously on the detail screen; the URL expansion codes decode correctly; battery and temperature from TLM match the vector.
3. **T1-3:** scenario `sensors` shows the Ruuvi documentation sample reading exactly; BTHome plain values decode; encrypted BTHome shows "encrypted" and no values.
4. **T1-4:** vendor hints carry "Unofficial hint" wording; no model or owner is ever claimed; a device with no recognisable data shows no decoded section and no error.
5. **T1-5:** the radio section shows PHY, legacy/extended and, for scenarios that provide it, SID and periodic interval; unavailable values are omitted, not shown as 127 or 255.
6. **T1-6:** interval and distance appear only under the stated conditions and are labelled as estimates.
7. **T1-7:** on the Pixel, a BlueZ-hosted beacon and a Ruuvi-format payload decode to the configured values; scanning ordinary neighbourhood devices produces no crash and no obviously wrong claims (reviewed by the owner).
8. **T1-8:** no new permission in the manifest (checked by the manifest test) and the 200-device performance check does not regress.

## 7. Risks and open questions

| Risk or question | Plan |
|---|---|
| Spec vectors mis-transcribed | Each vector cites its source; a second reading by an independent check (a human, or a separate agent session) before merging |
| Decoder claims overreach | Confidence enum, wording file, review gate in T1-M3 |
| Cost per advertisement | Perf check; short-circuit on company id/service UUID before decoding |
| iBeacon/Eddystone are filtered by `neverForLocation` on Android 12+ | **Confirmed on the Pixel (X1) and resolved: the flag was removed (D-P2-8).** |
| Address type availability | Spike T1-S1 |

Out of scope for Tier 1: any decoding that needs a key, any identification of individuals, scanning in the background, sending data anywhere.
