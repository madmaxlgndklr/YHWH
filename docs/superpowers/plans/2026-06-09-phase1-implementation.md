# YHWH God Simulator — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the bedrock MVVM+ECS architecture and a fully playable Cosmology epoch for the YHWH God Simulator Android app.

**Architecture:** `GameEngine` owns an ECS `World` and emits an immutable `GameSnapshot` via `StateFlow` after each 1-TPS tick. `GameViewModel` maps snapshots to two flows: `GameUiState` (TopBar + tabs) and `CosmosState` (canvas only). `CosmologySystem` handles all Epoch 1 game logic. `SaveManager` persists state as JSON and computes offline delta on restore.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (Material 3), Compose BOM 2024.12.01, Navigation Compose 2.8.5, Lifecycle/ViewModel 2.8.7, kotlinx.serialization 1.7.3, Coroutines 1.9.0, JUnit 4.13.2, AGP 8.7.0

---

## File Map

**New files:**
```
gradle/libs.versions.toml
settings.gradle.kts
build.gradle.kts
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/madmaxlgndklr/yhwh/
  YhwhApplication.kt
  MainActivity.kt
  navigation/AppNavigation.kt
  engine/
    math/BigDouble.kt
    EpochType.kt
    ResourceType.kt
    GameEvent.kt
    Component.kt
    World.kt
    System.kt
    GameSnapshot.kt
    GameEngine.kt
  systems/
    CosmologySystem.kt
  persistence/
    SaveData.kt
    SaveManager.kt
  ui/
    state/GameUiState.kt
    state/CosmosState.kt
    GameViewModel.kt
    screen/GameScreen.kt
    screen/SettingsScreen.kt
    components/TopBar.kt
    components/CosmosCanvas.kt
    components/ActionPanel.kt
app/src/test/java/com/madmaxlgndklr/yhwh/
  engine/math/BigDoubleTest.kt
  engine/WorldTest.kt
  engine/GameEngineTest.kt
  systems/CosmologySystemTest.kt
  persistence/SaveManagerTest.kt
```

---

## Task 1: Project Scaffolding

**Files:**
- Create: `gradle/libs.versions.toml`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/YhwhApplication.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

> No tests for scaffolding. Verify with a clean build.

- [ ] **Step 1: Create the version catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.0"
kotlin = "2.1.0"
composeBom = "2024.12.01"
coroutines = "1.9.0"
lifecycle = "2.8.7"
navigation = "2.8.5"
serialization = "1.7.3"
activityCompose = "1.9.3"
junit = "4.13.2"
junitExt = "1.2.1"
espresso = "3.6.1"

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

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "YHWH"
include(":app")
```

- [ ] **Step 3: Create root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 4: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.madmaxlgndklr.yhwh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.madmaxlgndklr.yhwh"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
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

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(composeBom)
}
```

- [ ] **Step 5: Create AndroidManifest.xml**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

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

- [ ] **Step 6: Create resource files**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">YHWH</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.YHWH" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 7: Create YhwhApplication.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/YhwhApplication.kt`:
```kotlin
package com.madmaxlgndklr.yhwh

import android.app.Application

class YhwhApplication : Application()
```

- [ ] **Step 8: Create stub MainActivity.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/MainActivity.kt`:
```kotlin
package com.madmaxlgndklr.yhwh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { Text("YHWH") }
            }
        }
    }
}
```

- [ ] **Step 9: Verify the project builds**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add .
git commit -m "feat: scaffold Android project with Compose + ECS dependencies"
```

---

## Task 2: BigDouble Math Class

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/math/BigDouble.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/engine/math/BigDoubleTest.kt`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/madmaxlgndklr/yhwh/engine/math/BigDoubleTest.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine.math

import org.junit.Assert.*
import org.junit.Test

class BigDoubleTest {

    @Test fun `of constructs from double`() {
        val bd = BigDouble.of(1500.0)
        assertEquals(1.5, bd.mantissa, 1e-9)
        assertEquals(3, bd.exponent)
    }

    @Test fun `of constructs zero`() {
        assertEquals(BigDouble.ZERO, BigDouble.of(0.0))
    }

    @Test fun `addition same exponent`() {
        val a = BigDouble.of(1.5e10)
        val b = BigDouble.of(2.5e10)
        val result = a + b
        assertEquals(BigDouble.of(4.0e10).mantissa, result.mantissa, 1e-6)
        assertEquals(10, result.exponent)
    }

    @Test fun `addition different exponent`() {
        val a = BigDouble.of(1.0e10)
        val b = BigDouble.of(1.0e3)
        val result = a + b
        // 1.0e10 dominates; 1e3 is negligible at this scale
        assertEquals(10, result.exponent)
    }

    @Test fun `subtraction result clamps to zero`() {
        val a = BigDouble.of(5.0)
        val b = BigDouble.of(10.0)
        assertEquals(BigDouble.ZERO, a - b)
    }

    @Test fun `subtraction normal case`() {
        val a = BigDouble.of(10.0)
        val b = BigDouble.of(3.0)
        val result = a - b
        assertEquals(7.0, result.toDouble(), 1e-6)
    }

    @Test fun `multiplication`() {
        val a = BigDouble.of(2.0e5)
        val b = BigDouble.of(3.0e5)
        val result = a * b
        assertEquals(6.0, result.mantissa, 1e-9)
        assertEquals(10, result.exponent)
    }

    @Test fun `division`() {
        val a = BigDouble.of(6.0e10)
        val b = BigDouble.of(2.0e5)
        val result = a / b
        assertEquals(3.0, result.mantissa, 1e-9)
        assertEquals(5, result.exponent)
    }

    @Test fun `compareTo larger exponent wins`() {
        val a = BigDouble.of(1.0e10)
        val b = BigDouble.of(9.99e9)
        assertTrue(a > b)
    }

    @Test fun `compareTo same exponent uses mantissa`() {
        val a = BigDouble.of(2.5e5)
        val b = BigDouble.of(1.5e5)
        assertTrue(a > b)
    }

    @Test fun `toDisplayString under 1000`() {
        assertEquals("500.00", BigDouble.of(500.0).toDisplayString())
    }

    @Test fun `toDisplayString thousands`() {
        assertEquals("1.50K", BigDouble.of(1500.0).toDisplayString())
    }

    @Test fun `toDisplayString millions`() {
        assertEquals("2.50M", BigDouble.of(2_500_000.0).toDisplayString())
    }

