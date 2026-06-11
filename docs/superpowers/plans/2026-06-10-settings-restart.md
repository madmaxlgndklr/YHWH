# Settings Menu & New Game+ Restart — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent meta-save layer that tracks restart count and seed bonus, wire a Restart Game option into the existing Settings screen, surface Settings from the Stats tab, and apply the seed bonus to fresh Cosmology runs.

**Architecture:** `SeedBonus` (engine package) + `MetaSave`/`MetaSaveManager` (persistence) hold restart state across game wipes. `CosmologySystem` reads a `seedBonus` property set by `GameViewModel` before `initNewGame()`. `GameEngine.resetAndRegister()` replaces the active system and creates a fresh `World` so leftover epoch components don't pollute the new run. UI changes are additive: Settings link in StatsTab, Restart row in SettingsScreen, restart counter in StatsTab; the existing AccountSection is removed from StatsTab (sign-in lives in Settings → Account → ProfileScreen).

**Tech Stack:** Kotlin, Jetpack Compose, `kotlinx.serialization`, existing ECS engine

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/SeedBonus.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSave.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManager.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManagerTest.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemSeedTest.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineResetTest.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt` — `seedBonus` property, apply in `initialize()`/`tick()`/`onTap()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt` — `var world`, `resetAndRegister()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — `restartCount`, `activeSeedMultiplier`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — meta load, `computedSeedBonus()`, `restartGame()`, `toUiState()` update
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt` — Restart Game row + dialog + `onNavigateToGame` param
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/ActionPanel.kt` — `onNavigateToSettings` param, Settings row, restart counter, remove AccountSection
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt` — thread `onNavigateToSettings` into ActionPanel
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt` — `onNavigateToGame` for SettingsScreen

---

