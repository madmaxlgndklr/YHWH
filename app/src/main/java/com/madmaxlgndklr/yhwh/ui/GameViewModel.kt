package com.madmaxlgndklr.yhwh.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madmaxlgndklr.yhwh.data.remote.AuthRepository
import com.madmaxlgndklr.yhwh.data.remote.ConflictState
import com.madmaxlgndklr.yhwh.data.remote.SyncRepository
import com.madmaxlgndklr.yhwh.data.remote.SyncResult
import com.madmaxlgndklr.yhwh.engine.GameEngine
import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import com.madmaxlgndklr.yhwh.engine.ResourceType
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AuthState {
    object Anonymous : AuthState()
    data class SignedIn(val email: String?) : AuthState()
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveFile = File(application.filesDir, "yhwh_save.json")
    private val saveManager = SaveManager(saveFile)
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

        engine.registerSystem(CosmologySystem())
        engine.onSaveDue = { snapshot ->
            withContext(Dispatchers.IO) { saveManager.save(snapshot) }
        }

        viewModelScope.launch {
            engine.snapshot.filterNotNull().collect { snapshot ->
                val newUiState = snapshot.toUiState()
                val showTransition = snapshot.epochProgress >= 1f &&
                        !epochTransitionAcknowledged &&
                        !_uiState.value.showEpochTransition
                _uiState.value = newUiState.copy(
                    showEpochTransition = showTransition || _uiState.value.showEpochTransition,
                    transitionMessage = if (showTransition)
                        "A world has formed. Life stirs in the primordial ocean."
                    else _uiState.value.transitionMessage,
                    offlineEarningsSummary = _uiState.value.offlineEarningsSummary,
                    tutorialStep = _uiState.value.tutorialStep
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
    }

    fun dismissOfflineSummary() {
        _uiState.value = _uiState.value.copy(offlineEarningsSummary = null)
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

    private fun formatOfflineTime(ticks: Long): String = when {
        ticks < 60 -> "${ticks}s"
        ticks < 3600 -> "${ticks / 60}m"
        else -> "${ticks / 3600}h ${(ticks % 3600) / 60}m"
    }
}
