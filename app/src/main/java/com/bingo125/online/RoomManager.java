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

/** Client-side room operations. Firebase Functions remain authoritative for calls and winners. */
public class RoomManager {

    public interface RoomListener {
        void onRoomUpdated(RoomModel room);
        void onError(String message);
    }

    private final FirebaseManager firebase = FirebaseManager.getInstance();
    private ValueEventListener activeListener;
    private String activeRoomCode;

    public void createRoom(String uid, String displayName, RoomListener callback) {
        createRoomAttempt(uid, displayName, callback, 0);
    }

    private void createRoomAttempt(String uid, String displayName, RoomListener callback, int attempt) {
        if (attempt >= 5) {
            callback.onError("Could not create a unique room. Please try again.");
            return;
        }
        String roomCode = generateRoomCode();
        DatabaseReference roomRef = firebase.roomRef(roomCode);
        roomRef.get().addOnSuccessListener(existing -> {
            if (existing.exists()) {
                createRoomAttempt(uid, displayName, callback, attempt + 1);
                return;
            }
            Map<String, Object> room = new HashMap<>();
            room.put("host", uid);
            room.put("status", "waiting");
            room.put("createdAt", ServerValue.TIMESTAMP);

            Map<String, Object> player = new HashMap<>();
            player.put("name", displayName == null ? "Player" : displayName);
            player.put("ready", true);
            player.put("cardLocked", false);

            Map<String, Object> players = new HashMap<>();
            players.put(uid, player);
            room.put("players", players);

            roomRef.setValue(room)
                    .addOnSuccessListener(v -> listenToRoom(roomCode, callback))
                    .addOnFailureListener(e -> callback.onError("Could not create room: " + e.getMessage()));
        }).addOnFailureListener(e -> callback.onError("Network error: " + e.getMessage()));
    }

    public void joinRoom(String roomCode, String uid, String displayName, RoomListener callback) {
        if (roomCode == null || !roomCode.matches("\\d{6}")) {
            callback.onError("Enter a valid 6-digit room code.");
            return;
        }
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
            player.put("name", displayName == null ? "Player" : displayName);
            player.put("ready", true);
            player.put("cardLocked", false);
            roomRef.child("players").child(uid).setValue(player)
                    .addOnSuccessListener(v -> listenToRoom(roomCode, callback))
                    .addOnFailureListener(e -> callback.onError("Could not join room: " + e.getMessage()));
        }).addOnFailureListener(e -> callback.onError("Network error: " + e.getMessage()));
    }

    /** Host-only request. The backend changes waiting -> filling and creates the deadline. */
    public void startGame(String roomCode) {
        firebase.roomRef(roomCode).child("status").setValue("filling");
    }

    /** Ask the backend to move to calling immediately when the 2-minute fill timer expires. */
    public void requestCalling(String roomCode, String uid) {
        firebase.roomRef(roomCode).child("startCallingRequests").child(uid).setValue(ServerValue.TIMESTAMP);
    }

    public void placeNumber(String roomCode, String uid, int row, int col, int number, int[][] fullCardSoFar) {
        firebase.playerRef(roomCode, uid).child("card").setValue(fullCardSoFar);
    }

    public void lockCard(String roomCode, String uid) {
        firebase.playerRef(roomCode, uid).child("cardLocked").setValue(true);
    }

    public void markNumber(String roomCode, String uid, boolean[][] fullMarkedGrid) {
        firebase.playerRef(roomCode, uid).child("marked").setValue(fullMarkedGrid);
    }

    public void claimBingo(String roomCode, String uid) {
        firebase.roomRef(roomCode).child("claims").child(uid).setValue(ServerValue.TIMESTAMP);
    }

    /** Host-only real-time request; the backend appends the next authoritative number. */
    public void requestNextNumber(String roomCode, String uid) {
        firebase.roomRef(roomCode).child("callRequests").child(uid).setValue(ServerValue.TIMESTAMP);
    }

    public void listenToRoom(String roomCode, RoomListener callback) {
        stopListening();
        activeRoomCode = roomCode;
        activeListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onError("Room no longer exists.");
                    return;
                }
                RoomModel room = RoomMapper.fromSnapshot(roomCode, snapshot);
                callback.onRoomUpdated(room);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
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
        return String.valueOf(100000 + rng.nextInt(900000));
    }
}
