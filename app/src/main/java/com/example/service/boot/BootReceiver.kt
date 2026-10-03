package com.example.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.core.datastore.StudyLockPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("StudyLockBoot", "Device boot completed, restoring StudyLock state")
        val prefs = StudyLockPreferences(context)
        CoroutineScope(Dispatchers.IO).launch {
            prefs.saveTimeIntegrityCheckpoint(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        }
    }
}
