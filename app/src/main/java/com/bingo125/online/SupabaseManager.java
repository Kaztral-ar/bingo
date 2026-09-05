package com.bingo125.online;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small Java REST client for the Supabase project used by online Bingo. */
public final class SupabaseManager {
    public static final String SUPABASE_URL = "https://dgcphhplyujadgjkofgk.supabase.co";
    public static final String PUBLISHABLE_KEY = "sb_publishable_YkJhlRikv-yYGw0RGG8WUA_jR26IVk6";
    private static final String PREFS = "supabase_session";
    private static final String TOKEN = "access_token";
    private static final String UID = "uid";
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    public interface Callback { void success(JSONObject value); void error(String message); }

    private SupabaseManager() {}

    public static String getUid(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(UID, null); }
    public static String getToken(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null); }

    public static void anonymousSignIn(Context context, Callback callback) {
        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject result = request("POST", "/auth/v1/signup", body, null);
                String token = result.optString("access_token", null);
                JSONObject user = result.optJSONObject("user");
                String uid = user == null ? null : user.optString("id", null);
                if (token == null || uid == null) throw new Exception("Supabase anonymous sign-in failed. Enable Anonymous Sign-ins in Supabase Auth.");
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN, token).putString(UID, uid).apply();
                callback.success(result);
            } catch (Exception e) { callback.error(e.getMessage() == null ? "Supabase connection failed." : e.getMessage()); }
        });
    }

    public static void function(Context context, JSONObject body, Callback callback) {
        IO.execute(() -> {
            try {
                String token = getToken(context);
                if (token == null) throw new Exception("Not signed in to Supabase.");
                JSONObject result = request("POST", "/functions/v1/bingo-server", body, token);
                if (result.has("error") && !result.optString("error").isEmpty()) throw new Exception(result.optString("error"));
                callback.success(result);
            } catch (Exception e) { callback.error(e.getMessage() == null ? "Supabase request failed." : e.getMessage()); }
        });
    }

    private static JSONObject request(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(SUPABASE_URL + path).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("apikey", PUBLISHABLE_KEY);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            con.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = con.getOutputStream()) { out.write(bytes); }
        }
        int code = con.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder(); String line;
        while ((line = reader.readLine()) != null) text.append(line);
        String raw = text.toString();
        if (code < 200 || code >= 300) {
            try { throw new Exception(new JSONObject(raw).optString("message", raw)); }
            catch (org.json.JSONException ignored) { throw new Exception(raw.isEmpty() ? "HTTP " + code : raw); }
        }
        return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    public static RoomModel roomFromFunction(JSONObject root) throws Exception {
        JSONObject r = root.getJSONObject("room");
        RoomModel room = new RoomModel();
        room.roomCode = r.optString("room_code");
        room.host = r.optString("host", null);
        room.status = r.optString("status", "waiting");
        room.fillDeadline = r.isNull("fill_deadline") ? null : parseTime(r.optString("fill_deadline"));
        room.currentIndex = r.optInt("current_index", 0);
        room.winnerUid = r.optString("winner_uid", null);
        room.winningPattern = r.optString("winning_pattern", null);
        room.firstCaller = r.optString("first_caller", null);
        JSONArray called = r.optJSONArray("called_numbers");
        room.calledNumbers = new java.util.ArrayList<>();
        if (called != null) for (int i=0;i<called.length();i++) room.calledNumbers.add(called.optInt(i));
        JSONArray players = root.optJSONArray("players");
        if (players != null) for (int i=0;i<players.length();i++) {
            JSONObject p = players.getJSONObject(i); RoomModel.PlayerModel pm = new RoomModel.PlayerModel();
            String uid = p.optString("uid"); pm.name=p.optString("name","Player"); pm.ready=p.optBoolean("ready",true); pm.cardLocked=p.optBoolean("card_locked",false);
            pm.card=jsonGrid(p.optJSONArray("card"),false); pm.marked=jsonGrid(p.optJSONArray("marked"),true); room.players.put(uid,pm);
        }
        return room;
    }
    private static Long parseTime(String iso) { try { return javax.xml.bind.DatatypeConverter.parseDateTime(iso).getTimeInMillis(); } catch(Exception e) { try { return java.time.Instant.parse(iso).toEpochMilli(); } catch(Exception ignored) { return null; } } }
    private static int[][] jsonGrid(JSONArray a, boolean bool) throws Exception { if(a==null)return null; int[][] out=new int[5][5]; for(int r=0;r<5;r++)for(int c=0;c<5;c++)out[r][c]=a.optJSONArray(r).optInt(c); return out; }
}