## Task 1: SeedBonus, MetaSave, MetaSaveManager

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/SeedBonus.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSave.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManager.kt`
- Test: `app/src/test/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManagerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManagerTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.SeedBonus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MetaSaveManagerTest {

    private lateinit var tempFile: File
    private lateinit var manager: MetaSaveManager

    @Before fun setup() {
        tempFile = File.createTempFile("meta_test", ".json")
        manager = MetaSaveManager(tempFile)
    }

    @After fun cleanup() { tempFile.delete() }

    @Test fun `load returns default when file absent`() {
        tempFile.delete()
        val meta = manager.load()
        assertEquals(0, meta.restartCount)
        assertEquals(1.0f, meta.seedBonus.globalMultiplier, 0.001f)
        assertEquals(0.0, meta.seedBonus.startingEnergy, 0.001)
        assertEquals(0.0, meta.seedBonus.startingMatter, 0.001)
    }

    @Test fun `save and load round-trips correctly`() {
        val bonus = SeedBonus(globalMultiplier = 1.35f, startingEnergy = 50.0, startingMatter = 25.0)
        val meta = MetaSave(restartCount = 3, seedBonus = bonus)
        manager.save(meta)
        val loaded = manager.load()
        assertEquals(3, loaded.restartCount)
        assertEquals(1.35f, loaded.seedBonus.globalMultiplier, 0.001f)
        assertEquals(50.0, loaded.seedBonus.startingEnergy, 0.001)
        assertEquals(25.0, loaded.seedBonus.startingMatter, 0.001)
    }

    @Test fun `load returns default when file is corrupt`() {
        tempFile.writeText("not valid json {{{")
        val meta = manager.load()
        assertEquals(0, meta.restartCount)
        assertEquals(1.0f, meta.seedBonus.globalMultiplier, 0.001f)
    }

    @Test fun `save overwrites previous save`() {
        manager.save(MetaSave(restartCount = 1, seedBonus = SeedBonus()))
        manager.save(MetaSave(restartCount = 5, seedBonus = SeedBonus(globalMultiplier = 1.65f)))
        val loaded = manager.load()
        assertEquals(5, loaded.restartCount)
        assertEquals(1.65f, loaded.seedBonus.globalMultiplier, 0.001f)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.persistence.MetaSaveManagerTest" 2>&1 | tail -10
```

Expected: compilation error — `MetaSaveManager` not found.

- [ ] **Step 3: Create SeedBonus.kt**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/engine/SeedBonus.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
data class SeedBonus(
    val globalMultiplier: Float = 1.0f,
    val startingEnergy: Double = 0.0,
    val startingMatter: Double = 0.0
)
```

- [ ] **Step 4: Create MetaSave.kt**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSave.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.SeedBonus
import kotlinx.serialization.Serializable

@Serializable
data class MetaSave(
    val restartCount: Int = 0,
    val seedBonus: SeedBonus = SeedBonus()
)
```

- [ ] **Step 5: Create MetaSaveManager.kt**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManager.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MetaSaveManager(private val metaFile: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): MetaSave {
        if (!metaFile.exists()) return MetaSave()
        return try {
            json.decodeFromString<MetaSave>(metaFile.readText())
        } catch (e: Exception) {
            MetaSave()
        }
    }

    fun save(meta: MetaSave) {
        metaFile.writeText(json.encodeToString(meta))
    }
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.persistence.MetaSaveManagerTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` with all 4 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/SeedBonus.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSave.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManager.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManagerTest.kt
git commit -m "$(cat <<'EOF'
feat: add SeedBonus, MetaSave, MetaSaveManager for New Game+ persistence

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: CosmologySystem Seed Bonus

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt`
- Test: `app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemSeedTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemSeedTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CosmologySystemSeedTest {

    private lateinit var world: World

    @Before fun setup() { world = World() }

    @Test fun `no seed — passive energy per tick equals base rate`() {
        val system = CosmologySystem()
        system.initialize(world)
        system.tick(world, 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(5.0, energy.amount.toDouble(), 0.01)
    }

    @Test fun `seed startingEnergy applied on initialize`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(startingEnergy = 100.0)
        }
        system.initialize(world)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(100.0, energy.amount.toDouble(), 0.01)
    }

    @Test fun `seed startingMatter applied on initialize`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(startingMatter = 50.0)
        }
        system.initialize(world)
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertEquals(50.0, matter.amount.toDouble(), 0.01)
    }

    @Test fun `globalMultiplier doubles passive energy per tick`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 2.0f)
        }
        system.initialize(world)
        system.tick(world, 1L)
        // BASE_ENERGY_PER_TICK = 5.0, multiplier 2.0 → 10.0 (starting = 0)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(10.0, energy.amount.toDouble(), 0.01)
    }

    @Test fun `globalMultiplier doubles tap production`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 2.0f)
        }
        system.initialize(world)
        system.onTap(world)
        // BASE_TAP_MATTER = 1.0, multiplier 2.0 → 2.0
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertEquals(2.0, matter.amount.toDouble(), 0.01)
    }

    @Test fun `globalMultiplier 1_0 means no change`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 1.0f)
        }
        system.initialize(world)
        system.tick(world, 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(5.0, energy.amount.toDouble(), 0.01)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.systems.CosmologySystemSeedTest" 2>&1 | tail -10
```

Expected: compilation error — `CosmologySystem.seedBonus` not found.

- [ ] **Step 3: Modify CosmologySystem.kt**

Add import at top:
```kotlin
import com.madmaxlgndklr.yhwh.engine.SeedBonus
```

Add a private field and public property after the existing private fields (`collapseCost`, `firstStarFired`, `firstPlanetFired`):

```kotlin
    private var globalMultiplier: BigDouble = BigDouble.ONE
    var seedBonus: SeedBonus? = null
```

At the **end** of `initialize()`, after all `world.put(...)` calls, add:

```kotlin
        // Apply seed bonus from a prior run
        seedBonus?.let { bonus ->
            world.get<ResourceComponent>(KEY_RES_ENERGY)?.amount = BigDouble.of(bonus.startingEnergy)
            world.get<ResourceComponent>(KEY_RES_MATTER)?.amount = BigDouble.of(bonus.startingMatter)
            globalMultiplier = BigDouble.of(bonus.globalMultiplier.toDouble())
        }
```

In `tick()`, change the passive energy line from:
```kotlin
        resourceComp(world, KEY_RES_ENERGY)?.let {
            it.amount = it.amount + BASE_ENERGY_PER_TICK * bigDelta
        }
```
To:
```kotlin
        resourceComp(world, KEY_RES_ENERGY)?.let {
            it.amount = it.amount + BASE_ENERGY_PER_TICK * bigDelta * globalMultiplier
        }
```

In `runGenerator()`, change the production line from:
```kotlin
        prodRes.amount = prodRes.amount + gen.productionRate * delta
```
To:
```kotlin
        prodRes.amount = prodRes.amount + gen.productionRate * delta * globalMultiplier
```

In `onTap()`, change from:
```kotlin
    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_MATTER)?.let { it.amount = it.amount + tapAmount }
    }
