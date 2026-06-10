# Biology Epoch — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Biology epoch — second playable epoch after Cosmology — with a full resource chain (Amino Acids → Proteins → Cells → Organisms), 4 generators, 6 upgrades, ViewModel-driven epoch transition, and organic canvas visuals.

**Architecture:** `BiologySystem` mirrors `CosmologySystem`'s ECS pattern. `GameEngine` gains `advanceEpoch()` for hot-swap. A new `Restorable` interface replaces the hardcoded `CosmologySystem` cast in `restore()`. `GameViewModel.dismissEpochTransition()` triggers the swap; `toCosmosState()` branches by epoch for Biology visuals.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, existing ECS engine (`World`, `GameSystem`, `PlayerActionHandler`)

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/Restorable.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/BiologySystem.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/engine/AdvanceEpochTest.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/systems/BiologySystemTest.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt` — add AMINO_ACIDS, PROTEINS, CELLS, ORGANISMS
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt` — add `advanceEpoch()`, fix `restore()` to use `Restorable`, remove stale `CosmologySystem` import
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt` — add `: Restorable` to class declaration
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — add `aminoAcidLevel`, `cellLevel` fields
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch-aware init + dismissEpochTransition + toCosmosState Biology branch
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Biology background + organic particles + cell membranes

---

## Task 1: Foundation — ResourceType, Restorable, GameEngine

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/Restorable.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/engine/AdvanceEpochTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/madmaxlgndklr/yhwh/engine/AdvanceEpochTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvanceEpochTest {

    private fun baseSnapshot(epoch: EpochType = EpochType.COSMOLOGY) = GameSnapshot(
        tick = 0, epoch = epoch,
        resources = emptyMap(), generators = emptyList(),
        upgrades = emptyList(), epochProgress = 0f, events = emptyList()
    )

    private fun makeSystem(epoch: EpochType) = object : GameSystem {
        override fun initialize(world: World) {}
        override fun tick(world: World, delta: Long): List<GameEvent> = emptyList()
        override fun toSnapshot(world: World, tick: Long): GameSnapshot = baseSnapshot(epoch)
    }

    @Test fun `advanceEpoch changes snapshot epoch to Biology`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L)
        engine.registerSystem(makeSystem(EpochType.COSMOLOGY))
        engine.initNewGame()
        assertEquals(EpochType.COSMOLOGY, engine.snapshot.value?.epoch)

        engine.advanceEpoch(makeSystem(EpochType.BIOLOGY))
        assertEquals(EpochType.BIOLOGY, engine.snapshot.value?.epoch)
    }

    @Test fun `restore calls syncStateFromWorld on Restorable systems`() = runTest {
        var synced = false
        val restorableSystem = object : GameSystem, Restorable {
            override fun initialize(world: World) {}
            override fun tick(world: World, delta: Long): List<GameEvent> = emptyList()
            override fun toSnapshot(world: World, tick: Long): GameSnapshot = baseSnapshot()
            override fun syncStateFromWorld(world: World) { synced = true }
        }
        val engine = GameEngine(tickIntervalMs = 1000L)
        engine.registerSystem(restorableSystem)
        engine.restore(baseSnapshot(), missedTicks = 0L)
        assertTrue(synced)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.engine.AdvanceEpochTest" 2>&1 | tail -10
```

Expected: compilation error — `Restorable` not found, `advanceEpoch` not found.

- [ ] **Step 3: Add 4 new ResourceType values**

In `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt`, replace the entire file:

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
    PLANETS("Planets", "♁"),
    AMINO_ACIDS("Amino Acids", "🧪"),
    PROTEINS("Proteins", "🔗"),
    CELLS("Cells", "🔬"),
    ORGANISMS("Organisms", "🦠")
}
```

- [ ] **Step 4: Create Restorable interface**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/engine/Restorable.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.engine

interface Restorable {
    fun syncStateFromWorld(world: World)
}
```

- [ ] **Step 5: Update GameEngine — advanceEpoch + restore fix**

In `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt`:

Remove the import at line 4:
```kotlin
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
```

