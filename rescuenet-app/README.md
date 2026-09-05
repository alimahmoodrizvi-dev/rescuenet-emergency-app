# RescueNet — Phase 6: AI

Builds on Phases 2-5. Replaces the mocked AI summary step and the scripted Voice Mode
transcript with real implementations, both designed to degrade honestly rather than fail
outright when unavailable.

## What's new in this phase

- **`AiRepository`** (`data/repository/AiRepository.kt`) — the client-side half of the
  Part 8 AI abstraction layer. Prefers the backend's `POST /api/ai/analyze-incident`
  (verified in Phase 5 to correctly parse the Urdu/English example from Part 6 — confirmed
  again here field-for-field against the Android DTOs) and falls back to a deterministic
  on-device rule-based extractor when offline or the backend is unreachable. The fallback is
  a deliberate line-for-line port of the backend's own `MockProvider` keyword logic, so a
  report structured offline and one structured by the backend's safe-default provider behave
  the same way.
- **Selection-merge logic in `EmergencyReportViewModel`**: per Part 6 ("AI drafts, human
  confirms"), an explicit button tap (incident type, injury level) always wins over the AI's
  guess at the same field — AI only fills in what the user didn't already state, chiefly
  severity and, for the voice-only flow, incident type. This is implemented in
  `mergeWithUserSelections()`, not left implicit.
- **`BackendSessionManager`** — auth is now shared and cached across `SyncWorker` and
  `AiRepository` instead of each independently re-registering the device with the backend.
- **Real Voice Emergency Mode** (`data/voice/VoiceRecognitionProvider.kt`,
  `VoiceModeViewModel`, rewritten `VoiceModeScreen`): uses Android's on-device/OS
  `SpeechRecognizer` with a real state machine (Idle → Listening → Partial results streaming
  live → Done, or → Failed with a "type instead" fallback) — no more scripted delay-then-
  fixed-transcript. Language is selected from the user's onboarding language preference
  (`en-US` / `ur-PK`), matching Part 7's English/Urdu scope with a clear extension point for
  more languages later (`languagePrefToTag()`).
- Manifest updated with a `<queries>` declaration for `android.speech.RecognitionService`,
  required for Android 11+ package visibility.

## Honesty notes carried into this phase

- Voice recognition quality/availability depends on what the OS and installed assistant
  (typically Google's) support for a given language and device — `VoiceRecognitionProvider`
  surfaces failures as a first-class `Failed` state with a manual-typing fallback rather than
  pretending it always works.
- The AI's `is_simulated` flag (shown in the UI's `SIMULATION` tag on the summary screen) is
  now meaningfully different depending on path: `true` from the offline fallback, and
  whatever the backend's active `AIProvider` reports (`true` for `MockProvider`, `false` for
  a real `AnthropicProvider` if `RESCUENET_ANTHROPIC_API_KEY` is configured server-side) — the
  tag is no longer unconditionally shown.
- Confidence scores from both the backend and the offline fallback are capped below 100 by
  design (max 90) — neither claims near-certainty, per Part 26.

## How to test

