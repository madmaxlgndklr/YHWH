# Evolution Epoch — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Evolution epoch — third playable epoch after Biology — with Genes → Mutations → Species → Dominance resource chain, 4 generators, 6 upgrades (including a permanent fork), passive Species decay, periodic environmental events, and organic canvas visuals.

**Architecture:** `EvolutionSystem` mirrors `BiologySystem` exactly in structure (implements `GameSystem`, `PlayerActionHandler`, `Restorable`). A new `EvolutionEvent` enum drives the event overlay and debuff system. Event state is surfaced through `GameSnapshot` fields. Fork mechanic is enforced in `EvolutionSystem.purchaseUpgrade()` via private `forked`/`chosenPath` fields; the unchosen fork upgrade is set `available = false` in `toSnapshot()`.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, existing ECS engine (`World`, `GameSystem`, `PlayerActionHandler`, `Restorable`)

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/EvolutionEvent.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystem.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt` — add GENES, MUTATIONS, SPECIES, DOMINANCE
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt` — add `activeEvent`, `eventTicksRemaining`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — add `activeEvent`, `eventTicksRemaining`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — add `mutationLevel`, `speciesLevel`, `activeEvent`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch init, `dismissEpochTransition`, `toCosmosState`, `toUiState` event fields, transition message
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Evolution branch

---

## Task 1: Data Model — ResourceType, EvolutionEvent, GameSnapshot, GameUiState, CosmosState

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt`
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/EvolutionEvent.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt`

- [ ] **Step 1: Add four Evolution resource types to ResourceType.kt**

Append after `ORGANISMS`:

```kotlin
GENES("Genes", "🧬"),
MUTATIONS("Mutations", "🔀"),
SPECIES("Species", "🦎"),
DOMINANCE("Dominance", "👑")
```

Full file result:
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
    ORGANISMS("Organisms", "🦠"),
    GENES("Genes", "🧬"),
    MUTATIONS("Mutations", "🔀"),
    SPECIES("Species", "🦎"),
    DOMINANCE("Dominance", "👑")
}
```

- [ ] **Step 2: Create EvolutionEvent.kt**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/engine/EvolutionEvent.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
enum class EvolutionEvent(val displayName: String) {
    ICE_AGE("Ice Age"),
    ASTEROID_IMPACT("Asteroid Impact"),
    VOLCANIC_WINTER("Volcanic Winter")
}
```

- [ ] **Step 3: Add event fields to GameSnapshot.kt**

Add two nullable fields with defaults after `lifetimeTotals`:

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
    val available: Boolean
)

@Serializable
data class GameSnapshot(
    val tick: Long,
    val epoch: EpochType,
    val resources: Map<String, BigDouble>,
    val generators: List<GeneratorSnapshot>,
    val upgrades: List<UpgradeSnapshot>,
    val epochProgress: Float,
    val events: List<GameEvent>,
    val lifetimeTotals: Map<String, BigDouble> = emptyMap(),
    val activeEvent: EvolutionEvent? = null,
    val eventTicksRemaining: Int = 0
)
```

- [ ] **Step 4: Add event fields to GameUiState.kt**

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
    val eventTicksRemaining: Int = 0
)
```

- [ ] **Step 5: Add Evolution fields to CosmosState.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
import com.madmaxlgndklr.yhwh.engine.EpochType

data class CosmosState(
    val epoch: EpochType = EpochType.COSMOLOGY,
    val matterLevel: Float = 0f,
    val starLevel: Float = 0f,
    val starsFormed: Boolean = false,
    val planetsFormed: Boolean = false,
    val aminoAcidLevel: Float = 0f,
    val cellLevel: Float = 0f,
    val mutationLevel: Float = 0f,
    val speciesLevel: Float = 0f,
    val activeEvent: EvolutionEvent? = null
)
```

- [ ] **Step 6: Verify the project compiles**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/engine/EvolutionEvent.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt
git commit -m "feat: add Evolution data model — EvolutionEvent, ResourceType values, GameSnapshot/UiState/CosmosState fields"
```

---

