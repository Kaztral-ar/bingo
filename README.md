# Bingo 1–25 — Android Project

Native Android app (Java + XML) with a 5×5 Bingo game using numbers 1–25.

## Play modes
- **VS Computer** — fully offline.
- **2-Player Online** — Supabase Auth + Supabase Edge Functions + Postgres.
- **How to Play, Statistics, Settings** — included.

## Bingo gameplay
- Both players fill their cards during the same setup phase.
- Numbers are placed in strict **1 → 25** order.
- After setup, the players choose who calls first.
- Calling alternates one number at a time.
- Every called number automatically marks **both cards**.
- A completed row, column, or diagonal counts as one Bingo letter.
- Completed lines progress **B → I → N → G → O**; five completed lines wins.
- Multiple lines completed by one call count separately.

## Online multiplayer
The online backend is Supabase project `dgcphhplyujadgjkofgk`.
- Supabase Auth provides anonymous player sessions.
- The `bingo-server` Edge Function handles room creation, joining, card locking, caller selection, number calls, automatic marking, and winner verification.
- Postgres stores rooms, players, and private call sequences.
- The Android client uses the Supabase publishable key and authenticated Edge Function requests.

## Building
Open the repository in Android Studio and sync the Gradle project. The project targets Android 16 / API 36 and uses Android Gradle Plugin 8.10.1 with Gradle 8.11.1. A JDK 17 environment is required by this AGP version.

The app's Android package is `com.bingo125`, version **1.0.1**, version code **2**.