```
To:
```kotlin
    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world) * globalMultiplier
        resourceComp(world, KEY_RES_MATTER)?.let { it.amount = it.amount + tapAmount }
    }
```

Also update `syncStateFromWorld()` to reset `globalMultiplier` safely — add at the top of the existing method:
```kotlin
        globalMultiplier = if (seedBonus != null)
            BigDouble.of(seedBonus!!.globalMultiplier.toDouble()) else BigDouble.ONE
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.systems.CosmologySystemSeedTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` with all 6 tests passing.

- [ ] **Step 5: Run full test suite**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/systems/CosmologySystemSeedTest.kt
git commit -m "$(cat <<'EOF'
feat: apply seed bonus in CosmologySystem — starting resources and global multiplier

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: GameEngine.resetAndRegister

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt`
- Test: `app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineResetTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineResetTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineResetTest {

    @Test fun `resetAndRegister replaces active system with fresh world`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = GameEngine(scope = this)

        // Start a game and accumulate some state
        engine.registerSystem(CosmologySystem())
        engine.initNewGame()
        // Tick to get some resources
        engine.snapshot.value // not null

        // Reset with a new system
        val newCosmo = CosmologySystem()
        engine.resetAndRegister(newCosmo)
        engine.initNewGame()

        val snap = engine.snapshot.value
        assertNotNull(snap)
        assertEquals(EpochType.COSMOLOGY, snap!!.epoch)
        assertEquals(0L, snap.tick)
        // All resources should be zero (fresh world, no bonus)
        val energy = snap.resources[ResourceType.ENERGY.name] ?: BigDouble.ZERO
        assertEquals(0.0, energy.toDouble(), 0.01)
    }

    @Test fun `resetAndRegister clears lifetime totals`() = runTest {
        val engine = GameEngine(scope = this)
        engine.registerSystem(CosmologySystem())
        engine.initNewGame()

        val newCosmo = CosmologySystem()
        engine.resetAndRegister(newCosmo)
        engine.initNewGame()

        val snap = engine.snapshot.value!!
        assertTrue(snap.lifetimeTotals.values.all { it == BigDouble.ZERO || it.toDouble() == 0.0 })
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.engine.GameEngineResetTest" 2>&1 | tail -10
```

Expected: compilation error — `resetAndRegister` not found.

- [ ] **Step 3: Modify GameEngine.kt**

Change `private val world = World()` to `private var world = World()` (line ~28).

Add `resetAndRegister` function after `registerSystem`:

```kotlin
    /**
     * Clears all registered systems, resets the world and tick counter, and registers
     * [system] as the sole active system. Call before [initNewGame] when restarting.
     */
    fun resetAndRegister(system: GameSystem) {
        systems.clear()
        world = World()
        tickCount = 0L
        lifetimeTotals.clear()
        systems.add(system)
        activeSystem = system
    }
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.engine.GameEngineResetTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` with both tests passing.

- [ ] **Step 5: Run full test suite**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/engine/GameEngineResetTest.kt
git commit -m "$(cat <<'EOF'
feat: add GameEngine.resetAndRegister for clean game restart

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: GameViewModel Wiring

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt`

- [ ] **Step 1: Add restartCount and activeSeedMultiplier to GameUiState**

In `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt`, add two fields with defaults at the end of the `GameUiState` data class:

```kotlin
    val restartCount: Int = 0,
    val activeSeedMultiplier: Float = 1.0f
