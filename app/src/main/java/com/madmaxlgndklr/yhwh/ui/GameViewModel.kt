package com.madmaxlgndklr.yhwh.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madmaxlgndklr.yhwh.data.remote.AuthRepository
import com.madmaxlgndklr.yhwh.data.remote.ConflictState
import com.madmaxlgndklr.yhwh.data.remote.SyncRepository
import com.madmaxlgndklr.yhwh.data.remote.SyncResult
import com.madmaxlgndklr.yhwh.engine.EpochType
import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
import com.madmaxlgndklr.yhwh.engine.GameEngine
import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import com.madmaxlgndklr.yhwh.engine.GameSystem
import com.madmaxlgndklr.yhwh.engine.ResourceType
import com.madmaxlgndklr.yhwh.engine.SeedBonus
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.persistence.MetaSave
import com.madmaxlgndklr.yhwh.persistence.MetaSaveManager
import com.madmaxlgndklr.yhwh.persistence.SaveData
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import com.madmaxlgndklr.yhwh.systems.BiologySystem
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
import com.madmaxlgndklr.yhwh.systems.CivilizationSystem
import com.madmaxlgndklr.yhwh.systems.InterstellarSystem
import com.madmaxlgndklr.yhwh.systems.EvolutionSystem
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import com.madmaxlgndklr.yhwh.ui.state.ResourceDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import java.io.File

sealed class AuthState {
    object Anonymous : AuthState()
    data class SignedIn(val email: String?) : AuthState()
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveFile = File(application.filesDir, "yhwh_save.json")
    private val saveManager = SaveManager(saveFile)
    private val metaFile = File(application.filesDir, "yhwh_meta.json")
    private val metaSaveManager = MetaSaveManager(metaFile)
    @Volatile private var meta = metaSaveManager.load()
    private val engine = GameEngine(scope = viewModelScope)
    private val tutorialPrefs = TutorialPrefs(application)
    val authRepository = AuthRepository()
    private val syncRepository = SyncRepository(authRepository, saveManager)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var epochTransitionAcknowledged = false