Replace the one line in `restore()` that reads:
```kotlin
systems.filterIsInstance<CosmologySystem>().forEach { it.syncStateFromWorld(world) }
```
with:
```kotlin
systems.filterIsInstance<Restorable>().forEach { it.syncStateFromWorld(world) }
```

Add `advanceEpoch()` after the `stop()` function:
```kotlin
fun advanceEpoch(nextSystem: GameSystem) {
    systems.add(nextSystem)
    activeSystem = nextSystem
    nextSystem.initialize(world)
    emitSnapshot()
}
```

- [ ] **Step 6: Make CosmologySystem implement Restorable**

In `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt`, change the class declaration from:

```kotlin
class CosmologySystem : GameSystem, PlayerActionHandler {
```

to:

```kotlin
class CosmologySystem : GameSystem, PlayerActionHandler, Restorable {
```

No other changes — `syncStateFromWorld(world)` is already defined in the class body.

- [ ] **Step 7: Run tests — must all pass**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, all tests pass (32 existing + 2 new = 34 total).

- [ ] **Step 8: Commit**

```bash
git add \
  app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt \
  app/src/main/java/com/madmaxlgndklr/yhwh/engine/Restorable.kt \
  app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt \
  app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt \
  app/src/test/java/com/madmaxlgndklr/yhwh/engine/AdvanceEpochTest.kt
git commit -m "feat: add Restorable interface, advanceEpoch, and Biology ResourceType values"
```

---