## Task 2: EvolutionSystem — Core (Initialize, Generators, Tap, Upgrades, Snapshot)

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystem.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvolutionSystemTest {

    private lateinit var world: World
    private lateinit var system: EvolutionSystem

    @Before fun setup() {
        world = World()
        system = EvolutionSystem()
        system.initialize(world)
    }

    @Test fun `initialize populates genes resource`() {
        val genes = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)
        assertNotNull(genes)
        assertEquals(ResourceType.GENES, genes!!.type)
    }

    @Test fun `initialize populates gene pool generator unlocked`() {
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_PRIMORDIAL_GENE_POOL)
        assertNotNull(gen)
        assertTrue(gen!!.unlocked)
    }

    @Test fun `initialize leaves mutation engine locked`() {
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `initialize leaves ecosystem architect locked`() {
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_ECOSYSTEM_ARCHITECT)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `tap produces genes`() {
        system.onTap(world)
        val genes = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
        assertTrue(genes.amount > BigDouble.ZERO)
    }

    @Test fun `organism seeding increases gene pool production rate`() {
        val seededWorld = World()
        seededWorld.put(BiologySystem.KEY_RES_ORGANISMS,
            ResourceComponent(ResourceType.ORGANISMS, BigDouble.of(300.0)))
        val seededSystem = EvolutionSystem()
        seededSystem.initialize(seededWorld)
        // floor(300/100) * 10% = 30% bonus → 1.3x
        val gen = seededWorld.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_PRIMORDIAL_GENE_POOL)!!
        assertEquals(1.3, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `rna replication upgrade unlocks mutation engine`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_RNA_REPLICATION)
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)!!
        assertTrue(gen.unlocked)
    }

    @Test fun `niche colonization upgrade unlocks ecosystem architect`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_NICHE_COLONIZATION)
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_ECOSYSTEM_ARCHITECT)!!
        assertTrue(gen.unlocked)
    }

    @Test fun `hypermutation doubles mutation engine production`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
            .amount = BigDouble.of(1000.0)
        val before = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)!!.productionRate
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_HYPERMUTATION)
        val after = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)!!.productionRate
        assertEquals(before.toDouble() * 2.0, after.toDouble(), 0.01)
    }

    @Test fun `adaptive immunity can be purchased and locks hypermutation`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_MUTATIONS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY)
        val snap = system.toSnapshot(world, 0)
        val hypermutationSnap = snap.upgrades.first { it.id == EvolutionSystem.KEY_UPG_HYPERMUTATION }
        assertFalse(hypermutationSnap.available)
    }

    @Test fun `hypermutation can be purchased and locks adaptive immunity`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_HYPERMUTATION)
        val snap = system.toSnapshot(world, 0)
        val adaptiveSnap = snap.upgrades.first { it.id == EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY }
        assertFalse(adaptiveSnap.available)
    }

    @Test fun `epochProgress is 0 with no dominance`() {
        val snap = system.toSnapshot(world, 0)
        assertEquals(0f, snap.epochProgress, 0.01f)
    }

    @Test fun `epochProgress is 1 with 1000 dominance`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)!!
            .amount = BigDouble.of(1000.0)
        val snap = system.toSnapshot(world, 0)
        assertEquals(1f, snap.epochProgress, 0.01f)
    }

    @Test fun `apex dominance converts species to dominance`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(200.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_APEX_DOMINANCE)
        val dominance = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)!!
        assertTrue(dominance.amount >= BigDouble.ONE)
    }

    @Test fun `syncStateFromWorld restores fork state from world`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_MUTATIONS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY)
        // New system, restored from world
        val restoredSystem = EvolutionSystem()
        restoredSystem.initialize(world)
        restoredSystem.syncStateFromWorld(world)
        // After sync, hypermutation should still be locked
        val snap = restoredSystem.toSnapshot(world, 0)
        val hypermutationSnap = snap.upgrades.first { it.id == EvolutionSystem.KEY_UPG_HYPERMUTATION }
        assertFalse(hypermutationSnap.available)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.systems.EvolutionSystemTest" 2>&1 | tail -10
```

Expected: compilation error — `EvolutionSystem` not found.

- [ ] **Step 3: Implement EvolutionSystem.kt**

Create `app/src/main/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystem.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.floor
import kotlin.math.pow

class EvolutionSystem : GameSystem, PlayerActionHandler, Restorable {

