# RescueNet Command Center — Phase 7

A React + TypeScript web dashboard for rescue coordinators, consuming the Phase 5
`rescuenet-backend` API and its `/ws/incidents` WebSocket for live updates.

**Verified working, not just written:** `tsc -b` compiles clean, `npm run build` produces a
working production bundle, and the full login -> create-incident -> list-incidents ->
dashboard-statistics flow was exercised with real HTTP calls against a live backend instance
during development (the exact same calls `src/api/client.ts` makes) -- a headless-browser
click-through wasn't possible in the sandbox this was built in (no network path to download
a browser binary), so treat the visual layer as reviewed-by-eye on top of a verified API
contract, and flag anything that doesn't render as expected.

## Quick start

```bash
npm install
npm run dev
```

Opens at `http://localhost:5173`. On first load you'll hit the login screen -- see "Creating
a login" below, since there's deliberately no public self-signup for these roles.

## Creating a login

Per Part 24, Volunteer/Rescue Operator/Command Center Admin accounts are provisioned
directly, not self-served. From the `rescuenet-backend/` directory:

```bash
python scripts/create_org_user.py --username admin --password changeme --role COMMAND_CENTER_ADMIN
```

Then sign in with that username/password on the login screen -- it also has a field for the
backend URL (defaults to `http://localhost:8000`), stored in `localStorage` so you don't
need a build-time environment variable to point it at a different backend (e.g. a laptop's
LAN IP during a live demo).

## Pages

- **Dashboard** -- live incident counts by severity/type (charts via `recharts`), resource
  and mesh-relay statistics from `GET /api/dashboard/statistics`.
- **Live Map** (`react-leaflet` + OpenStreetMap tiles, no API key required) -- incidents
  colored by severity, resources, shelters, and hospitals plotted together.
- **Incidents** -- filterable list; selecting a row opens a detail panel with the Part 12
  Smart Resource Match (shows the reasoning, not just a pick), one-click assignment, and an
  incident-update/status-change form.
- **AI Clusters** -- runs the backend's proximity-based clustering (Part 12) on demand and
  displays each cluster's likely type and estimated people affected, framed explicitly as an
  AI recommendation to review rather than an automatic dispatch.
- **Resources** -- read-only status overview (assignment happens from the Incidents page,
  where the reasoning for a pick is visible).
- **Alerts** -- broadcast form (role-gated server-side; a citizen or unprivileged token will
  get a clear error) with a required "mark as demo/test alert" checkbox reflecting the
  backend's `is_demo` field, and a list of active alerts with a `DEMO` tag where applicable.

## Live updates

`useIncidentSocket` connects to `/ws/incidents` on load, reconnecting with a fixed 3s backoff
if dropped -- a dashboard left open for hours during a real event shouldn't need a manual
refresh. The initial incident list still comes from `GET /api/incidents` (the socket doesn't
replay history), then the socket pushes deltas as `incident_created`/`incident_updated`
events.

## What's intentionally NOT in this phase

- Real-time collaborative editing indicators (e.g. "another operator is viewing this
  incident") -- out of scope for a prototype command center
- Server-side WebSocket topic filtering -- every connected client receives every event; fine
  at demo scale, not at scale with many concurrent command centers (noted in the backend's
  `app/websocket.py`)
- A build-time-configurable default backend URL -- currently hardcoded to
  `http://localhost:8000` as the fallback in `src/api/client.ts`, overridable at runtime from
  the login screen

## Next step

Phase 8 -- Demo Mode: a coordinated judge-facing walkthrough spanning the Android app,
backend, and this Command Center simultaneously (the Android app already has its own
Demo Mode screen from Phase 2 -- this phase would extend that scenario to also drive what
appears here, so judges see the full loop from citizen report to command-center map in one
sitting).
