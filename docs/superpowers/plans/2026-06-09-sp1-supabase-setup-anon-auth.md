# Supabase Setup + Anonymous Auth — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install Supabase SDK, configure credentials via BuildConfig, create SupabaseModule and AuthRepository, and add silent anonymous sign-in to GameViewModel init.

**Architecture:** `local.properties` → `BuildConfig` → `SupabaseModule` singleton → `AuthRepository` suspend wrappers → `GameViewModel` calls `authRepository.signInAnonymously()` async after engine starts.

**Tech Stack:** Supabase Kotlin SDK 2.6.1 (`auth-kt`, `postgrest-kt`, `compose-auth`), Ktor Android 3.0.3, AGP 9.2.1, Kotlin 2.2.10

---

## Prerequisites (Manual — Before Any Code)

1. **Add credentials to `local.properties`** (in the project root, never committed — add to `.gitignore` if not already there):
   ```properties
   SUPABASE_URL=https://qwresuyroqzyxbqrvrdh.supabase.co
   SUPABASE_ANON_KEY=sb_publishable_wjOQSsIfe7GVZheX2UTc2g_SITcXNzX
   GOOGLE_SERVER_CLIENT_ID=
   ```
   `GOOGLE_SERVER_CLIENT_ID` can be empty for now — Google sign-in is SubProject 2.

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SupabaseModule.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/AuthRepository.kt`

**Modified:**
- `gradle/libs.versions.toml` — add `supabase = "2.6.1"` and `ktor = "3.0.3"` versions + 4 library entries
- `app/build.gradle.kts` — add `buildConfig = true`, read `local.properties`, add 3 `buildConfigField` entries, add 4 Supabase/Ktor `implementation` deps
- `app/src/main/AndroidManifest.xml` — add `INTERNET` permission
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `authRepository` field, async anon sign-in in `init`

---

## Task 1: Gradle + BuildConfig

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Update gradle/libs.versions.toml**

Replace the entire file:

```toml
[versions]
agp = "9.2.1"
kotlin = "2.2.10"
composeBom = "2026.02.01"
coroutines = "1.9.0"
lifecycle = "2.8.7"
navigation = "2.8.5"
serialization = "1.8.1"
activityCompose = "1.9.3"
junit = "4.13.2"
junitExt = "1.2.1"
espresso = "3.6.1"
supabase = "2.6.1"
ktor = "3.0.3"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
junit-ext = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
espresso = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt", version.ref = "supabase" }
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt", version.ref = "supabase" }
supabase-compose-auth = { group = "io.github.jan-tennert.supabase", name = "compose-auth", version.ref = "supabase" }
ktor-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Update app/build.gradle.kts**

Replace the entire file:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }
    ?.inputStream()?.use { localProps.load(it) }

android {
    namespace = "com.madmaxlgndklr.yhwh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.madmaxlgndklr.yhwh"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL",
            "\"${localProps["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID",
            "\"${localProps["GOOGLE_SERVER_CLIENT_ID"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    // Supabase
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.compose.auth)
    implementation(libs.ktor.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(composeBom)
}
```

- [ ] **Step 3: Add INTERNET permission to AndroidManifest.xml**

Replace the entire file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".YhwhApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.YHWH">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 4: Verify dependencies download and project compiles**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. If dependency resolution fails, check internet connectivity and Gradle cache.

- [ ] **Step 5: Run existing tests to confirm no regressions**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: existing tests all pass.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml \
        app/build.gradle.kts \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add Supabase + Ktor dependencies and BuildConfig credentials"
```

---

## Task 2: SupabaseModule + AuthRepository

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SupabaseModule.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/AuthRepository.kt`

- [ ] **Step 1: Create the data/remote directory and SupabaseModule.kt**

```kotlin
package com.madmaxlgndklr.yhwh.data.remote

import com.madmaxlgndklr.yhwh.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseModule {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(ComposeAuth) {
            googleNativeLogin(serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID)
        }
    }
}
```

- [ ] **Step 2: Create AuthRepository.kt**

```kotlin
package com.madmaxlgndklr.yhwh.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo

class AuthRepository {
    private val auth = SupabaseModule.client.auth