1. Backend running, phone online: send a report with a description like "fire in the
   building, 3 people trapped" — the AI Summary screen should show `FIRE`, the extracted
   people count, and `model_provider: mock` (or `anthropic` if you've configured a real key)
   with `is_simulated` reflecting which.
2. Airplane mode on: send the same report — it should still produce a structured summary
   (via the offline fallback) and clearly show `SIMULATION`, then queue for sync as before.
3. Voice Mode: tap "Speak instead," grant mic permission if prompted, and speak a sentence —
   partial results should appear live as you talk, then the final transcript should carry
   through to the description field (editable on the next screen) and prefill the AI
   summary.

## What's intentionally NOT in this phase

- On-device/offline speech-to-text (Voice Mode's STT still requires connectivity to the OS's
  speech service on most devices/configurations — a fully offline STT model is future work,
  Part 17)
- Uploading photos to the backend for AI analysis (text-only, consistent with every prior
  phase's scope)
- Fine-tuning or evaluating the real `AnthropicProvider` path's accuracy — it's implemented
  and reachable, but hasn't been benchmarked against real emergency-report language beyond
  the Part 6 example

## Next step

Phase 7 — Command Center: a web dashboard consuming the backend's `GET /api/incidents`,
`/ws/incidents`, and resource/alert/AI endpoints — this is the piece that finally lets a
human coordinator see what all six prior phases have been feeding into.

---

# Phase 8 — Demo Mode (complete)

`DemoModeScreen`/`DemoModeViewModel` now drive **real** actions through the app's actual
repositories — real AI parsing, a real locally-persisted incident, a real forced sync to the
backend, real seed data for the Command Center's clustering to find — instead of only
animating UI state. **See `DemoRunbook.md` at the project root for the full scene-by-scene
breakdown of exactly what's real vs. simulated at each step**, and for how to make the
offline/mesh segment genuinely real with two physical phones instead of the in-app display
override.

Verified end-to-end against a live backend during development: the exact demo sequence (AI
parses the Part 6 Urdu example -> incident created -> two nearby seeds -> clustering finds
all three -> resource recommendation correctly picks the closest seeded team) was run with
real HTTP calls and produced the expected result, including landing on "Rescue Team 3" --
matching the Phase 1 architecture doc's own illustrative example.

## What's new in this phase

- `DemoModeViewModel` (`data/viewmodel/DemoModeViewModel.kt`) calls the real
  `IncidentRepository`, `AiRepository`, and `FamilyRepository` at the appropriate scenario
  steps instead of only flipping display flags.
- `OfflineQueueManager.runSyncNow()` forces an immediate sync attempt (WorkManager
  `ExistingWorkPolicy.REPLACE`) so the "internet returns" step resolves promptly on stage
  rather than waiting on normal scheduling latency -- still a real sync attempt that can
  genuinely fail/retry, not a fake progress animation.
- `FamilyRepository.setMemberStatusForDemo()` is explicitly documented as a local-only demo
  write, distinct from the real `markSelfSafe()` path that actually syncs to the backend.

## What's intentionally NOT in this phase

- A fully automated cross-device demo -- the mesh-relay portion is most convincing with two
  real physical phones (see DemoRunbook.md), which can't be scripted from within one app
- Any change to the backend/Command Center specifically for demo purposes -- they're driven
  through their normal, real APIs with no demo-only code paths on that side

## Next step

Phase 9 -- Testing: systematic coverage of online/offline mode, sync, multiple devices,
duplicate messages, GPS/Bluetooth/backend/AI failure, low battery, and permission denial, per
the Part 39 testing checklist.

---

# Phase 9 — Testing (complete)

Full breakdown in `TESTING.md` at the project root. Summary: the backend gained 12 new
failure-mode/edge-case tests (19 total, all passing) covering GPS-failure equivalents,
multi-device dedup, auth failures, clustering edge cases, and alert expiry — plus a real
cross-test-file database isolation bug was found and fixed along the way (see
`rescuenet-backend/tests/conftest.py`).

On the Android side, four pure-logic classes were extracted specifically for testability
(`OfflineAiHeuristics`, `IncidentSummaryMerger`, `MeshMessageCrypto`, `MeshWireFormat`) and
given real JUnit test files — writing `IncidentSummaryMergerTest` caught and fixed a real
bug where `InjuryLevel.UNKNOWN` was silently being treated the same as an explicit "no
injuries," contradicting the app's own documented merge-logic intent. These Android tests
could not be executed in the sandbox this project was built in (no Android SDK, no network
path to Google's Maven repositories) — `TESTING.md` explains this limitation precisely and
lists exactly what to run and where once you have Android Studio open.

---

# Phase 10 — Competition Polish (complete)

Full breakdown in `POLISH.md` at the project root. Summary: local database encryption at
rest is now genuinely implemented (SQLCipher + Keystore-backed passphrase, closing a gap
flagged since Phase 3), mesh scanning is now battery-aware with a unit-tested pure policy
function (Part 30), and a targeted accessibility pass added haptic feedback on emergency
actions plus live-region screen-reader announcements for critical state changes (network
status, a family member needing help).

**This is the final phase of the original 10-phase roadmap.** `POLISH.md` ends with a final,
one-paragraph honest status statement — worth reading before treating any part of this
project as competition-ready as-is.