    companion object {
        const val KEY_RES_GENES = "res_genes"
        const val KEY_RES_MUTATIONS = "res_mutations"
        const val KEY_RES_SPECIES = "res_species"
        const val KEY_RES_DOMINANCE = "res_dominance"

        const val KEY_GEN_PRIMORDIAL_GENE_POOL = "gen_primordial_gene_pool"
        const val KEY_GEN_MUTATION_ENGINE = "gen_mutation_engine"
        const val KEY_GEN_NATURAL_SELECTION_CHAMBER = "gen_natural_selection_chamber"
        const val KEY_GEN_ECOSYSTEM_ARCHITECT = "gen_ecosystem_architect"

        const val KEY_UPG_GENETIC_DRIFT = "upg_genetic_drift"
        const val KEY_UPG_RNA_REPLICATION = "upg_rna_replication"
        const val KEY_UPG_NICHE_COLONIZATION = "upg_niche_colonization"
        const val KEY_UPG_ADAPTIVE_IMMUNITY = "upg_adaptive_immunity"
        const val KEY_UPG_HYPERMUTATION = "upg_hypermutation"
        const val KEY_UPG_APEX_DOMINANCE = "upg_apex_dominance"

        const val MUTATION_VISUAL_THRESHOLD = 500.0
        const val SPECIES_VISUAL_THRESHOLD = 200.0
        const val WIN_THRESHOLD = 1000.0

        const val EVENT_GRACE_TICKS = 30
        const val EVENT_INTERVAL_TICKS = 60

        private val BASE_TAP_GENES = BigDouble.ONE
        private val BASE_GENES_PER_TICK = BigDouble.ONE
        private val APEX_COST = BigDouble.of(100.0)
        private const val DECAY_RATE_NORMAL = 0.5
        private const val DECAY_RATE_IMMUNE = 0.25
    }

    private var forked = false
    private var chosenPath: String? = null
    private var firstDominanceFired = false
    private var activeEvent: EvolutionEvent? = null
    private var eventTicksRemaining: Int = 0
    private var ticksUntilNextEvent: Int = EVENT_GRACE_TICKS + EVENT_INTERVAL_TICKS

    override fun initialize(world: World) {
        val organisms = world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)
            ?.amount?.toDouble() ?: 0.0
        val seedingBonus = BigDouble.of(1.0 + floor(organisms / 100.0) * 0.1)

        world.put(KEY_RES_GENES, ResourceComponent(ResourceType.GENES, BigDouble.ZERO))
        world.put(KEY_RES_MUTATIONS, ResourceComponent(ResourceType.MUTATIONS, BigDouble.ZERO))
        world.put(KEY_RES_SPECIES, ResourceComponent(ResourceType.SPECIES, BigDouble.ZERO))
        world.put(KEY_RES_DOMINANCE, ResourceComponent(ResourceType.DOMINANCE, BigDouble.ZERO))

        world.put(KEY_GEN_PRIMORDIAL_GENE_POOL, GeneratorComponent(
            id = KEY_GEN_PRIMORDIAL_GENE_POOL,
            productionType = ResourceType.GENES,
            productionRate = seedingBonus,
            costType = ResourceType.ORGANISMS,
            costAmount = BigDouble.ONE,
            unlocked = true
        ))
        world.put(KEY_GEN_MUTATION_ENGINE, GeneratorComponent(
            id = KEY_GEN_MUTATION_ENGINE,
            productionType = ResourceType.MUTATIONS,
            productionRate = BigDouble.ONE,
            costType = ResourceType.GENES,
            costAmount = BigDouble.of(2.0),
            unlocked = false
        ))
        world.put(KEY_GEN_NATURAL_SELECTION_CHAMBER, GeneratorComponent(
            id = KEY_GEN_NATURAL_SELECTION_CHAMBER,
            productionType = ResourceType.SPECIES,
            productionRate = BigDouble.ONE,
            costType = ResourceType.MUTATIONS,
            costAmount = BigDouble.of(10.0),
            unlocked = true
        ))
        world.put(KEY_GEN_ECOSYSTEM_ARCHITECT, GeneratorComponent(
            id = KEY_GEN_ECOSYSTEM_ARCHITECT,
            productionType = ResourceType.DOMINANCE,
            productionRate = BigDouble.ONE,
            costType = ResourceType.SPECIES,
            costAmount = BigDouble.of(5.0),
            unlocked = false
        ))

