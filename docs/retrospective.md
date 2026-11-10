# Retrospective

Written at the end of milestone M6 (2026-11-09). It covers what an AI agent could do unaided on this POC, where a human had to step in, what the tooling was worth, and what remains unproven. It is based on the commit history, the roadmap notes and the session itself. Things that were not measured (wall-clock time, token cost) are marked as such.

## Outcome

A working Android app: it scans for nearby BLE devices, lists them live, and opens a detail screen, and it never connects to anything. It runs on an emulator (with a scripted fake scanner) and on a Pixel 9a with the real radio (8 real devices listed in the real-device run). Delivered in 11 commits across milestones M0 to M6 (this one included).

| | |
|---|---|
| Production Kotlin (main, debug, release) | about 2,700 lines |
| Test Kotlin (JVM, Managed Device, on-device) | about 2,700 lines |
| Scripts | about 700 lines of shell and Python |
| Docs | about 900 lines (plan, spec, roadmap, this file, AGENTS.md) |
| Tests | 171 JVM unit/Compose/navigation/screenshot tests, 3 Managed-Device tests, 2 physical-device tests, a 56-check emulator smoke script, a minified-release smoke script, a real-radio smoke script |
| Reference screenshots | 21, each looked at by the agent before being committed |

## What the agent did without a human

- Wrote the plan, then built every milestone against it, keeping the roadmap and docs in step with the code.
- Resolved current dependency versions and found the incompatibilities itself (compileSdk 37 required by current Compose and lifecycle, detekt needing a pinned Kotlin, Robolectric needing a JDK flag, deprecated Compose test APIs under warnings-as-errors).
- Drove the running app on an emulator and a phone through its own scripts: install, launch with a scenario, read the UI as structured text, tap by test tag, take screenshots, read logs, handle the real system permission dialog.
- Designed the fake-scanner seam so almost all behaviour could be built and tested with no radio, then switched to the real scanner with no change above the port.
- Found and fixed its own bugs through the gates (listed below).

## Where a human was needed

| When | What | Why it could not be automated |
|---|---|---|
| Planning | Answered four scoping questions (BLE only, phone available, package id, min SDK) | Product decisions |
| M0 | Installed the JDK with `apt` | No passwordless `sudo` |
| M0 | Accepted the Android SDK licences | Legal consent is the user's |
| M0 | Enabled USB debugging and authorised the computer | Device-side trust prompt |
| M1, M5, M6 | Unlocked the phone (and in M1 ran the first phone smoke test) | The lock screen cannot and should not be bypassed |
| M3 | Restarted the session after the OS killed the Claude desktop process for memory | Machine resource limit, see below |
| Throughout | Approved the downloads and the milestone go-aheads | Scope and consent |

No human wrote or edited code.

## Problems the agent caused or hit, and how they surfaced

Honest list, most instructive first.

1. **Out-of-memory kill (M3).** A `verify.sh --full` run (Gradle with a 3 GB heap, a Kotlin daemon, two emulators) next to a loaded desktop got the Claude desktop process killed. The agent had designed that gate without budgeting memory. Fixed with smaller heaps, a smaller emulator, stopping the Gradle daemon before the emulator stage, and a note to close browsers. The cause was inferred from the circumstances; the kernel log did not name it.
2. **Navigation lifecycle bug (M4).** Stop-on-background was attached to a navigation screen's lifecycle, so opening a device stopped the scan. Caught by the full-app navigation test, not by review.
3. **Bluetooth loss and rotation (M6).** Losing Bluetooth mid-scan left a phantom "Scanning" state after it returned; rotation stopped the scan. Both were found by deliberately testing lifecycle edge cases, not by the earlier gates, and both are now covered by tests.
4. **Flaky harness (M0 to M3).** The emulator script raced a Gradle Managed Device that took the same port, and a freshly booted emulator could look locked. Found when full runs failed intermittently; fixed in the script.
5. **Stale Gradle build cache (M1).** After a folder rename the cache served an old resource result. The cache was switched off and the reason recorded in AGENTS.md.
6. **A hung test run (M4).** An infinite "age ticker" inside a ViewModel made the coroutine test runner spin forever. The ticker now runs only while observed.
7. **Test-script mistakes.** Wrong assumptions about debounce timing, about which nodes carry text in a UI dump, and a stray redirect that hid output. Each failed loudly and was corrected. One run on the phone failed for a script-timing reason that was fixed; a second failure mode could not be reproduced afterwards.
8. **A visual bug a test could not see (M1).** The "dark" reference screenshot was actually light because the test omitted the surface the app wraps content in. Found only because the agent opened the image.
9. **A shell slip.** A `pkill -f` pattern matched the agent's own command line and killed the shell.
10. **An unprompted download.** Running `--help` on Google's `android` CLI made it download an embedded installation and revealed that it sends usage metrics. The agent should have read about it before running it; it is now flagged in AGENTS.md and the CLI is only used with `--no-metrics`.

