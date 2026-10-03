package com.example.core.ads

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

object AdManager {
    // Official Google sample test ad unit for Banners (safe for all testing & review)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            // Set test device configuration for development emulator
            val configuration = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(configuration)

            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d("AdManager", "Google Mobile Ads initialized: $status")
            }
        } catch (e: Exception) {
            Log.w("AdManager", "Mobile Ads initialization deferred: ${e.message}")
        }
    }
}

@Composable
fun AdBannerView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdManager.TEST_BANNER_AD_UNIT_ID
) {
    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }
    var adFailedToLoad by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdManager.initialize(context.applicationContext)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateElevated)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.wrapContentSize(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                            adFailedToLoad = false
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            Log.w("AdBannerView", "Ad failed to load: ${loadAdError.message}")
                            isAdLoaded = false
                            adFailedToLoad = true
                        }
                    }
                    val adRequest = AdRequest.Builder().build()
                    loadAd(adRequest)
                }
            }
        )

        // Fallback placeholder display if ad is loading or offline
        if (!isAdLoaded && adFailedToLoad) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google AdSense · Sponsored Study Partner",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                )
            }
        }
    }
}
