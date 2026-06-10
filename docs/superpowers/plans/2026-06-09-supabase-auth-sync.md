# Supabase Auth + Cloud Save Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Supabase email + Google authentication and cloud save sync to YHWH, mirroring the Pokedex pattern, with a player-facing conflict resolution dialog when both a local and cloud save exist.

**Architecture:** `SupabaseModule` singleton → `AuthRepository` (auth operations) → `SyncRepository` (push/pull `game_saves` table, returns `SyncResult`) → `GameViewModel` (orchestrates engine + sync + exposes `authState`/`conflictState`) → `ProfileScreen` (auth UI) + `ConflictDialog` in `GameScreen`.

**Tech Stack:** Supabase Kotlin SDK 2.6.1 (`auth-kt`, `postgrest-kt`, `compose-auth`), Ktor Android 3.0.3, `kotlinx.serialization`, Kotlin Coroutines, Jetpack Compose Material3

---

## Prerequisites (Manual — Before Any Code)

1. **Supabase SQL** — Run the schema from Task 1 in the Supabase dashboard SQL editor
2. **Google Cloud Console** — Create an OAuth 2.0 Web Client ID for Google Sign-In:
   - Go to console.cloud.google.com → APIs & Services → Credentials → Create OAuth client ID
   - Type: **Web application** (used as `serverClientId` for Android native Google Sign-In)
   - Note the Client ID — this is `GOOGLE_SERVER_CLIENT_ID`
3. **`local.properties`** — Add credentials to `local.properties` (never committed):
   ```
   SUPABASE_URL=https://qwresuyroqzyxbqrvrdh.supabase.co
   SUPABASE_ANON_KEY=sb_publishable_wjOQSsIfe7GVZheX2UTc2g_SITcXNzX
   GOOGLE_SERVER_CLIENT_ID=<your-web-client-id>.apps.googleusercontent.com
   ```

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SupabaseModule.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/AuthRepository.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt`

**Modified:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`

---

## Task 1: Supabase Database Schema

**Files:** None (manual step in Supabase dashboard)

- [ ] **Step 1: Run the following SQL in the Supabase SQL editor**

Navigate to: https://supabase.com/dashboard/project/qwresuyroqzyxbqrvrdh/sql/new

Paste and run:

```sql
create table game_saves (
  user_id       uuid references auth.users(id) on delete cascade primary key,
  save_json     text    not null,
  tick          bigint  not null default 0,
  epoch         text    not null default 'COSMOLOGY',
  last_saved_at bigint  not null
);

alter table game_saves enable row level security;

create policy "Users manage own save"
  on game_saves for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

- [ ] **Step 2: Verify the table was created**

In the Supabase Table Editor, confirm `game_saves` appears with the correct columns and RLS enabled.

---

## Task 2: Gradle Dependencies + BuildConfig

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add Supabase + Ktor to libs.versions.toml**

Replace the entire file with:

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

Replace the entire file with:

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
        versionName = "0.1.0"
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

Replace the entire file with:

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

- [ ] **Step 4: Verify build with new dependencies**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml \
        app/build.gradle.kts \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add Supabase + Ktor dependencies and BuildConfig credentials"
```

---

## Task 3: SupabaseModule + AuthRepository

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SupabaseModule.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/AuthRepository.kt`

- [ ] **Step 1: Create SupabaseModule.kt**

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

    fun isAnonymous(): Boolean = auth.currentUserOrNull()?.role == "anon"
        || auth.currentUserOrNull() == null

    fun currentUserId(): String? = auth.currentUserOrNull()?.id
}
```

- [ ] **Step 3: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/data/
git commit -m "feat: add SupabaseModule and AuthRepository"
```

---

## Task 4: SyncRepository

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.data.remote

import org.junit.Assert.*
import org.junit.Test

class SyncRepositoryTest {

    @Test fun `ConflictState None is initial default`() {
        assertEquals(ConflictState.None, ConflictState.None)
    }

    @Test fun `ConflictState Pending holds both saves`() {
        val row = RemoteSaveRow(
            userId = "uid", saveJson = "{}", tick = 100L,
            epoch = "COSMOLOGY", lastSavedAt = 999L
        )
        // Verify the data class holds values correctly
        assertEquals(100L, row.tick)
        assertEquals("COSMOLOGY", row.epoch)
    }

    @Test fun `SyncResult types are distinct`() {
        val noAction: SyncResult = SyncResult.NoAction
        assertTrue(noAction is SyncResult.NoAction)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.data.remote.SyncRepositoryTest" 2>&1 | tail -10
```

