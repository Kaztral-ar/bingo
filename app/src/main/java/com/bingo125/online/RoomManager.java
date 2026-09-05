package com.bingo125.online;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

/** Supabase-backed room service. */
public class RoomManager {
    public interface RoomListener { void onRoomUpdated(RoomModel room); void onError(String message); }
    private final Handler main=new Handler(Looper.getMainLooper()); private Context context; private String activeRoom; private RoomListener listener; private boolean listening;
    public void init(Context c){context=c.getApplicationContext();}
    private void send(JSONObject b,SupabaseManager.Callback cb){SupabaseManager.function(context,b,cb);}
    public void createRoom(String uid,String name,RoomListener cb){try{JSONObject b=new JSONObject();b.put("action","create");b.put("name",name==null?"Player":name);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){listenToRoom(r.optString("roomCode"),cb);}public void error(String m){main.post(()->cb.onError(m));}});}catch(Exception e){cb.onError(e.getMessage());}}
    public void joinRoom(String code,String uid,String name,RoomListener cb){try{JSONObject b=new JSONObject();b.put("action","join");b.put("roomCode",code);b.put("name",name==null?"Player":name);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){listenToRoom(code,cb);}public void error(String m){main.post(()->cb.onError(m));}});}catch(Exception e){cb.onError(e.getMessage());}}
    public void startGame(String code){try{JSONObject b=new JSONObject();b.put("action","start");b.put("roomCode",code);send(b,null);}catch(Exception ignored){}}
    public void saveCard(String code,String uid,int[][] card,boolean locked){try{JSONObject b=new JSONObject();b.put("action","saveCard");b.put("roomCode",code);b.put("card",grid(card));b.put("locked",locked);send(b,null);}catch(Exception ignored){}}
    public void placeNumber(String code,String uid,int row,int col,int number,int[][] card){saveCard(code,uid,card,false);}
    public void lockCard(String code,String uid){/* saveCard(..., true) is called after the final placement */}
    public void requestCalling(String code,String uid){/* first caller is selected by the host dialog */}
    public void startCalling(String code,String firstCaller){try{JSONObject b=new JSONObject();b.put("action","startCalling");b.put("roomCode",code);b.put("firstCaller",firstCaller);send(b,null);}catch(Exception ignored){}}
    public void requestNextNumber(String code,String uid){try{JSONObject b=new JSONObject();b.put("action","call");b.put("roomCode",code);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){}public void error(String m){}});}catch(Exception ignored){}}
    public void callNumber(String code,SupabaseManager.Callback cb){try{JSONObject b=new JSONObject();b.put("action","call");b.put("roomCode",code);send(b,cb);}catch(Exception e){if(cb!=null)cb.error(e.getMessage());}}
    public void listenToRoom(String code,RoomListener cb){stopListening();activeRoom=code;listener=cb;listening=true;poll();}
    private void poll(){if(!listening)return;try{JSONObject b=new JSONObject();b.put("action","getRoom");b.put("roomCode",activeRoom);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){try{RoomModel room=SupabaseManager.parseRoom(r);main.post(()->{if(listening&&listener!=null)listener.onRoomUpdated(room);});}catch(Exception e){main.post(()->{if(listener!=null)listener.onError("Could not read game room.");});}if(listening)main.postDelayed(()->poll(),700);}public void error(String m){if(listening)main.postDelayed(()->poll(),1200);}});}catch(Exception ignored){if(listening)main.postDelayed(()->poll(),1200);}}
    public void stopListening(){listening=false;activeRoom=null;listener=null;}
    private JSONArray grid(int[][] a){JSONArray out=new JSONArray();for(int r=0;r<5;r++){JSONArray row=new JSONArray();for(int c=0;c<5;c++)row.put(a[r][c]);out.put(row);}return out;}
}
