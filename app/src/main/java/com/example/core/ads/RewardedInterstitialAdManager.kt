package com.example.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages loading and displaying Google Mobile Ads Rewarded Interstitial Ads
 * to incentivize students after completing deep-work study sessions.
 */
class RewardedInterstitialAdManager(private val context: Context) {

    companion object {
        private const val TAG = "RewardedInterstitialAd"
        // Official Google AdMob Test Rewarded Interstitial Ad Unit for Android
        const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    }

    private val creatorAdSenseManager = CreatorAdSenseManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        loadAd()
    }

    fun loadAd() {
        if (_isLoading.value || _isAdLoaded.value) return

        _isLoading.value = true
        _lastError.value = null

        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(
            context,
            TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d(TAG, "Rewarded interstitial ad loaded successfully.")
                    rewardedInterstitialAd = ad
                    _isAdLoaded.value = true
                    _isLoading.value = false
                    _lastError.value = null
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val errorMsg = "Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})"
                    Log.w(TAG, errorMsg)
                    rewardedInterstitialAd = null
                    _isAdLoaded.value = false
                    _isLoading.value = false
                    _lastError.value = errorMsg
                }
            }
        )
    }

    fun showAd(
        activity: Activity,
        onUserEarnedReward: (RewardItem?) -> Unit,
        onAdDismissed: () -> Unit
    ): Boolean {
        val ad = rewardedInterstitialAd
        if (ad == null) {
            Log.w(TAG, "Ad not ready to show. Re-requesting ad.")
            loadAd()
            return false
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial ad dismissed by user.")
                rewardedInterstitialAd = null
                _isAdLoaded.value = false
                onAdDismissed()
                // Preload next ad for the next study session
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                rewardedInterstitialAd = null
                _isAdLoaded.value = false
                _lastError.value = adError.message
                onAdDismissed()
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial ad opened full screen.")
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded interstitial impression logged.")
                scope.launch {
                    creatorAdSenseManager.recordRewardedInterstitialImpression()
                }
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            scope.launch {
                creatorAdSenseManager.recordRewardedInterstitialImpression()
            }
            onUserEarnedReward(rewardItem)
        }

        return true
    }
}
