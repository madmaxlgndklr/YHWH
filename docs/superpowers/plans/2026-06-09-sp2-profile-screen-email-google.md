# Profile Screen + Email/Google Sign-In — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ProfileScreen with email/Google sign-in UI, expose `authState`/`authLoading`/`authError` StateFlows from GameViewModel, add an Account row to SettingsScreen, and wire a PROFILE route into AppNavigation.

**Architecture:** `GameViewModel` gains `AuthState` sealed class + 5 new StateFlows + 5 new public methods. `ProfileScreen` reads those flows and calls those methods. `SettingsScreen` gains an Account row showing current auth status. `AppNavigation` hoists ViewModel and routes PROFILE.

**Tech Stack:** Supabase Kotlin SDK 3.6.0 (`compose-auth` for Google), Jetpack Compose Material3, Kotlin Coroutines StateFlow

---

## Prerequisites

- SubProject 1 complete: `AuthRepository` exists at `data/remote/AuthRepository.kt`, `authRepository` field is on `GameViewModel`.
- Supabase project has Anonymous sign-ins enabled (`Auth → Settings` in Supabase dashboard).
- `GOOGLE_SERVER_CLIENT_ID` in `local.properties` can remain empty until Google sign-in is tested — the button will show but the flow will fail gracefully.

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `AuthState`, `authState`, `authLoading`, `authError` StateFlows; add `signInWithEmail`, `signUpWithEmail`, `signOut`, `onGoogleSignInSuccess`, `clearAuthError`, `updateAuthState`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt` — add `onNavigateToProfile` param, Account row with auth status
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt` — add `Routes.PROFILE`, pass `onNavigateToProfile` to SettingsScreen, add ProfileScreen composable

---

## Task 1: GameViewModel Auth Methods + AuthState

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

- [ ] **Step 1: Replace GameViewModel.kt with the updated version**

Key additions vs current file:
- `AuthState` sealed class (top-level, before the class)
- `_authState`, `authState`, `_authLoading`, `authLoading`, `_authError`, `authError` fields
- Call `updateAuthState()` in init after `authRepository.signInAnonymously()`
- `signInWithEmail`, `signUpWithEmail`, `signOut`, `onGoogleSignInSuccess`, `clearAuthError`, `updateAuthState` methods

```kotlin
package com.madmaxlgndklr.yhwh.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madmaxlgndklr.yhwh.data.remote.AuthRepository
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
            runCatching { authRepository.signInWithEmail(email, password) }
                .onSuccess { updateAuthState() }
                .onFailure { _authError.value = it.message ?: "Sign in failed" }
            _authLoading.value = false
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authLoading.value = true
            _authError.value = null
            runCatching { authRepository.signUpWithEmail(email, password) }
                .onSuccess { updateAuthState() }
                .onFailure { _authError.value = it.message ?: "Sign up failed" }
            _authLoading.value = false
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
        viewModelScope.launch(Dispatchers.IO) { updateAuthState() }
    }

    fun clearAuthError() { _authError.value = null }

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

Expected: all tests pass.

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
git commit -m "feat: add AuthState, authState/loading/error flows and auth methods to GameViewModel"
```

---

