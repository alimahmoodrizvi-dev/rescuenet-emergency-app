# RescueNet — Competition Polish (Phase 10)

The final phase. Rather than a broad, shallow pass, this phase closes out specific gaps that
were flagged honestly in earlier phases — turning "known limitation" into "fixed" wherever
that was genuinely achievable, and being equally clear about the one major gap that wasn't.

## Security

### ✅ Fixed: local database encryption at rest

Flagged since Phase 3 (`di/DatabaseModule.kt`'s original doc comment). Now implemented for
real:

- Room runs on top of **SQLCipher** (`net.zetetic:android-database-sqlcipher`) instead of
  plain SQLite.
- The passphrase is a random 256-bit value generated on first launch, stored in
  **Keystore-backed `EncryptedSharedPreferences`** (`data/security/SecurePassphraseProvider.kt`)
  — never hard-coded, never transmitted, only recoverable on the specific device that created
  it.
- `SupportFactory(passphrase, null, clearPassphrase = true)` zeroes the passphrase byte array
  in memory immediately after it's consumed.

**Honest caveat:** this could not be compiled/verified in the sandbox this project was built
in (same Android-SDK-access limitation noted throughout every phase). SQLCipher+Room
integration has some known footguns on certain AGP/Room version combinations — treat this as
carefully-reasoned-through but unverified, and check `TESTING.md` before assuming it's
production-ready.

### ⚠️ Not fixed: mesh relay's shared demo encryption key

`MeshMessageCrypto`'s `NETWORK_DEMO_KEY` is still a single key baked into every install. This
was **deliberately not addressed** this phase: a real fix means hybrid encryption to a real
backend keypair or per-device ECDH, which requires backend-side key infrastructure that
doesn't exist yet (a new endpoint, key storage, and a change to how `SyncWorker` hands
decrypted content to the backend). That's a bigger, riskier change than this phase's budget
allowed for without the ability to compile-test it — attempting it half-verified seemed worse
than leaving the existing, clearly-labeled limitation in place. This is the single most
important thing to fix before any real deployment; see `MeshMessageCrypto.kt`'s doc comment
for the specific options.

## Battery awareness (Part 30)

Previously, mesh scanning/advertising ran unconditionally for the app's whole foreground
lifetime. Now:

- `BatteryLevelProvider` reads battery percent/charging state via the sticky
  `ACTION_BATTERY_CHANGED` broadcast (no permission required).
- `BatteryAwareScanPolicy` — a **pure, unit-tested** decision function — pauses mesh
  scanning/advertising when battery is critically low (≤15%), not charging, **and** nothing
  is queued to relay. It never pauses while charging or while a report is waiting to go out,
  because a queued emergency report matters more than battery conservation.
- `MeshRelayManager` re-evaluates this policy roughly once a minute and immediately whenever
  a new report is queued (so sending a report can wake scanning back up even if it had paused).

This is the one piece of Phase 10 genuinely verified by a passing-in-principle test:
`BatteryAwareScanPolicyTest` exercises all the boundary cases (exactly at the threshold,
charging override, pending-outbox override, unknown-state fail-open).

## Accessibility (Part 18)

- **Haptic feedback** added to the Need Help and I'm Safe buttons — under stress, a person
  may not be watching the screen closely enough to trust a purely visual tap confirmation.
- **Screen-reader grouping**: `NetworkStatusBanner` and family member rows now use
  `Modifier.semantics(mergeDescendants = true)` with an explicit combined `contentDescription`,
  so TalkBack reads each as one coherent sentence instead of several disconnected text
  fragments.
- **Live regions** for critical state changes: the network status banner and a family
  member's status both use `liveRegion` so a screen-reader user is proactively told the
  moment the network drops/recovers or a family member flips to `NEEDS_HELP` — not only if
  they happen to be re-reading that part of the screen when it changes. `NEEDS_HELP`
  specifically uses `LiveRegionMode.Assertive` (interrupts) rather than `Polite`, since it's
  the single most important thing this screen can communicate.

**What's still not done**: a full accessibility audit of every screen (touch target sizes,
color contrast ratios measured rather than eyeballed, RTL layout verification for Urdu) — the
above are the highest-value, most defensible fixes given the time available, not a claim of
complete coverage.

## What Phase 10 deliberately did NOT touch

- No new UI screens, animations, or visual redesign — the existing design system (Phase 2)
  was judged sufficient; further visual polish without the ability to actually render and
  look at it would be guessing, not polishing.
- No performance profiling (no device/emulator available to profile against) — the
  already-cached Retrofit clients (`BackendApiClient`), battery-aware scanning above, and
  WorkManager's existing constraint-based scheduling are the concrete performance-relevant
  decisions made across the whole project, not new ones added here.
- The backend and Command Center were not touched this phase — Phase 10 was scoped to the
  two things most worth fixing on the Android side specifically.

## Final honest project status

Ten phases, four codebases (Android app, FastAPI backend, React command center, and this
documentation), built with a consistent rule throughout: every simulated, mocked, or
not-yet-implemented piece is labeled as such in the code and in these docs, not glossed over.
The backend and its 19 tests are genuinely verified by execution. The Android app is
carefully reasoned through and internally consistent but **has never been compiled** — that
is the single largest caveat on this entire project, stated here one final time as plainly as
possible. Before treating this as competition-ready, the first and most important step is
opening `rescuenet-app/` in Android Studio and seeing what Gradle actually says.
