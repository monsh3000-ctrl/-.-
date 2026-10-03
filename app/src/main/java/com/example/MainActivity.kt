package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.navigation.StudyLockApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
            ?: if (intent.action == android.content.Intent.ACTION_SEND || intent.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
                "school_materials"
            } else null

        setContent {
            MyApplicationTheme {
                StudyLockApp(initialDestination = navigateTo)
            }
        }
    }
}
