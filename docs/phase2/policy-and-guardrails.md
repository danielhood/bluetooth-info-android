# Policy and Guardrails for Phase 2

**Status:** Plan. Implement the "X2" items below **before** starting Tier 2 or Tier 3, so each later tier changes a rule deliberately and visibly rather than by drift. Phase 1's guardrails (architecture tests, manifest test, `AGENTS.md` prohibitions) are what kept the app within its stated scope; Phase 2 widens the scope, so those guardrails must widen in a controlled way, not be removed.

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
| Never request `BLUETOOTH_CONNECT` | unchanged | **relaxed:** allowed in the manifest and in `data/ble/android` | unchanged from Tier 2 |
| Never call `connectGatt` | unchanged | unchanged | **relaxed:** allowed in exactly one file, `data/ble/android/AndroidGattInspector.kt` |
| `android.bluetooth.*` only under `data/ble/android` | unchanged | unchanged | unchanged |
| No network calls (manifest has no INTERNET) | unchanged | unchanged | unchanged |
| BLE only (decision D7) | unchanged | **reopened only for 2b** (classic discovery), owner decision D-P2-2 | unchanged |
| No persistence (decision D12) | unchanged | unchanged | unchanged |
| Fakes live in `src/debug`, never in release | unchanged | unchanged | unchanged (fake GATT included) |

New rules introduced by Phase 2 and enforced by tests (see section 3): no characteristic writes, no pairing calls, no reflection on Bluetooth classes, no logging of identifiers or values in release code, decoders are pure.

## 3. Enforcement in code (the X2 work item)

All checks extend the existing `ArchitectureTest` (import and text scanning, no new dependency) and the manifest test. They are added **in the tier that needs them**, not before, except those marked now.

| Check | When added | Rule |
|---|---|---|
| Forbid characteristic writes | **Now** (X2) | No source text containing `writeCharacteristic` anywhere in `app/src` |
| Forbid pairing and bond changes | **Now** (X2) | No `createBond`, `removeBond`, `setPin`, `setPairingConfirmation`, `cancelBondProcess` |
| Forbid reflection into Bluetooth internals | **Now** (X2) | No `getDeclaredMethod`/`getMethod`/`Class.forName` in files under `data/ble` or that import `android.bluetooth` |
| Decoders and parsers are pure | Tier 1 | Files under `domain/decode` and `domain/gatt/parsers` import no `android.*` (already covered by the domain rule; add an explicit test so a future package move cannot escape it) |
| Manifest permissions are an explicit allowlist | **Now** (X2), updated per tier | The manifest test asserts the *exact set* of requested permissions instead of only forbidding some. Tier 1: unchanged set. Tier 2 adds `BLUETOOTH_CONNECT` (API 31+). A new permission fails the test until the policy doc and the test are updated together. |
| `connectGatt` confined | Tier 3 | Allowed only in `AndroidGattInspector.kt`; the Tier 2 "forbidden everywhere" check is narrowed in the same commit |
| `writeDescriptor` confined | Tier 3 v2 | Only in the file containing the notification helper, and only referencing the CCCD constant `0x2902` |
| Logging hygiene | Tier 2 | In `src/main` and `src/release`, `AppLog`/`Log` calls must not interpolate `address`, `name`, `serial`, `value` or `rawBytes` identifiers. Implemented as a text rule on log call arguments (simple, conservative, with an allowlisted escape comment for justified cases). Debug-only code (`src/debug`) is exempt. |
| No `INTERNET` / network classes | Existing | Keep; also forbid imports of `java.net.*`, `okhttp`, `retrofit` in main code |
| One GATT session at a time | Tier 3 | Unit test on the session coordinator |
| Consent required before contact | Tier 2c, Tier 3 | A ViewModel test asserts that the "start" intent without an accepted confirmation never calls the port (fake records zero calls) |

A new test, `PolicyDocConsistencyTest`, is optional but recommended: it reads the permissions list from this document's table (or a small machine-readable file next to it) and compares it with the manifest, so the doc and the code cannot diverge silently.

## 4. Consent and user-facing wording

Wording is part of the safety design and lives in one string resources file so it can be reviewed in one place. Draft copy (owner to approve before Tier 2c/3 ship):

**Permission explanation (Tier 2a)** "To show what your phone already knows about this device (its name, whether it is paired, and its type), the app needs the Nearby devices permission for connecting. Scanning works without it. The app never pairs with, connects to or changes any device in this step."

*(Adjust the last sentence when 2c/Tier 3 are enabled; it must never over-promise.)*

**Classic scan notice (Tier 2b, first use per run)** "Classic scanning looks for older Bluetooth devices for about 12 seconds. It can briefly disturb audio playing on your headphones."

**Connection confirmation (Tier 2c and Tier 3)** "Connect to *<name or address>* and read its standard information? Only do this for devices you own or have permission to inspect. The device may be briefly unavailable to its owner while connected. Some devices may ask to pair; you can decline. The app will not change anything on the device."

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
| P-1 | Approve the draft consent wording, or adjust? | Approve after seeing it in screenshots |
| P-2 | Is an "own devices" notice sufficient, or should connecting require an additional deliberate step (for example typing a confirmation word) for devices not previously bonded? | Dialog plus notice is sufficient for a personal POC; revisit before any wider distribution |
| P-3 | Should real third-party neighbourhood scans be allowed in committed documentation (only fake data in git)? | No: fake data only in git |
| P-4 | Distribution intent: personal use only, or could this be shared? | If shared, add a privacy review, store-policy review (permissions declarations), and remove or further restrict Tier 3 |
