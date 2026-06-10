package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madmaxlgndklr.yhwh.data.remote.ConflictState
import com.madmaxlgndklr.yhwh.data.remote.RemoteSaveRow
import com.madmaxlgndklr.yhwh.engine.EpochType
import com.madmaxlgndklr.yhwh.persistence.SaveData
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.components.ActionPanel
import com.madmaxlgndklr.yhwh.ui.components.CosmosCanvas
import com.madmaxlgndklr.yhwh.ui.components.GameTopBar
import com.madmaxlgndklr.yhwh.ui.components.TutorialOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GameScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: GameViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cosmosState by viewModel.cosmosState.collectAsStateWithLifecycle()
    val conflictState by viewModel.conflictState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { GameTopBar(state = uiState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CosmosCanvas(
                state = cosmosState,
                onTap = viewModel::onQuantumFluctuationTap,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            ActionPanel(
                state = uiState,
                viewModel = viewModel,
                onTap = viewModel::onQuantumFluctuationTap,
                onUpgradePurchase = viewModel::onUpgradePurchase,
                onGeneratorPurchase = viewModel::onGeneratorPurchase,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Epoch transition overlay
        AnimatedVisibility(
            visible = uiState.showEpochTransition,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(paddingValues)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(text = "♁", fontSize = 64.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = uiState.transitionMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = viewModel::dismissEpochTransition) {
                        Text("Continue")
                    }
                }
            }
        }

        // Tutorial coach-mark overlay
        if (uiState.tutorialStep in 1..3) {
            TutorialOverlay(
                step = uiState.tutorialStep,
                onNext = viewModel::onTutorialNext
            )
        }

        // Offline earnings dialog — shown on return, dismissed by tapping Collect
        uiState.offlineEarningsSummary?.let { summary ->
            OfflineEarningsDialog(summary = summary, onCollect = viewModel::dismissOfflineSummary)
        }

        // Save conflict dialog — not dismissible without choosing
        if (conflictState is ConflictState.Pending) {
            val pending = conflictState as ConflictState.Pending
            ConflictDialog(
                local = pending.local,
                cloud = pending.cloud,
                onUseLocal = { viewModel.resolveConflict(useCloud = false) },
                onUseCloud = { viewModel.resolveConflict(useCloud = true) }
            )
        }
    }
}

@Composable
private fun OfflineEarningsDialog(summary: String, onCollect: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* must tap Collect */ },
        containerColor = Color(0xFF1A1A4E),
        title = {
            Text("Welcome back!", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Text(
                text = summary,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onCollect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4466AA))
            ) {
                Text("Collect")
            }
        }
    )
}

@Composable
private fun ConflictDialog(
    local: SaveData,
    cloud: RemoteSaveRow,
    onUseLocal: () -> Unit,
    onUseCloud: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d  HH:mm", Locale.getDefault()) }
    val cloudEpochDisplay = remember(cloud.epoch) {
        runCatching { EpochType.valueOf(cloud.epoch).displayName }.getOrElse { cloud.epoch }
    }

    AlertDialog(
        onDismissRequest = { /* not dismissible without choosing */ },
        containerColor = Color(0xFF1A1A4E),
        title = {
            Text(
                "Two saves found",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaveCard(
                    label = "This Device",
                    epochDisplay = local.snapshot.epoch.displayName,
                    tick = local.snapshot.tick,
                    timestamp = fmt.format(Date(local.lastTickTimestamp)),
                    onSelect = onUseLocal,
                    modifier = Modifier.weight(1f)
                )
                SaveCard(
                    label = "Cloud",
                    epochDisplay = cloudEpochDisplay,
                    tick = cloud.tick,
                    timestamp = fmt.format(Date(cloud.lastSavedAt)),
                    onSelect = onUseCloud,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SaveCard(
    label: String,
    epochDisplay: String,
    tick: Long,
    timestamp: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A6E))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            Text(epochDisplay, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "%,d".format(tick).let { "Tick $it" },
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(timestamp, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSelect,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4466AA))
            ) {
                Text("Use this", fontSize = 11.sp)
            }
        }
    }
}
