package com.bingo125.online;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side room operations. Anything security-sensitive (the official
 * shuffled call sequence, deciding who won) is intentionally NOT done here —
 * it happens in Cloud Functions (/functions/index.js) which the database
 * rules trust and the client does not. This class only ever writes to the
 * fields the rules allow a client to touch: presence, readiness, and its own
 * card during the filling phase.
 */
public class RoomManager {

    public interface RoomListener {
        void onRoomUpdated(RoomModel room);
        void onError(String message);
    }

    private final FirebaseManager firebase = FirebaseManager.getInstance();
    private ValueEventListener activeListener;
    private String activeRoomCode;

    /** Generates a 6-digit room code and creates the room, with the caller as host. */
    public void createRoom(String uid, String displayName, RoomListener callback) {
        String roomCode = generateRoomCode();
        DatabaseReference roomRef = firebase.roomRef(roomCode);

        Map<String, Object> room = new HashMap<>();
        room.put("host", uid);
        room.put("status", "waiting");
        room.put("createdAt", ServerValue.TIMESTAMP);

        Map<String, Object> player = new HashMap<>();
        player.put("name", displayName);
        player.put("ready", true);
        player.put("cardLocked", false);

        Map<String, Object> players = new HashMap<>();
        players.put(uid, player);
        room.put("players", players);

        roomRef.setValue(room)
                .addOnSuccessListener(v -> listenToRoom(roomCode, callback))
                .addOnFailureListener(e -> callback.onError("Could not create room: " + e.getMessage()));
    }

    /** Joins an existing room by code, if it exists, isn't full, and hasn't started. */
    public void joinRoom(String roomCode, String uid, String displayName, RoomListener callback) {
        DatabaseReference roomRef = firebase.roomRef(roomCode);

        roomRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                callback.onError("Room not found. Check the code and try again.");
                return;
            }
            String status = snapshot.child("status").getValue(String.class);
            long playerCount = snapshot.child("players").getChildrenCount();

            if (!"waiting".equals(status)) {
                callback.onError("This game has already started.");
                return;
            }
            if (playerCount >= 2 && !snapshot.child("players").hasChild(uid)) {
                callback.onError("This room is full.");
                return;
            }

            Map<String, Object> player = new HashMap<>();
            player.put("name", displayName);
            player.put("ready", true);
            player.put("cardLocked", false);

            roomRef.child("players").child(uid).setValue(player)
                    .addOnSuccessListener(v -> listenToRoom(roomCode, callback))
                    .addOnFailureListener(e -> callback.onError("Could not join room: " + e.getMessage()));
        }).addOnFailureListener(e -> callback.onError("Network error: " + e.getMessage()));
    }

    /** Host-only: begins the 2-minute filling phase for everyone in the room. */
    public void startGame(String roomCode) {
        DatabaseReference roomRef = firebase.roomRef(roomCode);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "filling");
        // fillDeadline = now + 120s, stamped with the SERVER clock so both
        // devices count down against the same authority (spec section 18).
        updates.put("fillDeadline", ServerValue.TIMESTAMP);
        roomRef.updateChildren(updates);
        // A Cloud Function (onRoomStatusChange) reads this write, adds 120000ms
        // to the server timestamp, and stores the real deadline + triggers the
        // auto-fill/calling phase transition — see functions/index.js.
    }

    /** Writes one placed number into the caller's own card during the filling phase. */
    public void placeNumber(String roomCode, String uid, int row, int col, int number, int[][] fullCardSoFar) {
        firebase.playerRef(roomCode, uid).child("card").setValue(fullCardSoFar);
    }

    public void lockCard(String roomCode, String uid) {
        firebase.playerRef(roomCode, uid).child("cardLocked").setValue(true);
    }

    /** Writes a mark for a number the client believes was called; the server re-validates it. */
    public void markNumber(String roomCode, String uid, boolean[][] fullMarkedGrid) {
        firebase.playerRef(roomCode, uid).child("marked").setValue(fullMarkedGrid);
    }

    /**
     * Submits a bingo claim for server verification. The server (Cloud Function
     * `claimBingo`) independently checks the player's stored card, the official
     * called-numbers list, and the winning pattern before accepting it — the
     * client's own belief that it won is never trusted (spec sections 9, 22).
     */
    public void claimBingo(String roomCode, String uid) {
        firebase.roomRef(roomCode).child("claims").child(uid).setValue(ServerValue.TIMESTAMP);
    }

    public void listenToRoom(String roomCode, RoomListener callback) {
        stopListening();
        activeRoomCode = roomCode;
        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                RoomModel room = RoomMapper.fromSnapshot(roomCode, snapshot);
                callback.onRoomUpdated(room);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };
        firebase.roomRef(roomCode).addValueEventListener(activeListener);
    }

    public void stopListening() {
        if (activeListener != null && activeRoomCode != null) {
            firebase.roomRef(activeRoomCode).removeEventListener(activeListener);
        }
        activeListener = null;
        activeRoomCode = null;
    }

    public void leaveRoom(String roomCode, String uid) {
        firebase.playerRef(roomCode, uid).removeValue();
    }

    private String generateRoomCode() {
        SecureRandom rng = new SecureRandom();
        int code = 100000 + rng.nextInt(900000); // always 6 digits
        return String.valueOf(code);
    }
}
