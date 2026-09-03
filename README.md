# Bingo 1–25 — Android Project

Native Android app (Java + XML), matching the full spec: home screen, VS
Computer, 2-Player Online, 2-minute ordered card filling with auto-fill,
row/column/diagonal win detection, How to Play, Statistics, Settings, AdMob.

## What's fully working out of the box
- **Home → Game Mode → How to Play / Statistics / Settings** — all functional.
- **VS Computer** — complete end-to-end: 2-minute `CountDownTimer` filling
  phase, tap-to-place in strict 1→25 order, auto-fill of empty cells at 0:00,
  computer opponent (instant random valid card, auto-marks), shared shuffled
  call sequence, live row/column/diagonal detection, Result screen, local
  stats tracking. Playable immediately after building, no setup required.
- **Sound / vibration / auto-mark / dark theme toggles** — wired to
  `SharedPreferences` via `PrefsManager`.

## What needs your own setup before it runs
**1. Firebase (for Online Multiplayer)**
   - Create a Firebase project, enable **Realtime Database** and
     **Anonymous Authentication**.
   - Download `google-services.json` and drop it into `app/`.
   - Deploy the security rules: `firebase deploy --only database` (uses
     `database.rules.json` at the project root).
   - Deploy the server logic: `cd functions && npm install && firebase
     deploy --only functions` (uses `functions/index.js` — this is what
     actually owns the shuffled call sequence and verifies every bingo claim,
     per the anti-cheat requirements; the app is deliberately never trusted
     with that authority).
   - Without this, `CreateRoomActivity` / `JoinRoomActivity` will show a
     connection error — everything else in the app works fine offline.

**2. AdMob**
   - The manifest and `AdManager` currently use **Google's published test
     IDs**, so ads will show real test creatives once you add the Mobile Ads
     SDK dependency (already in `build.gradle`) — safe for development.
   - Before release: replace the test IDs in `AdManager.java` and the
     `AndroidManifest.xml` `APPLICATION_ID` with your real AdMob IDs, and add
     Google's User Messaging Platform (UMP) consent flow before your first ad
     request in `BingoApplication` — required in the EU/UK and recommended
     globally.

**3. Sound effects**
   - `SoundManager` is wired to load `.ogg`/`.wav` files from `res/raw/`
     (`place_tick`, `number_call`, `bingo_win`) but the load calls are
     commented out since no audio assets are included. Add your files and
     uncomment the three lines in `SoundManager`'s constructor.

**4. App icon**
   - A simple placeholder adaptive icon is included
     (`drawable/ic_launcher_foreground.xml`). Swap it for real artwork via
     Android Studio's Image Asset tool before shipping.

## Faster online calling cadence
`functions/tickCalledNumbers` uses Cloud Scheduler's 1-minute minimum
granularity for simplicity. For the snappier ~2–3 second call pace real
Bingo wants, replace it with a **Cloud Tasks** queue: after each call,
schedule the next tick a few seconds out instead of polling every minute.
The rest of the anti-cheat/authority design (server-owned sequence, marked
recomputed server-side, no client-decided winner) doesn't change.

## Building
Open the project root in Android Studio (Giraffe+ / AGP 8.4), let it sync,
then **Build → Generate Signed Bundle / APK**. This was authored directly as
source files rather than built here, so give it one sync-and-build pass to
confirm everything resolves in your environment before generating a release
build.