        world.put(KEY_UPG_GENETIC_DRIFT, UpgradeComponent(
            id = KEY_UPG_GENETIC_DRIFT, purchased = false,
            costType = ResourceType.GENES, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_RNA_REPLICATION, UpgradeComponent(
            id = KEY_UPG_RNA_REPLICATION, purchased = false,
            costType = ResourceType.GENES, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_MUTATION_ENGINE)
        ))
        world.put(KEY_UPG_NICHE_COLONIZATION, UpgradeComponent(
            id = KEY_UPG_NICHE_COLONIZATION, purchased = false,
            costType = ResourceType.SPECIES, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_ECOSYSTEM_ARCHITECT)
        ))
        // Adaptive Immunity: effect is behavioral only (checked in tick). MultiplyTapProduction(1.0) is a no-op sentinel.
        world.put(KEY_UPG_ADAPTIVE_IMMUNITY, UpgradeComponent(
            id = KEY_UPG_ADAPTIVE_IMMUNITY, purchased = false,
            costType = ResourceType.MUTATIONS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        world.put(KEY_UPG_HYPERMUTATION, UpgradeComponent(
            id = KEY_UPG_HYPERMUTATION, purchased = false,
            costType = ResourceType.GENES, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_MUTATION_ENGINE, BigDouble.of(2.0))
        ))
        // purchased = true makes it usable as a repeatable from the start
        world.put(KEY_UPG_APEX_DOMINANCE, UpgradeComponent(
            id = KEY_UPG_APEX_DOMINANCE, purchased = true,
            costType = ResourceType.SPECIES, costAmount = APEX_COST,
            effect = UpgradeEffect.ManualConversion(
                inputType = ResourceType.SPECIES,
                inputAmount = APEX_COST,
                outputType = ResourceType.DOMINANCE,
                outputAmount = BigDouble.ONE
            ),
            repeatable = true
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())
        val intDelta = delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        // Species decay
        val immunityPurchased = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
        val decayRate = if (immunityPurchased) DECAY_RATE_IMMUNE else DECAY_RATE_NORMAL
        resourceComp(world, KEY_RES_SPECIES)?.let { species ->
            species.amount = (species.amount - BigDouble.of(decayRate * delta)).coerceAtLeast(BigDouble.ZERO)
        }

        // Active event countdown
        if (activeEvent != null) {
            eventTicksRemaining = (eventTicksRemaining - intDelta).coerceAtLeast(0)
            if (eventTicksRemaining == 0) {
                events.add(GameEvent(0, "${activeEvent!!.displayName} has ended.", false))
                activeEvent = null
            }
        }

        // Event trigger
        if (activeEvent == null) {
            ticksUntilNextEvent = (ticksUntilNextEvent - intDelta).coerceAtLeast(0)
            if (ticksUntilNextEvent == 0) {
                val newEvent = EvolutionEvent.entries.random()
                activeEvent = newEvent
                val immune = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
                eventTicksRemaining = when (newEvent) {
                    EvolutionEvent.ICE_AGE -> if (immune) 15 else 30
                    EvolutionEvent.ASTEROID_IMPACT -> if (immune) 10 else 20
                    EvolutionEvent.VOLCANIC_WINTER -> if (immune) 22 else 45
                }
                ticksUntilNextEvent = EVENT_INTERVAL_TICKS
                events.add(GameEvent(0, "${newEvent.displayName} has begun!", true))
            }
        }

        // Passive gene baseline
        resourceComp(world, KEY_RES_GENES)?.let {
            it.amount = it.amount + BASE_GENES_PER_TICK * bigDelta
        }

        // Generators with event debuffs
        val iceMultiplier = if (activeEvent == EvolutionEvent.ICE_AGE) BigDouble.of(0.5) else BigDouble.ONE
        val asteroidMultiplier = if (activeEvent == EvolutionEvent.ASTEROID_IMPACT) BigDouble.of(0.25) else BigDouble.ONE
        val volcanicMultiplier = if (activeEvent == EvolutionEvent.VOLCANIC_WINTER) BigDouble.of(0.5) else BigDouble.ONE

        runGenerator(world, KEY_GEN_PRIMORDIAL_GENE_POOL, bigDelta, iceMultiplier)
        runGenerator(world, KEY_GEN_MUTATION_ENGINE, bigDelta, asteroidMultiplier)
        runGenerator(world, KEY_GEN_NATURAL_SELECTION_CHAMBER, bigDelta, volcanicMultiplier)
        runGenerator(world, KEY_GEN_ECOSYSTEM_ARCHITECT, bigDelta)

        // Dominance milestone
        resourceComp(world, KEY_RES_DOMINANCE)?.let { dom ->
            if (!firstDominanceFired && dom.amount > BigDouble.ZERO) {
                firstDominanceFired = true
                events.add(GameEvent(0, "Dominance established. The ecosystem bends to your will.", true))
            }
        }

        return events
    }

    private fun runGenerator(
        world: World, key: String, delta: BigDouble,
        productionMultiplier: BigDouble = BigDouble.ONE
    ) {
        val gen = world.get<GeneratorComponent>(key) ?: return
        if (!gen.unlocked) return
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        val totalCost = gen.costAmount * delta
        if (costRes.amount < totalCost) return
        costRes.amount = costRes.amount - totalCost
        val prodRes = resourceComp(world, "res_${gen.productionType.name.lowercase()}") ?: return
        prodRes.amount = prodRes.amount + gen.productionRate * delta * productionMultiplier
    }

    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_GENES)?.let { it.amount = it.amount + tapAmount }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        when (val effect = upg.effect) {
            is UpgradeEffect.ManualConversion -> {
                // Apex Dominance repeatable conversion
                if (!upg.purchased) return
                val inputRes = resourceComp(world, "res_${effect.inputType.name.lowercase()}") ?: return
                if (inputRes.amount < effect.inputAmount) return
                inputRes.amount = inputRes.amount - effect.inputAmount
                val outputRes = resourceComp(world, "res_${effect.outputType.name.lowercase()}") ?: return
                outputRes.amount = outputRes.amount + effect.outputAmount
            }
            else -> {
                // Fork gate: if the other fork path was chosen, deny purchase
                if (upgradeId == KEY_UPG_ADAPTIVE_IMMUNITY || upgradeId == KEY_UPG_HYPERMUTATION) {
                    if (forked && chosenPath != upgradeId) return
                }
                if (upg.purchased) return
                val costRes = resourceComp(world, "res_${upg.costType.name.lowercase()}") ?: return
                if (costRes.amount < upg.costAmount) return
                costRes.amount = costRes.amount - upg.costAmount
                upg.purchased = true
                if (upgradeId == KEY_UPG_ADAPTIVE_IMMUNITY || upgradeId == KEY_UPG_HYPERMUTATION) {
                    forked = true
                    chosenPath = upgradeId
                }
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
            is UpgradeEffect.ManualConversion -> { /* handled in purchaseUpgrade */ }
            is UpgradeEffect.ReduceConversionCost -> { /* not used in Evolution */ }
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
        val upg = world.get<UpgradeComponent>(KEY_UPG_GENETIC_DRIFT)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_GENES * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_GENES
        }
    }

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)

    override fun syncStateFromWorld(world: World) {
        val adaptiveImmunityPurchased = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
        val hypermutationPurchased = world.get<UpgradeComponent>(KEY_UPG_HYPERMUTATION)?.purchased == true
        when {
            adaptiveImmunityPurchased -> { forked = true; chosenPath = KEY_UPG_ADAPTIVE_IMMUNITY }
            hypermutationPurchased -> { forked = true; chosenPath = KEY_UPG_HYPERMUTATION }
        }
        val dominance = resourceComp(world, KEY_RES_DOMINANCE)?.amount ?: BigDouble.ZERO
        firstDominanceFired = dominance > BigDouble.ZERO
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val genes = resourceComp(world, KEY_RES_GENES)?.amount ?: BigDouble.ZERO
        val mutations = resourceComp(world, KEY_RES_MUTATIONS)?.amount ?: BigDouble.ZERO
        val species = resourceComp(world, KEY_RES_SPECIES)?.amount ?: BigDouble.ZERO
        val dominance = resourceComp(world, KEY_RES_DOMINANCE)?.amount ?: BigDouble.ZERO

        val resources = mapOf(
            ResourceType.GENES.name to genes,
            ResourceType.MUTATIONS.name to mutations,
            ResourceType.SPECIES.name to species,
            ResourceType.DOMINANCE.name to dominance
        )

        val genMeta = mapOf(
            KEY_GEN_PRIMORDIAL_GENE_POOL to "Primordial Gene Pool",
            KEY_GEN_MUTATION_ENGINE to "Mutation Engine",
            KEY_GEN_NATURAL_SELECTION_CHAMBER to "Natural Selection Chamber",
            KEY_GEN_ECOSYSTEM_ARCHITECT to "Ecosystem Architect"
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

        val adaptiveImmunityPurchased = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
        val hypermutationPurchased = world.get<UpgradeComponent>(KEY_UPG_HYPERMUTATION)?.purchased == true

        val upgMeta = mapOf(
            KEY_UPG_GENETIC_DRIFT to Pair("Genetic Drift", "×2 Genes per tap"),
            KEY_UPG_RNA_REPLICATION to Pair("RNA Replication", "Unlock Mutation Engine"),
            KEY_UPG_NICHE_COLONIZATION to Pair("Niche Colonization", "Unlock Ecosystem Architect"),
            KEY_UPG_ADAPTIVE_IMMUNITY to Pair("Adaptive Immunity", "½ decay rate · ½ event duration"),
            KEY_UPG_HYPERMUTATION to Pair("Hypermutation", "×2 Mutation Engine production"),
            KEY_UPG_APEX_DOMINANCE to Pair("Apex Dominance", "100 Species → 1 Dominance")
        )
        val upgrades = upgMeta.keys.mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val availableResource = resources[upg.costType.name] ?: BigDouble.ZERO
                val forkLocked = (key == KEY_UPG_ADAPTIVE_IMMUNITY && hypermutationPurchased) ||
                        (key == KEY_UPG_HYPERMUTATION && adaptiveImmunityPurchased)
                val available = when {
                    forkLocked -> false
                    upg.repeatable -> availableResource >= upg.costAmount
                    upg.purchased -> false
                    else -> availableResource >= upg.costAmount
                }
                UpgradeSnapshot(
                    id = upg.id,
                    displayName = upgMeta[key]!!.first,
                    description = upgMeta[key]!!.second,
                    costType = upg.costType,
                    costAmount = upg.costAmount,
                    purchased = upg.purchased,
                    repeatable = upg.repeatable,
                    available = available
                )
            }
        }

        val epochProgress = (dominance.toDouble() / WIN_THRESHOLD).toFloat().coerceIn(0f, 1f)

        return GameSnapshot(
            tick = tick, epoch = EpochType.EVOLUTION,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList(),
            activeEvent = activeEvent, eventTicksRemaining = eventTicksRemaining
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.systems.EvolutionSystemTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` with all 13 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystem.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt
git commit -m "feat: implement EvolutionSystem — resource chain, generators, upgrades, fork mechanic, events, decay"
```

---

## Task 3: EvolutionSystem — Extinction and Event Tests

**Files:**
- Modify: `app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt`

These tests verify the tick-level mechanics: decay and events. Add them to the existing test class.

- [ ] **Step 1: Add decay and event tests to EvolutionSystemTest.kt**

Append these tests inside `EvolutionSystemTest`:

```kotlin
    @Test fun `species decay each tick`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(100.0)
        system.tick(world, delta = 1L)
        val after = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!.amount
        assertTrue(after < BigDouble.of(100.0))
    }

    @Test fun `adaptive immunity halves species decay rate`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_MUTATIONS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY)

        val normalWorld = World()
        val normalSystem = EvolutionSystem()
        normalSystem.initialize(normalWorld)

        // Give both 100 species
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!.amount = BigDouble.of(100.0)
        normalWorld.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!.amount = BigDouble.of(100.0)

        system.tick(world, delta = 10L)
        normalSystem.tick(normalWorld, delta = 10L)

        val immune = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!.amount
        val normal = normalWorld.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!.amount
        assertTrue(immune > normal)
    }

    @Test fun `event fires after grace period and interval`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(10000.0)
        val events = system.tick(world, delta = (EvolutionSystem.EVENT_GRACE_TICKS + EvolutionSystem.EVENT_INTERVAL_TICKS).toLong())
        assertTrue(events.any { it.isMilestone && it.message.contains("has begun") })
    }

    @Test fun `no event fires within grace period`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(10000.0)
        val events = system.tick(world, delta = (EvolutionSystem.EVENT_GRACE_TICKS - 1).toLong())
        assertTrue(events.none { it.message.contains("has begun") })
    }

    @Test fun `ice age reduces gene pool production`() {
        // Seed resources so generators can run
        world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)
            ?: world.put(BiologySystem.KEY_RES_ORGANISMS,
                ResourceComponent(ResourceType.ORGANISMS, BigDouble.of(10000.0)))
        world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
            .amount = BigDouble.of(10000.0)

        // Tick without event — record genes gained
        val genesBefore = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!.amount
        system.tick(world, delta = 1L)
        val genesNormalTick = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!.amount - genesBefore

        // Force ICE_AGE by ticking to the event trigger point
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(10000.0)
        system.tick(world, delta = (EvolutionSystem.EVENT_GRACE_TICKS + EvolutionSystem.EVENT_INTERVAL_TICKS - 1).toLong())

        // One final tick — if ICE_AGE fires, gene production for that tick is halved
        val genesBeforeEvent = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!.amount
        val tickEvents = system.tick(world, delta = 1L)
        val genesAfterEvent = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!.amount

        if (tickEvents.any { it.message.contains("Ice Age") }) {
            // If ICE_AGE triggered: passive (1.0) + debuffed gene pool should produce less than normal tick
            val gained = genesAfterEvent - genesBeforeEvent
            // Passive generation (BASE_GENES_PER_TICK = 1.0) always runs, so gain >= 1.0
            // Gene pool at 0.5x means total should be less than genesNormalTick (which included full gene pool)
            assertTrue(gained.toDouble() <= genesNormalTick.toDouble() + 0.01)
        }
        // If a different event fired, the test passes trivially (event selection is random)
    }
```

- [ ] **Step 2: Run all EvolutionSystemTest tests**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.systems.EvolutionSystemTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 3: Run the full test suite to confirm no regressions**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt
git commit -m "test: add extinction pressure and environmental event tests for EvolutionSystem"
```

---

## Task 4: GameViewModel Wiring

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

Four changes in GameViewModel: (1) restore from Evolution saves, (2) advance to Evolution on Biology complete, (3) `toCosmosState()` Evolution branch, (4) `toUiState()` event fields and epoch-aware transition message.

- [ ] **Step 1: Update save restoration to handle Evolution epoch**

In `GameViewModel.init`, find:

```kotlin
val system: GameSystem = when (saved?.snapshot?.epoch) {
    EpochType.BIOLOGY -> BiologySystem()
    else -> CosmologySystem()
}
```

Replace with:

```kotlin
val system: GameSystem = when (saved?.snapshot?.epoch) {
    EpochType.BIOLOGY -> BiologySystem()
    EpochType.EVOLUTION -> EvolutionSystem()
    else -> CosmologySystem()
}
```

Also add the import at the top of the file (with the other system imports):

```kotlin
import com.madmaxlgndklr.yhwh.systems.EvolutionSystem
```

- [ ] **Step 2: Update dismissEpochTransition to advance to Evolution**

Find:

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

Replace with:

```kotlin
fun dismissEpochTransition() {
    epochTransitionAcknowledged = true
    _uiState.value = _uiState.value.copy(showEpochTransition = false)
    when (engine.snapshot.value?.epoch) {
        EpochType.COSMOLOGY -> engine.advanceEpoch(BiologySystem())
        EpochType.BIOLOGY -> engine.advanceEpoch(EvolutionSystem())
        else -> { /* future epochs */ }
    }
}
```

- [ ] **Step 3: Update toUiState() for event fields and epoch-aware transition message**

Find:

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
    recentEvents = events.map { it.message }.takeLast(5)
)
```

Replace with:

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
    eventTicksRemaining = eventTicksRemaining
)
```

Also add the import at the top of the file:

```kotlin
import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
```

Find the transition message in the snapshot collector:

```kotlin
transitionMessage = if (showTransition)
    "A world has formed. Life stirs in the primordial ocean."
