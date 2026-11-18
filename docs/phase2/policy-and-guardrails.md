# Policy and Guardrails for Phase 2

**Status:** X2 implemented 2026-11-09 (checks marked Done below, in `ArchitectureTest`, each proven to fail on a deliberate violation); the rest is Plan. Implement the "X2" items below **before** starting Tier 2 or Tier 3, so each later tier changes a rule deliberately and visibly rather than by drift. Phase 1's guardrails (architecture tests, manifest test, `AGENTS.md` prohibitions) are what kept the app within its stated scope; Phase 2 widens the scope, so those guardrails must widen in a controlled way, not be removed.

## 1. Principles

1. **Passive first.** Prefer information obtained without touching the other device. Each step toward contact (classic inquiry, SDP query, GATT connection) needs a stronger reason and a stronger consent.
2. **The user directs, one device at a time.** The app never contacts a device on its own. Every contact is the result of an explicit action on a specific device, confirmed in a dialog that names it.
3. **Read-only.** The app does not change the state of any device it inspects. No characteristic writes. The single narrow exception is writing the standard notification-enable descriptor (Tier 3 v2).
4. **No pairing, ever.** The app never creates, requests, confirms, removes or influences a bond. It may *display* an existing bond state.
5. **No persistence, no network.** Nothing about nearby devices is stored across runs or sent anywhere. The only data export is a user-initiated clipboard copy of the screen the user is looking at.
6. **Honest labelling.** Decoded or inferred information carries its confidence: specification, unofficial hint, encrypted, estimate, cached. The app never claims to identify a person, an owner, or a specific tracker.
7. **Own devices.** The UI states that connecting to a device is for devices the user owns or has permission to inspect.
8. **Least permission, requested late.** `BLUETOOTH_CONNECT` is requested only when the user opens a feature that needs it; scanning keeps working without it.

## 2. What changes from Phase 1 rules

| Phase 1 rule (AGENTS.md / tests) | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| `BLUETOOTH_SCAN` asserts `neverForLocation` (Phase 1) | **relaxed (D-P2-8):** not asserted; Location permission and Location services are required to scan | unchanged | unchanged |
| Never request `BLUETOOTH_CONNECT` | unchanged | **relaxed (done, 2026-11-14):** allowed in the manifest and in `data/ble/android` only (`ArchitectureTest`: no other layer names it; the UI gets permission names from the `PermissionChecker` port) | unchanged from Tier 2 |
| Never call `connectGatt` | unchanged | unchanged | **relaxed:** allowed in exactly one file, `data/ble/android/AndroidGattInspector.kt` |
| `android.bluetooth.*` only under `data/ble/android` | unchanged | unchanged | unchanged |
| No network calls (manifest has no INTERNET) | unchanged | unchanged | unchanged |
| BLE only (decision D7) | unchanged | **reopened for 2b (done, 2026-11-14)**: classic discovery approved by the owner (D-P2-2) on the condition that it is gated by the UI (one explicit button plus a first-use notice); see decisions.md D7 | unchanged |
| No persistence (decision D12) | unchanged | unchanged | unchanged |
| Fakes live in `src/debug`, never in release | unchanged | unchanged | unchanged (fake GATT included) |

New rules introduced by Phase 2 and enforced by tests (see section 3): no characteristic writes, no pairing calls, no reflection on Bluetooth classes, no logging of identifiers or values in release code, decoders are pure.

## 3. Enforcement in code (the X2 work item)

All checks extend the existing `ArchitectureTest` (import and text scanning, no new dependency) and the manifest test. They are added **in the tier that needs them**, not before, except those marked now.

