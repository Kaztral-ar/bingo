package com.bingo125.online;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Central access point for the Firebase Realtime Database.
 *
 * Data model (see /database.rules.json at project root for the matching
 * security rules, and /functions/index.js for the server-side logic that
 * actually owns the number sequence and bingo verification):
 *
 * rooms/
 *   {roomCode}/
 *     host: uid
 *     status: "waiting" | "filling" | "calling" | "finished"
 *     createdAt: server timestamp
 *     fillDeadline: server timestamp (set when status -> "filling")
 *     calledNumbers: [ int, ... ]        // written ONLY by Cloud Functions
 *     currentIndex: int                   // written ONLY by Cloud Functions
 *     winnerUid: uid | null
 *     winningPattern: string | null
 *     players/
 *       {uid}/
 *         name: string
 *         ready: bool
 *         cardLocked: bool
 *         card: [[int x5] x5]             // player writes only while status == "filling"
 *         marked: [[bool x5] x5]          // player writes only cells that were actually called
 */
public class FirebaseManager {

    private static FirebaseManager instance;
    private final FirebaseDatabase database;

    private FirebaseManager() {
        database = FirebaseDatabase.getInstance();
        database.setPersistenceEnabled(true); // survives brief disconnects
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) instance = new FirebaseManager();
        return instance;
    }

    public DatabaseReference roomsRef() {
        return database.getReference("rooms");
    }

    public DatabaseReference roomRef(String roomCode) {
        return database.getReference("rooms").child(roomCode);
    }

    public DatabaseReference playerRef(String roomCode, String uid) {
        return roomRef(roomCode).child("players").child(uid);
    }
}
