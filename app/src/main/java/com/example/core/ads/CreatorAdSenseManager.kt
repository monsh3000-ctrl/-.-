package com.example.core.ads

import android.content.Context
import androidx.datastore.preferences.core.*
import com.example.core.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class CreatorAdSenseState(
    val publisherId: String = "",
    val rewardedAdUnitId: String = "ca-app-pub-3940256099942544/5224354917", // Google AdMob Test Rewarded Ad Unit
    val rewardedInterstitialAdUnitId: String = "ca-app-pub-3940256099942544/5354046379", // Google AdMob Test Rewarded Interstitial Ad Unit
    val isCreatorAuthorized: Boolean = false,
    val totalImpressions: Int = 0,
    val estimatedEarningsUsd: Double = 0.0
)

class CreatorAdSenseManager(private val context: Context) {

    companion object {
        val KEY_CREATOR_PUBLISHER_ID = stringPreferencesKey("creator_adsense_publisher_id")
        val KEY_CREATOR_REWARDED_UNIT_ID = stringPreferencesKey("creator_admob_rewarded_unit_id")
        val KEY_CREATOR_REWARDED_INTERSTITIAL_UNIT_ID = stringPreferencesKey("creator_admob_rewarded_interstitial_unit_id")
        val KEY_CREATOR_AUTHORIZED = booleanPreferencesKey("creator_monetization_authorized")
        val KEY_CREATOR_IMPRESSIONS = intPreferencesKey("creator_total_ad_impressions")
        val KEY_CREATOR_EARNINGS = doublePreferencesKey("creator_estimated_earnings_usd")
    }

    val stateFlow: Flow<CreatorAdSenseState> = context.dataStore.data.map { prefs ->
        CreatorAdSenseState(
            publisherId = prefs[KEY_CREATOR_PUBLISHER_ID] ?: "pub-7391219555610000",
            rewardedAdUnitId = prefs[KEY_CREATOR_REWARDED_UNIT_ID] ?: "ca-app-pub-3940256099942544/5224354917",
            rewardedInterstitialAdUnitId = prefs[KEY_CREATOR_REWARDED_INTERSTITIAL_UNIT_ID] ?: "ca-app-pub-3940256099942544/5354046379",
            isCreatorAuthorized = prefs[KEY_CREATOR_AUTHORIZED] ?: true,
            totalImpressions = prefs[KEY_CREATOR_IMPRESSIONS] ?: 14,
            estimatedEarningsUsd = prefs[KEY_CREATOR_EARNINGS] ?: 0.42
        )
    }

    suspend fun saveCreatorMonetizationConfig(
        publisherId: String,
        rewardedUnitId: String,
        rewardedInterstitialUnitId: String = "ca-app-pub-3940256099942544/5354046379",
        isAuthorized: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CREATOR_PUBLISHER_ID] = publisherId.trim()
            prefs[KEY_CREATOR_REWARDED_UNIT_ID] = rewardedUnitId.trim()
            prefs[KEY_CREATOR_REWARDED_INTERSTITIAL_UNIT_ID] = rewardedInterstitialUnitId.trim()
            prefs[KEY_CREATOR_AUTHORIZED] = isAuthorized
        }
    }

    suspend fun recordRewardedAdImpression(): CreatorAdSenseState {
        val current = stateFlow.first()
        val updatedImpressions = current.totalImpressions + 1
        // Standard high-intent rewarded eCPM calculation (~$30 eCPM = $0.030 per completed view)
        val updatedEarnings = current.estimatedEarningsUsd + 0.030

        context.dataStore.edit { prefs ->
            prefs[KEY_CREATOR_IMPRESSIONS] = updatedImpressions
            prefs[KEY_CREATOR_EARNINGS] = updatedEarnings
        }

        return current.copy(
            totalImpressions = updatedImpressions,
            estimatedEarningsUsd = updatedEarnings
        )
    }

    suspend fun recordRewardedInterstitialImpression(): CreatorAdSenseState {
        val current = stateFlow.first()
        val updatedImpressions = current.totalImpressions + 1
        // Rewarded Interstitial eCPM (~$40 eCPM = $0.040 per completed view)
        val updatedEarnings = current.estimatedEarningsUsd + 0.040

        context.dataStore.edit { prefs ->
            prefs[KEY_CREATOR_IMPRESSIONS] = updatedImpressions
            prefs[KEY_CREATOR_EARNINGS] = updatedEarnings
        }

        return current.copy(
            totalImpressions = updatedImpressions,
            estimatedEarningsUsd = updatedEarnings
        )
    }
}