## Task 2: ProfileScreen

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt`

- [ ] **Step 1: Create ProfileScreen.kt**

Google sign-in imports use the same package as the Pokedex project (`io.github.jan.supabase.compose.auth.composable`).

```kotlin
package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madmaxlgndklr.yhwh.data.remote.SupabaseModule
import com.madmaxlgndklr.yhwh.ui.AuthState
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val loading by viewModel.authLoading.collectAsStateWithLifecycle()
    val error by viewModel.authError.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val googleSignIn = SupabaseModule.client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            if (result is NativeSignInResult.Success) viewModel.onGoogleSignInSuccess()
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A4E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = authState) {
                is AuthState.SignedIn -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A4E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Signed in",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = state.email ?: "Google Account",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::signOut,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4466AA))
                    ) {
                        Text("Sign Out")
                    }
                }

                is AuthState.Anonymous -> {
                    Text(
                        "Sign in to sync your save across devices.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(false to "Sign In", true to "Create Account").forEach { (signup, label) ->
                            TextButton(
                                onClick = { isSignUp = signup; viewModel.clearAuthError() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    label,
                                    color = if (isSignUp == signup) Color(0xFF88CCFF)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSignUp == signup) FontWeight.Bold
                                                 else FontWeight.Normal
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            if (isSignUp) viewModel.signUpWithEmail(email, password)
                            else viewModel.signInWithEmail(email, password)
                        })
                    )

                    if (error != null) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                keyboard?.hide()
                                if (isSignUp) viewModel.signUpWithEmail(email, password)
                                else viewModel.signInWithEmail(email, password)
                            },
                            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4466AA))
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(if (isSignUp) "Create" else "Sign In")
                            }
                        }

                        OutlinedButton(
                            onClick = { googleSignIn.startFlow() },
                            enabled = !loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Google")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt
git commit -m "feat: add ProfileScreen with email and Google sign-in UI"
```

---

## Task 3: SettingsScreen + AppNavigation

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`

- [ ] **Step 1: Replace SettingsScreen.kt**

Adds `onNavigateToProfile` parameter and an Account row above the existing tutorial toggle.

```kotlin
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
```

- [ ] **Step 2: Replace AppNavigation.kt**

Adds `Routes.PROFILE`, passes `onNavigateToProfile` to SettingsScreen, adds ProfileScreen composable.

```kotlin
package com.madmaxlgndklr.yhwh.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.screen.GameScreen
import com.madmaxlgndklr.yhwh.ui.screen.ProfileScreen
import com.madmaxlgndklr.yhwh.ui.screen.SettingsScreen

private object Routes {
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.GAME) {
        composable(Routes.GAME) {
            GameScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = gameViewModel
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = gameViewModel,
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = gameViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 3: Verify full build + tests**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt
git commit -m "feat: add Account row to SettingsScreen and PROFILE route to AppNavigation"
```

---

## Task 4: Install + Verify

- [ ] **Step 1: Connect device**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
```

- [ ] **Step 2: Install**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb install -r \
  /home/madmaxlgndklr/Git/sandbox/YHWH/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 3: Manual verification checklist**

On device:
- [ ] Open app → tap Settings icon → see "Account" row showing "Not signed in · tap to sync your save"
- [ ] Tap Account row → ProfileScreen opens with "Sign In" / "Create Account" tabs, email + password fields, Google button
- [ ] Back arrow returns to SettingsScreen
- [ ] Enter an email and password → tap "Create" → account created, ProfileScreen shows email + "Sign Out" button
- [ ] Account row in SettingsScreen now shows the email address
- [ ] Tap "Sign Out" → ProfileScreen returns to sign-in UI, Account row shows "Not signed in"
- [ ] Sign back in with same credentials → ProfileScreen shows email again

- [ ] **Step 4: Push to GitHub**

```bash
git push origin main
```

---

## Self-Review

- [x] **Spec §3 AuthState** — `sealed class AuthState` with `Anonymous` and `SignedIn(email: String?)` defined in `GameViewModel.kt` (Task 1)
- [x] **Spec §4 authState StateFlow** — `val authState: StateFlow<AuthState>` on GameViewModel (Task 1)
- [x] **Spec §4 authLoading, authError** — both StateFlows exposed (Task 1)
- [x] **Spec §5 signInWithEmail, signUpWithEmail** — coroutines, set loading/error, call updateAuthState on success (Task 1)
- [x] **Spec §5 signOut** — swallows errors, calls updateAuthState (Task 1)
- [x] **Spec §5 onGoogleSignInSuccess** — calls updateAuthState in IO scope (Task 1)
- [x] **Spec §5 clearAuthError** — sets `_authError.value = null` (Task 1)
- [x] **Spec §6 ProfileScreen anonymous UI** — Sign In/Create Account tabs, email + password, Google button, loading indicator, error display (Task 2)
- [x] **Spec §6 ProfileScreen signed-in UI** — email card, Sign Out button (Task 2)
- [x] **Spec §6 ProfileScreen navigation** — back arrow, TopAppBar (Task 2)
- [x] **Spec §7 SettingsScreen Account row** — clickable, reads authState for subtitle (Task 3)
- [x] **Spec §8 AppNavigation PROFILE route** — added with ProfileScreen composable (Task 3)
- [x] **Type consistency** — `AuthState.SignedIn(val email: String?)` matches usage in SettingsScreen (`s.email ?: "Google Account"`) and ProfileScreen (`state.email ?: "Google Account"`)
- [x] **Existing tutorial toggle unchanged** — still present in SettingsScreen below the new Account row
