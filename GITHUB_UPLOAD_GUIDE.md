# Uploading RescueNet to GitHub

A plain-English walkthrough for getting this whole project onto GitHub so competition judges
can view it. No prior Git experience assumed.

---

## 1. Install Git (if you don't have it)

Check first:
```powershell
git --version
```
If that prints a version number, skip to step 2. If it says "not recognized," download Git
from **[git-scm.com/download/win](https://git-scm.com/download/win)**, run the installer
accepting all the defaults, then **open a new terminal window** (same PATH-refresh issue as
Node earlier) and check `git --version` again.

## 2. Create a GitHub account (if you don't have one)

Go to **[github.com](https://github.com/)** → Sign up. Free accounts are all you need — this
project doesn't need anything paid.

## 3. Create a new repository on GitHub

1. Click the **+** icon top-right → **New repository**.
2. Name it something like `rescuenet` or `rescuenet-emergency-app`.
3. Add a short description (you can paste the one-line tagline: *"When the network goes
   down, the rescue network comes alive."*).
4. Set it to **Public** — competition hosts almost always need public access to review a
   submission; check your competition's rules if unsure.
5. **Important: do NOT check "Add a README," "Add .gitignore," or "Choose a license."** This
   project already includes all three — letting GitHub create its own would conflict with
   the files you're about to upload.
6. Click **Create repository**. GitHub will show you a page with setup commands — keep that
   page open, you'll need the URL it shows (looks like
   `https://github.com/your-username/rescuenet.git`).

## 4. Prepare your local folder

Make sure your project folder is laid out like this (this is the same layout from
`SETUP_AND_RUN_GUIDE.md`):

```
rescuenet-project/          <- this is the folder you'll upload
├── README.md
├── LICENSE
├── .gitignore
├── SETUP_AND_RUN_GUIDE.md
├── rescuenet-app/
├── rescuenet-backend/
└── rescuenet-command-center/
```

**Before you go further, do a quick sanity check** — make sure these heavy, regenerable
folders are NOT sitting inside your project (the `.gitignore` will stop them from being
committed, but it's worth confirming they aren't lingering from earlier build attempts):
- `rescuenet-command-center/node_modules/` (can be 150–200MB)
- `rescuenet-app/app/build/` and any `.gradle/` folders
- Any `.db` files in `rescuenet-backend/`

If any of those exist, it's fine to leave them — the `.gitignore` handles it — but deleting
them first makes the next steps faster.

## 5. Upload it

Open a terminal **in the `rescuenet-project` folder** (the one containing `README.md`,
`LICENSE`, etc. — not inside one of the three sub-folders), then run these one at a time:

```powershell
git init
git add .
git commit -m "Initial commit: RescueNet - offline-first emergency response platform"
git branch -M main
git remote add origin https://github.com/your-username/rescuenet.git
git push -u origin main
```

Replace the URL in the `git remote add origin` line with the actual URL GitHub showed you in
step 3.

**The first time you push, Git will ask you to sign in.** A browser window usually pops up
automatically for this — just log into GitHub there. (GitHub retired plain password
authentication for this a while back; if you're prompted for a "personal access token"
instead of a password and the browser popup doesn't appear, see the troubleshooting section
below.)

## 6. Check it worked

Visit `https://github.com/your-username/rescuenet` (your actual repo URL) in a browser. You
should see:
- The `README.md` content rendered as the repo's front page
- Three folders: `rescuenet-app`, `rescuenet-backend`, `rescuenet-command-center`
- **Not** a `node_modules` folder, `build` folder, or any `.db` files in the file listing —
  if you see these, the `.gitignore` didn't take effect (see troubleshooting)

## 7. Give it to the competition host

- If the competition just needs a public link: copy the repo URL from your browser's address
  bar and submit that.
- If they specifically ask for collaborator access on a private repo: go to your repo →
  **Settings → Collaborators → Add people** → enter their GitHub username or email.
- Consider adding **Topics** (repo page → gear icon next to "About") like `android`
  `fastapi` `react` `disaster-response` `hackathon` — helps with discoverability if the
  competition has a public gallery.

---

## Troubleshooting

**"remote origin already exists"** — you ran `git remote add origin` twice. Fix with:
```powershell
git remote set-url origin https://github.com/your-username/rescuenet.git
```

**Push asks for a password and rejects it** — GitHub no longer accepts your account password
for this. Easiest fix: install **[GitHub Desktop](https://desktop.github.com/)** instead of
using the command line — sign in there once, then use its "Publish repository" button
instead of `git push`, and it handles authentication for you.

**`node_modules` or `build` folders show up on GitHub anyway** — this means they were
already tracked by Git before the `.gitignore` existed (this can happen if you ran `git add
.` before the `.gitignore` file was in place). Fix:
```powershell
git rm -r --cached rescuenet-command-center/node_modules
git rm -r --cached rescuenet-app/app/build
git commit -m "Remove accidentally committed build artifacts"
git push
```

**A file is "too large" and GitHub rejects the push** — GitHub blocks any single file over
100MB. Nothing in this project should hit that (the largest tracked file is the ~43KB Gradle
wrapper jar) — if you see this error, it almost certainly means something in `.gitignore`
didn't get excluded properly. Check what triggered it in the error message and add that
specific path to `.gitignore`.

**You want to make a change after the first push** — the cycle is always the same three
commands from inside `rescuenet-project`:
```powershell
git add .
git commit -m "describe what you changed"
git push
```