    private val _cosmosState = MutableStateFlow(CosmosState())
    val cosmosState: StateFlow<CosmosState> = _cosmosState.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Anonymous)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _conflictState = MutableStateFlow<ConflictState>(ConflictState.None)
    val conflictState: StateFlow<ConflictState> = _conflictState.asStateFlow()

    init {
        val initialTutorialStep = when {
            !tutorialPrefs.completed || tutorialPrefs.enabledOnNextLaunch -> {
                tutorialPrefs.enabledOnNextLaunch = false
                1
            }
            else -> 4
        }
        _uiState.value = _uiState.value.copy(tutorialStep = initialTutorialStep)

        engine.onSaveDue = { snapshot ->
            withContext(Dispatchers.IO) {
                val ts = System.currentTimeMillis()
                saveManager.save(snapshot, overrideTimestamp = ts)
                if (!authRepository.isAnonymous()) {
                    runCatching {
                        syncRepository.pushSave(SaveData(lastTickTimestamp = ts, snapshot = snapshot))
                    }.onFailure { Log.e("GameViewModel", "periodic cloud push failed", it) }
                }
            }
        }

        viewModelScope.launch {
            engine.snapshot.filterNotNull().collect { snapshot ->
                val newUiState = snapshot.toUiState()
                val showTransition = snapshot.epochProgress >= 1f &&
                        !epochTransitionAcknowledged &&
                        !_uiState.value.showEpochTransition
                _uiState.value = newUiState.copy(
                    showEpochTransition = showTransition || _uiState.value.showEpochTransition,
                    transitionMessage = if (showTransition) when (snapshot.epoch) {
                        EpochType.COSMOLOGY -> "A world has formed. Life stirs in the primordial ocean."
                        EpochType.BIOLOGY -> "Organisms compete for survival. Evolution begins."
                        EpochType.EVOLUTION -> "Survivors of a million years rise from the wilderness. Civilization begins."
                        EpochType.CIVILIZATION -> "The great civilization looks to the stars. The interstellar age begins."
                        EpochType.INTERSTELLAR -> "Humanity's legacy endures. The cosmos remembers."
                    } else _uiState.value.transitionMessage,
                    offlineEarningsSummary = _uiState.value.offlineEarningsSummary,
                    tutorialStep = _uiState.value.tutorialStep
                )
                _cosmosState.value = snapshot.toCosmosState()
            }
        }

        val saved = saveManager.load()
        val system: GameSystem = when (saved?.snapshot?.epoch) {
            EpochType.BIOLOGY -> BiologySystem()
            EpochType.EVOLUTION -> EvolutionSystem()
            EpochType.CIVILIZATION -> CivilizationSystem()
            EpochType.INTERSTELLAR -> InterstellarSystem()
            else -> CosmologySystem().also { it.seedBonus = meta.seedBonus }
        }
        engine.registerSystem(system)
        if (saved != null) {
            val missed = saveManager.computeMissedTicks(saved.lastTickTimestamp)
            engine.restore(saved.snapshot, missed)
            if (missed > 0) {
                val summary = buildOfflineSummary(saved.snapshot.resources, engine.snapshot.value?.resources ?: emptyMap(), missed)
                if (summary != null) {
                    _uiState.value = _uiState.value.copy(offlineEarningsSummary = summary)
                }
            }
        } else {
            engine.initNewGame()
        }
        engine.start()

        // Anonymous sign-in only — no sync for anonymous sessions
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authRepository.signInAnonymously()
                updateAuthState()
                Log.d("GameViewModel", "Anonymous auth: userId=${authRepository.currentUserId()}")
            } catch (e: Exception) {
                Log.e("GameViewModel", "Anonymous sign-in failed — continuing offline", e)
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authLoading.value = true
            _authError.value = null
            try {
                authRepository.signInWithEmail(email, password)
                updateAuthState()
                performSync()
            } catch (e: Exception) {
                _authError.value = e.message ?: "Sign in failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authLoading.value = true
            _authError.value = null
            try {
                authRepository.signUpWithEmail(email, password)
                updateAuthState()
                performSync()
            } catch (e: Exception) {
                _authError.value = e.message ?: "Sign up failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { authRepository.signOut() }
                .onSuccess { updateAuthState() }
                .onFailure { Log.e("GameViewModel", "signOut failed", it) }
        }
    }

    fun onGoogleSignInSuccess() {
        viewModelScope.launch(Dispatchers.IO) {
            updateAuthState()
            performSync()
        }
    }

    fun clearAuthError() { _authError.value = null }

    /** Called when the player taps "Use this" on either save card in the conflict dialog. */
    fun resolveConflict(useCloud: Boolean) {
        val pending = _conflictState.value as? ConflictState.Pending ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (useCloud) {
                val decoded = syncRepository.decodeSaveData(pending.cloud.saveJson)
                saveManager.save(decoded.snapshot, decoded.lastTickTimestamp)
                withContext(Dispatchers.Main) {
                    engine.stop()
                    engine.restore(decoded.snapshot, 0L)
                    engine.start()
                }
            } else {
                syncRepository.pushSave(pending.local)
            }
            withContext(Dispatchers.Main) {
                _conflictState.value = ConflictState.Resolved
            }
        }
    }

    private suspend fun performSync() {
        when (val result = syncRepository.syncOnOpen()) {
            is SyncResult.CloudRestoreAvailable -> {
                saveManager.save(result.savedData.snapshot, result.savedData.lastTickTimestamp)
                withContext(Dispatchers.Main) {
                    engine.stop()
                    engine.restore(result.savedData.snapshot, 0L)
                    engine.start()
                }
            }
            is SyncResult.ConflictDetected -> {
                withContext(Dispatchers.Main) {
                    _conflictState.value = ConflictState.Pending(result.local, result.cloud)
                }
            }
            else -> { /* NoAction or PushedToCloud — nothing to do */ }
        }
    }

    private fun updateAuthState() {
        val user = authRepository.currentUser()
        _authState.value = if (user != null && !authRepository.isAnonymous()) {
            AuthState.SignedIn(user.email)
        } else {
            AuthState.Anonymous
        }
    }

    fun onQuantumFluctuationTap() { engine.onPlayerTap() }
    fun onUpgradePurchase(upgradeId: String) { engine.purchaseUpgrade(upgradeId) }
    fun onGeneratorPurchase(generatorId: String) { engine.purchaseGenerator(generatorId) }

    fun dismissEpochTransition() {
        epochTransitionAcknowledged = true
        _uiState.value = _uiState.value.copy(showEpochTransition = false)
        val advanced = when (engine.snapshot.value?.epoch) {
            EpochType.COSMOLOGY    -> { engine.advanceEpoch(BiologySystem());      true }
            EpochType.BIOLOGY      -> { engine.advanceEpoch(EvolutionSystem());    true }
            EpochType.EVOLUTION    -> { engine.advanceEpoch(CivilizationSystem()); true }
            EpochType.CIVILIZATION -> { engine.advanceEpoch(InterstellarSystem()); true }
            else -> false // INTERSTELLAR is the final epoch — keep acknowledged to suppress re-trigger
        }
        if (advanced) epochTransitionAcknowledged = false
    }

    fun dismissOfflineSummary() {
        _uiState.value = _uiState.value.copy(offlineEarningsSummary = null)
    }

    fun computedSeedBonus(): SeedBonus {
        val snap = engine.snapshot.value ?: return meta.seedBonus
        val epochMultiplier = when (snap.epoch) {
            EpochType.COSMOLOGY -> 1.00f
            EpochType.BIOLOGY -> 1.15f
            EpochType.EVOLUTION -> 1.35f
            EpochType.CIVILIZATION -> 1.65f
            EpochType.INTERSTELLAR -> 2.00f
        }
        val lifetimeEnergy = snap.lifetimeTotals[ResourceType.ENERGY.name]?.toDouble() ?: 0.0
        val lifetimeMatter = snap.lifetimeTotals[ResourceType.MATTER.name]?.toDouble() ?: 0.0
        val newStartingEnergy = (meta.seedBonus.startingEnergy +
            floor(lifetimeEnergy / 1000.0) * 10.0).coerceAtMost(500.0)
        val newStartingMatter = (meta.seedBonus.startingMatter +
            floor(lifetimeMatter / 1000.0) * 5.0).coerceAtMost(250.0)
        val newMultiplier = (meta.seedBonus.globalMultiplier + (epochMultiplier - 1.0f))
            .coerceAtLeast(1.0f)
        return SeedBonus(newMultiplier, newStartingEnergy, newStartingMatter)
    }

    fun restartGame() {
        val newBonus = computedSeedBonus()
        val newMeta = MetaSave(restartCount = meta.restartCount + 1, seedBonus = newBonus)
        viewModelScope.launch(Dispatchers.IO) {
            metaSaveManager.save(newMeta)
            if (!saveFile.delete()) Log.e("GameViewModel", "restartGame: failed to delete save file")
            withContext(Dispatchers.Main) {
                meta = newMeta
                epochTransitionAcknowledged = false
                engine.stop()
                engine.resetAndRegister(CosmologySystem().also { it.seedBonus = newMeta.seedBonus })
                engine.initNewGame()
                engine.start()
            }
        }
    }

    fun onTutorialNext() {
        val next = _uiState.value.tutorialStep + 1
        if (next >= 4) tutorialPrefs.completed = true
        _uiState.value = _uiState.value.copy(tutorialStep = next.coerceAtMost(4))
    }

    fun onTutorialReset(enabled: Boolean) { tutorialPrefs.enabledOnNextLaunch = enabled }

    fun isTutorialResetPending(): Boolean = tutorialPrefs.enabledOnNextLaunch

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        engine.snapshot.value?.let { snapshot ->
            viewModelScope.launch(Dispatchers.IO) { saveManager.save(snapshot) }
        }
    }

    private fun GameSnapshot.toUiState(): GameUiState {
        val resourceDisplays = resources.mapNotNull { (typeName, amount) ->
            runCatching { ResourceType.valueOf(typeName) }.getOrNull()?.let { type ->
                ResourceDisplay(symbol = type.symbol, displayName = type.displayName, value = amount.toDisplayString())
            }
        }
        val allResourceDisplays = lifetimeTotals.mapNotNull { (typeName, total) ->
            if (total > BigDouble.ZERO) {
                runCatching { ResourceType.valueOf(typeName) }.getOrNull()?.let { type ->
                    ResourceDisplay(symbol = type.symbol, displayName = type.displayName, value = total.toDisplayString())
                }
            } else null
        }
        val nextEpochName = when (epoch) {
            EpochType.COSMOLOGY -> "Biology"
            EpochType.BIOLOGY -> "Evolution"
            EpochType.EVOLUTION -> "Civilization"
            EpochType.CIVILIZATION -> "Interstellar"
            EpochType.INTERSTELLAR -> "Complete"
        }
        return GameUiState(
            epochName = epoch.displayName,
            nextEpochName = nextEpochName,
            tickDisplay = "Tick $tick",
            resources = resourceDisplays,
            allResources = allResourceDisplays,
            epochProgress = epochProgress,
            generators = generators,
            upgrades = upgrades,
            recentEvents = events.map { it.message }.takeLast(5),
            activeEvent = activeEvent,
            eventTicksRemaining = eventTicksRemaining,
            restartCount = meta.restartCount,
            activeSeedMultiplier = meta.seedBonus.globalMultiplier,
            unrestLevel = if (epoch == EpochType.CIVILIZATION) unrestLevel else 0f,
            civilizationEraName = if (epoch == EpochType.CIVILIZATION) {
                when (civEraLevel()) {
                    2 -> "Industrial Era"
                    1 -> "Medieval Era"
                    else -> "Ancient Era"
                }
            } else "",
            interstellarPhaseName = if (epoch == EpochType.INTERSTELLAR) {
                when (interstellarDrivePhase()) {
                    2 -> "Hyperdrive Era"
                    1 -> "Ion Age"
                    else -> "Sublight Era"
                }
            } else "",
            vesselDecayRate = if (epoch == EpochType.INTERSTELLAR) vesselDecayRate else 0f,
        )
    }

    private fun GameSnapshot.toCosmosState(): CosmosState {
        return when (epoch) {
            EpochType.BIOLOGY -> {
                val aminoAcids = resources[ResourceType.AMINO_ACIDS.name] ?: BigDouble.ZERO
                val cells = resources[ResourceType.CELLS.name] ?: BigDouble.ZERO
                CosmosState(
                    epoch = epoch,
                    aminoAcidLevel = (aminoAcids.toDouble() / BiologySystem.AMINO_ACID_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f),
                    cellLevel = (cells.toDouble() / BiologySystem.CELL_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f)
                )
            }
            EpochType.EVOLUTION -> {
                val mutations = resources[ResourceType.MUTATIONS.name] ?: BigDouble.ZERO
                val species = resources[ResourceType.SPECIES.name] ?: BigDouble.ZERO
                CosmosState(
                    epoch = epoch,
                    mutationLevel = (mutations.toDouble() / EvolutionSystem.MUTATION_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f),
                    speciesLevel = (species.toDouble() / EvolutionSystem.SPECIES_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f),
                    activeEvent = activeEvent
                )
            }
            EpochType.CIVILIZATION -> {
                val knowledge = resources[ResourceType.KNOWLEDGE.name] ?: BigDouble.ZERO
                CosmosState(
                    epoch = epoch,
                    civEraLevel = civEraLevel(),
                    civilizationLevel = (knowledge.toDouble() / CivilizationSystem.KNOWLEDGE_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f),
                    civilUnrestActive = civilUnrestActive
                )
            }
            EpochType.INTERSTELLAR -> {
                val colonies = resources[ResourceType.COLONIES.name] ?: BigDouble.ZERO
                CosmosState(
                    epoch = epoch,
                    drivePhase = interstellarDrivePhase(),
                    legacyLevel = (colonies.toDouble() / InterstellarSystem.COLONY_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f)
                )
            }
            else -> {
                val matter = resources[ResourceType.MATTER.name] ?: BigDouble.ZERO
                val stars = resources[ResourceType.STARS.name] ?: BigDouble.ZERO
                val planets = resources[ResourceType.PLANETS.name] ?: BigDouble.ZERO
                CosmosState(
                    epoch = epoch,
                    matterLevel = (matter.toDouble() / CosmologySystem.MATTER_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f),
                    starLevel = (stars.toDouble() / CosmologySystem.STAR_VISUAL_THRESHOLD)
                        .toFloat().coerceIn(0f, 1f),
                    starsFormed = stars > BigDouble.ZERO,
                    planetsFormed = planets >= BigDouble.ONE
                )
            }
        }
    }

    private fun buildOfflineSummary(
        before: Map<String, BigDouble>,
        after: Map<String, BigDouble>,
        missedTicks: Long
    ): String? {
        val gains = after.mapNotNull { (typeName, afterAmount) ->
            val delta = afterAmount - (before[typeName] ?: BigDouble.ZERO)
            if (delta > BigDouble.ZERO) {
                runCatching { ResourceType.valueOf(typeName) }.getOrNull()?.let { type ->
                    "+${delta.toDisplayString()} ${type.displayName}"
                }
            } else null
        }
        if (gains.isEmpty()) return null
        return "Away for ~${formatOfflineTime(missedTicks)}\n\n${gains.joinToString("\n")}"
    }

    private fun formatOfflineTime(ticks: Long): String = when {
        ticks < 60 -> "${ticks}s"
        ticks < 3600 -> "${ticks / 60}m"
        else -> "${ticks / 3600}h ${(ticks % 3600) / 60}m"
    }

    private fun GameSnapshot.civEraLevel(): Int {
        val medievalPurchased = upgrades.find { it.id == CivilizationSystem.KEY_UPG_MEDIEVAL_ERA }?.purchased == true
        val industrialPurchased = upgrades.find { it.id == CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA }?.purchased == true
        return when {
            industrialPurchased -> 2
            medievalPurchased -> 1
            else -> 0
        }
    }

    private fun GameSnapshot.interstellarDrivePhase(): Int {
        val ionPurchased = upgrades.find { it.id == InterstellarSystem.KEY_UPG_ION_DRIVE }?.purchased == true
        val hyperdrivePurchased = upgrades.find { it.id == InterstellarSystem.KEY_UPG_HYPERDRIVE }?.purchased == true
        return when {
            hyperdrivePurchased -> 2
            ionPurchased -> 1
            else -> 0
        }
    }
}