Expected: compilation error — types don't exist yet.

- [ ] **Step 3: Create SyncRepository.kt**

```kotlin
package com.madmaxlgndklr.yhwh.data.remote

import android.util.Log
import com.madmaxlgndklr.yhwh.persistence.SaveData
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── Remote DTO ────────────────────────────────────────────────────────────────

@Serializable
data class RemoteSaveRow(
    @SerialName("user_id")       val userId: String,
    @SerialName("save_json")     val saveJson: String,
    val tick: Long,
    val epoch: String,
    @SerialName("last_saved_at") val lastSavedAt: Long
)

// ── State types ───────────────────────────────────────────────────────────────

sealed class ConflictState {
    object None : ConflictState()
    data class Pending(val local: SaveData, val cloud: RemoteSaveRow) : ConflictState()
    object Resolved : ConflictState()
}

sealed class SyncResult {
    object NoAction : SyncResult()
    data class CloudRestoreAvailable(val savedData: SaveData) : SyncResult()
    object PushedToCloud : SyncResult()
    data class ConflictDetected(val local: SaveData, val cloud: RemoteSaveRow) : SyncResult()
}

// ── Repository ────────────────────────────────────────────────────────────────

class SyncRepository(
    private val authRepository: AuthRepository,
    private val saveManager: SaveManager
) {
    private val pg = SupabaseModule.client.postgrest
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Pull cloud save, compare with local save, return a [SyncResult] describing
     * what action the caller should take. Does NOT touch the local file or engine.
     */
    suspend fun syncOnOpen(): SyncResult {
        val uid = authRepository.currentUserId() ?: return SyncResult.NoAction
        return try {
            val cloudRow = pg.from("game_saves")
                .select(Columns.list("user_id", "save_json", "tick", "epoch", "last_saved_at")) {
                    filter { eq("user_id", uid) }
                }
                .decodeSingleOrNull<RemoteSaveRow>()

            val localSave = saveManager.load()

            when {
                cloudRow == null && localSave == null -> SyncResult.NoAction
                cloudRow == null && localSave != null -> {
                    pushSave(localSave)
                    SyncResult.PushedToCloud
                }
                cloudRow != null && localSave == null -> {
                    SyncResult.CloudRestoreAvailable(decodeSaveData(cloudRow.saveJson))
                }
                else -> SyncResult.ConflictDetected(localSave!!, cloudRow!!)
            }
        } catch (e: Exception) {
            Log.e("SyncRepo", "syncOnOpen failed", e)
            SyncResult.NoAction
        }
    }

    /** Upsert the given save data to the cloud. Silently swallows network errors. */
    suspend fun pushSave(data: SaveData) {
        val uid = authRepository.currentUserId() ?: return
        try {
            val encoded = json.encodeToString(data)
            pg.from("game_saves").upsert(buildJsonObject {
                put("user_id", uid)
                put("save_json", encoded)
                put("tick", data.snapshot.tick)
                put("epoch", data.snapshot.epoch.name)
                put("last_saved_at", data.lastTickTimestamp)
            })
        } catch (e: Exception) {
            Log.e("SyncRepo", "pushSave failed", e)
        }
    }

    /** Decode a raw JSON string into a [SaveData] for engine restoration. */
    fun decodeSaveData(rawJson: String): SaveData =
        json.decodeFromString<SaveData>(rawJson)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.data.remote.SyncRepositoryTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run full suite**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: 41 tests (38 existing + 3 new), 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTest.kt
git commit -m "feat: add SyncRepository with ConflictState and SyncResult types"
```

---

## Task 5: GameViewModel Supabase Integration

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

- [ ] **Step 1: Replace GameViewModel.kt with the updated version**

The key additions: `authRepository`, `syncRepository`, `authState`, `conflictState`, `authLoading`, `authError`; async Supabase init in `init` block after engine starts; updated `onSaveDue` to push to cloud; new `resolveConflict`, `signInWithEmail`, `signUpWithEmail`, `signOut`, `clearAuthError`, `updateAuthState`.

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

