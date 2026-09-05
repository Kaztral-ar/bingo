package com.bingo125.util;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Wraps Google Mobile Ads: banner on menu/result screens, interstitial between
 * completed games, optional rewarded ad for a non-essential bonus.
 *
 * Banner uses the AdMob banner unit supplied for this app. Interstitial and
 * rewarded units remain Google's test IDs until production units are supplied.
 * Add the app's real AdMob APPLICATION_ID to AndroidManifest.xml before release.
 */
public class AdManager {

    // AdMob banner unit supplied by the app owner.
    public static final String BANNER_AD_UNIT_ID = "ca-app-pub-6906509746436244/2220867988";

    // Google test ad unit IDs (safe for development).
    public static final String INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917";

    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;

    public static void init(Context context) {
        MobileAds.initialize(context, initializationStatus -> { /* no-op */ });
    }

    public void attachBanner(Activity activity, FrameLayout container) {
        AdView adView = new AdView(activity);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(BANNER_AD_UNIT_ID);
        container.removeAllViews();
        container.addView(adView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        adView.loadAd(new AdRequest.Builder().build());
    }

    public void loadInterstitial(Context context) {
        InterstitialAd.load(context, INTERSTITIAL_TEST_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                    }
                });
    }

    /** Call between completed games — never during filling, calling, or active multiplayer. */
    public void showInterstitialIfReady(Activity activity) {
        if (interstitialAd != null) {
            interstitialAd.show(activity);
            interstitialAd = null;
        }
    }

    public void loadRewarded(Context context) {
        RewardedAd.load(context, REWARDED_TEST_ID, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                    }
                });
    }

    public boolean isRewardedReady() {
        return rewardedAd != null;
    }

    public void showRewarded(Activity activity, com.google.android.gms.ads.OnUserEarnedRewardListener listener) {
        if (rewardedAd != null) {
            rewardedAd.show(activity, listener);
            rewardedAd = null;
        }
    }
}