Style and static-analysis rules (ktlint, detekt magic numbers, line length) cost many small iterations. They are cheap to fix and the formatter does most of it, but a significant share of the edit-run cycles went there.

## What earned its keep

- **One gate (`scripts/verify.sh`).** Every milestone ended with a single command and a clear pass or fail with the log path. Agents never had to decide what "done" means.
- **The fake scanner behind a port, feeding the same parser.** It made list, detail, permissions, failures and performance buildable and testable with no radio, and it meant the first real-radio run on the phone worked first time with no UI changes.
- **JVM screenshot tests plus reading the images.** Fast, no emulator, and the only thing that catches "it compiles and tests pass but it looks wrong".
- **Stable test tags exposed as resource ids.** `ui.sh find/tap` by tag made the E2E scripts short and robust. The one subtlety (a plain container needs a content description or UI Automator does not expose it) is written down.
- **Architecture tests by import scanning.** Cheap, no dependency, and they turned conventions into failures (and one false positive on a comment, which taught the rule).
- **Whole-app JVM tests with real time.** They found the lifecycle bugs that unit tests of each piece could not.
- **The real-device scripts as separate, explicit steps.** Keeping them out of `--full` meant the default gate stays runnable without hardware.

## What did not work as well, and limits of this evidence

- The emulator has a Bluetooth adapter but no way (in this SDK) to inject advertisers, so real advertisements were only ever tested on the one phone, on the newest Android version. The Android 8 to 11 location-permission path is covered by Robolectric alone.
- Reference screenshots are accepted on the agent's own visual judgement; there is no independent check of design quality or text contrast.
- Everything depends on a phone and a human to unlock it; there is no way to automate that safely.
- Wall-clock time, token use and cost were not measured, so this retrospective says nothing about efficiency, only about reliability and where help was needed.
- One agent session built and checked its own work. The tests it wrote verify its own understanding of the spec; an independent review would add confidence.

## Recommendations if this approach is repeated

1. Write down the verification gate and the memory budget before writing any app code.
2. Put the hardware boundary behind a port on day one and make the fake emit the same bytes as the real thing.
3. Test lifecycle edges (rotation, backgrounding, adapter loss, navigation) with whole-app tests early, not at the end.
4. Keep real-device checks as named scripts a human can trigger, and make them refuse politely (locked phone, Bluetooth off) instead of failing obscurely.
5. Treat each external tool the agent runs for the first time (downloads, telemetry, on-device installs) as a decision, not a convenience.
6. Have a human or a second agent review the screenshots and the spec-to-test mapping before declaring a milestone complete.

## Left undone

- The optional debug state-dump receiver (nothing needed it).
- Text contrast checks, localisation (English only), tablet layouts, anything on the out-of-scope list in the roadmap.
- Any change to production Bluetooth behaviour on Android versions other than 17 (tested for real) and 16 (emulator, no devices).
