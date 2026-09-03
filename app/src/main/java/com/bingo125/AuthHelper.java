package com.bingo125;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

/** Anonymous auth is enough here: rooms are joined by a shareable 6-digit code,
 *  not by identity, but every write still needs a stable uid for the security
 *  rules (see /database.rules.json) to attribute ownership correctly. */
public class AuthHelper {

    public interface UidCallback {
        void onReady(String uid);
        void onError(String message);
    }

    public static void ensureSignedIn(UidCallback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            callback.onReady(auth.getCurrentUser().getUid());
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(Task<AuthResult> task) {
                if (task.isSuccessful() && auth.getCurrentUser() != null) {
                    callback.onReady(auth.getCurrentUser().getUid());
                } else {
                    callback.onError("Could not connect. Check your internet connection.");
                }
            }
        });
    }
}