| Check | When added | Rule |
|---|---|---|
| Forbid characteristic writes | **Done (X2)** | No source text containing `writeCharacteristic` anywhere in `app/src` |
| Forbid pairing and bond changes | **Done (X2)** | No `createBond`, `removeBond`, `setPin`, `setPairingConfirmation`, `cancelBondProcess` |
| Forbid reflection into Bluetooth internals | **Done (X2)** | No `getDeclaredMethod`/`getMethod`/`Class.forName` in files under `data/ble` or that import `android.bluetooth` |
| Decoders and parsers are pure | Tier 1 | Files under `domain/decode` and `domain/gatt/parsers` import no `android.*` (already covered by the domain rule; add an explicit test so a future package move cannot escape it) |
| Manifest permissions are an explicit allowlist | **Done (X2)**, updated per tier | The manifest test asserts the *exact set* of requested permissions instead of only forbidding some. Tier 1 (decision D-P2-8, 2026-11-14): adds `ACCESS_COARSE_LOCATION` and makes `ACCESS_FINE_LOCATION` unrestricted (the scan needs both on every version, `neverForLocation` is no longer asserted; the test fails if it comes back). Tier 2a (done) adds `BLUETOOTH_CONNECT` (API 31+). A new permission fails the test until the policy doc and the test are updated together. |
| Over-the-air contact calls are confined | **Done (Tier 2a, relaxed for 2b and 2c)** | `connectGatt` is forbidden everywhere (relaxed in Tier 3). `startDiscovery` and `cancelDiscovery` are allowed only in `data/ble/android/AndroidClassicScanner.kt` (2b) and `startClassicDiscovery(` may be called only from `DeviceListViewModel`. `fetchUuidsWithSdp` is allowed only in `data/ble/android/AndroidProfileLookup.kt` (2c) and `fetchProfiles(` may be called only from `DeviceDetailViewModel` (plus the port, its implementations and the container's delegate). All enforced by `ArchitectureTest`, each shown to fail on a violation |
| `connectGatt` confined | Tier 3 | Allowed only in `AndroidGattInspector.kt`; the Tier 2 "forbidden everywhere" check is narrowed in the same commit |
| `writeDescriptor` confined | Tier 3 v2 | Only in the file containing the notification helper, and only referencing the CCCD constant `0x2902` |
| Logging hygiene | **Done (Tier 2a)** | In `src/main` and `src/release`, `AppLog`/`Log` calls must not interpolate `address`, `name`, `serial`, `value` or `rawBytes` identifiers. Implemented as a text rule on log call arguments (simple, conservative, with an allowlisted escape comment for justified cases). Debug-only code (`src/debug`) is exempt. |
| No `INTERNET` / network classes | Existing; import rule **done (X2)**, `android.net.Uri` is the only allowed `android.net` class | Keep; also forbid imports of `java.net.*`, `okhttp`, `retrofit` in main code |
| One GATT session at a time | Tier 3 | Unit test on the session coordinator |
| Consent required before contact | Tier 2c, Tier 3 | A ViewModel test asserts that the "start" intent without an accepted confirmation never calls the port (fake records zero calls) |

A new test, `PolicyDocConsistencyTest`, is optional but recommended: it reads the permissions list from this document's table (or a small machine-readable file next to it) and compares it with the manifest, so the doc and the code cannot diverge silently.

## 4. Consent and user-facing wording

**Approved 2026-11-14 (owner), as it stands in `app/src/main/res/values/strings.xml` at the Tier 2a commit:** the scan permission and Location-off screens, the System information section (permission, denied, loading, nothing-known, read-only footer, pairing and cached-services notes), the three confidence labels, the hint and tracker wording (kept as written, including the "Tracker? (unverified)" list chip), and the interval and distance notes. **Revisit when Tier 3 arrives:** "BT Scan never pairs with, connects to or changes any device" and "BT Scan did not contact the device" stop being true once the app connects, and Android may start pairing by itself when a read needs encryption; both must be rewritten in the same change, not softened beforehand.

Wording is part of the safety design and lives in one string resources file so it can be reviewed in one place. Draft copy (owner to approve before Tier 2c/3 ship):

**Scan permissions (Tier 1, in the app now)** "BT Scan needs the Nearby devices and Location permissions to look for Bluetooth devices around you. Choose precise location when asked: Android counts Bluetooth scan results as location data. BT Scan never uses your location and stores and sends nothing." The wording must stay true: the no-persistence and no-network checks are what back the last sentence.

**Permission explanation (Tier 2a, in the app now)** "To show what your phone already knows about this device (its name, whether it is paired, and its type), BT Scan needs your permission to read it. Scanning works without it. BT Scan never pairs with, connects to or changes any device." On Android 12+ the connect permission shares the Nearby devices group already allowed for scanning, so Android grants it silently when asked (observed, spike T2-S2); the in-app button is therefore the consent step.

*(Adjust the last sentence when 2c/Tier 3 are enabled; it must never over-promise.)*

**Classic scan notice (Tier 2b, in the app now; first use per process; approved 2026-11-17 by the owner)** "Classic scanning looks for older Bluetooth devices for about 12 seconds. It can briefly disturb audio playing on your headphones." Buttons: Scan, Cancel. Also in the app: "The BLE scan was stopped for the classic scan: the two cannot run together." and the reverse.

**Profile lookup confirmation (Tier 2c, in the app now; approved 2026-11-17 by the owner, after the wording was revised from "ask" to "query")** "Query this device for its profiles? BT Scan will briefly contact <name or address> over classic Bluetooth and query it for the profiles it supports. Only do this for devices you own or have permission to inspect. The device may be briefly busy while it answers, and some devices may ask to pair; you can decline. BT Scan will not change anything on the device." Buttons: Query the device, Cancel. Shown every time, never remembered. The "may ask to pair" sentence is observed behaviour, not boilerplate: Android attempted to pair with the test host during a lookup.

**Wording changed since the 2026-11-14 approval (re-approved 2026-11-17 by the owner):** the System information permission text now ends "BT Scan never pairs with or changes any device, and contacts a device only when you choose to query it" (it said "never pairs with, connects to or changes"), and the footer now reads "BT Scan did not contact the device to get them". Both had to change because a profile lookup, once the user confirms it, does contact the device. The same approval covers the lookup button, progress, result, timeout and "could not query" texts as they are in `strings.xml` (all use "query", not "ask").

**Connection confirmation (Tier 3)** "Connect to *<name or address>* and read its standard information? Only do this for devices you own or have permission to inspect. The device may be briefly unavailable to its owner while connected. Some devices may ask to pair; you can decline. The app will not change anything on the device."

**Result screen footer** "Read-only. Nothing is saved or sent anywhere."

## 5. Data handling

- **In memory only.** Results live in ViewModel state for the screen's lifetime. No database, preferences, files, caches, or analytics.
- **No transmission.** No network permission; the manifest test and the import rule keep it that way.
- **Clipboard copy is the only export,** triggered by the user, of exactly what is on screen. Android 13+ shows its own clipboard notice.
- **Logs.** Release builds log lifecycle and counts only ("scan started", "inspection finished: 3 of 9 reads"), never addresses, names, serials or values. Debug builds may log more; the smoke and real-device scripts' screenshots stay in the git-ignored `build/agent/` directory and are handled as private (they can show neighbours' devices).
- **Screenshots in git.** Reference screenshots in `docs/screens/` come only from fake data. A check in the review step confirms no real device names or addresses are committed.
- **Test peripherals.** Real-radio tests use the owner's own test peripherals or devices (X1), never third-party devices as targets of a connection.