## Task 2: BiologySystem

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/systems/BiologySystem.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/systems/BiologySystemTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/madmaxlgndklr/yhwh/systems/BiologySystemTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BiologySystemTest {

    private lateinit var world: World
    private lateinit var system: BiologySystem

    @Before fun setup() {
        world = World()
        system = BiologySystem()
        system.initialize(world)
    }

    @Test fun `initialize populates amino acids resource`() {
        val aa = world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)
        assertNotNull(aa)
        assertEquals(ResourceType.AMINO_ACIDS, aa!!.type)
    }

    @Test fun `initialize populates prebiotic soup generator unlocked`() {
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_PREBIOTIC_SOUP)
        assertNotNull(gen)
        assertTrue(gen!!.unlocked)
    }

    @Test fun `initialize leaves protein synthesizer locked`() {
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_PROTEIN_SYNTHESIZER)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `initialize leaves organism incubator locked`() {
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_ORGANISM_INCUBATOR)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `tap produces amino acids`() {
        system.onTap(world)
        val aa = world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)!!
        assertTrue(aa.amount > BigDouble.ZERO)
    }

    @Test fun `planet seeding increases prebiotic soup rate`() {
        val worldWithPlanets = World()
        // Simulate Cosmology having formed 3 planets
        worldWithPlanets.put(CosmologySystem.KEY_RES_PLANETS,
            ResourceComponent(ResourceType.PLANETS, BigDouble.of(3.0)))
        val seededSystem = BiologySystem()
        seededSystem.initialize(worldWithPlanets)
        val gen = worldWithPlanets.get<GeneratorComponent>(BiologySystem.KEY_GEN_PREBIOTIC_SOUP)!!
        // Expected rate: 1.0 + 3 * 0.1 = 1.3
        assertEquals(1.3, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `prebiotic soup produces amino acids when energy available`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)
            ?: world.put(CosmologySystem.KEY_RES_ENERGY,
                ResourceComponent(ResourceType.ENERGY, BigDouble.of(1000.0)))
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
            .amount = BigDouble.of(1000.0)
        system.tick(world, delta = 1L)
        val aa = world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)!!
        assertTrue(aa.amount > BigDouble.ZERO)
    }

    @Test fun `rna world upgrade unlocks protein synthesizer`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, BiologySystem.KEY_UPG_RNA_WORLD)
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_PROTEIN_SYNTHESIZER)!!
        assertTrue(gen.unlocked)
    }

    @Test fun `epochProgress is 0 with no organisms`() {
        val snap = system.toSnapshot(world, tick = 0)
        assertEquals(0f, snap.epochProgress, 0.01f)
    }

    @Test fun `epochProgress is 1 with 1000 organisms`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
            .amount = BigDouble.of(1000.0)
        val snap = system.toSnapshot(world, tick = 0)
        assertEquals(1f, snap.epochProgress, 0.01f)
    }

    @Test fun `evolutionary pressure converts cells to organism`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_CELLS)!!
            .amount = BigDouble.of(200.0)
        system.purchaseUpgrade(world, BiologySystem.KEY_UPG_EVOLUTIONARY_PRESSURE)
        val organisms = world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
        assertTrue(organisms.amount >= BigDouble.ONE)
    }

    @Test fun `syncStateFromWorld sets firstOrganismFired from world state`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
            .amount = BigDouble.of(5.0)
        // Before sync: tick won't fire milestone event again after restore
        system.syncStateFromWorld(world)
        val events = system.tick(world, delta = 1L)
        assertTrue(events.none { it.isMilestone })
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.systems.BiologySystemTest" 2>&1 | tail -10
```

Expected: compilation error — `BiologySystem` not found.

- [ ] **Step 3: Create BiologySystem.kt**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/systems/BiologySystem.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.pow

class BiologySystem : GameSystem, PlayerActionHandler, Restorable {

    companion object {
        const val KEY_RES_AMINO_ACIDS = "res_amino_acids"
        const val KEY_RES_PROTEINS = "res_proteins"
        const val KEY_RES_CELLS = "res_cells"
        const val KEY_RES_ORGANISMS = "res_organisms"

        const val KEY_GEN_PREBIOTIC_SOUP = "gen_prebiotic_soup"
        const val KEY_GEN_PROTEIN_SYNTHESIZER = "gen_protein_synthesizer"
        const val KEY_GEN_CELL_DIVISION = "gen_cell_division"
        const val KEY_GEN_ORGANISM_INCUBATOR = "gen_organism_incubator"

        const val KEY_UPG_CATALYST_ENZYME = "upg_catalyst_enzyme"
        const val KEY_UPG_RNA_WORLD = "upg_rna_world"
        const val KEY_UPG_LIPID_MEMBRANE = "upg_lipid_membrane"
        const val KEY_UPG_MULTICELLULARITY = "upg_multicellularity"
        const val KEY_UPG_EVOLUTIONARY_PRESSURE = "upg_evolutionary_pressure"
        const val KEY_UPG_HORIZONTAL_GENE_TRANSFER = "upg_horizontal_gene_transfer"

        const val AMINO_ACID_VISUAL_THRESHOLD = 500.0
        const val CELL_VISUAL_THRESHOLD = 200.0
        const val WIN_THRESHOLD = 1000.0

        private val BASE_TAP_AMINO_ACIDS = BigDouble.ONE
        private val INITIAL_EVOLUTION_COST = BigDouble.of(100.0)
    }

    private var evolutionCost = INITIAL_EVOLUTION_COST
    private var firstOrganismFired = false

    override fun initialize(world: World) {
        val planetCount = world.get<ResourceComponent>(CosmologySystem.KEY_RES_PLANETS)
            ?.amount?.toDouble() ?: 0.0
        val prebioticRate = BigDouble.of(1.0 + planetCount * 0.1)

        world.put(KEY_RES_AMINO_ACIDS, ResourceComponent(ResourceType.AMINO_ACIDS, BigDouble.ZERO))
        world.put(KEY_RES_PROTEINS, ResourceComponent(ResourceType.PROTEINS, BigDouble.ZERO))
        world.put(KEY_RES_CELLS, ResourceComponent(ResourceType.CELLS, BigDouble.ZERO))
        world.put(KEY_RES_ORGANISMS, ResourceComponent(ResourceType.ORGANISMS, BigDouble.ZERO))

        world.put(KEY_GEN_PREBIOTIC_SOUP, GeneratorComponent(
            id = KEY_GEN_PREBIOTIC_SOUP, productionType = ResourceType.AMINO_ACIDS,
            productionRate = prebioticRate, costType = ResourceType.ENERGY,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        world.put(KEY_GEN_PROTEIN_SYNTHESIZER, GeneratorComponent(
            id = KEY_GEN_PROTEIN_SYNTHESIZER, productionType = ResourceType.PROTEINS,
            productionRate = BigDouble.ONE, costType = ResourceType.AMINO_ACIDS,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))
        world.put(KEY_GEN_CELL_DIVISION, GeneratorComponent(
            id = KEY_GEN_CELL_DIVISION, productionType = ResourceType.CELLS,
            productionRate = BigDouble.ONE, costType = ResourceType.PROTEINS,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        world.put(KEY_GEN_ORGANISM_INCUBATOR, GeneratorComponent(
            id = KEY_GEN_ORGANISM_INCUBATOR, productionType = ResourceType.ORGANISMS,
            productionRate = BigDouble.ONE, costType = ResourceType.CELLS,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))

        world.put(KEY_UPG_CATALYST_ENZYME, UpgradeComponent(
            id = KEY_UPG_CATALYST_ENZYME, purchased = false,
            costType = ResourceType.AMINO_ACIDS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_RNA_WORLD, UpgradeComponent(
            id = KEY_UPG_RNA_WORLD, purchased = false,
            costType = ResourceType.AMINO_ACIDS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_PROTEIN_SYNTHESIZER)
        ))
        world.put(KEY_UPG_LIPID_MEMBRANE, UpgradeComponent(
            id = KEY_UPG_LIPID_MEMBRANE, purchased = false,
            costType = ResourceType.PROTEINS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_CELL_DIVISION, BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_MULTICELLULARITY, UpgradeComponent(
            id = KEY_UPG_MULTICELLULARITY, purchased = false,
            costType = ResourceType.CELLS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_ORGANISM_INCUBATOR)
        ))
        world.put(KEY_UPG_EVOLUTIONARY_PRESSURE, UpgradeComponent(
            id = KEY_UPG_EVOLUTIONARY_PRESSURE, purchased = true,
            costType = ResourceType.CELLS, costAmount = evolutionCost,
            effect = UpgradeEffect.ManualConversion(
                inputType = ResourceType.CELLS,
                inputAmount = evolutionCost,
                outputType = ResourceType.ORGANISMS,
                outputAmount = BigDouble.ONE
            ),
            repeatable = true
        ))
        world.put(KEY_UPG_HORIZONTAL_GENE_TRANSFER, UpgradeComponent(
            id = KEY_UPG_HORIZONTAL_GENE_TRANSFER, purchased = false,
            costType = ResourceType.CELLS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.ReduceConversionCost(
                targetUpgradeId = KEY_UPG_EVOLUTIONARY_PRESSURE,
                multiplier = BigDouble.of(0.5)
            )
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())

        runGenerator(world, KEY_GEN_PREBIOTIC_SOUP, bigDelta)
        runGenerator(world, KEY_GEN_PROTEIN_SYNTHESIZER, bigDelta)
        runGenerator(world, KEY_GEN_CELL_DIVISION, bigDelta)
        runGenerator(world, KEY_GEN_ORGANISM_INCUBATOR, bigDelta)

        resourceComp(world, KEY_RES_ORGANISMS)?.let { orgs ->
            if (!firstOrganismFired && orgs.amount > BigDouble.ZERO) {
                firstOrganismFired = true
                events.add(GameEvent(0, "Life stirs for the first time.", isMilestone = true))
            }
        }

        return events
    }

    private fun runGenerator(world: World, key: String, delta: BigDouble) {
        val gen = world.get<GeneratorComponent>(key) ?: return
        if (!gen.unlocked) return
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        val totalCost = gen.costAmount * delta
        if (costRes.amount < totalCost) return
        costRes.amount = costRes.amount - totalCost
        val prodRes = resourceComp(world, "res_${gen.productionType.name.lowercase()}") ?: return
        prodRes.amount = prodRes.amount + gen.productionRate * delta
    }

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)

    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_AMINO_ACIDS)?.let { it.amount = it.amount + tapAmount }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        when (val effect = upg.effect) {
            is UpgradeEffect.ManualConversion -> {
                if (!upg.purchased) return
                val inputRes = resourceComp(world, "res_${effect.inputType.name.lowercase()}") ?: return
                if (inputRes.amount < effect.inputAmount) return
                inputRes.amount = inputRes.amount - effect.inputAmount
                val outputRes = resourceComp(world, "res_${effect.outputType.name.lowercase()}") ?: return
                outputRes.amount = outputRes.amount + effect.outputAmount
            }
            else -> {
                if (upg.purchased) return
                val costRes = resourceComp(world, "res_${upg.costType.name.lowercase()}") ?: return
                if (costRes.amount < upg.costAmount) return
                costRes.amount = costRes.amount - upg.costAmount
                upg.purchased = true
                applyUpgradeEffect(world, effect)
            }
        }
    }

    private fun applyUpgradeEffect(world: World, effect: UpgradeEffect) {
        when (effect) {
            is UpgradeEffect.UnlockGenerator ->
                world.get<GeneratorComponent>(effect.generatorId)?.unlocked = true
            is UpgradeEffect.MultiplyProduction ->
                world.get<GeneratorComponent>(effect.generatorId)?.let {
                    it.productionRate = it.productionRate * effect.multiplier
                }
            is UpgradeEffect.MultiplyTapProduction -> { /* applied dynamically in currentTapProduction */ }
            is UpgradeEffect.ReduceConversionCost -> {
                val target = world.get<UpgradeComponent>(effect.targetUpgradeId) ?: return
                target.costAmount = target.costAmount * effect.multiplier
                val targetEffect = target.effect
                if (targetEffect is UpgradeEffect.ManualConversion) {
                    targetEffect.inputAmount = targetEffect.inputAmount * effect.multiplier
                }
                evolutionCost = evolutionCost * effect.multiplier
            }
            is UpgradeEffect.ManualConversion -> { /* handled in purchaseUpgrade */ }
        }
    }

    override fun purchaseGenerator(world: World, generatorId: String) {
        val gen = world.get<GeneratorComponent>(generatorId) ?: return
        if (!gen.unlocked) return
        val levelUpCost = gen.costAmount * BigDouble.of(1.15.pow(gen.level.toDouble()))
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        if (costRes.amount < levelUpCost) return
        costRes.amount = costRes.amount - levelUpCost
        gen.productionRate = gen.productionRate * BigDouble.of(1.1)
        gen.level++
    }

    private fun currentTapProduction(world: World): BigDouble {
        val upg = world.get<UpgradeComponent>(KEY_UPG_CATALYST_ENZYME)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_AMINO_ACIDS * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_AMINO_ACIDS
        }
    }

    override fun syncStateFromWorld(world: World) {
        val organisms = world.get<ResourceComponent>(KEY_RES_ORGANISMS)?.amount ?: BigDouble.ZERO
        firstOrganismFired = organisms > BigDouble.ZERO
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val aminoAcids = resourceComp(world, KEY_RES_AMINO_ACIDS)?.amount ?: BigDouble.ZERO
        val proteins = resourceComp(world, KEY_RES_PROTEINS)?.amount ?: BigDouble.ZERO
        val cells = resourceComp(world, KEY_RES_CELLS)?.amount ?: BigDouble.ZERO
        val organisms = resourceComp(world, KEY_RES_ORGANISMS)?.amount ?: BigDouble.ZERO

        val resources = mapOf(
            ResourceType.AMINO_ACIDS.name to aminoAcids,
            ResourceType.PROTEINS.name to proteins,
            ResourceType.CELLS.name to cells,
            ResourceType.ORGANISMS.name to organisms
        )

        val genMeta = mapOf(
            KEY_GEN_PREBIOTIC_SOUP to "Prebiotic Soup",
            KEY_GEN_PROTEIN_SYNTHESIZER to "Protein Synthesizer",
            KEY_GEN_CELL_DIVISION to "Cell Division Chamber",
            KEY_GEN_ORGANISM_INCUBATOR to "Organism Incubator"
        )
        val generators = genMeta.keys.mapNotNull { key ->
            world.get<GeneratorComponent>(key)?.let { gen ->
                GeneratorSnapshot(
                    id = gen.id, displayName = genMeta[key] ?: key,
                    productionType = gen.productionType, productionRate = gen.productionRate,
                    costType = gen.costType, costAmount = gen.costAmount,
                    unlocked = gen.unlocked, level = gen.level
                )
            }
        }

        val upgMeta = mapOf(
            KEY_UPG_CATALYST_ENZYME to Pair("Catalyst Enzyme", "×2 Amino Acids per tap"),
            KEY_UPG_RNA_WORLD to Pair("RNA World", "Unlock Protein Synthesizer"),
            KEY_UPG_LIPID_MEMBRANE to Pair("Lipid Membrane", "×2 Cells/tick"),
            KEY_UPG_MULTICELLULARITY to Pair("Multicellularity", "Unlock Organism Incubator"),
            KEY_UPG_EVOLUTIONARY_PRESSURE to Pair("Evolutionary Pressure", "100 Cells → 1 Organism"),
            KEY_UPG_HORIZONTAL_GENE_TRANSFER to Pair("Horizontal Gene Transfer", "-50% Evolution cost")
        )
        val upgrades = upgMeta.keys.mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val availableResource = resources[upg.costType.name] ?: BigDouble.ZERO
                UpgradeSnapshot(
                    id = upg.id, displayName = upgMeta[key]!!.first,
                    description = upgMeta[key]!!.second,
                    costType = upg.costType, costAmount = upg.costAmount,
                    purchased = upg.purchased, repeatable = upg.repeatable,
                    available = availableResource >= upg.costAmount
                )
            }
        }

        val epochProgress = if (organisms >= BigDouble.of(WIN_THRESHOLD)) 1f else 0f

        return GameSnapshot(
            tick = tick, epoch = EpochType.BIOLOGY,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList()
        )
    }
}
```