```

Full updated file:

```kotlin
package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
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
    val tutorialStep: Int = 0,
    val activeEvent: EvolutionEvent? = null,
    val eventTicksRemaining: Int = 0,
    val restartCount: Int = 0,
    val activeSeedMultiplier: Float = 1.0f
)
```

- [ ] **Step 2: Add imports to GameViewModel.kt**

In `GameViewModel.kt`, add these imports alongside the existing ones:

```kotlin
import com.madmaxlgndklr.yhwh.engine.SeedBonus
import com.madmaxlgndklr.yhwh.persistence.MetaSave
import com.madmaxlgndklr.yhwh.persistence.MetaSaveManager
import com.madmaxlgndklr.yhwh.systems.EvolutionSystem
import kotlin.math.floor
```

- [ ] **Step 3: Add MetaSaveManager + meta field**

In `GameViewModel`, after `private val saveFile = File(...)` and `private val saveManager = ...`, add:

```kotlin
    private val metaFile = File(application.filesDir, "yhwh_meta.json")
    private val metaSaveManager = MetaSaveManager(metaFile)
    private var meta = metaSaveManager.load()
```

- [ ] **Step 4: Apply seed bonus when registering CosmologySystem**

In `GameViewModel.init`, find:

```kotlin
        val system: GameSystem = when (saved?.snapshot?.epoch) {
            EpochType.BIOLOGY -> BiologySystem()
            EpochType.EVOLUTION -> EvolutionSystem()
            else -> CosmologySystem()
        }
        engine.registerSystem(system)
```

Replace with:

```kotlin
        val system: GameSystem = when (saved?.snapshot?.epoch) {
            EpochType.BIOLOGY -> BiologySystem()
            EpochType.EVOLUTION -> EvolutionSystem()
            else -> CosmologySystem().also { it.seedBonus = meta.seedBonus }
        }
        engine.registerSystem(system)
```

- [ ] **Step 5: Add computedSeedBonus()**

Add this function to `GameViewModel`, after `dismissOfflineSummary()`:

```kotlin
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
```

- [ ] **Step 6: Add restartGame()**

Add this function immediately after `computedSeedBonus()`:

```kotlin
    fun restartGame() {
        val newBonus = computedSeedBonus()
        val newMeta = MetaSave(restartCount = meta.restartCount + 1, seedBonus = newBonus)
        viewModelScope.launch(Dispatchers.IO) {
            metaSaveManager.save(newMeta)
            saveFile.delete()
        }
        meta = newMeta
        epochTransitionAcknowledged = false
        engine.stop()
        engine.resetAndRegister(CosmologySystem().also { it.seedBonus = newBonus })
        engine.initNewGame()
        engine.start()
    }
```

- [ ] **Step 7: Update toUiState() to include restart fields**

In `toUiState()`, find the `return GameUiState(...)` call. Add two more fields at the end:

```kotlin
            activeEvent = activeEvent,
            eventTicksRemaining = eventTicksRemaining,
            restartCount = meta.restartCount,
            activeSeedMultiplier = meta.seedBonus.globalMultiplier
```

Full updated return block:

```kotlin
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
            activeSeedMultiplier = meta.seedBonus.globalMultiplier
        )
```

- [ ] **Step 8: Verify compilation**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Run full test suite**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt
git commit -m "$(cat <<'EOF'
feat: wire MetaSave into GameViewModel — computedSeedBonus, restartGame, restart UI state

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: SettingsScreen Restart UI

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`

- [ ] **Step 1: Rewrite SettingsScreen.kt**

Replace the full contents of `SettingsScreen.kt`:

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
    onNavigateToProfile: () -> Unit,
    onNavigateToGame: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var showRestartDialog by remember { mutableStateOf(false) }
    val bonus = remember { viewModel.computedSeedBonus() }

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

        HorizontalDivider()

        // Restart Game row
        val bonusText = buildString {
            val pct = ((bonus.globalMultiplier - 1.0f) * 100).toInt()
            if (pct > 0) append("+${pct}% production")
            if (bonus.startingEnergy > 0) {
                if (isNotEmpty()) append(" · ")
                append("${bonus.startingEnergy.toInt()} Energy")
            }
            if (bonus.startingMatter > 0) {
                if (isNotEmpty()) append(" · ")
                append("${bonus.startingMatter.toInt()} Matter")
            }
            if (isEmpty()) append("No bonus yet — play further to earn one")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRestartDialog = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Restart Game", fontSize = 15.sp)
                Text(
                    bonusText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Start a new game?") },
            text = {
                Text(
                    "Your progress earns: $bonusText\n\nThis cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.restartGame()
                    showRestartDialog = false
                    onNavigateToGame()
                }) {
                    Text("Restart")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestartDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
```

- [ ] **Step 2: Update AppNavigation.kt to pass onNavigateToGame**

In `AppNavigation.kt`, find:

```kotlin
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = gameViewModel,
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) }
            )
        }
