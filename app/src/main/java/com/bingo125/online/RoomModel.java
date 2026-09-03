package com.bingo125.online;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain data holder mirroring a room's shape in Firebase (see FirebaseManager). */
public class RoomModel {
    public String roomCode;
    public String host;
    public String status = "waiting"; // waiting | filling | calling | finished
    public Long fillDeadline;
    public List<Integer> calledNumbers;
    public Integer currentIndex;
    public String winnerUid;
    public String winningPattern;
    public Map<String, PlayerModel> players = new HashMap<>();

    public static class PlayerModel {
        public String name;
        public boolean ready;
        public boolean cardLocked;
        public int[][] card;     // null until the player has placed at least one number
        public boolean[][] marked;
    }
}