    @Test fun `toDisplayString scientific`() {
        val bd = BigDouble.of(1.23e45)
        assertEquals("1.23e45", bd.toDisplayString())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.engine.math.BigDoubleTest" 2>&1 | tail -20
```

Expected: compilation error — `BigDouble` does not exist yet.

- [ ] **Step 3: Implement BigDouble**

`app/src/main/java/com/madmaxlgndklr/yhwh/engine/math/BigDouble.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine.math

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Fixed-precision large number representation: mantissa × 10^exponent.
 * Mantissa is always normalized to [1.0, 10.0) except for ZERO.
 * Supports only non-negative values (resource amounts never go below zero).
 */
@Serializable
data class BigDouble(val mantissa: Double, val exponent: Int) : Comparable<BigDouble> {

    companion object {
        val ZERO = BigDouble(0.0, 0)
        val ONE = BigDouble(1.0, 0)

        fun of(value: Double): BigDouble {
            if (!value.isFinite() || value == 0.0) return ZERO
            val exp = floor(log10(abs(value))).toInt()
            val mant = value / 10.0.pow(exp)
            return normalize(BigDouble(mant, exp))
        }

        fun normalize(bd: BigDouble): BigDouble {
            if (bd.mantissa == 0.0 || !bd.mantissa.isFinite()) return ZERO
            var m = bd.mantissa
            var e = bd.exponent
            while (abs(m) >= 10.0) { m /= 10.0; e++ }
            while (abs(m) < 1.0) { m *= 10.0; e-- }
            return BigDouble(m, e)
        }
    }

    operator fun plus(other: BigDouble): BigDouble {
        if (this == ZERO) return other
        if (other == ZERO) return this
        val expDiff = exponent - other.exponent
        return when {
            expDiff > 15 -> this
            expDiff < -15 -> other
            else -> normalize(
                BigDouble(mantissa + other.mantissa * 10.0.pow(-expDiff), exponent)
            )
        }
    }

    operator fun minus(other: BigDouble): BigDouble {
        if (other == ZERO) return this
        if (other >= this) return ZERO
        val expDiff = exponent - other.exponent
        return if (expDiff > 15) this
        else normalize(BigDouble(mantissa - other.mantissa * 10.0.pow(-expDiff), exponent))
    }

    operator fun times(other: BigDouble): BigDouble {
        if (this == ZERO || other == ZERO) return ZERO
        return normalize(BigDouble(mantissa * other.mantissa, exponent + other.exponent))
    }

    operator fun div(other: BigDouble): BigDouble {
        require(other != ZERO) { "BigDouble division by zero" }
        return normalize(BigDouble(mantissa / other.mantissa, exponent - other.exponent))
    }

    override fun compareTo(other: BigDouble): Int {
        if (exponent != other.exponent) return exponent.compareTo(other.exponent)
        return mantissa.compareTo(other.mantissa)
    }

    fun toDouble(): Double = mantissa * 10.0.pow(exponent)

    fun toDisplayString(): String = when {
        exponent < 3 -> "%.2f".format(toDouble())
        exponent < 6 -> "%.2fK".format(mantissa * 10.0.pow(exponent - 3))
        exponent < 9 -> "%.2fM".format(mantissa * 10.0.pow(exponent - 6))
        exponent < 12 -> "%.2fB".format(mantissa * 10.0.pow(exponent - 9))
        exponent < 15 -> "%.2fT".format(mantissa * 10.0.pow(exponent - 12))
        else -> "%.2fe%d".format(mantissa, exponent)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.engine.math.BigDoubleTest"
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/math/BigDouble.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/engine/math/BigDoubleTest.kt
git commit -m "feat: add BigDouble math class for large resource numbers"
```

---

## Task 3: ECS Foundation — Component, World

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/EpochType.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/Component.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/World.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/engine/WorldTest.kt`

- [ ] **Step 1: Create EpochType.kt**

```kotlin
package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
enum class EpochType(val displayName: String) {
    COSMOLOGY("Cosmology"),
    BIOLOGY("Biology"),
    EVOLUTION("Evolution"),
    CIVILIZATION("Civilization"),
    INTERSTELLAR("Interstellar")
}
```

- [ ] **Step 2: Create ResourceType.kt**

```kotlin
package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ResourceType(val displayName: String, val symbol: String) {
    ENERGY("Energy", "⚡"),
    MATTER("Matter", "⬡"),
    HYDROGEN("Hydrogen", "H"),
    STARS("Stars", "★"),
    ACCRETION_DISKS("Accretion Disks", "◎"),
    PLANETS("Planets", "♁")
}
```

- [ ] **Step 3: Create Component.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/engine/Component.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.serialization.Serializable

/** Base marker for all ECS components. Each component is pure data. */
@Serializable
sealed interface Component

@Serializable
data class ResourceComponent(
    val type: ResourceType,
    var amount: BigDouble
) : Component

@Serializable
data class GeneratorComponent(
    val id: String,
    val productionType: ResourceType,
    var productionRate: BigDouble,
    val costType: ResourceType,
    var costAmount: BigDouble,
    var unlocked: Boolean,
    var level: Int = 1
) : Component

@Serializable
data class UpgradeComponent(
    val id: String,
    var purchased: Boolean,
    val costType: ResourceType,
    var costAmount: BigDouble,
    val effect: UpgradeEffect,
    val repeatable: Boolean = false
) : Component

@Serializable
data class EpochComponent(
    var epoch: EpochType,
    var progress: Float,
    var tick: Long
) : Component

/** Describes what an upgrade does when applied. */
@Serializable
sealed class UpgradeEffect {
    /** Multiply a generator's production rate by [multiplier]. */
    @Serializable
    data class MultiplyProduction(val generatorId: String, val multiplier: BigDouble) : UpgradeEffect()

    /** Set a generator's [unlocked] flag to true. */
    @Serializable
    data class UnlockGenerator(val generatorId: String) : UpgradeEffect()

    /** Multiply the tap production for the manual fluctuation action. */
    @Serializable
    data class MultiplyTapProduction(val multiplier: BigDouble) : UpgradeEffect()

    /** Manual conversion: spend [inputAmount] of [inputType], gain [outputAmount] of [outputType]. */
    @Serializable
    data class ManualConversion(
        val inputType: ResourceType,
        var inputAmount: BigDouble,
        val outputType: ResourceType,
        val outputAmount: BigDouble
    ) : UpgradeEffect()

    /** Reduce the cost of a ManualConversion upgrade by [multiplier] (e.g., 0.5 = -50%). */
    @Serializable
    data class ReduceConversionCost(val targetUpgradeId: String, val multiplier: BigDouble) : UpgradeEffect()
}
```

- [ ] **Step 4: Write the failing World tests**

`app/src/test/java/com/madmaxlgndklr/yhwh/engine/WorldTest.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Test

class WorldTest {

    @Test fun `put and retrieve component by key`() {
        val world = World()
        val comp = ResourceComponent(ResourceType.ENERGY, BigDouble.of(100.0))
        world.put("res_energy", comp)
        val retrieved = world.get<ResourceComponent>("res_energy")
        assertNotNull(retrieved)
        assertEquals(ResourceType.ENERGY, retrieved!!.type)
    }

    @Test fun `get returns null for missing key`() {
        val world = World()
        assertNull(world.get<ResourceComponent>("missing"))
    }

    @Test fun `getAll returns all components of given type`() {
        val world = World()
        world.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.ONE))
        world.put("res_matter", ResourceComponent(ResourceType.MATTER, BigDouble.ONE))
        world.put("gen_nebula", GeneratorComponent(
            id = "gen_nebula", productionType = ResourceType.MATTER,
            productionRate = BigDouble.ONE, costType = ResourceType.ENERGY,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        val resources = world.getAll<ResourceComponent>()
        assertEquals(2, resources.size)
    }

    @Test fun `remove deletes a component`() {
        val world = World()
        world.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.ONE))
        world.remove("res_energy")
        assertNull(world.get<ResourceComponent>("res_energy"))
    }

    @Test fun `toSnapshot round-trips all components`() {
        val world = World()
        world.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.of(50.0)))
        val snapshot = world.toSnapshot()
        assertEquals(1, snapshot.size)
        assertTrue(snapshot.containsKey("res_energy"))
    }

    @Test fun `fromSnapshot restores world state`() {
        val original = World()
        original.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.of(50.0)))
        val restored = World.fromSnapshot(original.toSnapshot())
        val comp = restored.get<ResourceComponent>("res_energy")
        assertNotNull(comp)
        assertEquals(50.0, comp!!.amount.toDouble(), 1e-6)
    }
}
```

- [ ] **Step 5: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.engine.WorldTest" 2>&1 | tail -10
```

Expected: compilation error — `World` does not exist.

- [ ] **Step 6: Implement World.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/engine/World.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine

/**
 * Flat-map ECS world. Each string key uniquely identifies one component
 * (e.g. "res_energy", "gen_nebula", "upg_particle_density", "epoch").
 * Keys are defined as constants in the system that owns them (CosmologySystem).
 */
class World {
    private val state: MutableMap<String, Component> = mutableMapOf()

    fun put(key: String, component: Component) { state[key] = component }

    inline fun <reified T : Component> get(key: String): T? = state[key] as? T

