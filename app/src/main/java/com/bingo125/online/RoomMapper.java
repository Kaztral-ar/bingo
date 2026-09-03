package com.bingo125.online;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.GenericTypeIndicator;

import java.util.HashMap;

/** Converts a raw Firebase DataSnapshot into the typed RoomModel used by the UI. */
class RoomMapper {

    static RoomModel fromSnapshot(String roomCode, DataSnapshot snapshot) {
        RoomModel room = new RoomModel();
        room.roomCode = roomCode;
        room.host = snapshot.child("host").getValue(String.class);
        String status = snapshot.child("status").getValue(String.class);
        room.status = status != null ? status : "waiting";
        room.fillDeadline = snapshot.child("fillDeadline").getValue(Long.class);
        room.currentIndex = snapshot.child("currentIndex").getValue(Integer.class);
        room.winnerUid = snapshot.child("winnerUid").getValue(String.class);
        room.winningPattern = snapshot.child("winningPattern").getValue(String.class);

        GenericTypeIndicator<java.util.List<Integer>> intListType =
                new GenericTypeIndicator<java.util.List<Integer>>() {};
        room.calledNumbers = snapshot.child("calledNumbers").getValue(intListType);

        for (DataSnapshot playerSnap : snapshot.child("players").getChildren()) {
            RoomModel.PlayerModel p = new RoomModel.PlayerModel();
            p.name = playerSnap.child("name").getValue(String.class);
            Boolean ready = playerSnap.child("ready").getValue(Boolean.class);
            p.ready = ready != null && ready;
            Boolean locked = playerSnap.child("cardLocked").getValue(Boolean.class);
            p.cardLocked = locked != null && locked;
            p.card = readIntGrid(playerSnap.child("card"));
            p.marked = readBoolGrid(playerSnap.child("marked"));
            room.players.put(playerSnap.getKey(), p);
        }
        return room;
    }

    private static int[][] readIntGrid(DataSnapshot snap) {
        if (!snap.exists()) return null;
        int[][] grid = new int[5][5];
        int r = 0;
        for (DataSnapshot rowSnap : snap.getChildren()) {
            int c = 0;
            for (DataSnapshot cellSnap : rowSnap.getChildren()) {
                Long v = cellSnap.getValue(Long.class);
                grid[r][c] = v != null ? v.intValue() : 0;
                c++;
            }
            r++;
        }
        return grid;
    }

    private static boolean[][] readBoolGrid(DataSnapshot snap) {
        if (!snap.exists()) return new boolean[5][5];
        boolean[][] grid = new boolean[5][5];
        int r = 0;
        for (DataSnapshot rowSnap : snap.getChildren()) {
            int c = 0;
            for (DataSnapshot cellSnap : rowSnap.getChildren()) {
                Boolean v = cellSnap.getValue(Boolean.class);
                grid[r][c] = v != null && v;
                c++;
            }
            r++;
        }
        return grid;
    }
}
