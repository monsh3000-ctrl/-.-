package com.example

import android.app.Application
import android.util.Log
import com.example.core.ads.AdMobManager

class StudyLockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("StudyLockApp", "Starting StudyLock Application...")

        // Initialize Google Mobile Ads SDK strictly once at application launch
        AdMobManager.initialize(this)
    }
}