    inline fun <reified T : Component> getAll(): List<Pair<String, T>> =
        state.entries.mapNotNull { (k, v) -> (v as? T)?.let { k to it } }

    fun remove(key: String) { state.remove(key) }

    fun toSnapshot(): Map<String, Component> = state.toMap()

    companion object {
        fun fromSnapshot(snapshot: Map<String, Component>): World {
            val world = World()
            snapshot.forEach { (key, component) -> world.put(key, component) }
            return world
        }
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.engine.WorldTest"
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/ \
        app/src/test/java/com/madmaxlgndklr/yhwh/engine/WorldTest.kt
git commit -m "feat: add ECS foundation — Component types, World, EpochType, ResourceType"
```

---

## Task 4: Game Types — Snapshot & Events

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEvent.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/System.kt`

> These are pure data/interface types. No unit tests needed beyond compilation.

- [ ] **Step 1: Create GameEvent.kt**

```kotlin
package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
data class GameEvent(
    val tick: Long,
    val message: String,
    val isMilestone: Boolean = false
)
```

- [ ] **Step 2: Create GameSnapshot.kt**

```kotlin
package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.serialization.Serializable

@Serializable
data class GeneratorSnapshot(
    val id: String,
    val displayName: String,
    val productionType: ResourceType,
    val productionRate: BigDouble,
    val costType: ResourceType,
    val costAmount: BigDouble,
    val unlocked: Boolean,
    val level: Int
)

@Serializable
data class UpgradeSnapshot(
    val id: String,
    val displayName: String,
    val description: String,
    val costType: ResourceType,
    val costAmount: BigDouble,
    val purchased: Boolean,
    val repeatable: Boolean,
    /** True if the player can afford and requirements are met. */
    val available: Boolean
)

/** Immutable snapshot of all game state, emitted by GameEngine each tick. */
@Serializable
data class GameSnapshot(
    val tick: Long,
    val epoch: EpochType,
    val resources: Map<ResourceType, BigDouble>,
    val generators: List<GeneratorSnapshot>,
    val upgrades: List<UpgradeSnapshot>,
    /** 0f–1f. Reaches 1.0 when epoch win condition is met. */
    val epochProgress: Float,
    /** Events generated this tick only (not cumulative). */
    val events: List<GameEvent>
)
```

- [ ] **Step 3: Create System.kt**

```kotlin
package com.madmaxlgndklr.yhwh.engine

/**
 * Interface for pluggable epoch systems. Each epoch implements this.
 * GameEngine calls tick() every game tick and initialize() once on new game.
 */
interface GameSystem {
    /** Called once when starting a fresh game for this epoch. Populates [world] with initial entities. */
    fun initialize(world: World)

    /** Called every tick. Must be delta-aware: all production × [delta]. */
    fun tick(world: World, delta: Long): List<GameEvent>

    /** Converts current [world] state to a [GameSnapshot] for emission. */
    fun toSnapshot(world: World, tick: Long): GameSnapshot
}
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/
git commit -m "feat: add game types — GameSnapshot, GameEvent, GameSystem interface"
```

---

## Task 5: GameEngine

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineTest.kt`

- [ ] **Step 1: Write the failing GameEngine tests**

`app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineTest.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineTest {

    private fun makeTestSystem(initialSnapshot: GameSnapshot) = object : GameSystem {
        var tickCount = 0
        var lastDelta = 0L

        override fun initialize(world: World) {}

        override fun tick(world: World, delta: Long): List<GameEvent> {
            tickCount++
            lastDelta = delta
            return emptyList()
        }

        override fun toSnapshot(world: World, tick: Long): GameSnapshot =
            initialSnapshot.copy(tick = tick)
    }

    private fun baseSnapshot() = GameSnapshot(
        tick = 0,
        epoch = EpochType.COSMOLOGY,
        resources = mapOf(ResourceType.ENERGY to BigDouble.ZERO),
        generators = emptyList(),
        upgrades = emptyList(),
        epochProgress = 0f,
        events = emptyList()
    )

    @Test fun `engine emits initial snapshot before first tick`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L)
        val system = makeTestSystem(baseSnapshot())
        engine.registerSystem(system)
        engine.initNewGame()

        val snapshot = engine.snapshot.first()
        assertEquals(EpochType.COSMOLOGY, snapshot.epoch)
    }

    @Test fun `engine increments tick on each cycle`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L, scope = this)
        val system = makeTestSystem(baseSnapshot())
        engine.registerSystem(system)
        engine.initNewGame()
        engine.start()

        advanceTimeBy(3100L)
        assertTrue(system.tickCount >= 3)
        engine.stop()
    }

    @Test fun `restore applies offline delta`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L)
        val system = makeTestSystem(baseSnapshot())
        engine.registerSystem(system)
        engine.restore(baseSnapshot(), missedTicks = 100L)

        assertEquals(100L, system.lastDelta)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.engine.GameEngineTest" 2>&1 | tail -10
```

Expected: compilation error — `GameEngine` does not exist.

- [ ] **Step 3: Implement GameEngine.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core game loop. Ticks at [tickIntervalMs] and emits an immutable [GameSnapshot]
 * after each tick. All world mutations happen here and nowhere else.
 *
 * [scope] is injectable for testing; production code uses the default background scope.
 */
class GameEngine(
    private val tickIntervalMs: Long = 1000L,
    private val maxOfflineTicks: Long = 28_800L, // 8 hours
    private val saveEveryNTicks: Int = 30,
    scope: CoroutineScope? = null
) {
    private val engineScope = scope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val world = World()
    private val systems = mutableListOf<GameSystem>()
    private var tickCount = 0L
    private var tickJob: Job? = null
    private var onSaveDue: (suspend (GameSnapshot) -> Unit)? = null

    private val _snapshot = MutableStateFlow<GameSnapshot?>(null)
    val snapshot: StateFlow<GameSnapshot?> = _snapshot.asStateFlow()

    fun registerSystem(system: GameSystem) { systems.add(system) }

    fun setOnSaveDue(callback: suspend (GameSnapshot) -> Unit) { onSaveDue = callback }

    /** Call on fresh game start (no save file). Initializes world via each system. */
    fun initNewGame() {
        systems.forEach { it.initialize(world) }
        emitSnapshot(delta = 0L)
    }

    /** Call on restore from save. Applies offline delta in a single batch tick. */
    fun restore(savedSnapshot: GameSnapshot, missedTicks: Long) {
        World.fromSnapshot(
            savedSnapshot.resources.entries.associate { (type, amount) ->
                "res_${type.name.lowercase()}" to ResourceComponent(type, amount)
            } + savedSnapshot.generators.associate { gen ->
                "gen_${gen.id}" to GeneratorComponent(
                    id = gen.id,
                    productionType = gen.productionType,
                    productionRate = gen.productionRate,
                    costType = gen.costType,
                    costAmount = gen.costAmount,
                    unlocked = gen.unlocked,
                    level = gen.level
                )
            } + savedSnapshot.upgrades.associate { upg ->
                "upg_${upg.id}" to systems.flatMap { _ ->
                    emptyList<Pair<String, Component>>()
                }.toMap().let {
                    // Upgrades are re-initialized by the system; purchased state is patched below
                    null
                }
            }.filterValues { it != null }.mapValues { it.value!! }
        ).also { restored ->
            // Delegate full restore to the active system (it knows the component structure)
            systems.forEach { it.initialize(world) }
            // Patch purchased flags from snapshot
            savedSnapshot.upgrades.filter { it.purchased }.forEach { upg ->
                world.get<UpgradeComponent>("upg_${upg.id}")?.purchased = true
            }
            // Patch resource amounts
            savedSnapshot.resources.forEach { (type, amount) ->
                world.get<ResourceComponent>("res_${type.name.lowercase()}")?.amount = amount
            }
        }
        tickCount = savedSnapshot.tick
        val clampedDelta = missedTicks.coerceAtMost(maxOfflineTicks)
        if (clampedDelta > 0L) runTick(clampedDelta)
        emitSnapshot(delta = 0L)
    }

    fun start() {
        tickJob = engineScope.launch {
            while (true) {
                delay(tickIntervalMs)
                runTick(delta = 1L)
                emitSnapshot(delta = 1L)
                if (tickCount % saveEveryNTicks == 0L) {
                    _snapshot.value?.let { onSaveDue?.invoke(it) }
                }
            }
        }
    }

    fun stop() { tickJob?.cancel() }

    /** Process a tap action from the player (manual Quantum Fluctuation). */
    fun onPlayerTap() {
        systems.forEach { (it as? CosmologyAware)?.onTap(world) }
        emitSnapshot(delta = 0L)
    }

    /** Process an upgrade purchase. */
    fun purchaseUpgrade(upgradeId: String) {
        systems.forEach { (it as? CosmologyAware)?.purchaseUpgrade(world, upgradeId) }
        emitSnapshot(delta = 0L)
    }

    /** Process a generator level-up purchase. */
    fun purchaseGenerator(generatorId: String) {
        systems.forEach { (it as? CosmologyAware)?.purchaseGenerator(world, generatorId) }
        emitSnapshot(delta = 0L)
    }

    private fun runTick(delta: Long) {
        systems.forEach { it.tick(world, delta) }
        tickCount += delta
    }

    private fun emitSnapshot(delta: Long) {
        val snap = systems.firstOrNull()?.toSnapshot(world, tickCount) ?: return
        _snapshot.value = snap
    }
}

/** Optional interface for systems that handle direct player actions. */
interface CosmologyAware {
    fun onTap(world: World)
    fun purchaseUpgrade(world: World, upgradeId: String)
    fun purchaseGenerator(world: World, generatorId: String)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.engine.GameEngineTest"
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineTest.kt
git commit -m "feat: add GameEngine with tick loop, offline restore, and player action routing"
```

