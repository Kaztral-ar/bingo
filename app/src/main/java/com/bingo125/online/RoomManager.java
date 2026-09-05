package com.bingo125.online;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Queue;

/** Supabase-backed room service. */
public class RoomManager {
    public interface RoomListener{void onRoomUpdated(RoomModel room);void onError(String message);}
    private final Handler main=new Handler(Looper.getMainLooper()); private Context context; private String activeRoom; private RoomListener listener; private boolean listening;
    private final Object cardLock=new Object(); private final Queue<CardWrite> cardQueue=new ArrayDeque<>(); private boolean cardWriteRunning;
    private static class CardWrite{final String code,uid;final int[][] card;final boolean locked;CardWrite(String code,String uid,int[][] card,boolean locked){this.code=code;this.uid=uid;this.card=copy(card);this.locked=locked;}}
    public void init(Context c){context=c.getApplicationContext();}
    private void send(JSONObject b,SupabaseManager.Callback cb){if(context==null){if(cb!=null)cb.error("Supabase client is not initialized.");return;}SupabaseManager.function(context,b,cb);}
    private void reportError(String prefix,String message){if(listener!=null)main.post(()->listener.onError(prefix+(message==null||message.isEmpty()?"Unknown error":message)));}
    public void createRoom(String uid,String name,RoomListener cb){try{JSONObject b=new JSONObject();b.put("action","create");b.put("name",name==null?"Player":name);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){String code=r.optString("roomCode","");if(code.isEmpty()){main.post(()->cb.onError("Create room failed: server returned no room code."));return;}listenToRoom(code,cb);}public void error(String m){main.post(()->cb.onError("Create room failed: "+m));}});}catch(Exception e){cb.onError("Create room failed: "+e.getMessage());}}
    public void joinRoom(String code,String uid,String name,RoomListener cb){try{JSONObject b=new JSONObject();b.put("action","join");b.put("roomCode",code);b.put("name",name==null?"Player":name);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){listenToRoom(code,cb);}public void error(String m){main.post(()->cb.onError("Join room failed: "+m));}});}catch(Exception e){cb.onError("Join room failed: "+e.getMessage());}}
    public void startGame(String code){try{JSONObject b=new JSONObject();b.put("action","start");b.put("roomCode",code);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){}public void error(String m){reportError("Start game failed: ",m);}});}catch(Exception e){reportError("Start game failed: ",e.getMessage());}}
    public void saveCard(String code,String uid,int[][] card,boolean locked){if(context==null||card==null){reportError("Card save failed: ","missing client context or card.");return;}synchronized(cardLock){cardQueue.add(new CardWrite(code,uid,card,locked));if(!cardWriteRunning){cardWriteRunning=true;writeNextCard();}}}
    private void writeNextCard(){final CardWrite w;synchronized(cardLock){w=cardQueue.poll();if(w==null){cardWriteRunning=false;return;}}try{JSONObject b=new JSONObject();b.put("action","saveCard");b.put("roomCode",w.code);b.put("card",grid(w.card));b.put("locked",w.locked);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){writeNextCard();}public void error(String m){reportError("Card save failed: ",m);writeNextCard();}});}catch(Exception e){reportError("Card save failed: ",e.getMessage());writeNextCard();}}
    public void placeNumber(String code,String uid,int row,int col,int number,int[][] card){saveCard(code,uid,card,complete(card));}
    public void lockCard(String code,String uid){}
    public void requestCalling(String code,String uid){}
    public void startCalling(String code,String firstCaller){try{JSONObject b=new JSONObject();b.put("action","startCalling");b.put("roomCode",code);b.put("firstCaller",firstCaller);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){}public void error(String m){reportError("Starting calls failed: ",m);}});}catch(Exception e){reportError("Starting calls failed: ",e.getMessage());}}
    public void requestNextNumber(String code,String uid){callNumber(code,0,new SupabaseManager.Callback(){public void success(JSONObject r){}public void error(String m){reportError("Calling number failed: ",m);}});}
    public void callNumber(String code,SupabaseManager.Callback cb){callNumber(code,0,cb);}
    public void callNumber(String code,int number,SupabaseManager.Callback cb){try{JSONObject b=new JSONObject();b.put("action","call");b.put("roomCode",code);b.put("number",number);send(b,cb);}catch(Exception e){if(cb!=null)cb.error(e.getMessage());else reportError("Calling number failed: ",e.getMessage());}}
    public void listenToRoom(String code,RoomListener cb){stopListening();activeRoom=code;listener=cb;listening=true;poll();}
    private void poll(){if(!listening)return;final String room=activeRoom;try{JSONObject b=new JSONObject();b.put("action","getRoom");b.put("roomCode",room);send(b,new SupabaseManager.Callback(){public void success(JSONObject r){try{RoomModel parsed=SupabaseManager.parseRoom(r);main.post(()->{if(listening&&listener!=null)listener.onRoomUpdated(parsed);});}catch(Exception e){reportError("Room data error: ",e.getMessage());}if(listening)main.postDelayed(()->poll(),700);}public void error(String m){reportError("Room sync failed: ",m);if(listening)main.postDelayed(()->poll(),1200);}});}catch(Exception e){reportError("Room sync failed: ",e.getMessage());if(listening)main.postDelayed(()->poll(),1200);}}
    public void stopListening(){listening=false;activeRoom=null;listener=null;}
    private JSONArray grid(int[][] a){JSONArray out=new JSONArray();for(int r=0;r<5;r++){JSONArray row=new JSONArray();for(int c=0;c<5;c++)row.put(a[r][c]);out.put(row);}return out;}
    private boolean complete(int[][] a){for(int r=0;r<5;r++)for(int c=0;c<5;c++)if(a[r][c]==0)return false;return true;}
    private static int[][] copy(int[][] a){int[][] out=new int[5][5];for(int r=0;r<5;r++)System.arraycopy(a[r],0,out[r],0,5);return out;}
}
