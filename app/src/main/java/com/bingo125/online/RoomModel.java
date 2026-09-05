package com.bingo125.online;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Supabase room model used by the online game. */
public class RoomModel {
    public String roomCode;
    public String host;
    public String status = "waiting";
    public Long fillDeadline;
    public List<Integer> calledNumbers;
    public Integer currentIndex;
    public String winnerUid;
    public String winningPattern;
    public String firstCaller;
    public Map<String, PlayerModel> players = new HashMap<>();

    public static class PlayerModel {
        public String name;
        public boolean ready;
        public boolean cardLocked;
        public int[][] card;
        public boolean[][] marked;
        public List<String> completedLines;
        public int bingoCount;
    }
}