## 6. Agent working rules for Phase 2 (to add to AGENTS.md as each tier starts)

1. Read this policy document before any change that touches contact with a device or permissions.
2. Never loosen an architecture or manifest test to make a change pass; the relaxation must be a separate, documented commit that updates this document first.
3. A tier is not finished until its guardrail tests exist and a deliberate violation was shown to fail them (as done for the Phase 1 architecture test).
4. Real-radio runs against third-party devices are limited to passive scanning. Connecting is allowed only to the owner's devices and the X1 test peripherals.
5. Any feature that would write, pair, persist, or transmit is out of scope and needs an owner decision recorded in this folder first.

## 7. Open questions for the owner

| ID | Question | Recommended |
|---|---|---|
| P-1 | Approve the draft consent wording, or adjust? | **Partly decided (2026-11-14):** the wording shipped through Tier 2a (scan and Location permission screens, System information section, confidence labels, hint and tracker wording, estimate notes) was approved as written, including the "Tracker? (unverified)" list chip. **Also approved 2026-11-17:** the profile lookup confirmation and result texts and the two re-worded System information sentences (see above). The classic scan notice (Tier 2b) was approved the same day. The connection consent wording for Tier 3 is still a draft. |
| P-2 | Is an "own devices" notice sufficient, or should connecting require an additional deliberate step (for example typing a confirmation word) for devices not previously bonded? | Dialog plus notice is sufficient for a personal POC; revisit before any wider distribution |
| P-3 | Should real third-party neighbourhood scans be allowed in committed documentation (only fake data in git)? | No: fake data only in git |
| P-4 | Distribution intent: personal use only, or could this be shared? | If shared, add a privacy review, store-policy review (permissions declarations), and remove or further restrict Tier 3 |