```

Replace with:

```kotlin
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = gameViewModel,
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToGame = { navController.popBackStack(Routes.GAME, inclusive = false) }
            )
        }
```

- [ ] **Step 3: Verify compilation**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt
git commit -m "$(cat <<'EOF'
feat: add Restart Game row and confirmation dialog to SettingsScreen

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Stats Tab + Navigation Threading

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/ActionPanel.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`

- [ ] **Step 1: Rewrite ActionPanel.kt**

Replace the full contents of `ActionPanel.kt`. Key changes: add `onNavigateToSettings` parameter to `ActionPanel` and `StatsTab`; add Settings row and restart counter to `StatsTab`; remove `AccountSection` and `googleSignIn` launcher (sign-in is now in Settings → Account → ProfileScreen).

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import com.madmaxlgndklr.yhwh.ui.state.ResourceDisplay

@Composable
fun ActionPanel(
    state: GameUiState,
    onTap: () -> Unit,
    onUpgradePurchase: (String) -> Unit,
    onGeneratorPurchase: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Actions", "Upgrades", "Stats")

    Column(modifier = modifier) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1A1A4E),
            contentColor = Color.White
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, color = Color.White) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)) {
            when (selectedTab) {
                0 -> ActionsTab(state.generators, onTap, onGeneratorPurchase)
                1 -> UpgradesTab(state.upgrades, onUpgradePurchase)
                2 -> StatsTab(state, onNavigateToSettings)
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
            Button(onClick = onTap, modifier = Modifier.fillMaxWidth()) {
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
private fun UpgradesTab(upgrades: List<UpgradeSnapshot>, onPurchase: (String) -> Unit) {
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
            else MaterialTheme.colorScheme.surfaceVariant
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
                Text("Cost: ${upg.costAmount.toDisplayString()} ${upg.costType.symbol}", fontSize = 11.sp)
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
private fun StatsTab(state: GameUiState, onNavigateToSettings: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Settings link
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSettings)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", fontSize = 15.sp)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
        }

        // Epoch progress
        item {
            Text("Epoch Progress", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { state.epochProgress }, modifier = Modifier.fillMaxWidth())
            Text("${(state.epochProgress * 100).toInt()}% to ${state.nextEpochName}", fontSize = 12.sp)
        }

        // All resources across all epochs
        if (state.allResources.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("All Resources", fontWeight = FontWeight.Bold)
            }
            items(state.allResources) { res ->
                ResourceRow(res)
            }
        }

        // Restart counter
        if (state.restartCount > 0) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Restarts: ${state.restartCount}  " +
                    "(×${"%.2f".format(state.activeSeedMultiplier)} production seed)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Recent events
        if (state.recentEvents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Recent Events", fontWeight = FontWeight.Bold)
            }
            items(state.recentEvents) { event ->
                Text("• $event", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResourceRow(res: ResourceDisplay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${res.symbol} ${res.displayName}", fontSize = 13.sp)
        Text(res.value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
```

- [ ] **Step 2: Update GameScreen.kt to thread onNavigateToSettings into ActionPanel**

In `GameScreen.kt`, find the `ActionPanel(...)` call and update it. The current call passes `viewModel` directly — remove that and add `onNavigateToSettings`:

Find:
```kotlin
            ActionPanel(
                state = uiState,
                viewModel = viewModel,
                onTap = viewModel::onQuantumFluctuationTap,
                onUpgradePurchase = viewModel::onUpgradePurchase,
                onGeneratorPurchase = viewModel::onGeneratorPurchase,
                modifier = Modifier.fillMaxWidth()
            )
```

Replace with:
```kotlin
            ActionPanel(
                state = uiState,
                onTap = viewModel::onQuantumFluctuationTap,
                onUpgradePurchase = viewModel::onUpgradePurchase,
                onGeneratorPurchase = viewModel::onGeneratorPurchase,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )
```

- [ ] **Step 3: Verify compilation**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run full test suite**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/ActionPanel.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt
git commit -m "$(cat <<'EOF'
feat: add Settings link and restart counter to StatsTab, remove inline AccountSection

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```
