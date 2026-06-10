package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madmaxlgndklr.yhwh.ui.AuthState
import com.madmaxlgndklr.yhwh.ui.GameViewModel

@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    onNavigateToProfile: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontSize = 22.sp, style = MaterialTheme.typography.headlineMedium)

        HorizontalDivider()

        // Account row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToProfile)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Account", fontSize = 15.sp)
                Text(
                    text = when (val s = authState) {
                        is AuthState.SignedIn -> s.email ?: "Google Account"
                        AuthState.Anonymous -> "Not signed in · tap to sync your save"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // Tutorial toggle
        var tutorialPending by remember { mutableStateOf(viewModel.isTutorialResetPending()) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show tutorial on next launch", fontSize = 15.sp)
                Text(
                    "Replays the 3-step beginner guide",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = tutorialPending,
                onCheckedChange = { enabled ->
                    tutorialPending = enabled
                    viewModel.onTutorialReset(enabled)
                }
            )
        }
    }
}
