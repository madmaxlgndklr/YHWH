package com.madmaxlgndklr.yhwh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.madmaxlgndklr.yhwh.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF1A1A4E),
                    onPrimary = Color.White,
                    background = Color(0xFF050510),
                    surface = Color(0xFF0D0D2E),
                    primaryContainer = Color(0xFF2A2A6E),
                    secondaryContainer = Color(0xFF1A3A2E)
                )
            ) {
                AppNavigation()
            }
        }
    }
}
