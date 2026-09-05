# RescueNet

**When the network goes down, the rescue network comes alive.**

RescueNet is an offline-first emergency communication and coordination platform built for
disaster-prone regions — designed around Pakistan's flood and earthquake emergencies, and
built to generalize anywhere normal infrastructure can fail exactly when people need it most.

---

## The problem

During floods, earthquakes, and major infrastructure failures, the thing that fails first is
the network — cellular towers go down, internet access disappears — right when people most
need to report emergencies, find their families, and locate safe routes. Most emergency
apps quietly assume the internet works. RescueNet doesn't.

## The idea

Turn ordinary smartphones into nodes of a resilient emergency network:

- **When internet is available**: reports flow straight to a backend and a live coordinator
  dashboard.
- **When it isn't**: reports are encrypted, stored locally, and relayed phone-to-phone over
  Bluetooth until *any* device in the chain regains connectivity — at which point the report
  reaches the backend automatically, no matter whose phone actually delivered it.

An AI layer turns messy, code-switched speech (English/Urdu) into structured, prioritized
incident data — always labeled with a confidence score, never presented as certain fact.

## What's actually in this repository

This is a complete, three-part system, not a single demo screen:

| Folder | What it is | Built with |
|---|---|---|
| [`rescuenet-app/`](./rescuenet-app) | The citizen-facing Android app | Kotlin, Jetpack Compose, Room, Hilt, WorkManager |
| [`rescuenet-backend/`](./rescuenet-backend) | The API everything talks to | Python, FastAPI, SQLAlchemy, WebSockets |
| [`rescuenet-command-center/`](./rescuenet-command-center) | The coordinator's live dashboard | React, TypeScript, Leaflet, Recharts |

### Key features

- 🚨 **One-tap emergency reporting** with AI-assisted structuring of freeform/voice
  descriptions (English + Urdu)
- 📡 **Offline peer-to-peer relay** over Bluetooth Low Energy — real GATT-based
  store-and-forward, not a simulation, with honest range limits stated up front
- 🟢 **Family safety network** — one-tap "I'm Safe," a live status board for trusted contacts
- 🗺️ **Live coordinator dashboard** — real-time incident map, AI-detected incident clusters,
  smart resource-matching with visible reasoning (not a black box)
- 🔐 **Encrypted local storage** (SQLCipher) and encrypted mesh relay payloads
- 🧪 **Genuinely tested backend** — 19 automated tests covering dedup, auth, failure modes,
  and multi-device scenarios (not just written — actually run, see below)

## Honesty, by design

Every simulated or not-yet-implemented piece in this project is labeled as such, in the code
and in the docs — not glossed over for a better pitch. Two documents make this explicit:

- **[`rescuenet-app/TESTING.md`](./rescuenet-app/TESTING.md)** — exactly what's automated,
  what's manual, and what genuinely needs physical hardware to verify
- **[`rescuenet-app/POLISH.md`](./rescuenet-app/POLISH.md)** — final security/accessibility
  status, including the one gap that was deliberately left open rather than half-fixed

The backend's 19 tests are real and pass on every commit-worthy change:
```
cd rescuenet-backend && pytest tests/ -v
# 19 passed
```

## Try it yourself

Full step-by-step instructions — tools to install, how to verify each piece, how to run all
three together — are in **[`SETUP_AND_RUN_GUIDE.md`](./SETUP_AND_RUN_GUIDE.md)**.

For a guided walkthrough of the whole system working together (the same one built for this
competition submission), see **[`rescuenet-app/DemoRunbook.md`](./rescuenet-app/DemoRunbook.md)**
— it also states plainly which parts of the demo are live system behavior versus a display
simulation, scene by scene.

## Architecture, at a glance

```
 Citizen's phone (Android)                  Coordinator's browser
 ┌─────────────────────────┐                ┌─────────────────────────┐
 │  Jetpack Compose UI      │                │  React dashboard         │
 │  Room (encrypted) ───────┼──offline───┐   │  Live map, clustering,  │
 │  BLE mesh relay          │  queue     │   │  resource matching       │
 └──────────┬───────────────┘            │   └───────────┬─────────────┘
            │ HTTPS / WebSocket           │               │ HTTPS / WebSocket
            ▼                             ▼               ▼
                    ┌───────────────────────────────────┐
                    │   FastAPI backend + database        │
                    │   Auth · Incidents · AI · Resources │
                    └───────────────────────────────────┘
```

## Roadmap beyond this submission

- Real hybrid encryption for mesh-relayed messages (currently a clearly-labeled demo-only
  shared key — see `POLISH.md`)
- LoRa / satellite gateway support (the transport layer is already built as a pluggable
  interface specifically for this)
- Real disaster-data integrations (official alerts, live road-hazard data) in place of the
  current seeded/simulated reference data
- Production-grade key management and a formal security review before any real deployment

## License

See [`LICENSE`](./LICENSE).
