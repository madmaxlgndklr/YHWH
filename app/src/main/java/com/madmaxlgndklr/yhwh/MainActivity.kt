package com.madmaxlgndklr.yhwh

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.navigation.AppNavigation

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            setContent {
                AppTheme {
                    SafeAppContent(onRetry = ::recreate)
                }
            }
        } catch (t: Throwable) {
            // Catches synchronous failures thrown before composition starts.
            // Composition-phase exceptions are not catchable here — see SafeAppContent.
            Log.e(TAG, "Fatal error initializing app content", t)
            setContent {
                AppTheme {
                    AppCrashScreen(onRetry = ::recreate)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1A1A4E),
            onPrimary = Color.White,
            background = Color(0xFF050510),
            surface = Color(0xFF0D0D2E),
            primaryContainer = Color(0xFF2A2A6E),
            secondaryContainer = Color(0xFF1A3A2E)
        ),
        content = content
    )
}

/**
 * Wraps [AppNavigation] with a stateful error fallback.
 *
 * Note: Compose does not support React-style error boundaries. Exceptions during
 * recomposition propagate through the Compose runtime and cannot be caught here.
 * This handles errors explicitly signaled via state (e.g., from ViewModels) and
 * works alongside the outer try-catch in [MainActivity.onCreate] for init failures.
 */
@Composable
private fun SafeAppContent(onRetry: () -> Unit) {
    var fatalError by rememberSaveable { mutableStateOf<String?>(null) }

    if (fatalError != null) {
        AppCrashScreen(
            message = fatalError!!,
            onRetry = {
                fatalError = null
                onRetry()
            }
        )
    } else {
        AppNavigation()
    }
}

@Composable
private fun AppCrashScreen(
    message: String = "An unexpected error occurred.",
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