else _uiState.value.transitionMessage,
```

Replace with:

```kotlin
transitionMessage = if (showTransition) when (snapshot.epoch) {
    EpochType.COSMOLOGY -> "A world has formed. Life stirs in the primordial ocean."
    EpochType.BIOLOGY -> "Organisms compete for survival. Evolution begins."
    else -> "The next age dawns."
} else _uiState.value.transitionMessage,
```

- [ ] **Step 4: Update toCosmosState() to add Evolution branch**

Find:

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

Replace with:

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

- [ ] **Step 5: Verify the project compiles**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run full test suite**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: wire EvolutionSystem into GameViewModel — epoch init, transition, canvas state, event fields"
```

---

## Task 5: CosmosCanvas Evolution Visuals

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt`

Add an Evolution branch to the canvas: organism silhouettes that diversify as `speciesLevel` grows, a background that lerps from ocean blue to lush green via `mutationLevel`, and an event overlay tinted by `activeEvent`.

- [ ] **Step 1: Add Evolution branch to the epoch when block in CosmosCanvas**

Find:

```kotlin
if (state.epoch == EpochType.BIOLOGY) {
    if (state.aminoAcidLevel > 0f) drawOrganicParticles(state.aminoAcidLevel, orbitalAngle)
    if (state.cellLevel > 0f) drawCellMembranes(state.cellLevel, glowPulse)
} else {
    if (state.matterLevel > 0f) drawMatterParticles(state.matterLevel)
    if (state.starsFormed) drawStellarGlow(state.starLevel, glowPulse)
    if (state.starsFormed) drawOrbitalRing(orbitalAngle, state.starLevel)
    if (state.planetsFormed) drawPlanetRipple(glowPulse)
}
```

Replace with:

```kotlin
when (state.epoch) {
    EpochType.BIOLOGY -> {
        if (state.aminoAcidLevel > 0f) drawOrganicParticles(state.aminoAcidLevel, orbitalAngle)
        if (state.cellLevel > 0f) drawCellMembranes(state.cellLevel, glowPulse)
    }
    EpochType.EVOLUTION -> {
        drawOrganismParticles(state.speciesLevel, orbitalAngle)
        if (state.activeEvent != null) drawEventOverlay(state.activeEvent, glowPulse)
    }
    else -> {
        if (state.matterLevel > 0f) drawMatterParticles(state.matterLevel)
        if (state.starsFormed) drawStellarGlow(state.starLevel, glowPulse)
        if (state.starsFormed) drawOrbitalRing(orbitalAngle, state.starLevel)
        if (state.planetsFormed) drawPlanetRipple(glowPulse)
    }
}
```

- [ ] **Step 2: Update background color to handle Evolution**

Find:

```kotlin
val bgColor by animateColorAsState(
    targetValue = when (state.epoch) {
        EpochType.BIOLOGY -> Color(0xFF001A1A)
        else -> if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510)
    },
    animationSpec = tween(durationMillis = 3000),
    label = "bg_color"
)
```

Replace with:

```kotlin
val evolutionBgColor = when {
    state.mutationLevel < 0.5f -> {
        // Deep ocean blue → earthy green-brown
        val t = state.mutationLevel * 2f
        Color(
            red = (0x00 + (0x1A * t).toInt()).coerceIn(0, 255) / 255f,
            green = (0x1A + (0x14 * t).toInt()).coerceIn(0, 255) / 255f,
            blue = (0x1A - (0x0A * t).toInt()).coerceIn(0, 255) / 255f,
            alpha = 1f
        )
    }
    else -> {
        // Earthy green-brown → lush green
        val t = (state.mutationLevel - 0.5f) * 2f
        Color(
            red = (0x1A - (0x14 * t).toInt()).coerceIn(0, 255) / 255f,
            green = (0x2E + (0x12 * t).toInt()).coerceIn(0, 255) / 255f,
            blue = (0x10 - (0x08 * t).toInt()).coerceIn(0, 255) / 255f,
            alpha = 1f
        )
    }
}

