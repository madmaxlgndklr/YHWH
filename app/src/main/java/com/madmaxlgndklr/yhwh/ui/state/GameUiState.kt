package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot

data class ResourceDisplay(
    val symbol: String,
    val displayName: String,
    val value: String
)

data class GameUiState(
    val epochName: String = "",
    val nextEpochName: String = "Biology",
    val tickDisplay: String = "Tick 0",
    val resources: List<ResourceDisplay> = emptyList(),
    val allResources: List<ResourceDisplay> = emptyList(),
    val epochProgress: Float = 0f,
    val generators: List<GeneratorSnapshot> = emptyList(),
    val upgrades: List<UpgradeSnapshot> = emptyList(),
    val recentEvents: List<String> = emptyList(),
    val offlineEarningsSummary: String? = null,
    val showEpochTransition: Boolean = false,
    val transitionMessage: String = "",
    val tutorialStep: Int = 0
)
