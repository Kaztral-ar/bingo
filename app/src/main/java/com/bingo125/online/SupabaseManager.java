package com.bingo125.online;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** REST client for Supabase Auth and the Bingo Edge Function. */
public final class SupabaseManager {
    public static final String SUPABASE_URL = "https://dgcphhplyujadgjkofgk.supabase.co";
    public static final String PUBLISHABLE_KEY = "sb_publishable_YkJhlRikv-yYGw0RGG8WUA_jR26IVk6";
    private static final String PREFS = "supabase_session";
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    public interface Callback { void success(JSONObject value); void error(String message); }
    private SupabaseManager() {}
    public static String getUid(Context c){return c.getSharedPreferences(PREFS,0).getString("uid",null);}
    public static String getToken(Context c){return c.getSharedPreferences(PREFS,0).getString("access_token",null);}
    public static void anonymousSignIn(Context c, Callback cb){IO.execute(()->{try{JSONObject r=request("POST","/auth/v1/signup",new JSONObject(),null);String t=r.optString("access_token",null);JSONObject u=r.optJSONObject("user");String id=u==null?null:u.optString("id",null);if(t==null||id==null)throw new Exception("Supabase anonymous sign-in failed. Enable Anonymous Sign-ins in Supabase Auth.");c.getSharedPreferences(PREFS,0).edit().putString("access_token",t).putString("uid",id).apply();cb.success(r);}catch(Exception e){cb.error(msg(e));}});}
    public static void function(Context c, JSONObject body, Callback cb){IO.execute(()->{try{String t=getToken(c);if(t==null)throw new Exception("Not signed in to Supabase.");JSONObject r=request("POST","/functions/v1/bingo-server",body,t);if(r.has("error"))throw new Exception(r.optString("error"));cb.success(r);}catch(Exception e){cb.error(msg(e));}});}
    private static String msg(Exception e){return e.getMessage()==null?"Supabase request failed.":e.getMessage();}
    private static JSONObject request(String method,String path,JSONObject body,String token)throws Exception{HttpURLConnection con=(HttpURLConnection)new URL(SUPABASE_URL+path).openConnection();con.setRequestMethod(method);con.setConnectTimeout(10000);con.setReadTimeout(10000);con.setRequestProperty("apikey",PUBLISHABLE_KEY);con.setRequestProperty("Content-Type","application/json");con.setRequestProperty("Accept","application/json");if(token!=null)con.setRequestProperty("Authorization","Bearer "+token);if(body!=null){con.setDoOutput(true);try(OutputStream o=con.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}int code=con.getResponseCode();BufferedReader rd=new BufferedReader(new InputStreamReader(code>=200&&code<300?con.getInputStream():con.getErrorStream(),StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String line;while((line=rd.readLine())!=null)s.append(line);String raw=s.toString();if(code<200||code>=300){try{throw new Exception(new JSONObject(raw).optString("message",raw));}catch(org.json.JSONException x){throw new Exception(raw.isEmpty()?"HTTP "+code:raw);}}return raw.isEmpty()?new JSONObject():new JSONObject(raw);}
    public static RoomModel parseRoom(JSONObject root)throws Exception{JSONObject r=root.getJSONObject("room");RoomModel m=new RoomModel();m.roomCode=r.optString("room_code");m.host=r.optString("host",null);m.status=r.optString("status","waiting");m.fillDeadline=r.isNull("fill_deadline")?null:Instant.parse(r.optString("fill_deadline")).toEpochMilli();m.currentIndex=r.optInt("current_index",0);m.winnerUid=r.optString("winner_uid",null);m.winningPattern=r.optString("winning_pattern",null);m.firstCaller=r.optString("first_caller",null);m.calledNumbers=new java.util.ArrayList<>();JSONArray ca=r.optJSONArray("called_numbers");if(ca!=null)for(int i=0;i<ca.length();i++)m.calledNumbers.add(ca.optInt(i));JSONArray ps=root.optJSONArray("players");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);RoomModel.PlayerModel q=new RoomModel.PlayerModel();String uid=p.optString("uid");q.name=p.optString("name","Player");q.ready=p.optBoolean("ready",true);q.cardLocked=p.optBoolean("card_locked",false);q.card=readIntGrid(p.optJSONArray("card"));q.marked=readBoolGrid(p.optJSONArray("marked"));m.players.put(uid,q);}return m;}
    private static int[][] readIntGrid(JSONArray a)throws Exception{if(a==null)return null;int[][] g=new int[5][5];for(int r=0;r<5;r++)for(int c=0;c<5;c++)g[r][c]=a.optJSONArray(r).optInt(c);return g;}
    private static boolean[][] readBoolGrid(JSONArray a)throws Exception{if(a==null)return new boolean[5][5];boolean[][] g=new boolean[5][5];for(int r=0;r<5;r++)for(int c=0;c<5;c++)g[r][c]=a.optJSONArray(r).optBoolean(c);return g;}
}
