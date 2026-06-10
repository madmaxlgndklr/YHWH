# Biology Epoch — Design Spec

**Date:** 2026-06-10
**Scope:** Second playable epoch — resource chain, generators, upgrades, epoch transition mechanics, canvas visuals, save/restore
**Version:** 1.0
**Follows:** Cosmology epoch (`CosmologySystem.kt`)

---

## 1. Overview

Biology is the second epoch, reached when the player forms 1 Planet during Cosmology. The player transitions from building a solar system to cultivating life. The resource chain ascends from Amino Acids to 1,000 Organisms (win condition). Planets accumulated in Cosmology carry over as a production seed bonus. The epoch transition is ViewModel-driven: dismissing the transition overlay triggers `engine.advanceEpoch(BiologySystem())`.

---

## 2. Resource Chain

**Chain:** Amino Acids → Proteins → Cells → Organisms

| Resource | Symbol | Display Name |
|---|---|---|
| AMINO_ACIDS | 🧪 | Amino Acids |
| PROTEINS | 🔗 | Proteins |
| CELLS | 🔬 | Cells |
| ORGANISMS | 🦠 | Organisms |

These 4 new values are added to `ResourceType`. Existing Cosmology resources (Energy, Matter, Hydrogen, Stars, Accretion Disks, Planets) remain in `ResourceType` and persist in the world but are not produced or consumed during Biology.

**Tap action:** Each tap produces Amino Acids directly (same mechanic as Cosmology Matter tap).

**Win condition:** `organisms >= 1000` → `epochProgress = 1f`

---

## 3. Generators (4)

| ID | Display Name | Produces | Costs | Starts |
|---|---|---|---|---|
| `gen_prebiotic_soup` | Prebiotic Soup | AMINO_ACIDS | ENERGY | Unlocked |
| `gen_protein_synthesizer` | Protein Synthesizer | PROTEINS | AMINO_ACIDS | Locked |
| `gen_cell_division` | Cell Division Chamber | CELLS | PROTEINS | Unlocked |
| `gen_organism_incubator` | Organism Incubator | ORGANISMS | CELLS | Locked |

**Planet seeding:** `gen_prebiotic_soup` starting `productionRate = 1.0 + (planetCount × 0.1)`. Read planet count from `world.get<ResourceComponent>("res_planets")?.amount?.toDouble() ?: 0.0` during `BiologySystem.initialize(world)`. Minimum rate 1.0 (no penalty for 0 planets).

**Generator costs (level 0):**
- Prebiotic Soup: 10 Energy/tick
- Protein Synthesizer: 5 Amino Acids/tick
- Cell Division Chamber: 10 Proteins/tick
- Organism Incubator: 5 Cells/tick

---

## 4. Upgrades (6)

| ID | Display Name | Cost | Effect | Repeatable |
|---|---|---|---|---|
| `upg_catalyst_enzyme` | Catalyst Enzyme | 50 Amino Acids | ×2 Amino Acids per tap | No |
| `upg_rna_world` | RNA World | 30 Amino Acids | Unlock Protein Synthesizer | No |
| `upg_lipid_membrane` | Lipid Membrane | 50 Proteins | ×2 Cells/tick | No |
| `upg_multicellularity` | Multicellularity | 30 Cells | Unlock Organism Incubator | No |
| `upg_evolutionary_pressure` | Evolutionary Pressure | 100 Cells | 100 Cells → 1 Organism | Yes |
| `upg_horizontal_gene_transfer` | Horizontal Gene Transfer | 50 Cells | −50% Evolutionary Pressure cost | No |

**Note:** `upg_evolutionary_pressure` starts with `purchased = true` (available from the start, like Gravitational Collapse) and is a `ManualConversion` effect. `upg_horizontal_gene_transfer` uses `ReduceConversionCost` targeting `upg_evolutionary_pressure`.

---

## 5. Epoch Transition Mechanics

### 5a. New `Restorable` interface

Create `app/src/main/java/com/madmaxlgndklr/yhwh/engine/Restorable.kt`:

```kotlin
interface Restorable {
    fun syncStateFromWorld(world: World)
}
```

Both `CosmologySystem` and `BiologySystem` implement `Restorable`.

`GameEngine.restore()` changes one line:
```kotlin
// Before:
systems.filterIsInstance<CosmologySystem>().forEach { it.syncStateFromWorld(world) }
// After:
systems.filterIsInstance<Restorable>().forEach { it.syncStateFromWorld(world) }
```

