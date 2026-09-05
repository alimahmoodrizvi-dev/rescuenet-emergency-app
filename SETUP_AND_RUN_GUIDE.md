# RescueNet — Setup, Verification & Run Guide

RescueNet has three separate pieces that work together. You don't have to run all three —
each is useful on its own — but the full experience (a phone report showing up live on a
coordinator's dashboard) needs all three running at once.

| Component | What it is | Where it runs |
|---|---|---|
| **Backend** | FastAPI + database, the API everything else talks to | Your computer (or a server) |
| **Command Center** | React web dashboard for coordinators | Browser, on your computer |
| **Android app** | The citizen-facing mobile app | Android emulator or physical phone |

Recommended order: **Backend → Command Center → Android app**. Each section below has a
"Check it" step before "Run it" — do the checks first so you catch a setup problem before it
shows up confusingly three steps later.

---

## 0. Tools & accounts checklist

Install these before you start. Version numbers are what this project was actually built
against — close/newer versions are generally fine.

| Tool | Version used | Get it from | Needed for |
|---|---|---|---|
| Python | 3.12 | [python.org](https://www.python.org/downloads/) | Backend |
| Node.js | 22 (npm 10) | [nodejs.org](https://nodejs.org/) | Command Center |
| Android Studio | Koala (2024.1) or newer | [developer.android.com/studio](https://developer.android.com/studio) | Android app — this bundles the Android SDK, an emulator, and Gradle, so you don't install those separately |
| JDK | 17 | Bundled with Android Studio — no separate install needed | Android app |
| A terminal | any | — | Backend, Command Center |

Optional:
- A physical Android phone (USB cable) if you don't want to use the emulator — real GPS,
  Bluetooth, and voice recognition only work fully on a real device.
- An Anthropic API key from [console.anthropic.com](https://console.anthropic.com/) — only
  if you want real AI analysis instead of the built-in offline-capable fallback (everything
  works without this).

You do **not** need: a database server (SQLite is the default), a cloud account, or Docker
(unless you specifically want the PostgreSQL-based setup — see the backend's own README).

---

## 1. Get the project

If you cloned this from GitHub, you already have the right layout — skip to step 2. If
you're working from a downloaded zip instead, unzip it so the three project folders sit as
**siblings**, matching this layout (this matters — the Android app's default settings and
the rest of this guide assume it):

```
rescuenet-project/
├── rescuenet-app/                (the Android app)
├── rescuenet-backend/            (the FastAPI backend)
└── rescuenet-command-center/     (the React dashboard)
```

---

## 2. Backend

### Check it

```bash
cd rescuenet-backend
pip install -r requirements.txt
pip install pytest
pytest tests/ -v
```

You should see **19 tests pass**, something like:

```
======================== 19 passed in ~1.5s ========================
```

If a test fails, stop here and fix it before moving on — everything downstream depends on
the backend behaving correctly. If you see a `passlib`/`bcrypt` error, make sure you're
using the `requirements.txt` from this project (it pins a working `bcrypt` version directly,
bypassing a known `passlib` compatibility bug).

### Create a Command Center login

There's no public sign-up for coordinator accounts by design. Create one now:

```bash
python scripts/create_org_user.py --username admin --password changeme --role COMMAND_CENTER_ADMIN
```

(Pick your own username/password — just remember them for step 3.)

### Run it

```bash
uvicorn app.main:app --reload
```

Leave this running. Check it worked by opening **http://localhost:8000/docs** in a browser
— you should see an interactive API documentation page. Keep this terminal open for as long
as you want the backend available.

---

## 3. Command Center

Open a **new terminal** (leave the backend running in the first one).

### Check it

```bash
cd rescuenet-command-center
npm install
npx tsc -b
npm run build
```

`tsc -b` and `npm run build` should both finish with no errors and end in something like
`✓ built in 1.3s`.

### Run it

```bash
npm run dev
```

Open **http://localhost:5173** in a browser. You'll land on a login screen:

- **Username / Password**: whatever you created in step 2
- **Backend URL**: `http://localhost:8000` (should already be filled in by default)

Sign in. You should land on an empty Dashboard — that's correct, there are no incidents yet.
Leave this running too.

---

## 4. Android app

This step needs **Android Studio**, not just a terminal.

### Check it

1. Open Android Studio → **Open** → select the `rescuenet-app/` folder.
2. Wait for Gradle to sync (bottom status bar). First sync can take a few minutes — it's
   downloading dependencies.
3. If sync fails, read the error in the **Build** panel — this is the single most useful
   signal for whether anything in the project needs a fix. (See the note at the bottom of
   this guide about what to expect here.)
4. Once synced, run the unit tests: right-click `app/src/test` in the project tree →
   **Run 'Tests in...'**. Expect these to pass (see `TESTING.md` in the `rescuenet-app/` folder for exactly
   what's covered and why they weren't run before you did this).
5. Build the app: **Build → Make Project**. Should finish with no red errors.

### Set up something to run it on

**Option A — Emulator (easier to start with, but BLE mesh relay won't work between two
emulators):**
1. **Tools → Device Manager → Create Device**, pick any modern phone profile, pick a system
   image with API level 26 or higher (30+ recommended), finish.
2. Click the ▶ next to your new virtual device to boot it.

**Option B — Physical phone (needed for real Bluetooth mesh testing):**
1. On the phone: **Settings → About phone → tap "Build number" 7 times** to enable Developer
   Options, then **Settings → Developer options → enable USB debugging**.
2. Plug it into your computer via USB, accept the "Allow USB debugging?" prompt on the phone.

### Run it

1. In Android Studio's toolbar, pick your emulator or device from the device dropdown.
2. Click the green ▶ **Run** button (or Shift+F10).
3. The app installs and launches, starting with a splash screen → onboarding (grant the
   permissions it asks for — location, microphone, nearby devices) → the home screen.

### Point it at your backend

1. In the app: **Home → Settings**.
2. Find **Backend URL**:
   - **Emulator**: leave it as `http://10.0.2.2:8000/` (this is a special address that
     means "the computer the emulator is running on").
   - **Physical phone**: this needs your computer's actual network address instead, since
     the phone isn't the same machine. Find it:
     - Mac/Linux: run `ifconfig` or `ip addr` in a terminal, look for something like
       `192.168.1.42`.
     - Windows: run `ipconfig`, look for "IPv4 Address."
     - Enter `http://192.168.1.42:8000/` (using your actual address), and make sure your
       phone and computer are on the same Wi-Fi network.
3. Tap **Save**.

---

## 5. Check the whole thing end-to-end

With all three still running:

1. In the Android app, go to **Home → Need Help**.
2. Pick any incident type, fill in the quick details, and send the report.
3. Switch to the Command Center browser tab (**Incidents** page). Within a few seconds, your
   report should appear — that confirms phone → backend → dashboard is genuinely working.

For a guided, scripted version of this (the same one built for judge presentations), use
**Home → Demo Mode** in the app and follow along with `DemoRunbook.md` in the project — it
walks through AI parsing, an offline/relay simulation, clustering, and resource matching, and
tells you exactly which parts are real live behavior vs. a display simulation at each step.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `pytest` fails on install | Wrong Python version or missing deps | Confirm `python --version` is 3.12+, re-run `pip install -r requirements.txt` |
| Command Center login fails | No org user created, or wrong backend URL | Re-run the `create_org_user.py` script; check the URL field on the login screen |
| Android Gradle sync fails | No internet access to Google's Maven repo, or an outdated Android Studio | Check your network/proxy settings; update Android Studio to the latest stable version |
| App can't reach the backend | Wrong backend URL for emulator vs. physical device, or backend not running | Re-check step 4's URL guidance; confirm `http://localhost:8000/docs` still loads in a browser |
| Report doesn't appear on Command Center | Backend not running, wrong URL, or the Command Center tab needs a refresh | Check the backend terminal is still running with no errors; refresh the Command Center page |
| BLE mesh features don't do anything on an emulator | Expected — BLE doesn't work reliably between two emulators | Use two physical phones for real mesh testing, or use Demo Mode's display simulation |

---

## One honest note before you start

This project was built and documented very thoroughly, but the Android app specifically was
**never compiled** during that process — the environment it was built in has no Android SDK
access. The backend and Command Center *were* built and tested for real (backend: 19 passing
automated tests; Command Center: a working production build) — the Android app is the one
piece where "Check it" in step 4 above is doing real, first-time verification work. If Gradle
sync or the build turns up errors, that's expected to be possible, not a sign you did
something wrong — see `TESTING.md` and `POLISH.md` in the `rescuenet-app/` folder for the full
honest rundown of what has and hasn't been verified.