- [ ] **Step 4: Run all tests — must pass**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, all 46 tests pass (34 previous + 12 new).

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/java/com/madmaxlgndklr/yhwh/systems/BiologySystem.kt \
  app/src/test/java/com/madmaxlgndklr/yhwh/systems/BiologySystemTest.kt
git commit -m "feat: implement BiologySystem with full resource chain, generators, and upgrades"
```

---

## Task 3: GameViewModel Epoch Integration

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

No new unit tests — GameViewModel requires an Android context. Verified by compile + assembleDebug.

- [ ] **Step 1: Add BiologySystem import**

In `GameViewModel.kt`, add after the existing CosmologySystem import line:

```kotlin
import com.madmaxlgndklr.yhwh.systems.BiologySystem
```

- [ ] **Step 2: Restructure init — epoch-aware system registration**

In the `init` block, the current line:
```kotlin
engine.registerSystem(CosmologySystem())
```
appears early in init (before `val saved = saveManager.load()`). The init must be restructured so the save is loaded first and used to select the right system.

Replace the entire `init` block with this version. Changes from the current file: (a) `engine.registerSystem(...)` moved after `val saved =`, epoch-aware; (b) `dismissEpochTransition()` updated; (c) `toCosmosState()` extended with Biology branch. Full `init` block:

```kotlin
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
    val system: GameSystem = when (saved?.snapshot?.epoch) {
        EpochType.BIOLOGY -> BiologySystem()
        else -> CosmologySystem()
    }
    engine.registerSystem(system)

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
```

- [ ] **Step 3: Update dismissEpochTransition()**

Replace the current `dismissEpochTransition()` method:

```kotlin
fun dismissEpochTransition() {
    epochTransitionAcknowledged = true
    _uiState.value = _uiState.value.copy(showEpochTransition = false)
    when (engine.snapshot.value?.epoch) {
        EpochType.COSMOLOGY -> engine.advanceEpoch(BiologySystem())
        else -> { /* future epochs */ }
    }
}
```

- [ ] **Step 4: Update toCosmosState() with Biology branch**

Replace the current `private fun GameSnapshot.toCosmosState()` extension function:

```kotlin
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
```

- [ ] **Step 5: Verify compilation + all tests**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: wire BiologySystem into GameViewModel — epoch-aware init, transition, and canvas state"
```

