package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot

data class GameUiState(
    val epochName: String = "",
    val tickDisplay: String = "Tick 0",
    val energyDisplay: String = "0",
    val matterDisplay: String = "0",
    val epochProgress: Float = 0f,
    val generators: List<GeneratorSnapshot> = emptyList(),
    val upgrades: List<UpgradeSnapshot> = emptyList(),
    val recentEvents: List<String> = emptyList(),
    val offlineEarningsSummary: String? = null,
    val showEpochTransition: Boolean = false,
    val transitionMessage: String = ""
)
