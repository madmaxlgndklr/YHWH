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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.components.ActionPanel
import com.madmaxlgndklr.yhwh.ui.components.CosmosCanvas
import com.madmaxlgndklr.yhwh.ui.components.GameTopBar
import com.madmaxlgndklr.yhwh.ui.components.TutorialOverlay

@Composable
fun GameScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cosmosState by viewModel.cosmosState.collectAsStateWithLifecycle()

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

        // Tutorial coach-mark overlay — renders on top of everything
        if (uiState.tutorialStep in 1..3) {
            TutorialOverlay(
                step = uiState.tutorialStep,
                onNext = viewModel::onTutorialNext
            )
        }
    }
}