### 5b. New `GameEngine.advanceEpoch()`

```kotlin
fun advanceEpoch(nextSystem: GameSystem) {
    systems.add(nextSystem)
    activeSystem = nextSystem
    nextSystem.initialize(world)
    emitSnapshot()
}
```

### 5c. `GameViewModel.dismissEpochTransition()` — epoch-aware advance

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

### 5d. `GameViewModel.init` — epoch-aware restore

When loading a saved game, register the correct system based on the saved epoch:

```kotlin
val system: GameSystem = when (saved?.snapshot?.epoch) {
    EpochType.BIOLOGY -> BiologySystem()
    else -> CosmologySystem()
}
engine.registerSystem(system)
```

### 5e. `BiologySystem.syncStateFromWorld(world)`

Syncs the `firstOrganismFired` milestone flag from world state:

```kotlin
override fun syncStateFromWorld(world: World) {
    val organisms = world.get<ResourceComponent>(KEY_RES_ORGANISMS)?.amount ?: BigDouble.ZERO
    firstOrganismFired = organisms > BigDouble.ZERO
}
```

---

## 6. CosmosCanvas Visuals

### 6a. New `CosmosState` fields

```kotlin
val aminoAcidLevel: Float = 0f   // 0–1, normalized against AMINO_ACID_VISUAL_THRESHOLD
val cellLevel: Float = 0f        // 0–1, normalized against CELL_VISUAL_THRESHOLD
```

`BiologySystem` companion object defines:
```kotlin
const val AMINO_ACID_VISUAL_THRESHOLD = 500.0
const val CELL_VISUAL_THRESHOLD = 200.0
```

`GameViewModel.toCosmosState()` — extended to handle Biology epoch:
```kotlin
EpochType.BIOLOGY -> CosmosState(
    epoch = epoch,
    aminoAcidLevel = (aminoAcids / BiologySystem.AMINO_ACID_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
    cellLevel = (cells / BiologySystem.CELL_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
    // Cosmology fields zeroed out
    matterLevel = 0f, starLevel = 0f, starsFormed = false, planetsFormed = false
)
```

### 6b. New draw functions in `CosmosCanvas`

**Background:** animates toward `Color(0xFF001A1A)` (warm ocean) when `aminoAcidLevel > 0`. The existing `animateColorAsState` target uses epoch:
```kotlin
targetValue = when (state.epoch) {
    EpochType.BIOLOGY -> Color(0xFF001A1A)
    else -> if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510)
}
```

**`drawOrganicParticles(aminoAcidLevel)`** — 40 slow-drifting green-tinted circles, positions driven by `sin`/`cos` of `orbitalAngle` with random offsets (seeded). Replaces matter particles when epoch is BIOLOGY.

**`drawCellMembranes(cellLevel, pulse)`** — 3–5 soft pulsing rings at fixed random positions, `Color(0xFF004040)` with alpha driven by `cellLevel * pulse`. Similar to `drawStellarGlow` but multiple instances.

**Existing Cosmology layers** — suppressed when `epoch == BIOLOGY`:
```kotlin
if (state.epoch != EpochType.BIOLOGY) {
    if (state.matterLevel > 0f) drawMatterParticles(state.matterLevel)
    if (state.starsFormed) drawStellarGlow(state.starLevel, glowPulse)
    if (state.starsFormed) drawOrbitalRing(orbitalAngle, state.starLevel)
    if (state.planetsFormed) drawPlanetRipple(glowPulse)
}
```

---

## 7. File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/Restorable.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/BiologySystem.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt` — add AMINO_ACIDS, PROTEINS, CELLS, ORGANISMS
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt` — add `advanceEpoch()`, fix `restore()` to use `Restorable`
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt` — implement `Restorable`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — add `aminoAcidLevel`, `cellLevel`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch-aware init, `dismissEpochTransition()` advance, `toCosmosState()` Biology branch
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Biology background + organic particles + cell membranes

---

## 8. Out of Scope

- Evolution, Civilization, Interstellar epochs
- Leaderboards or cross-epoch statistics
- Biology-specific tutorial steps
- Cosmology resources appearing in the Biology UI (they are dormant in the world but not displayed)
- Inter-epoch prestige mechanics