---

## Task 4: CosmosState + CosmosCanvas Biology Visuals

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt`

No unit tests (Canvas is not unit-testable). Verified by `assembleDebug`.

- [ ] **Step 1: Add Biology fields to CosmosState**

Replace `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EpochType

data class CosmosState(
    val epoch: EpochType = EpochType.COSMOLOGY,
    val matterLevel: Float = 0f,
    val starLevel: Float = 0f,
    val starsFormed: Boolean = false,
    val planetsFormed: Boolean = false,
    val aminoAcidLevel: Float = 0f,
    val cellLevel: Float = 0f
)
```

- [ ] **Step 2: Update CosmosCanvas — Biology background, particles, membranes**

Replace `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.madmaxlgndklr.yhwh.engine.EpochType
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

private data class TapBurst(val position: Offset, val startTime: Long)

private const val BURST_DURATION_MS = 400L
private const val BURST_PARTICLE_COUNT = 7
private const val BURST_MAX_RADIUS = 80f

@Composable
fun CosmosCanvas(
    state: CosmosState,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val starField = remember { generateStarField(count = 150) }
    val bursts = remember { mutableStateListOf<TapBurst>() }

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
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )
    val burstTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BURST_DURATION_MS.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "burst_tick"
    )

    val bgColor by animateColorAsState(
        targetValue = when (state.epoch) {
            EpochType.BIOLOGY -> Color(0xFF001A1A)
            else -> if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510)
        },
        animationSpec = tween(durationMillis = 3000),
        label = "bg_color"
    )

    val now = System.currentTimeMillis()
    bursts.removeAll { now - it.startTime > BURST_DURATION_MS }

    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onTap()
                bursts.add(TapBurst(position = offset, startTime = System.currentTimeMillis()))
            }
        }
    } else Modifier

    Box(modifier = modifier.then(tapModifier)) {
        Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
            drawStarField(starField)

            if (state.epoch == EpochType.BIOLOGY) {
                if (state.aminoAcidLevel > 0f) drawOrganicParticles(state.aminoAcidLevel, orbitalAngle)
                if (state.cellLevel > 0f) drawCellMembranes(state.cellLevel, glowPulse)
            } else {
                if (state.matterLevel > 0f) drawMatterParticles(state.matterLevel)
                if (state.starsFormed) drawStellarGlow(state.starLevel, glowPulse)
                if (state.starsFormed) drawOrbitalRing(orbitalAngle, state.starLevel)
                if (state.planetsFormed) drawPlanetRipple(glowPulse)
            }

            @Suppress("UNUSED_EXPRESSION") burstTick
            val drawNow = System.currentTimeMillis()
            bursts.forEach { burst -> drawBurst(burst, drawNow) }
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
    drawCircle(
        color = Color(0xFF4466AA).copy(alpha = 0.3f),
        radius = orbitRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f)
    )
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