    suspend fun signInAnonymously() {
        if (auth.currentUserOrNull() == null) {
            auth.signInAnonymously()
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUpWithEmail(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUser(): UserInfo? = auth.currentUserOrNull()

    /** Returns true when there is no session or the user is anonymous (role == "anon"). */
    fun isAnonymous(): Boolean {
        val user = auth.currentUserOrNull() ?: return true
        return user.role == "anon"
    }

    fun currentUserId(): String? = auth.currentUserOrNull()?.id
}
```

- [ ] **Step 3: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If you see unresolved reference errors on Supabase imports, verify the dependency names in `libs.versions.toml` match Task 1 Step 1 exactly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/data/
git commit -m "feat: add SupabaseModule singleton and AuthRepository"
```

---

## Task 3: GameViewModel Integration

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

> **Note on unit testing:** `AuthRepository` wraps the Supabase SDK which requires a real network session — it cannot be meaningfully unit-tested without mocking the entire Supabase client. This task therefore verifies via compilation and the manual device install in Task 4. The existing 41 unit tests must remain green.

- [ ] **Step 1: Add authRepository field and async sign-in to GameViewModel**

The only changes to `GameViewModel.kt` are:
1. Add import for `AuthRepository`
2. Add import for `android.util.Log`
3. Add `val authRepository = AuthRepository()` as a class field (after `tutorialPrefs`)
4. Add the async sign-in block at the end of `init` (after `engine.start()`)

Replace the entire file:

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

        // Silent anonymous sign-in — runs after engine starts, never blocks the game
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authRepository.signInAnonymously()
                Log.d("GameViewModel", "Anonymous auth: userId=${authRepository.currentUserId()}")
            } catch (e: Exception) {
                Log.e("GameViewModel", "Anonymous sign-in failed — continuing offline", e)
            }
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
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: all existing tests pass. Any failure here means the ViewModel change broke something.

- [ ] **Step 3: Full build**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: add AuthRepository to GameViewModel with silent anonymous sign-in"
```

---

## Task 4: Install and Verify Anonymous Auth

- [ ] **Step 1: Connect device**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
```

If no device listed:
```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb connect 192.168.1.214:34239
```

- [ ] **Step 2: Install**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb install -r \
  /home/madmaxlgndklr/Git/sandbox/YHWH/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 3: Verify anonymous auth in Logcat**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb logcat -s GameViewModel 2>&1 | head -20
```

Launch the app. Within ~5 seconds of launch, expect to see:
```
D GameViewModel: Anonymous auth: userId=<some-uuid>
```

If instead you see `Anonymous sign-in failed`, check:
- `local.properties` has the correct `SUPABASE_URL` and `SUPABASE_ANON_KEY`
- The Supabase project's `Auth → Settings → Anonymous sign-ins` is **enabled** in the Supabase dashboard

- [ ] **Step 4: Verify in Supabase dashboard**

Navigate to: https://supabase.com/dashboard/project/qwresuyroqzyxbqrvrdh/auth/users

A new anonymous user should appear with `Is anonymous: true` after launching the app.

- [ ] **Step 5: Push to GitHub**

```bash
git push origin main
```

---

## Self-Review

- [x] **Spec §3 BuildConfig credentials** — `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_SERVER_CLIENT_ID` all wired from `local.properties` (Task 1)
- [x] **Spec §4 SupabaseModule** — installs `Auth`, `Postgrest`, `ComposeAuth` with `googleNativeLogin` (Task 2)
- [x] **Spec §5 AuthRepository interface** — all 7 methods present: `signInAnonymously`, `signInWithEmail`, `signUpWithEmail`, `signOut`, `currentUser`, `isAnonymous`, `currentUserId` (Task 2)
- [x] **Spec §6 Silent anon sign-in** — `viewModelScope.launch(Dispatchers.IO)` after `engine.start()`, swallows exceptions (Task 3)
- [x] **Spec §8 Out of scope** — email/Google UI, database schema, sync, sign-out all absent (confirmed)
- [x] **Type consistency** — `AuthRepository` instantiated as `authRepository` in Task 2 and `val authRepository = AuthRepository()` in Task 3
- [x] **No SaveData changes** — `SaveManager.save()` returns `Unit`, no cloud push in this subproject