/** Auth status exposed to ProfileScreen and SettingsScreen. */
sealed class AuthState {
    object Anonymous : AuthState()
    data class SignedIn(val email: String?) : AuthState()
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveFile = File(application.filesDir, "yhwh_save.json")
    val saveManager = SaveManager(saveFile)         // internal — accessible to SyncRepository
    private val engine = GameEngine(scope = viewModelScope)
    private val tutorialPrefs = TutorialPrefs(application)
    val authRepository = AuthRepository()
    private val syncRepository = SyncRepository(authRepository, saveManager)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var epochTransitionAcknowledged = false

    private val _cosmosState = MutableStateFlow(CosmosState())
    val cosmosState: StateFlow<CosmosState> = _cosmosState.asStateFlow()

    private val _conflictState = MutableStateFlow<ConflictState>(ConflictState.None)
    val conflictState: StateFlow<ConflictState> = _conflictState.asStateFlow()

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
            withContext(Dispatchers.IO) {
                val data = saveManager.save(snapshot)
                // Also push to cloud if signed in
                if (!authRepository.isAnonymous()) {
                    val saved = saveManager.load()
                    if (saved != null) syncRepository.pushSave(saved)
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
                    transitionMessage = if (showTransition)
                        "A world has formed. Life stirs in the primordial ocean."
                    else _uiState.value.transitionMessage,
                    offlineEarningsSummary = _uiState.value.offlineEarningsSummary,
                    tutorialStep = _uiState.value.tutorialStep
                )
                _cosmosState.value = snapshot.toCosmosState()
            }
        }

        // Restore from local save or start new game
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

        // Async: sign in and sync (non-blocking, after engine starts)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authRepository.signInAnonymously()
                updateAuthState()
                if (!authRepository.isAnonymous()) {
                    performSync()
                }
            } catch (e: Exception) {
                Log.e("GameViewModel", "Supabase init failed", e)
            }
        }
    }

    private suspend fun performSync() {
        when (val result = syncRepository.syncOnOpen()) {
            is SyncResult.CloudRestoreAvailable -> {
                withContext(Dispatchers.Main) {
                    engine.stop()
                    saveManager.save(result.savedData.snapshot, result.savedData.lastTickTimestamp)
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
                // Use local — push local to overwrite cloud
                syncRepository.pushSave(pending.local)
            }
            withContext(Dispatchers.Main) {
                _conflictState.value = ConflictState.Resolved
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
            } catch (e: Exception) {
                _authError.value = e.message ?: "Sign up failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authRepository.signOut()
                updateAuthState()
            } catch (e: Exception) {
                Log.e("GameViewModel", "signOut failed", e)
            }
        }
    }

    fun clearAuthError() { _authError.value = null }

    fun onGoogleSignInSuccess() {
        viewModelScope.launch(Dispatchers.IO) {
            updateAuthState()
            performSync()
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

> **Note:** `SaveManager.save()` currently returns `Unit`. The `engine.onSaveDue` block calls `saveManager.save(snapshot)` then `saveManager.load()` to get the `SaveData` for cloud push. This is slightly redundant (save + re-read) but correct and simple.

- [ ] **Step 2: Verify compilation + tests**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 41 tests passing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: integrate Supabase auth and sync into GameViewModel"
```

---

## Task 6: ProfileScreen

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt`

- [ ] **Step 1: Create ProfileScreen.kt**

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            when (authState) {
                is AuthState.SignedIn -> {
                    val signedIn = authState as AuthState.SignedIn
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A4E))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text("Signed in", fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f))
                            Text(
                                text = signedIn.email ?: "Google Account",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Button(
                        onClick = viewModel::signOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4466AA)
                        ),
                        modifier = Modifier.fillMaxWidth()
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

                    // Sign in / Create account tabs
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

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp
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
git commit -m "feat: add ProfileScreen with email and Google sign-in"
```

---

## Task 7: SettingsScreen + AppNavigation

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`

- [ ] **Step 1: Replace SettingsScreen.kt**

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

- [ ] **Step 3: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt
git commit -m "feat: add Account row to SettingsScreen and PROFILE route to navigation"
```

---

## Task 8: GameScreen ConflictDialog + Build + Install

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`

- [ ] **Step 1: Add ConflictDialog to GameScreen.kt**

Replace the entire file:

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
                modifier = Modifier.weight(1f).fillMaxWidth()
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
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(color = Color.Black.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize()) {}
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(text = "♁", fontSize = 64.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = uiState.transitionMessage,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, color = Color.White
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = viewModel::dismissEpochTransition) { Text("Continue") }
                }
            }
        }

        // Tutorial coach-mark overlay
        if (uiState.tutorialStep in 1..3) {
            TutorialOverlay(step = uiState.tutorialStep, onNext = viewModel::onTutorialNext)
        }

        // Save conflict dialog
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
    val fmt = SimpleDateFormat("MMM d  HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = { /* not dismissible without choosing */ },
        title = {
            Text("Two saves found", fontWeight = FontWeight.Bold)
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaveCard(
                    label = "This Device",
                    epoch = local.snapshot.epoch.displayName,
                    tick = local.snapshot.tick,
                    timestamp = fmt.format(Date(local.lastTickTimestamp)),
                    onSelect = onUseLocal,
                    modifier = Modifier.weight(1f)
                )
                SaveCard(
                    label = "Cloud",
                    epoch = cloud.epoch,
                    tick = cloud.tick,
                    timestamp = fmt.format(Date(cloud.lastSavedAt)),
                    onSelect = onUseCloud,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {},   // buttons are inside the cards
        containerColor = Color(0xFF1A1A4E)
    )
}

@Composable
private fun SaveCard(
    label: String,
    epoch: String,
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
            Text(epoch, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Tick ${"%,d".format(tick)}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
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

- [ ] **Step 2: Full build + tests**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 41 tests, 0 failures.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt
git commit -m "feat: add ConflictDialog to GameScreen for save conflict resolution"
```

- [ ] **Step 4: Connect device and install**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
# If no device: connect via wireless debugging first
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 5: Push to GitHub**

```bash
git push origin main
```

---

## Self-Review

- [x] **Spec §2 Architecture** — `SupabaseModule`, `AuthRepository`, `SyncRepository`, `ProfileScreen` all created; all modified files listed (Task 3–8)
- [x] **Spec §3 Database schema** — `game_saves` table with RLS, `user_id` PK, `save_json`, `tick`, `epoch`, `last_saved_at` (Task 1)
- [x] **Spec §4 Client config** — `SupabaseModule` with `Auth`, `Postgrest`, `ComposeAuth`/Google; credentials from `BuildConfig` via `local.properties` (Task 2–3)
- [x] **Spec §5 Anonymous sign-in on launch** — `authRepository.signInAnonymously()` in `GameViewModel.init` async block (Task 5)
- [x] **Spec §5 Sign out leaves local save intact** — `signOut()` only calls `authRepository.signOut()`, engine continues (Task 5)
- [x] **Spec §6 syncOnOpen flow** — `SyncResult` covers all 4 cases: no saves, cloud only, local only, both (Task 4)
- [x] **Spec §6 Push on periodic save** — `engine.onSaveDue` calls `syncRepository.pushSave()` when not anonymous (Task 5)
- [x] **Spec §6 ConflictState Pending** — emitted by `GameViewModel.performSync()` (Task 5)
- [x] **Spec §7 ConflictState.Pending holds local + cloud** — `data class Pending(local: SaveData, cloud: RemoteSaveRow)` (Task 4)
- [x] **Spec §8 ConflictDialog** — two cards with epoch, tick, formatted timestamp, "Use this" buttons (Task 8)
- [x] **Spec §9 ProfileScreen** — anonymous/signed-in states, email fields, Google button, loading, error (Task 6)
- [x] **Spec §10 SettingsScreen Account row** — shows email or "Not signed in", navigates to ProfileScreen (Task 7)
- [x] **Spec §11 Dependencies** — `auth-kt`, `postgrest-kt`, `compose-auth`, `ktor-client-android` at 2.6.1/3.0.3 (Task 2)
- [x] **Type consistency** — `ConflictState`, `SyncResult`, `AuthState`, `RemoteSaveRow` used consistently across Tasks 4–8
