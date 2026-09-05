# RescueNet — Testing (Phase 9)

Maps every item in the Part 39 testing checklist to what's actually covered, honestly. Three
categories: **automated** (a test file exists and — for the backend — was run and passed),
**manual** (a documented QA step, no automated test), and **hardware-required** (cannot be
verified without a physical device or two, documented in `DemoRunbook.md` instead).

## Backend — automated, run, and passing (19/19)

```
cd rescuenet-backend && pytest tests/ -v
```

| Area | Test file | What it proves |
|---|---|---|
| Auth (device + org) | `test_smoke.py` | Idempotent device registration, role gating, invalid credentials rejected |
| Incident dedup | `test_smoke.py`, `test_failure_modes.py` | Same `event_uuid` submitted twice (same device, different devices, or via sync-batch) always resolves to one row |
| AI structuring | `test_smoke.py`, `test_failure_modes.py` | Correct extraction on the Part 6 example; empty/gibberish input never crashes or overstates confidence |
| GPS-failure equivalent | `test_failure_modes.py` | An incident with no lat/lng still succeeds; resource recommendation fails with a clear 400, not a crash, when location is missing |
| Auth failure | `test_failure_modes.py` | Missing/invalid tokens rejected (401); public reads (shelters/hospitals/incidents) work without auth |
| Multi-device dedup | `test_failure_modes.py` | Two *different* devices submitting the same relayed event (simulating mesh relay) still dedup to one row, both via direct POST and via sync-batch |
| Clustering edge cases | `test_failure_modes.py` | Fewer than 3 nearby incidents never forms a "cluster" |
| Resource assignment | `test_failure_modes.py` | Assigning to a nonexistent resource/incident ID returns 404, not a 500 |
| Alert expiry | `test_failure_modes.py` | An expired alert is excluded from the active list |

A real bug was caught and fixed during this phase: a cross-test-file database isolation
issue (both test modules were fighting over separate SQLAlchemy engines due to Python's
module-caching behavior) — see `tests/conftest.py`'s doc comment for the full explanation
and fix.

## Android — written, refactored for testability, **not executable in this sandbox**

This sandbox has no Android SDK and no network path to `google()`/`dl.google.com` (see the
allowed-domains list in the environment), so Gradle can't resolve AndroidX/Compose/Room
dependencies here — the same limitation noted in every prior phase's README. What Phase 9
does instead:

1. **Extracted pure-logic classes with zero Android-framework dependencies**, specifically so
   they're unit-testable in principle without Robolectric or a device:
   - `OfflineAiHeuristics` (was inline in `AiRepository`)
   - `IncidentSummaryMerger` (was inline in `EmergencyReportViewModel`) — writing its test
     **caught a real logic bug**: `InjuryLevel.UNKNOWN` was being treated identically to an
     explicit `NONE` (both silently reported "no injuries"), contradicting the documented
     intent that only a real answer should override the AI's read. Fixed in the same commit
     as the test — see `IncidentSummaryMerger.kt`'s updated `when` block.
   - `MeshMessageCrypto` (swapped `android.util.Base64` for `java.util.Base64` — functionally
     identical on-device, but removes the class's only Android dependency)
   - `MeshWireFormat` (was inline in `BleTransportProvider`)
2. **Wrote real JUnit 4 test files** (`app/src/test/java/...`) against all four, reasoned
   through line-by-line against the actual implementation rather than assumed. `build.gradle.kts`
   has the `testImplementation("junit:junit:4.13.2")` dependency wired up.
3. **Everything Room/Hilt/Context-dependent remains untested here** — DAO-level dedup
   (`OnConflictStrategy.IGNORE`), ViewModel state flows, WorkManager scheduling, and all UI
   are not unit-testable without Robolectric (not installable in this sandbox either) or an
   emulator. The DAO-level dedup contract these would check is the same contract already
   verified server-side in `test_incident_create_and_dedup`/`test_two_different_devices_...`
   — not a substitute, but not undocumented either.

**To actually run the Android tests:** open the project in Android Studio and run
`./gradlew testDebugUnitTest`, or run the four test classes directly from the IDE. If
anything fails, it's a real bug to fix, not a sandbox artifact — these were written with the
same care as the backend's (which did run and pass), just not machine-verified here.

## Manual QA checklist (no automated test, by nature of what's being tested)

| Scenario | How to check | Reference |
|---|---|---|
| Online mode, full flow | Send a report with real internet — should sync near-instantly | `DemoRunbook.md` |
| Offline mode (real) | Airplane mode on, send a report, confirm it queues (`SyncState.QUEUED`), airplane mode off, confirm it syncs | Phase 3 README |
| Two-device mesh relay | Two physical phones, BLE range, one offline — confirm "Nodes Nearby" updates and the report reaches the backend via the online phone | Phase 4 README, `DemoRunbook.md` |
| GPS permission denied | Deny location in Onboarding, send a report, confirm it still sends with `latitude: null` | `LocationProvider.kt`'s doc comment |
| Bluetooth disabled | Turn Bluetooth off, confirm the app doesn't crash and "Nodes Nearby" stays at 0 | `BleTransportProvider.start()` — no-ops if `!btAdapter.isEnabled` |
| Backend unreachable | Point Settings' backend URL at a dead address, send a report, confirm it still queues locally and the AI summary still appears (offline fallback) | `AiRepository.kt`, `SyncWorker.kt` |
| Low battery / background scanning | Verify BLE scan mode is `SCAN_MODE_LOW_POWER` by default (Part 30) and advertising mode is `ADVERTISE_MODE_BALANCED`, not the most aggressive settings | `BleTransportProvider.kt` |
| Voice recognition unavailable | Test on a device/emulator without Google's speech service installed — confirm the `Failed` state's "Type instead" fallback appears rather than a hang | `VoiceRecognitionProvider.kt` |

## What's still genuinely untested anywhere

- Real-world BLE range/reliability across many simultaneous devices (mesh "storms")
- The `AnthropicProvider` path's accuracy against varied real emergency-report language
  (only the Part 6 example has been checked, on both the mock and real-provider code paths)
- Load/concurrency behavior of the backend and WebSocket fan-out under many simultaneous
  connected command centers
- Any of this at real disaster scale — everything here is hackathon/demo-scale verification