private fun DrawScope.drawOrganicParticles(aminoAcidLevel: Float, orbitalAngle: Float) {
    val count = (aminoAcidLevel * 40).toInt().coerceAtLeast(1)
    val rng = Random(seed = 13L)
    repeat(count) { i ->
        val baseX = rng.nextFloat()
        val baseY = rng.nextFloat()
        val driftX = sin((orbitalAngle + i * 37f) * Math.PI.toFloat() / 180f) * 0.02f
        val driftY = cos((orbitalAngle + i * 23f) * Math.PI.toFloat() / 180f) * 0.02f
        val x = (baseX + driftX).coerceIn(0f, 1f) * size.width
        val y = (baseY + driftY).coerceIn(0f, 1f) * size.height
        drawCircle(
            color = Color(0xFF44BB66).copy(alpha = aminoAcidLevel * 0.5f),
            radius = rng.nextFloat() * 3f + 1.5f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawCellMembranes(cellLevel: Float, pulse: Float) {
    val rng = Random(seed = 99L)
    repeat(4) { i ->
        val cx = (rng.nextFloat() * 0.6f + 0.2f) * size.width
        val cy = (rng.nextFloat() * 0.6f + 0.2f) * size.height
        val radius = size.minDimension * (0.06f + i * 0.03f) * (0.8f + pulse * 0.4f)
        drawCircle(
            color = Color(0xFF004040).copy(alpha = cellLevel * pulse * 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawBurst(burst: TapBurst, now: Long) {
    val elapsed = (now - burst.startTime).coerceIn(0L, BURST_DURATION_MS)
    val progress = elapsed / BURST_DURATION_MS.toFloat()
    val rng = Random(burst.startTime.toInt())
    repeat(BURST_PARTICLE_COUNT) { i ->
        val angle = (i.toFloat() / BURST_PARTICLE_COUNT) * 2f * Math.PI.toFloat() +
                rng.nextFloat() * 0.4f
        val distance = BURST_MAX_RADIUS * progress
        val px = burst.position.x + cos(angle) * distance
        val py = burst.position.y + sin(angle) * distance
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val radius = (4f * (1f - progress * 0.5f)).coerceAtLeast(1f)
        drawCircle(
            color = Color(0xFF8080FF).copy(alpha = alpha * 0.9f),
            radius = radius,
            center = Offset(px, py)
        )
    }
}
```

- [ ] **Step 3: Full build**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all 46 tests pass.

- [ ] **Step 4: Install on device**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
```

If device shown:
```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb install -r \
  /home/madmaxlgndklr/Git/sandbox/YHWH/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 5: Manual verification checklist**

- [ ] Cosmology plays normally — canvas unchanged, generators work, upgrades work
- [ ] Form 1 planet (Gravitational Collapse) → epoch transition overlay appears with "A world has formed. Life stirs in the primordial ocean."
- [ ] Dismiss transition → canvas background shifts to dark ocean teal (`0xFF001A1A`)
- [ ] Tap canvas → produces Amino Acids (shown in resource displays)
- [ ] Amino Acid count rising → green organic particles appear on canvas
- [ ] Purchase RNA World → Protein Synthesizer unlocks
- [ ] Cell count rising → cell membrane rings pulse on canvas
- [ ] Accumulate 1,000 Organisms → epoch transition overlay fires again (Biology complete)

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt \
  app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt
git commit -m "feat: add Biology CosmosCanvas visuals — organic particles, cell membranes, ocean background"
```

- [ ] **Step 7: Push and tag**

```bash
git push origin main
git tag -a v0.2.0 -m "v0.2.0 — Biology epoch: full resource chain, generators, upgrades, canvas visuals"
git push origin v0.2.0
```

---

## Self-Review

- [x] **Spec §2 resource chain** — AMINO_ACIDS, PROTEINS, CELLS, ORGANISMS added to `ResourceType` (Task 1); BiologySystem initializes all 4 resources (Task 2)
- [x] **Spec §2 tap action** — `onTap` produces AMINO_ACIDS in BiologySystem (Task 2)
- [x] **Spec §2 win condition** — `organisms >= BigDouble.of(WIN_THRESHOLD)` (1000) → `epochProgress = 1f` in `toSnapshot` (Task 2)
- [x] **Spec §3 generators** — all 4 generators with correct costs/rates/lock state (Task 2)
- [x] **Spec §3 planet seeding** — `prebioticRate = 1.0 + planetCount * 0.1` read from world during `initialize` (Task 2)
- [x] **Spec §4 upgrades** — all 6 upgrades, correct effects, `upg_evolutionary_pressure` starts `purchased = true`, repeatable (Task 2)
- [x] **Spec §5a Restorable interface** — created in engine package (Task 1); CosmologySystem and BiologySystem both implement it (Tasks 1 and 2)
- [x] **Spec §5b advanceEpoch** — added to GameEngine (Task 1)
- [x] **Spec §5c dismissEpochTransition** — checks `engine.snapshot.value?.epoch`, advances on COSMOLOGY (Task 3)
- [x] **Spec §5d epoch-aware restore** — `when (saved?.snapshot?.epoch)` selects BiologySystem or CosmologySystem (Task 3)
- [x] **Spec §5e syncStateFromWorld** — sets `firstOrganismFired` from world state (Task 2)
- [x] **Spec §6a CosmosState fields** — `aminoAcidLevel`, `cellLevel` added (Task 4)
- [x] **Spec §6b Biology background** — `when (state.epoch) { EpochType.BIOLOGY -> Color(0xFF001A1A) }` (Task 4)
- [x] **Spec §6b drawOrganicParticles** — green-tinted drifting circles driven by `orbitalAngle` (Task 4)
- [x] **Spec §6b drawCellMembranes** — 4 pulsing rings in teal (Task 4)
- [x] **Spec §6b Cosmology layers suppressed** — `if (state.epoch == EpochType.BIOLOGY)` branch hides matter/stars/planet visuals (Task 4)
- [x] **Type consistency** — `KEY_RES_ORGANISMS`, `KEY_GEN_PREBIOTIC_SOUP`, etc. defined in companion and used consistently across BiologySystem, BiologySystemTest, GameViewModel
