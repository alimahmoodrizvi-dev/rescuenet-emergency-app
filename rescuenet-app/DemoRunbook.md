# RescueNet — Demo Runbook (Phase 8)

This is the honest breakdown of what happens during the in-app Demo Mode walkthrough
(Android app → Home → Demo Mode), scene by scene, matching the Part 31 competition demo
script. **What's genuinely real vs. what's necessarily simulated is stated plainly for every
step** — nothing here is presented as more real than it is.

## Before you start

1. Start the backend: `cd rescuenet-backend && uvicorn app.main:app --reload`
2. Create a Command Center login (once): `python scripts/create_org_user.py --username admin --password changeme --role COMMAND_CENTER_ADMIN`
3. Start the Command Center: `cd rescuenet-command-center && npm run dev`, sign in, open **Live Map** or **Incidents** in a browser window judges can see.
4. On the Android device/emulator, open **Settings** and confirm the backend URL points at
   your running backend (`http://10.0.2.2:8000/` for an emulator on the same machine;
   your computer's LAN IP for a physical phone).
5. Open **Home → Demo Mode**.

## Scene-by-scene

| # | Scene | What's genuinely real | What's simulated |
|---|---|---|---|
| 1 | Karachi Flood begins | — | Scenario framing text only |
| 2 | Citizen presses Need Help | — | Narrative only — the real "Need Help" flow is a separate, fully functional screen you can demo independently if you want to show the manual path |
| 3 | Urdu voice report → AI structures it | **Real AI call** — `AiRepository` calls the backend's `/api/ai/analyze-incident` (or the offline fallback if unreachable) on the literal Part 6 example text and displays the actual parsed result inline | The specific example sentence is scripted, not live speech — for a live-speech version, use the app's own Voice Mode screen separately, which uses real on-device speech recognition |
| 4 | Internet fails | The network status **banner display** flips to OFFLINE | This is a display override, not a real radio state change — your phone's actual internet connection is untouched. **For a genuinely offline demo**, put the phone in airplane mode for real instead of relying on this step; everything downstream (queuing, mesh relay) will then be authentically offline too |
| 5 | Offline relay active | **Real write** — a genuine `EmergencyIncident` row is created via `IncidentRepository` (Room + sync queue + mesh outbox), the same code path a real citizen report takes | If you didn't go into real airplane mode in step 4, this "offline" report is actually sitting on a device with real internet — it will likely sync almost immediately rather than waiting, which is honest, just faster than the narrative implies |
| 6 | Internet returns → sync | **Real sync attempt** — `OfflineQueueManager.runSyncNow()` forces an immediate `SyncWorker` run against the real backend | — |
| 7 | Command center ingest | **Real** — switch to the Command Center browser tab; the incident should already be visible in the Incidents list and on the Live Map, pushed live via the `/ws/incidents` WebSocket | — |
| 8 | AI cluster detected | **Real** — two more nearby flood reports are created through the same real `IncidentRepository` path (clearly logged as demo seed data); on the Command Center's **AI Clusters** page, click "Run clustering" — the clustering algorithm genuinely runs against these three real rows | The seed incidents' content is synthetic/scripted, not from real citizens — this is stated in the code (`DemoModeViewModel.seedNearbyIncidentsForClustering`) |
| 9 | Resource recommended | **Real** — on the Command Center's Incidents page, open the primary demo incident and click "Get recommendation." Verified during development to correctly recommend the closest seeded resource (Rescue Team 3) with real haversine-distance reasoning shown | The ETA is straight-line-distance-based, not real routing — the Command Center UI itself flags this (`is_simulated_eta`) |
| 10 | Safe route shown | Real screen, real UI | Route/hazard data itself is simulated and labeled as such directly on the Safe Route screen |
| 11 | Family dashboard updates | **Local write** — Ali's row in the local Room database flips to NEEDS HELP, visible immediately on the Home screen's family board | In real use, this would only ever happen because Ali's own phone posted a status update — see `FamilyRepository.setMemberStatusForDemo`'s doc comment. This step is a presentation convenience for showing the UI reacting, not a demonstration of a second device's real network round trip. **For a genuinely real version of this**, run the app on a second device/emulator, sign in as a different citizen, and press "I'm Safe" or "Need Help" there — it'll show up for real via the backend once family-contact linking is wired up (currently seeded, not yet a real add-contact flow — see the Phase 3 README) |

## Making it more real for a live judged demo

The single highest-impact upgrade: **actually use two physical phones for step 4-5** instead
of the in-app display override. Put phone A in real airplane mode, send a report, watch
"RescueNet Nodes Nearby" go from 0 to 1 as phone B (with real internet) comes within BLE
range, then watch phone B relay and sync the report on phone A's behalf. This demonstrates
the Phase 4 mesh relay and Phase 5 backend hand-off as genuinely as this system can be shown
without real disaster conditions — see the Phase 4 README's "How to test the relay" section.

## What every layer honestly still can't do

- No real disaster data (weather, road closures, official alerts) feeds any part of this —
  everything geographic beyond the seeded shelters/hospitals/resources is illustrative.
- The AI's `is_simulated` flag is real and meaningful (`MockProvider` unless
  `RESCUENET_ANTHROPIC_API_KEY` is configured), but neither provider has been benchmarked
  against real emergency-report language beyond the Part 6 example.
- BLE mesh relay range is genuinely short (tens–~100m) — nothing in this demo should imply
  otherwise.
