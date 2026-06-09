package com.madmaxlgndklr.yhwh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madmaxlgndklr.yhwh.engine.GameEngine
import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import com.madmaxlgndklr.yhwh.engine.ResourceType
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveFile = File(application.filesDir, "yhwh_save.json")
    private val saveManager = SaveManager(saveFile)
    private val engine = GameEngine(scope = viewModelScope)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var epochTransitionAcknowledged = false

    private val _cosmosState = MutableStateFlow(CosmosState())
    val cosmosState: StateFlow<CosmosState> = _cosmosState.asStateFlow()

    init {
        engine.registerSystem(CosmologySystem())
        engine.onSaveDue = { snapshot ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveManager.save(snapshot)
            }
        }

        viewModelScope.launch {
            engine.snapshot.filterNotNull().collect { snapshot ->
                val newUiState = snapshot.toUiState()
                // Detect epoch transition: progress reached 1.0 and we haven't shown the overlay yet
                val showTransition = snapshot.epochProgress >= 1f && !epochTransitionAcknowledged && !_uiState.value.showEpochTransition
                _uiState.value = newUiState.copy(
                    showEpochTransition = showTransition || _uiState.value.showEpochTransition,
                    transitionMessage = if (showTransition) "A world has formed. Life stirs in the primordial ocean." else _uiState.value.transitionMessage,
                    offlineEarningsSummary = _uiState.value.offlineEarningsSummary
                )
                _cosmosState.value = snapshot.toCosmosState()
            }
        }

        val saved = saveManager.load()
        if (saved != null) {
            val missed = saveManager.computeMissedTicks(saved.lastTickTimestamp)
            if (missed > 0) {
                _uiState.value = _uiState.value.copy(
                    offlineEarningsSummary = "You were away for ~${formatOfflineTime(missed)} — your generators kept working."
                )
            }
            engine.restore(saved.snapshot, missed)
        } else {
            engine.initNewGame()
        }

        engine.start()
    }

    fun onQuantumFluctuationTap() { engine.onPlayerTap() }

    fun onUpgradePurchase(upgradeId: String) { engine.purchaseUpgrade(upgradeId) }

    fun onGeneratorPurchase(generatorId: String) { engine.purchaseGenerator(generatorId) }

    fun dismissEpochTransition() {
        epochTransitionAcknowledged = true
        _uiState.value = _uiState.value.copy(showEpochTransition = false)
    }

    fun dismissOfflineSummary() {
        _uiState.value = _uiState.value.copy(offlineEarningsSummary = null)
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        engine.snapshot.value?.let { snapshot ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                saveManager.save(snapshot)
            }
        }
    }

    private fun GameSnapshot.toUiState(): GameUiState {
        val energy = resources[ResourceType.ENERGY.name] ?: BigDouble.ZERO
        val matter = resources[ResourceType.MATTER.name] ?: BigDouble.ZERO
        return GameUiState(
            epochName = epoch.displayName,
            tickDisplay = "Tick $tick",
            energyDisplay = energy.toDisplayString(),
            matterDisplay = matter.toDisplayString(),
            epochProgress = epochProgress,
            generators = generators,
            upgrades = upgrades,
            recentEvents = events.map { it.message }.takeLast(5)
        )
    }

    private fun GameSnapshot.toCosmosState(): CosmosState {
        val matter = resources[ResourceType.MATTER.name] ?: BigDouble.ZERO
        val stars = resources[ResourceType.STARS.name] ?: BigDouble.ZERO
        val planets = resources[ResourceType.PLANETS.name] ?: BigDouble.ZERO
        return CosmosState(
            epoch = epoch,
            matterLevel = (matter.toDouble() / CosmologySystem.MATTER_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
            starLevel = (stars.toDouble() / CosmologySystem.STAR_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
            starsFormed = stars > BigDouble.ZERO,
            planetsFormed = planets >= BigDouble.ONE
        )
    }

    private fun formatOfflineTime(ticks: Long): String {
        return when {
            ticks < 60 -> "${ticks}s"
            ticks < 3600 -> "${ticks / 60}m"
            else -> "${ticks / 3600}h ${(ticks % 3600) / 60}m"
        }
    }
}