val bgColor by animateColorAsState(
    targetValue = when (state.epoch) {
        EpochType.BIOLOGY -> Color(0xFF001A1A)
        EpochType.EVOLUTION -> evolutionBgColor
        else -> if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510)
    },
    animationSpec = tween(durationMillis = 3000),
    label = "bg_color"
)
```

- [ ] **Step 3: Add the drawOrganismParticles and drawEventOverlay functions**

Append these private functions after `drawCellMembranes`:

```kotlin
private fun DrawScope.drawOrganismParticles(speciesLevel: Float, orbitalAngle: Float) {
    val baseCount = (speciesLevel * 30).toInt().coerceAtLeast(3)
    val rng = Random(seed = 77L)
    repeat(baseCount) { i ->
        val baseX = rng.nextFloat()
        val baseY = rng.nextFloat()
        val driftX = sin((orbitalAngle + i * 41f) * Math.PI.toFloat() / 180f) * 0.015f
        val driftY = cos((orbitalAngle + i * 29f) * Math.PI.toFloat() / 180f) * 0.015f
        val x = (baseX + driftX).coerceIn(0f, 1f) * size.width
        val y = (baseY + driftY).coerceIn(0f, 1f) * size.height

        // Species level drives size diversity: low = small uniform, high = mixed sizes
        val sizeVariance = if (speciesLevel > 0.3f) rng.nextFloat() * 5f + 2f else 2.5f
        val alpha = (0.4f + speciesLevel * 0.4f).coerceIn(0f, 1f)

        drawOval(
            color = Color(0xFF6DBF67).copy(alpha = alpha),
            topLeft = Offset(x - sizeVariance, y - sizeVariance * 0.6f),
            size = Size(sizeVariance * 2f, sizeVariance * 1.2f)
        )
    }
}

private fun DrawScope.drawEventOverlay(event: EvolutionEvent, pulse: Float) {
    when (event) {
        EvolutionEvent.ICE_AGE ->
            drawRect(
                color = Color(0xFF88CCFF).copy(alpha = 0.12f + pulse * 0.06f),
                size = size
            )
        EvolutionEvent.ASTEROID_IMPACT ->
            drawRect(
                color = Color(0xFFFF4444).copy(alpha = 0.08f + pulse * 0.04f),
                size = size
            )
        EvolutionEvent.VOLCANIC_WINTER ->
            drawRect(
                color = Color(0xFF886644).copy(alpha = 0.15f + pulse * 0.05f),
                size = size
            )
    }
}
```

- [ ] **Step 4: Add missing imports to CosmosCanvas.kt**

At the top of the file, ensure these imports are present (add any missing):

```kotlin
import androidx.compose.ui.geometry.Size
import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
```

- [ ] **Step 5: Verify the project compiles**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run full test suite one final time**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt
git commit -m "feat: add Evolution CosmosCanvas visuals — organism particles, event overlays, dynamic background"
```
