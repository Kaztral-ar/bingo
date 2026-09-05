package com.bingo125.online;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/** Supabase-backed room service. Polling is used for broad Android compatibility. */
public class RoomManager {
    public interface RoomListener { void onRoomUpdated(RoomModel room); void onError(String message); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private Context context;
    private String activeRoom;
    private RoomListener listener;
    private boolean listening;

    public void init(Context c){ context=c.getApplicationContext(); }

    public void createRoom(String uid,String name,RoomListener cb){
        JSONObject b=new JSONObject();try{b.put("action","create");b.put("name",name==null?"Player":name);}catch(Exception ignored){}
        SupabaseManager.function(context,b,new SupabaseManager.Callback(){public void success(JSONObject r){listenToRoom(r.optString("roomCode"),cb);}public void error(String m){main.post(()->cb.onError(m));}});
    }
    public void joinRoom(String code,String uid,String name,RoomListener cb){
        if(code==null||!code.matches("\\d{6}")){cb.onError("Enter a valid 6-digit room code.");return;}
        JSONObject b=new JSONObject();try{b.put("action","join");b.put("roomCode",code);b.put("name",name==null?"Player":name);}catch(Exception ignored){}
        SupabaseManager.function(context,b,new SupabaseManager.Callback(){public void success(JSONObject r){listenToRoom(code,cb);}public void error(String m){main.post(()->cb.onError(m));}});
    }
    public void startGame(String code){call(code,"start",null,null);}
    public void saveCard(String code,String uid,int[][] card,boolean locked){JSONObject b=new JSONObject();try{b.put("action","saveCard");b.put("roomCode",code);b.put("card",new org.json.JSONArray(card));b.put("locked",locked);}catch(Exception ignored){}send(b,null);}
    public void startCalling(String code,String firstCaller){JSONObject b=new JSONObject();try{b.put("action","startCalling");b.put("roomCode",code);b.put("firstCaller",firstCaller);}catch(Exception ignored){}send(b,null);}
    public void callNumber(String code,RoomListener cb){JSONObject b=new JSONObject();try{b.put("action","call");b.put("roomCode",code);}catch(Exception ignored){}send(b,new SupabaseManager.Callback(){public void success(JSONObject r){if(cb!=null)main.post(()->cb.onRoomUpdated(null));}public void error(String m){if(cb!=null)main.post(()->cb.onError(m));}});}
    private void call(String code,String action,String unused,Object x){JSONObject b=new JSONObject();try{b.put("action",action);b.put("roomCode",code);}catch(Exception ignored){}send(b,null);}
    private void send(JSONObject b,SupabaseManager.Callback cb){SupabaseManager.function(context,b,new SupabaseManager.Callback(){public void success(JSONObject r){if(cb!=null)cb.success(r);}public void error(String m){if(cb!=null)cb.error(m);}});}

    public void listenToRoom(String code,RoomListener cb){
        stopListening(); activeRoom=code; listener=cb; listening=true; poll();
    }
    private void poll(){if(!listening)return;JSONObject b=new JSONObject();try{b.put("action","getRoom");b.put("roomCode",activeRoom);}catch(Exception ignored){}SupabaseManager.function(context,b,new SupabaseManager.Callback(){public void success(JSONObject r){try{RoomModel room=SupabaseManager.parseRoom(r);main.post(()->{if(listening&&listener!=null)listener.onRoomUpdated(room);});}catch(Exception e){main.post(()->listener.onError("Could not read game room."));}if(listening)main.postDelayed(()->poll(),700);}public void error(String m){if(listening)main.postDelayed(()->poll(),1200);}});}
    public void stopListening(){listening=false;activeRoom=null;listener=null;}
}
