# Bingo 1–25 — Android Project

Native Android app (Java + XML) with a 5×5 Bingo game using numbers 1–25.

## Play modes
- **VS Computer** — fully offline and playable without Firebase.
- **2-Player Online** — Firebase Realtime Database + Anonymous Authentication.
- **How to Play, Statistics, Settings** — included.

## VS Computer gameplay
- 2-minute card-filling phase.
- Numbers must be placed in strict **1 → 25** order.
- Remaining cells are automatically filled when the timer ends.
- Shared shuffled 1–25 call sequence.
- Automatic or manual marking, depending on Settings.
- Row, column, and diagonal Bingo detection.
- Result screen and local statistics.
- Sound/vibration settings are wired; audio assets are optional.

## Online multiplayer setup
Online multiplayer requires your Firebase project:
1. Enable **Realtime Database** and **Anonymous Authentication**.
2. Download `google-services.json` and place it in `app/`.
3. Deploy `database.rules.json`.
4. Deploy the Cloud Functions in `functions/`.

The Android build is intentionally configured so Firebase is only enabled at build time when `app/google-services.json` exists. This keeps the offline VS Computer mode buildable without Firebase configuration.

## Ads
The app currently uses Google's published **test** AdMob IDs. Replace them with your production IDs and add the required consent flow before publishing.

## Building
Open the repository in Android Studio and sync the Gradle project. The project targets **Android 16 / API 36** and uses Android Gradle Plugin 8.10.1 with Gradle 8.11.1. A JDK 17 environment is required by this AGP version.

The app's Android package is `com.bingo125`, version **1.0.1**, version code **2**.

## Release checklist
- [ ] Add `app/google-services.json` and deploy Firebase for online multiplayer.
- [ ] Replace AdMob test IDs with production IDs.
- [ ] Add real sound assets if desired.
- [ ] Generate a signed release APK/AAB with your release keystore.
- [ ] Complete Google Play listing, Data safety, content rating, and privacy requirements.