---

## Task 6: CosmologySystem

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemTest.kt`

- [ ] **Step 1: Write the failing CosmologySystem tests**

`app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemTest.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CosmologySystemTest {

    private lateinit var world: World
    private lateinit var system: CosmologySystem

    @Before fun setup() {
        world = World()
        system = CosmologySystem()
        system.initialize(world)
    }

    @Test fun `initialize populates energy resource`() {
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)
        assertNotNull(energy)
        assertEquals(ResourceType.ENERGY, energy!!.type)
    }

    @Test fun `initialize populates nebula generator unlocked`() {
        val nebula = world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_NEBULA)
        assertNotNull(nebula)
        assertTrue(nebula!!.unlocked)
    }

    @Test fun `initialize leaves fusion generator locked`() {
        val fusion = world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_FUSION)
        assertNotNull(fusion)
        assertFalse(fusion!!.unlocked)
    }

    @Test fun `tick with delta 1 produces energy passively`() {
        system.tick(world, delta = 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertTrue(energy.amount > BigDouble.ZERO)
    }

    @Test fun `nebula generator produces matter when energy is sufficient`() {
        // Give enough energy to run the nebula
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!.amount = BigDouble.of(1000.0)
        system.tick(world, delta = 1L)
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertTrue(matter.amount > BigDouble.ZERO)
    }

    @Test fun `onTap adds matter to resource pool`() {
        system.onTap(world)
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertTrue(matter.amount > BigDouble.ZERO)
    }

    @Test fun `purchaseUpgrade particle density doubles tap production`() {
        // Fund the upgrade
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!.amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, CosmologySystem.KEY_UPG_PARTICLE_DENSITY)

        val upg = world.get<UpgradeComponent>(CosmologySystem.KEY_UPG_PARTICLE_DENSITY)!!
        assertTrue(upg.purchased)
    }

    @Test fun `purchaseUpgrade nuclear ignition unlocks fusion generator`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!.amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, CosmologySystem.KEY_UPG_NUCLEAR_IGNITION)

        val fusion = world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_FUSION)!!
        assertTrue(fusion.unlocked)
    }

    @Test fun `epochProgress is 0 with no planets`() {
        val snapshot = system.toSnapshot(world, tick = 0)
        assertEquals(0f, snapshot.epochProgress, 0.01f)
    }

    @Test fun `epochProgress is 1 when planets greater than 0`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_PLANETS)!!.amount = BigDouble.ONE
        val snapshot = system.toSnapshot(world, tick = 0)
        assertEquals(1f, snapshot.epochProgress, 0.01f)
    }

    @Test fun `gravitational collapse converts disks to planet`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ACCRETION_DISKS)!!.amount = BigDouble.of(200.0)
        // Mark the upgrade as purchased (available)
        world.get<UpgradeComponent>(CosmologySystem.KEY_UPG_GRAVITATIONAL_COLLAPSE)!!.purchased = true
        system.purchaseUpgrade(world, CosmologySystem.KEY_UPG_GRAVITATIONAL_COLLAPSE)
        val planets = world.get<ResourceComponent>(CosmologySystem.KEY_RES_PLANETS)!!
        assertTrue(planets.amount >= BigDouble.ONE)
    }

    @Test fun `tick generates milestone event on first star`() {
        // Give enough resources to produce a star
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_STARS)!!.amount = BigDouble.ZERO
        world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_STELLAR)!!.unlocked = true
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_HYDROGEN)!!.amount = BigDouble.of(1000.0)
        val events = system.tick(world, delta = 1L)
        val stars = world.get<ResourceComponent>(CosmologySystem.KEY_RES_STARS)!!
        if (stars.amount > BigDouble.ZERO) {
            assertTrue(events.any { it.isMilestone })
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.systems.CosmologySystemTest" 2>&1 | tail -10
```

Expected: compilation error — `CosmologySystem` does not exist.

- [ ] **Step 3: Implement CosmologySystem.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble

/**
 * Implements all Cosmology epoch game logic: generator chain, upgrade effects,
 * win condition, and event generation.
 */
class CosmologySystem : GameSystem, CosmologyAware {

    companion object {
        // Resource keys
        const val KEY_RES_ENERGY = "res_energy"
        const val KEY_RES_MATTER = "res_matter"
        const val KEY_RES_HYDROGEN = "res_hydrogen"
        const val KEY_RES_STARS = "res_stars"
        const val KEY_RES_ACCRETION_DISKS = "res_accretion_disks"
        const val KEY_RES_PLANETS = "res_planets"

        // Generator keys
        const val KEY_GEN_NEBULA = "gen_nebula"
        const val KEY_GEN_FUSION = "gen_fusion"
        const val KEY_GEN_STELLAR = "gen_stellar"
        const val KEY_GEN_ACCRETION = "gen_accretion"

        // Upgrade keys
        const val KEY_UPG_PARTICLE_DENSITY = "upg_particle_density"
        const val KEY_UPG_NUCLEAR_IGNITION = "upg_nuclear_ignition"
        const val KEY_UPG_STELLAR_COMPRESSION = "upg_stellar_compression"
        const val KEY_UPG_PROTOPLANETARY = "upg_protoplanetary"
        const val KEY_UPG_GRAVITATIONAL_COLLAPSE = "upg_gravitational_collapse"
        const val KEY_UPG_TECTONIC_STABILIZATION = "upg_tectonic_stabilization"

        // Visual thresholds for CosmosState normalization
        const val MATTER_VISUAL_THRESHOLD = 1_000.0
        const val STAR_VISUAL_THRESHOLD = 100.0

        // Base tap production
        private val BASE_TAP_MATTER = BigDouble.ONE
        // Base passive energy generation per tick
        private val BASE_ENERGY_PER_TICK = BigDouble.of(5.0)
        // Gravitational Collapse cost
        private var COLLAPSE_COST = BigDouble.of(100.0)
    }

    private var firstStarEventFired = false
    private var firstPlanetEventFired = false

    override fun initialize(world: World) {
        // Resources (all start at zero except as noted)
        world.put(KEY_RES_ENERGY, ResourceComponent(ResourceType.ENERGY, BigDouble.ZERO))
        world.put(KEY_RES_MATTER, ResourceComponent(ResourceType.MATTER, BigDouble.ZERO))
        world.put(KEY_RES_HYDROGEN, ResourceComponent(ResourceType.HYDROGEN, BigDouble.ZERO))
        world.put(KEY_RES_STARS, ResourceComponent(ResourceType.STARS, BigDouble.ZERO))
        world.put(KEY_RES_ACCRETION_DISKS, ResourceComponent(ResourceType.ACCRETION_DISKS, BigDouble.ZERO))
        world.put(KEY_RES_PLANETS, ResourceComponent(ResourceType.PLANETS, BigDouble.ZERO))

        // Generators
        world.put(KEY_GEN_NEBULA, GeneratorComponent(
            id = KEY_GEN_NEBULA, productionType = ResourceType.MATTER,
            productionRate = BigDouble.ONE, costType = ResourceType.ENERGY,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        world.put(KEY_GEN_FUSION, GeneratorComponent(
            id = KEY_GEN_FUSION, productionType = ResourceType.HYDROGEN,
            productionRate = BigDouble.ONE, costType = ResourceType.MATTER,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))
        world.put(KEY_GEN_STELLAR, GeneratorComponent(
            id = KEY_GEN_STELLAR, productionType = ResourceType.STARS,
            productionRate = BigDouble.ONE, costType = ResourceType.HYDROGEN,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        world.put(KEY_GEN_ACCRETION, GeneratorComponent(
            id = KEY_GEN_ACCRETION, productionType = ResourceType.ACCRETION_DISKS,
            productionRate = BigDouble.ONE, costType = ResourceType.STARS,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))

        // Upgrades
        world.put(KEY_UPG_PARTICLE_DENSITY, UpgradeComponent(
            id = KEY_UPG_PARTICLE_DENSITY, purchased = false,
            costType = ResourceType.ENERGY, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_NUCLEAR_IGNITION, UpgradeComponent(
            id = KEY_UPG_NUCLEAR_IGNITION, purchased = false,
            costType = ResourceType.MATTER, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_FUSION)
        ))
        world.put(KEY_UPG_STELLAR_COMPRESSION, UpgradeComponent(
            id = KEY_UPG_STELLAR_COMPRESSION, purchased = false,
            costType = ResourceType.HYDROGEN, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_STELLAR, BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_PROTOPLANETARY, UpgradeComponent(
            id = KEY_UPG_PROTOPLANETARY, purchased = false,
            costType = ResourceType.STARS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_ACCRETION)
        ))
        world.put(KEY_UPG_GRAVITATIONAL_COLLAPSE, UpgradeComponent(
            id = KEY_UPG_GRAVITATIONAL_COLLAPSE, purchased = false,
            costType = ResourceType.ACCRETION_DISKS, costAmount = COLLAPSE_COST,
            effect = UpgradeEffect.ManualConversion(
                inputType = ResourceType.ACCRETION_DISKS,
                inputAmount = COLLAPSE_COST,
                outputType = ResourceType.PLANETS,
                outputAmount = BigDouble.ONE
            ),
            repeatable = true
        ))
        world.put(KEY_UPG_TECTONIC_STABILIZATION, UpgradeComponent(
            id = KEY_UPG_TECTONIC_STABILIZATION, purchased = false,
            costType = ResourceType.STARS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.ReduceConversionCost(
                targetUpgradeId = KEY_UPG_GRAVITATIONAL_COLLAPSE,
                multiplier = BigDouble.of(0.5)
            )
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())

        // Passive energy generation
        val energy = world.get<ResourceComponent>(KEY_RES_ENERGY)!!
        energy.amount = energy.amount + BASE_ENERGY_PER_TICK * bigDelta

        // Run unlocked generators
        runGenerator(world, KEY_GEN_NEBULA, bigDelta)
        runGenerator(world, KEY_GEN_FUSION, bigDelta)
        runGenerator(world, KEY_GEN_STELLAR, bigDelta)
        runGenerator(world, KEY_GEN_ACCRETION, bigDelta)

        // Milestone events
        val stars = world.get<ResourceComponent>(KEY_RES_STARS)!!
        if (!firstStarEventFired && stars.amount > BigDouble.ZERO) {
            firstStarEventFired = true
            events.add(GameEvent(0, "The first star ignites in the void.", isMilestone = true))
        }
        val planets = world.get<ResourceComponent>(KEY_RES_PLANETS)!!
        if (!firstPlanetEventFired && planets.amount > BigDouble.ZERO) {
            firstPlanetEventFired = true
            events.add(GameEvent(0, "A world coalesces from the accretion disk.", isMilestone = true))
        }

        return events
    }

    private fun runGenerator(world: World, key: String, delta: BigDouble) {
        val gen = world.get<GeneratorComponent>(key) ?: return
        if (!gen.unlocked) return
        val costRes = resourceFor(world, gen.costType) ?: return
        val totalCost = gen.costAmount * delta
        if (costRes.amount < totalCost) return
        costRes.amount = costRes.amount - totalCost
        val prodRes = resourceFor(world, gen.productionType) ?: return
        prodRes.amount = prodRes.amount + gen.productionRate * delta
    }

    private fun resourceFor(world: World, type: ResourceType): ResourceComponent? =
        world.get<ResourceComponent>("res_${type.name.lowercase()}")

    override fun onTap(world: World) {
        val tapProduction = currentTapProduction(world)
        val matter = world.get<ResourceComponent>(KEY_RES_MATTER)!!
        matter.amount = matter.amount + tapProduction
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        if (!upg.repeatable && upg.purchased) return

        when (val effect = upg.effect) {
            is UpgradeEffect.ManualConversion -> {
                // Spend input resource, gain output resource
                val inputRes = resourceFor(world, effect.inputType) ?: return
                if (inputRes.amount < effect.inputAmount) return
                inputRes.amount = inputRes.amount - effect.inputAmount
                val outputRes = resourceFor(world, effect.outputType) ?: return
                outputRes.amount = outputRes.amount + effect.outputAmount
            }
            else -> {
                // One-time purchase
                val costRes = resourceFor(world, upg.costType) ?: return
                if (costRes.amount < upg.costAmount) return
                costRes.amount = costRes.amount - upg.costAmount
                upg.purchased = true
                applyUpgradeEffect(world, upg)
            }
        }
    }

    private fun applyUpgradeEffect(world: World, upg: UpgradeComponent) {
        when (val effect = upg.effect) {
            is UpgradeEffect.UnlockGenerator -> {
                world.get<GeneratorComponent>(effect.generatorId)?.unlocked = true
            }
            is UpgradeEffect.MultiplyProduction -> {
                val gen = world.get<GeneratorComponent>(effect.generatorId) ?: return
                gen.productionRate = gen.productionRate * effect.multiplier
            }
            is UpgradeEffect.MultiplyTapProduction -> { /* applied dynamically in onTap */ }
            is UpgradeEffect.ReduceConversionCost -> {
                val target = world.get<UpgradeComponent>(effect.targetUpgradeId) ?: return
                target.costAmount = target.costAmount * effect.multiplier
                if (target.effect is UpgradeEffect.ManualConversion) {
                    (target.effect as UpgradeEffect.ManualConversion).inputAmount =
                        (target.effect as UpgradeEffect.ManualConversion).inputAmount * effect.multiplier
                }
                if (target.id == KEY_UPG_GRAVITATIONAL_COLLAPSE) {
                    COLLAPSE_COST = COLLAPSE_COST * effect.multiplier
                }
            }
            is UpgradeEffect.ManualConversion -> { /* handled in purchaseUpgrade */ }
        }
    }

    override fun purchaseGenerator(world: World, generatorId: String) {
        val gen = world.get<GeneratorComponent>(generatorId) ?: return
        if (!gen.unlocked) return
        val costRes = resourceFor(world, gen.costType) ?: return
        val levelUpCost = gen.costAmount * BigDouble.of(1.15.pow(gen.level.toDouble()))
        if (costRes.amount < levelUpCost) return
        costRes.amount = costRes.amount - levelUpCost
        gen.productionRate = gen.productionRate * BigDouble.of(1.1)
        gen.level++
    }

    private fun currentTapProduction(world: World): BigDouble {
        val densityUpg = world.get<UpgradeComponent>(KEY_UPG_PARTICLE_DENSITY)
        return if (densityUpg?.purchased == true) {
            BASE_TAP_MATTER * (densityUpg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_MATTER
        }
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val resources = mapOf(
            ResourceType.ENERGY to (world.get<ResourceComponent>(KEY_RES_ENERGY)?.amount ?: BigDouble.ZERO),
            ResourceType.MATTER to (world.get<ResourceComponent>(KEY_RES_MATTER)?.amount ?: BigDouble.ZERO),
            ResourceType.HYDROGEN to (world.get<ResourceComponent>(KEY_RES_HYDROGEN)?.amount ?: BigDouble.ZERO),
            ResourceType.STARS to (world.get<ResourceComponent>(KEY_RES_STARS)?.amount ?: BigDouble.ZERO),
            ResourceType.ACCRETION_DISKS to (world.get<ResourceComponent>(KEY_RES_ACCRETION_DISKS)?.amount ?: BigDouble.ZERO),
            ResourceType.PLANETS to (world.get<ResourceComponent>(KEY_RES_PLANETS)?.amount ?: BigDouble.ZERO),
        )

        val genDisplayNames = mapOf(
            KEY_GEN_NEBULA to "Nebula Condenser",
            KEY_GEN_FUSION to "Hydrogen Fusion",
            KEY_GEN_STELLAR to "Stellar Nursery",
            KEY_GEN_ACCRETION to "Accretion Engine"
        )
        val generators = listOf(KEY_GEN_NEBULA, KEY_GEN_FUSION, KEY_GEN_STELLAR, KEY_GEN_ACCRETION)
            .mapNotNull { key ->
                world.get<GeneratorComponent>(key)?.let { gen ->
                    GeneratorSnapshot(
                        id = gen.id, displayName = genDisplayNames[key] ?: key,
                        productionType = gen.productionType, productionRate = gen.productionRate,
                        costType = gen.costType, costAmount = gen.costAmount,
                        unlocked = gen.unlocked, level = gen.level
                    )
                }
            }

        val upgDisplayNames = mapOf(
            KEY_UPG_PARTICLE_DENSITY to "Particle Density",
            KEY_UPG_NUCLEAR_IGNITION to "Nuclear Ignition",
            KEY_UPG_STELLAR_COMPRESSION to "Stellar Compression",
            KEY_UPG_PROTOPLANETARY to "Protoplanetary Disk",
            KEY_UPG_GRAVITATIONAL_COLLAPSE to "Gravitational Collapse",
            KEY_UPG_TECTONIC_STABILIZATION to "Tectonic Stabilization"
        )
        val upgDescriptions = mapOf(
            KEY_UPG_PARTICLE_DENSITY to "×2 Matter per tap",
            KEY_UPG_NUCLEAR_IGNITION to "Unlock Hydrogen Fusion",
            KEY_UPG_STELLAR_COMPRESSION to "×2 Stars/tick",
            KEY_UPG_PROTOPLANETARY to "Unlock Accretion Engine",
            KEY_UPG_GRAVITATIONAL_COLLAPSE to "100 Disks → 1 Planet",
            KEY_UPG_TECTONIC_STABILIZATION to "−50% Planet formation cost"
        )
        val upgrades = listOf(
            KEY_UPG_PARTICLE_DENSITY, KEY_UPG_NUCLEAR_IGNITION, KEY_UPG_STELLAR_COMPRESSION,
            KEY_UPG_PROTOPLANETARY, KEY_UPG_GRAVITATIONAL_COLLAPSE, KEY_UPG_TECTONIC_STABILIZATION
        ).mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val costResource = resources[upg.costType] ?: BigDouble.ZERO
                UpgradeSnapshot(
                    id = upg.id, displayName = upgDisplayNames[key] ?: key,
                    description = upgDescriptions[key] ?: "",
                    costType = upg.costType, costAmount = upg.costAmount,
                    purchased = upg.purchased, repeatable = upg.repeatable,
                    available = costResource >= upg.costAmount
                )
            }
        }

        val planets = resources[ResourceType.PLANETS] ?: BigDouble.ZERO
        val epochProgress = if (planets >= BigDouble.ONE) 1f else 0f

        return GameSnapshot(
            tick = tick, epoch = EpochType.COSMOLOGY,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList()
        )
    }

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.systems.CosmologySystemTest"
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemTest.kt
git commit -m "feat: implement CosmologySystem with generator chain, upgrades, and win condition"
```

---

## Task 7: SaveManager

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/SaveData.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/SaveManager.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/persistence/SaveManagerTest.kt`

- [ ] **Step 1: Create SaveData.kt**

```kotlin
package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class SaveData(
    val version: Int = 1,
    val lastTickTimestamp: Long,
    val snapshot: GameSnapshot
)
```

- [ ] **Step 2: Write the failing SaveManager tests**

`app/src/test/java/com/madmaxlgndklr/yhwh/persistence/SaveManagerTest.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveManagerTest {

    @get:Rule val tempFolder = TemporaryFolder()
    private lateinit var saveFile: File
    private lateinit var manager: SaveManager

    private fun baseSnapshot(tick: Long = 0L) = GameSnapshot(
        tick = tick,
        epoch = EpochType.COSMOLOGY,
        resources = mapOf(ResourceType.ENERGY to BigDouble.of(42.0)),
        generators = emptyList(),
        upgrades = emptyList(),
        epochProgress = 0f,
        events = emptyList()
    )

    @Before fun setup() {
        saveFile = File(tempFolder.root, "yhwh_save.json")
        manager = SaveManager(saveFile)
    }

    @Test fun `save then load returns same snapshot`() {
        manager.save(baseSnapshot(tick = 10L))
        val loaded = manager.load()
        assertNotNull(loaded)
        assertEquals(10L, loaded!!.snapshot.tick)
        assertEquals(42.0, loaded.snapshot.resources[ResourceType.ENERGY]!!.toDouble(), 1e-6)
    }

    @Test fun `load returns null when no file exists`() {
        assertNull(manager.load())
    }

    @Test fun `missedTicks calculates offline delta`() {
        val now = System.currentTimeMillis()
        val fiveSecondsAgo = now - 5_000L
        manager.save(baseSnapshot(), overrideTimestamp = fiveSecondsAgo)
        val loaded = manager.load()!!
        val missed = manager.computeMissedTicks(loaded.lastTickTimestamp, tickIntervalMs = 1000L)
        assertTrue("Expected ~5 missed ticks, got $missed", missed in 4..6)
    }

    @Test fun `missedTicks is capped at max`() {
        val tenHoursAgo = System.currentTimeMillis() - 10 * 3_600_000L
        manager.save(baseSnapshot(), overrideTimestamp = tenHoursAgo)
        val loaded = manager.load()!!
        val missed = manager.computeMissedTicks(
            loaded.lastTickTimestamp,
            tickIntervalMs = 1000L,
            maxTicks = 28_800L
        )
        assertEquals(28_800L, missed)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.persistence.SaveManagerTest" 2>&1 | tail -10
```

Expected: compilation error — `SaveManager` does not exist.

- [ ] **Step 4: Implement SaveManager.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/persistence/SaveManager.kt`:
```kotlin
package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SaveManager(private val saveFile: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(snapshot: GameSnapshot, overrideTimestamp: Long? = null) {
        val data = SaveData(
            lastTickTimestamp = overrideTimestamp ?: System.currentTimeMillis(),
            snapshot = snapshot
        )
        saveFile.writeText(json.encodeToString(data))
    }

    fun load(): SaveData? {
        if (!saveFile.exists()) return null
        return try {
            json.decodeFromString<SaveData>(saveFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun computeMissedTicks(
        lastTickTimestamp: Long,
        tickIntervalMs: Long = 1000L,
        maxTicks: Long = 28_800L
    ): Long {
        val elapsed = System.currentTimeMillis() - lastTickTimestamp
        val missed = elapsed / tickIntervalMs
        return missed.coerceAtMost(maxTicks)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.madmaxlgndklr.yhwh.persistence.SaveManagerTest"
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/persistence/ \
        app/src/test/java/com/madmaxlgndklr/yhwh/persistence/SaveManagerTest.kt
git commit -m "feat: add SaveManager — JSON persistence and offline delta calculation"
```

---

## Task 8: UI State Types & GameViewModel

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

> UI state types are pure data; ViewModel is tested indirectly through UI. No additional unit tests.

- [ ] **Step 1: Create GameUiState.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EpochType
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
```

- [ ] **Step 2: Create CosmosState.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EpochType

data class CosmosState(
    val epoch: EpochType = EpochType.COSMOLOGY,
    /** Normalized 0f–1f: min(matter / MATTER_VISUAL_THRESHOLD, 1f) */
    val matterLevel: Float = 0f,
    /** Normalized 0f–1f: min(stars / STAR_VISUAL_THRESHOLD, 1f) */
    val starLevel: Float = 0f,
    val starsFormed: Boolean = false,
    val planetsFormed: Boolean = false
)
```

- [ ] **Step 3: Create GameViewModel.kt**

`app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`:
```kotlin
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

    private val _cosmosState = MutableStateFlow(CosmosState())
    val cosmosState: StateFlow<CosmosState> = _cosmosState.asStateFlow()

    init {
        engine.registerSystem(CosmologySystem())
        engine.setOnSaveDue { snapshot -> saveManager.save(snapshot) }

        viewModelScope.launch {
            engine.snapshot.filterNotNull().collect { snapshot ->
                _uiState.value = snapshot.toUiState()
                _cosmosState.value = snapshot.toCosmosState()

                // Epoch transition
                if (snapshot.epochProgress >= 1f && !_uiState.value.showEpochTransition) {
                    _uiState.value = _uiState.value.copy(
                        showEpochTransition = true,
                        transitionMessage = "A world has formed. Life stirs in the primordial ocean."
                    )
                }
            }
        }

        val saved = saveManager.load()
        if (saved != null) {
            val missed = saveManager.computeMissedTicks(saved.lastTickTimestamp)
            var offlineSummary: String? = null
            if (missed > 0) {
                offlineSummary = "You were away for ~${formatOfflineTime(missed)} — your generators kept working."
            }
            engine.restore(saved.snapshot, missed)
            if (offlineSummary != null) {
                _uiState.value = _uiState.value.copy(offlineEarningsSummary = offlineSummary)
            }
        } else {
            engine.initNewGame()
        }

        engine.start()
    }

    fun onQuantumFluctuationTap() { engine.onPlayerTap() }

    fun onUpgradePurchase(upgradeId: String) { engine.purchaseUpgrade(upgradeId) }

    fun onGeneratorPurchase(generatorId: String) { engine.purchaseGenerator(generatorId) }

    fun dismissEpochTransition() {
        _uiState.value = _uiState.value.copy(showEpochTransition = false)
    }

    fun dismissOfflineSummary() {
        _uiState.value = _uiState.value.copy(offlineEarningsSummary = null)
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        engine.snapshot.value?.let { saveManager.save(it) }
    }

    private fun GameSnapshot.toUiState(): GameUiState {
        val energy = resources[ResourceType.ENERGY] ?: BigDouble.ZERO
        val matter = resources[ResourceType.MATTER] ?: BigDouble.ZERO
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
        val matter = resources[ResourceType.MATTER] ?: BigDouble.ZERO
        val stars = resources[ResourceType.STARS] ?: BigDouble.ZERO
        val planets = resources[ResourceType.PLANETS] ?: BigDouble.ZERO
        return CosmosState(
            epoch = epoch,
            matterLevel = (matter.toDouble() / CosmologySystem.MATTER_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
            starLevel = (stars.toDouble() / CosmologySystem.STAR_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
            starsFormed = stars > BigDouble.ZERO,
            planetsFormed = planets >= BigDouble.ONE
        )
    }

    private fun formatOfflineTime(ticks: Long): String {
        val seconds = ticks
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/
git commit -m "feat: add GameUiState, CosmosState, and GameViewModel wired to engine"
```

---

## Task 9: TopBar Composable

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/TopBar.kt`

- [ ] **Step 1: Implement TopBar.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.ui.state.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTopBar(state: GameUiState) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.epochName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = state.tickDisplay,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResourceChip(symbol = "⚡", value = state.energyDisplay)
                    ResourceChip(symbol = "⬡", value = state.matterDisplay)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ResourceChip(symbol: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = symbol, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/TopBar.kt
git commit -m "feat: add GameTopBar composable with epoch name, tick, and resource display"
```

---

## Task 10: CosmosCanvas Composable

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt`

- [ ] **Step 1: Implement CosmosCanvas.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

@Composable
fun CosmosCanvas(state: CosmosState, modifier: Modifier = Modifier) {
    // Seed a fixed star-field so it doesn't regenerate on recomposition
    val starField = remember { generateStarField(count = 150) }

    // Infinite rotation for orbital ring
    val infiniteTransition = rememberInfiniteTransition(label = "cosmos")
    val orbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_angle"
    )

    // Pulsing glow for stellar intensity
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Background color animates on epoch transition
    val bgColor by animateColorAsState(
        targetValue = if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510),
        animationSpec = tween(durationMillis = 3000),
        label = "bg_color"
    )

    Canvas(modifier = modifier.fillMaxSize().background(bgColor)) {
        // Layer 1: Star-field (always visible)
        drawStarField(starField)

        // Layer 2: Matter particle clusters (scales with matterLevel)
        if (state.matterLevel > 0f) {
            drawMatterParticles(state.matterLevel)
        }

        // Layer 3: Stellar glow (scales with starLevel)
        if (state.starsFormed) {
            drawStellarGlow(state.starLevel, glowPulse)
        }

        // Layer 4: Orbital ring (appears when stars exist)
        if (state.starsFormed) {
            drawOrbitalRing(orbitalAngle, state.starLevel)
        }

        // Layer 5: Planet formation ripple
        if (state.planetsFormed) {
            drawPlanetRipple(glowPulse)
        }
    }
}

private fun generateStarField(count: Int): List<Star> {
    val rng = Random(seed = 42L)
    return List(count) {
        Star(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            radius = rng.nextFloat() * 1.5f + 0.5f,
            alpha = rng.nextFloat() * 0.6f + 0.3f
        )
    }
}

private fun DrawScope.drawStarField(stars: List<Star>) {
    stars.forEach { star ->
        drawCircle(
            color = Color.White.copy(alpha = star.alpha),
            radius = star.radius,
            center = Offset(star.x * size.width, star.y * size.height)
        )
    }
}

private fun DrawScope.drawMatterParticles(matterLevel: Float) {
    val count = (matterLevel * 60).toInt().coerceAtLeast(1)
    val rng = Random(seed = 7L)
    repeat(count) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height
        drawCircle(
            color = Color(0xFF8080FF).copy(alpha = matterLevel * 0.5f),
            radius = rng.nextFloat() * 3f + 1f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawStellarGlow(starLevel: Float, pulse: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.15f * (0.8f + starLevel * 0.4f) * pulse
    drawCircle(
        color = Color(0xFFFFDD88).copy(alpha = starLevel * pulse * 0.4f),
        radius = radius * 2.5f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFFFFEEAA).copy(alpha = starLevel * 0.8f),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawOrbitalRing(angleDeg: Float, starLevel: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val orbitRadius = size.minDimension * 0.28f
    val dotRadius = 3f + starLevel * 3f
    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val dotX = cx + orbitRadius * cos(angleRad)
    val dotY = cy + orbitRadius * sin(angleRad)
    // Ring outline
    drawCircle(
        color = Color(0xFF4466AA).copy(alpha = 0.3f),
        radius = orbitRadius,
        center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
    )
    // Orbiting body
    drawCircle(
        color = Color(0xFF88CCFF).copy(alpha = 0.9f),
        radius = dotRadius,
        center = Offset(dotX, dotY)
    )
}

private fun DrawScope.drawPlanetRipple(pulse: Float) {
    val cx = size.width / 2f
    val cy = size.height * 0.65f
    drawCircle(
        color = Color(0xFF2266AA).copy(alpha = (1f - pulse) * 0.4f),
        radius = size.minDimension * 0.12f * (0.7f + pulse * 0.6f),
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF44AA77).copy(alpha = 0.6f),
        radius = size.minDimension * 0.08f,
        center = Offset(cx, cy)
    )
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt
git commit -m "feat: add CosmosCanvas atmospheric backdrop with 5 Cosmology visual layers"
```

---

## Task 11: ActionPanel (Bottom Tabs)

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/ActionPanel.kt`

- [ ] **Step 1: Implement ActionPanel.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot
import com.madmaxlgndklr.yhwh.ui.state.GameUiState

@Composable
fun ActionPanel(
    state: GameUiState,
    onTap: () -> Unit,
    onUpgradePurchase: (String) -> Unit,
    onGeneratorPurchase: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Actions", "Upgrades", "Stats")

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp)) {
            when (selectedTab) {
                0 -> ActionsTab(state.generators, onTap, onGeneratorPurchase)
                1 -> UpgradesTab(state.upgrades, onUpgradePurchase)
                2 -> StatsTab(state)
            }
        }
    }
}

@Composable
private fun ActionsTab(
    generators: List<GeneratorSnapshot>,
    onTap: () -> Unit,
    onGeneratorPurchase: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(
                onClick = onTap,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚡ Quantum Fluctuation", fontSize = 16.sp)
            }
        }
        items(generators.filter { it.unlocked }) { gen ->
            GeneratorCard(gen, onGeneratorPurchase)
        }
    }
}

@Composable
private fun GeneratorCard(gen: GeneratorSnapshot, onPurchase: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gen.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "+${gen.productionRate.toDisplayString()} ${gen.productionType.symbol}/tick",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Lv.${gen.level}  Cost: ${gen.costAmount.toDisplayString()} ${gen.costType.symbol}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onPurchase(gen.id) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("▲", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun UpgradesTab(
    upgrades: List<UpgradeSnapshot>,
    onPurchase: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(upgrades.filter { !it.purchased || it.repeatable }) { upg ->
            UpgradeCard(upg, onPurchase)
        }
    }
}

@Composable
private fun UpgradeCard(upg: UpgradeSnapshot, onPurchase: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (upg.available)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(upg.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(upg.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Cost: ${upg.costAmount.toDisplayString()} ${upg.costType.symbol}",
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = { onPurchase(upg.id) },
                enabled = upg.available,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (upg.repeatable) "Use" else "Buy")
            }
        }
    }
}

@Composable
private fun StatsTab(state: GameUiState) {
    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.offlineEarningsSummary?.let { summary ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }
        Text("Epoch Progress", fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { state.epochProgress },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${(state.epochProgress * 100).toInt()}% to Biology", fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (state.recentEvents.isNotEmpty()) {
            Text("Recent Events", fontWeight = FontWeight.Bold)
            state.recentEvents.forEach { event ->
                Text("• $event", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/ActionPanel.kt
git commit -m "feat: add ActionPanel with Actions, Upgrades, and Stats tabs"
```

---

## Task 12: GameScreen Assembly

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsScreen.kt (stub)**

```kotlin
package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings — Coming Soon")
    }
}
```

- [ ] **Step 2: Create GameScreen.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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

@Composable
fun GameScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cosmosState by viewModel.cosmosState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GameTopBar(state = uiState)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Central atmospheric canvas
            CosmosCanvas(
                state = cosmosState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            // Bottom action panel
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
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
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
                    Text(
                        text = "♁",
                        fontSize = 64.sp
                    )
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
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/
git commit -m "feat: assemble GameScreen with canvas, action panel, and epoch transition overlay"
```

---

## Task 13: Navigation, MainActivity, and Full Wiring

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/MainActivity.kt`

- [ ] **Step 1: Create AppNavigation.kt**

```kotlin
package com.madmaxlgndklr.yhwh.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.madmaxlgndklr.yhwh.ui.screen.GameScreen
import com.madmaxlgndklr.yhwh.ui.screen.SettingsScreen

private object Routes {
    const val GAME = "game"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.GAME) {
        composable(Routes.GAME) {
            GameScreen(onNavigateToSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
```

- [ ] **Step 2: Update MainActivity.kt with Material theme and navigation**

```kotlin
package com.madmaxlgndklr.yhwh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.madmaxlgndklr.yhwh.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF1A1A4E),
                    onPrimary = Color.White,
                    background = Color(0xFF050510),
                    surface = Color(0xFF0D0D2E),
                    primaryContainer = Color(0xFF2A2A6E),
                    secondaryContainer = Color(0xFF1A3A2E)
                )
            ) {
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 3: Run full build and unit tests**

```bash
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with all unit tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/MainActivity.kt
git commit -m "feat: wire navigation and dark cosmic theme — Phase 1 complete"
```

- [ ] **Step 5: Push to GitHub**

```bash
git push -u origin main
```

---

## Self-Review Checklist

- [x] **Spec §2 Tech Stack** — all dependencies in `libs.versions.toml` (Task 1)
- [x] **Spec §3 Layer Map** — GameEngine → GameSnapshot → ViewModel → UI (Tasks 5, 8, 12)
- [x] **Spec §4 Tick loop + offline progress** — `GameEngine.restore()` + `SaveManager.computeMissedTicks()` (Tasks 5, 7)
- [x] **Spec §4 ECS World** — `World` flat-map, `Component` sealed interface, keys as constants (Tasks 3, 6)
- [x] **Spec §5 BigDouble** — full arithmetic suite + display (Task 2)
- [x] **Spec §5 GameSnapshot** — immutable, emitted every tick (Tasks 4, 5)
- [x] **Spec §5 CosmosState thresholds** — `MATTER_VISUAL_THRESHOLD`, `STAR_VISUAL_THRESHOLD` in `CosmologySystem` (Task 6)
- [x] **Spec §6 Single Activity + NavHost** — `AppNavigation` with GAME + SETTINGS routes (Task 13)
- [x] **Spec §6 Three-zone layout** — TopBar + Canvas + ActionPanel (Tasks 9–12)
- [x] **Spec §6 CosmosCanvas 5 layers** — star-field, matter, glow, ring, planet ripple (Task 10)
- [x] **Spec §6 Three tabs** — Actions, Upgrades, Stats (Task 11)
- [x] **Spec §7 All 6 resources** — ENERGY through PLANETS initialized (Task 6)
- [x] **Spec §7 Generator chain** — tap → nebula → fusion → stellar → accretion (Task 6)
- [x] **Spec §7 All 6 upgrades** — particle density through tectonic stabilization (Task 6)
- [x] **Spec §7 Win condition** — `epochProgress = 1f` when `planets >= 1`, transition overlay (Tasks 6, 8, 12)
- [x] **Spec §9 Out of scope** — no Epochs 2–5, no cloud save, no sound, no IAP present in plan
