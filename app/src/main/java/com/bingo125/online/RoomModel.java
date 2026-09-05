package com.bingo125.online;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain data holder mirroring a room's shape in Firebase. */
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
        public int[][] card;
        public boolean[][] marked;       // server-authoritative marks from called numbers
        public List<String> completedLines; // R1-R5, C1-C5, D1-D2
        public int bingoCount;            // 0..5 = B-I-N-G-O progress
    }
}
