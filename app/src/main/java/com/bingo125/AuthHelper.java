package com.bingo125;

import android.content.Context;
import com.bingo125.online.SupabaseManager;

/** Supabase anonymous authentication helper. */
public class AuthHelper {
    public interface UidCallback { void onReady(String uid); void onError(String message); }
    public static void ensureSignedIn(Context context, UidCallback callback) {
        String uid=SupabaseManager.getUid(context);
        String token=SupabaseManager.getToken(context);
        if(uid!=null&&token!=null){callback.onReady(uid);return;}
        SupabaseManager.anonymousSignIn(context,new SupabaseManager.Callback(){
            public void success(org.json.JSONObject value){callback.onReady(SupabaseManager.getUid(context));}
            public void error(String message){callback.onError(message);}
        });
    }
}
