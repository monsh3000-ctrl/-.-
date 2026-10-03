package com.example.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated Google AdMob Manager initialized strictly once from Application.onCreate().
 * Manages official Rewarded and Rewarded Interstitial advertisements with real callbacks.
 */
object AdMobManager {
    private const val TAG = "AdMobManager"

    // Official Google AdMob Test Ad Unit IDs (Android)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"

    private val isInitialized = AtomicBoolean(false)
    private val isRewardedLoading = AtomicBoolean(false)
    private val isRewardedInterstitialLoading = AtomicBoolean(false)

    private var cachedRewardedAd: RewardedAd? = null
    private var cachedRewardedInterstitialAd: RewardedInterstitialAd? = null

    /**
     * Initializes Google Mobile Ads SDK strictly once at application launch.
     */
    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                // Configure test devices for emulator development
                val requestConfiguration = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(requestConfiguration)

                MobileAds.initialize(context.applicationContext) { status ->
                    Log.i(TAG, "AdMob MobileAds initialized successfully: ${status.adapterStatusMap.keys}")
                    // Preload first ad
                    preloadRewardedAd(context.applicationContext)
                    preloadRewardedInterstitialAd(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AdMob initialization failed or deferred: ${e.message}")
            }
        }
    }

    fun preloadRewardedAd(context: Context) {
        if (cachedRewardedAd != null || isRewardedLoading.get()) return
        isRewardedLoading.set(true)

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            TEST_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    cachedRewardedAd = ad
                    isRewardedLoading.set(false)
                    Log.d(TAG, "RewardedAd preloaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    cachedRewardedAd = null
                    isRewardedLoading.set(false)
                    Log.w(TAG, "RewardedAd failed to preload: ${error.message}")
                }
            }
        )
    }

    fun preloadRewardedInterstitialAd(context: Context) {
        if (cachedRewardedInterstitialAd != null || isRewardedInterstitialLoading.get()) return
        isRewardedInterstitialLoading.set(true)

        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(
            context,
            TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    cachedRewardedInterstitialAd = ad
                    isRewardedInterstitialLoading.set(false)
                    Log.d(TAG, "RewardedInterstitialAd preloaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    cachedRewardedInterstitialAd = null
                    isRewardedInterstitialLoading.set(false)
                    Log.w(TAG, "RewardedInterstitialAd failed to preload: ${error.message}")
                }
            }
        )
    }

    fun isRewardedAdReady(): Boolean = cachedRewardedAd != null
    fun isRewardedInterstitialAdReady(): Boolean = cachedRewardedInterstitialAd != null

    /**
     * Displays real Rewarded Ad from a valid Activity.
     * Guaranteed: onRewardEarned is ONLY called when the user actually watched and earned the reward.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (RewardItem) -> Unit,
        onAdDismissed: () -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        val ad = cachedRewardedAd
        if (ad == null) {
            onAdFailed("Rewarded ad unavailable right now. You can continue studying.")
            preloadRewardedAd(activity.applicationContext)
            return
        }

        cachedRewardedAd = null
        var userEarnedReward = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                onAdDismissed()
                preloadRewardedAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                onAdFailed(adError.message)
                preloadRewardedAd(activity.applicationContext)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad opened fullscreen")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.i(TAG, "User earned real AdMob reward: ${rewardItem.amount} ${rewardItem.type}")
            userEarnedReward = true
            onRewardEarned(rewardItem)
        }
    }

    /**
     * Displays real Rewarded Interstitial Ad from a valid Activity.
     */
    fun showRewardedInterstitialAd(
        activity: Activity,
        onRewardEarned: (RewardItem) -> Unit,
        onAdDismissed: () -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        val ad = cachedRewardedInterstitialAd
        if (ad == null) {
            onAdFailed("Rewarded interstitial ad unavailable right now.")
            preloadRewardedInterstitialAd(activity.applicationContext)
            return
        }

        cachedRewardedInterstitialAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial dismissed")
                onAdDismissed()
                preloadRewardedInterstitialAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Rewarded interstitial failed to show: ${adError.message}")
                onAdFailed(adError.message)
                preloadRewardedInterstitialAd(activity.applicationContext)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial showed fullscreen")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.i(TAG, "User legitimately earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewardEarned(rewardItem)
        }
    }
}
