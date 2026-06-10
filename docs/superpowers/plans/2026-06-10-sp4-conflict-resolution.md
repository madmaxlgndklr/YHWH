# Conflict Resolution — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `SyncRepository` into `GameViewModel`, expose a `conflictState` StateFlow, and add a `ConflictDialog` to `GameScreen` so players see two save cards with timestamps and can choose which save to keep when both local and cloud saves exist.

**Architecture:** `GameViewModel` creates `SyncRepository(authRepository, saveManager)`, exposes `conflictState: StateFlow<ConflictState>`, calls `performSync()` after non-anonymous sign-in (not after anonymous sign-in), and provides `resolveConflict(useCloud: Boolean)`. `GameScreen` observes `conflictState` and renders `ConflictDialog` when `Pending`.

**Tech Stack:** Kotlin Coroutines (`Dispatchers.IO`, `withContext`), `ConflictState`/`SyncResult`/`SyncRepository` from SubProject 3, Jetpack Compose Material3 `AlertDialog`

---

## Prerequisites

- SubProject 3 complete: `SyncRepository`, `ConflictState`, `SyncResult`, `RemoteSaveRow` all exist in `data/remote/SyncRepository.kt`
- `game_saves` table created in Supabase (manual SQL from SP3 prerequisites)

---

## File Map

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `syncRepository`, `conflictState`, `performSync()`, `resolveConflict()`, restructure sign-in methods to call `performSync()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt` — observe `conflictState`, add `ConflictDialog` + `SaveCard` composables, render dialog when `Pending`

---

## Task 1: GameViewModel Sync Integration

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

- [ ] **Step 1: Replace GameViewModel.kt with the updated version**

Key changes vs current file:
1. Import `SyncRepository`, `ConflictState`, `SyncResult` from `data.remote`
2. Add `private val syncRepository = SyncRepository(authRepository, saveManager)` after `authRepository`
3. Add `_conflictState` + `conflictState` StateFlow
4. Add `private suspend fun performSync()` 
5. Add `fun resolveConflict(useCloud: Boolean)`
6. Restructure `signInWithEmail`, `signUpWithEmail`, `onGoogleSignInSuccess` from `runCatching` to `try/catch` so they can call the suspend `performSync()` on success

```kotlin
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
```

- [ ] **Step 2: Run existing tests — must all pass**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: all 32 tests pass (26 existing + 6 SyncRepository type tests).

- [ ] **Step 3: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: add SyncRepository, conflictState, performSync, and resolveConflict to GameViewModel"
```

---

## Task 2: ConflictDialog in GameScreen

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`

- [ ] **Step 1: Replace GameScreen.kt with the updated version**

Adds: `conflictState` collection, `ConflictDialog` rendered when `Pending`, `ConflictDialog` composable, `SaveCard` nested composable. `EpochType.valueOf()` converts the cloud row's epoch string to a display name.

```kotlin
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
```

> **Note on `GameScreen` signature:** The `viewModel: GameViewModel = viewModel()` default parameter has been removed in favour of a required parameter — `AppNavigation` always provides the hoisted instance, so the default was dead code and a potential source of confusion.

- [ ] **Step 2: Full build + tests**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt
git commit -m "feat: add ConflictDialog to GameScreen for save conflict resolution"
```

---

## Task 3: Install + Verify

- [ ] **Step 1: Connect device**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
```

If no device listed, check Developer Options → Wireless Debugging on device for current IP:port, then:
```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb connect <IP:PORT>
```

- [ ] **Step 2: Install**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb install -r \
  /home/madmaxlgndklr/Git/sandbox/YHWH/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 3: Manual verification checklist**

- [ ] Open app → Settings → Account → sign in with email → if Supabase already has a cloud save AND local save exists, the `ConflictDialog` appears
- [ ] Dialog shows two cards: "This Device" (local epoch, tick, timestamp) and "Cloud" (cloud epoch, tick, timestamp)
- [ ] Tapping "Use this" on either card dismisses the dialog and the game continues with the chosen save
- [ ] Dialog is NOT dismissible by tapping outside it (only "Use this" buttons work)
- [ ] If no conflict (only one save exists), dialog never appears — sync happens silently

- [ ] **Step 4: Push to GitHub**

```bash
git push origin main
```

---

## Self-Review

- [x] **Spec §2 syncRepository field** — `private val syncRepository = SyncRepository(authRepository, saveManager)` declared after `authRepository` (Task 1)
- [x] **Spec §2 conflictState StateFlow** — `val conflictState: StateFlow<ConflictState>` backed by `_conflictState` (Task 1)
- [x] **Spec §2 performSync()** — private suspend, handles all 4 `SyncResult` cases; `CloudRestoreAvailable` stops+restores+starts engine on Main; `ConflictDetected` emits `Pending` on Main (Task 1)
- [x] **Spec §2 resolveConflict(useCloud)** — reads `Pending` state, restores cloud+engine if `useCloud=true`, pushes local if `false`, emits `Resolved` on Main (Task 1)
- [x] **Spec §2 performSync called on sign-in** — called in `signInWithEmail`, `signUpWithEmail`, `onGoogleSignInSuccess` after auth succeeds (Task 1)
- [x] **Spec §2 anonymous sign-in does NOT call performSync** — init block only calls `signInAnonymously()` + `updateAuthState()` (Task 1)
- [x] **Spec §3 ConflictDialog** — `AlertDialog` with two `SaveCard` components, `onDismissRequest = {}` prevents tapping outside (Task 2)
- [x] **Spec §3 SaveCard fields** — label, epoch display name, tick (comma-formatted), timestamp ("MMM d HH:mm"), "Use this" button (Task 2)
- [x] **Spec §3 epoch display** — local uses `epoch.displayName`; cloud uses `EpochType.valueOf(cloud.epoch).displayName` with fallback to raw string (Task 2)
- [x] **Spec §3 render condition** — `if (conflictState is ConflictState.Pending)` after tutorial overlay (Task 2)
- [x] **Type consistency** — `ConflictState.Pending`, `SyncResult.CloudRestoreAvailable`, `SyncResult.ConflictDetected` all used with correct field names matching SP3 definitions
