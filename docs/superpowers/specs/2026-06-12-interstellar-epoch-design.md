# Interstellar Epoch — Design Spec

**Date:** 2026-06-12
**Status:** Approved
**Epoch:** Interstellar (5th of 5 — COSMOLOGY → BIOLOGY → EVOLUTION → CIVILIZATION → **INTERSTELLAR**)

---

## Overview

Interstellar is the final playable epoch. Players take civilization into the cosmos — building a scientific foundation, assembling a starfleet, planting colonies across the stars, and ultimately etching humanity's Legacy into the universe. It is the culminating epoch of the god simulator arc.

The unique pressure mechanic is **Vessel Decay**: starships are lost to the void at a flat rate each tick. Drive Phase upgrades (Ion Drive → Hyperdrive) double all production but also double attrition, creating the same risk/reward loop as Civilization's Unrest × Era Advancement. The player manages decay passively via Hull Plating and actively via Emergency Repairs.

---

## Resource Chain

**Research → Vessels → Colonies → Legacy**

| Resource | Symbol | Thematic role |
|---|---|---|
| RESEARCH | 🔭 | Scientific foundation — tapped and passively trickled |
| VESSELS | 🚀 | Starships — built from Research, decay over time |
| COLONIES | 🌌 | Star settlements — consume Vessels, drive Legacy |
| LEGACY | 🌠 | Win resource — humanity's mark on the cosmos |

---

## Seeding from Civilization

Every 100 Civilization the player finished Civilization with grants +10% Research Institute production rate.

- Minimum Civilization win: 1,000 → **+100% (2× base)** Research production at Interstellar start
- Formula: `floor(civilization / 100.0) * 10%` — e.g., 1,500 Civilization = +150%
- Read from `world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)` in `InterstellarSystem.initialize()`
- Mirrors the Dominance → Followers seeding pattern from Civilization

---

## Win Condition

**1,000 Legacy** — consistent with all prior epochs.

`epochProgress = min(legacy / 1000.0, 1.0).toFloat()`

After winning, the epoch transition overlay shows. Dismissing it hits the `else -> {}` branch of `dismissEpochTransition()` — no further epoch advance. The epoch stays at INTERSTELLAR with epochProgress = 1.0 and `nextEpochName = "Complete"`. The player presses the existing restart button to begin a new run with the full 2.00× seed multiplier.

---

## Passive Generation and Tap

**Tap:** `onTap()` produces 1 RESEARCH by default. `upg_advanced_sensors` doubles it to 2.

`BASE_RESEARCH_PER_TICK = BigDouble.of(2.0)` — a passive Research trickle applied every tick unconditionally (like Civilization's `BASE_FOLLOWERS_PER_TICK`). This ensures the player always has some Research income once the Civilization stockpile runs dry, so the game never gets stuck.

---

## Generators

Four generators. Gen 1 (`gen_research_institute`) and Gen 2 (`gen_shipyard`) start unlocked at level 0. Gen 3 unlocks when `upg_ion_drive` is purchased; Gen 4 unlocks when `upg_hyperdrive` is purchased.

| Key | Display Name | Produces | Costs (per tick) | Default |
|---|---|---|---|---|
| `gen_research_institute` | Research Institute | RESEARCH | CIVILIZATION | Unlocked |
| `gen_shipyard` | Shipyard | VESSELS | RESEARCH | Unlocked |
| `gen_colony_fleet` | Colony Fleet | COLONIES | VESSELS | **Locked** (Ion Drive) |
| `gen_galactic_senate` | Galactic Senate | LEGACY | COLONIES | **Locked** (Hyperdrive) |

**Initial costs and rates:**

| Key | `costAmount` | `productionRate` |
|---|---|---|
| `gen_research_institute` | 1.0 CIVILIZATION | seeding bonus (see above) |
| `gen_shipyard` | 0.5 RESEARCH | 1.0 |
| `gen_colony_fleet` | 0.5 VESSELS | 1.0 |
| `gen_galactic_senate` | 0.5 COLONIES | 1.0 |

All generator output is multiplied by `driveMultiplier` (see Drive Phases).

Gen 2 starts unlocked but requires Research to run — which only exists once the player has tapped or the Civilization stockpile feeds the Research Institute. The cost guard in `runGenerator()` prevents premature production naturally.

---

## Vessel Decay

`vesselDecayRate: Float` — flat Vessels lost per tick, floor at 0. Applied in `tick()` before generators run.

**Decay rates by drive phase:**

| Phase | Rate (per tick) | With Hull Plating |
|---|---|---|
| Sublight (phase 0) | −0.1 | −0.05 |
| Ion (phase 1) | −0.2 | −0.10 |
| Hyperdrive (phase 2) | −0.4 | −0.20 |

In `tick()`:
```kotlin
val hullPurchased = world.get<UpgradeComponent>(KEY_UPG_HULL_PLATING)?.purchased == true
val decayAmount = if (hullPurchased) vesselDecayRate * 0.5f else vesselDecayRate
resourceComp(world, KEY_RES_VESSELS)?.let { vessels ->
    vessels.amount = (vessels.amount - BigDouble.of(decayAmount.toDouble())).coerceAtLeast(BigDouble.ZERO)
}
```

`vesselDecayRate` changes when drive phase advances (see Drive Phases). No new `GameSnapshot` field needed — `vesselDecayRate` is fully derivable from `drivePhase` and Hull Plating purchased state via `syncStateFromWorld()`.

**Design tension:** Advancing drive phases is always powerful but immediately increases attrition. The player must level the Shipyard before advancing to sustain the fleet. Hull Plating (passive, purchased once) and Emergency Repairs (active, repeatable) are complementary mitigation tools.

---

## Upgrades

Six upgrades. `upg_hyperdrive` is blocked until `upg_ion_drive` is purchased. `upg_hull_plating`, `upg_emergency_repairs`, and both drive upgrades use sentinel effects handled behaviorally in `InterstellarSystem.purchaseUpgrade()`.

| # | Key | Display Name | Effect | Cost Type | Cost |
|---|---|---|---|---|---|
| 1 | `upg_advanced_sensors` | Advanced Sensors | ×2 tap Research | RESEARCH | 50 |
| 2 | `upg_hull_plating` | Hull Plating | Halve Vessel decay rate | VESSELS | 30 |
| 3 | `upg_emergency_repairs` | Emergency Repairs | Repeatable: 50 Research → +25 Vessels | RESEARCH | 50 |
| 4 | `upg_long_range_comms` | Long-Range Comms | ×2 Colony Fleet production | COLONIES | 50 |
| 5 | `upg_ion_drive` | Ion Drive | Phase 1: unlock Colony Fleet + ×2 all gen production | RESEARCH | 50 |
| 6 | `upg_hyperdrive` | Hyperdrive | Phase 2: unlock Galactic Senate + ×2 all gen (×4 total) | COLONIES | 200 |

### Upgrade effect mapping

- `upg_advanced_sensors` → `UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))` (existing type)
- `upg_hull_plating` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel — effect applied behaviorally in `tick()` by checking `purchased` flag
- `upg_emergency_repairs` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel, `repeatable = true` — in `purchaseUpgrade()`: spend 50 RESEARCH → add 25 VESSELS (floored at 0 source check)
- `upg_long_range_comms` → `UpgradeEffect.MultiplyProduction(KEY_GEN_COLONY_FLEET, BigDouble.of(2.0))` (existing type)
- `upg_ion_drive` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel — in `purchaseUpgrade()`: sets `drivePhase = 1`, `driveMultiplier = BigDouble.of(2.0)`, `vesselDecayRate = 0.2f`, unlocks Colony Fleet
- `upg_hyperdrive` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel — in `purchaseUpgrade()`: guarded by `drivePhase >= 1`; sets `drivePhase = 2`, `driveMultiplier = BigDouble.of(4.0)`, `vesselDecayRate = 0.4f`, unlocks Galactic Senate; in `toSnapshot()`, `upg_hyperdrive.available = false` when `drivePhase < 1`

### `currentTapProduction()` note

Like Civilization's `currentTapProduction()`, this method checks only `upg_advanced_sensors` by key — not all purchased `MultiplyTapProduction` effects — because the three sentinel upgrades also use that effect type with `multiplier = 1.0`.

---

## Drive Phases

Tracked in `InterstellarSystem`:

```kotlin
private var drivePhase: Int = 0             // 0=Sublight, 1=Ion, 2=Hyperdrive
private var driveMultiplier: BigDouble = BigDouble.ONE  // 1×, 2×, or 4×
private var vesselDecayRate: Float = 0.1f
```

`driveMultiplier` is applied in `runGenerator()` — all generators multiply their output by it. Mirrors `eraMultiplier` in `CivilizationSystem`.

**Restore (`syncStateFromWorld()`):**
```kotlin
val ionPurchased = world.get<UpgradeComponent>(KEY_UPG_ION_DRIVE)?.purchased == true
val hyperdrivePurchased = world.get<UpgradeComponent>(KEY_UPG_HYPERDRIVE)?.purchased == true
drivePhase = when {
    hyperdrivePurchased -> 2
    ionPurchased -> 1
    else -> 0
}
driveMultiplier = when (drivePhase) {
    2 -> BigDouble.of(4.0)
    1 -> BigDouble.of(2.0)
    else -> BigDouble.ONE
}
vesselDecayRate = when (drivePhase) {
    2 -> 0.4f
    1 -> 0.2f
    else -> 0.1f
}
```

No new `GameSnapshot` field needed for drive phase — fully derivable from purchased upgrade state.

---

## Milestone Events

| Trigger | Message | `isMilestone` |
|---|---|---|
| First RESEARCH > 0 | "The stars await. Research begins." | true |
| First VESSELS > 0 | "First starship assembled and launched." | true |
| First COLONIES > 0 | "A new world settles among the stars." | true |
| First LEGACY > 0 | "Humanity's legacy endures beyond the cosmos." | true |

Guarded by boolean flags (`firstResearchFired`, etc.) identical to `firstCivilizationFired` in Civilization.

---

## Data Model Changes

### Modified: `ResourceType.kt`

Add four entries (after CIVILIZATION):
```kotlin
RESEARCH("Research", "🔭"),
VESSELS("Vessels", "🚀"),
COLONIES("Colonies", "🌌"),
LEGACY("Legacy", "🌠"),
```

### Modified: `GameUiState.kt`

Add two fields (with defaults for backwards compatibility):
```kotlin
val interstellarPhaseName: String = "",
val vesselDecayRate: Float = 0f,
```

### Modified: `CosmosState.kt`

Add two fields:
```kotlin
val drivePhase: Int = 0,       // 0/1/2 — drives background + particle style
val legacyLevel: Float = 0f,   // 0..1, drives visual density (from Colony count)
```

---

## Canvas Visuals

### Background

Deep space gradient — near-black to deep purple-blue. The background does not shift between phases; instead, density and motion increase.

### Particles

| Phase | Particle style |
|---|---|
| Sublight (0) | Tiny static star points, slow subtle twinkle |
| Ion (1) | Add slow-moving vessel silhouettes (small rocket shapes) crossing the field |
| Hyperdrive (2) | Faster vessels + faint horizontal hyperspace streaks across the background |

`legacyLevel` (0→1, driven by Colony count approaching a visual threshold) increases overall star density — near win, the canvas feels full and alive.

### Tap feedback

Each tap spawns a brief burst of bright teal/cyan Research sparks at the tap point — distinct from prior epoch bursts (amino acids = green, genes = blue-green, followers = warm humanoid).

---

## Architecture

`InterstellarSystem` mirrors `CivilizationSystem` exactly in structure: implements `GameSystem`, `PlayerActionHandler`, and `Restorable`.

Private state:
- `drivePhase: Int` and `driveMultiplier: BigDouble` — drive phase tracking
- `vesselDecayRate: Float` — current decay rate
- `firstResearchFired`, `firstVesselsFired`, `firstColoniesFired`, `firstLegacyFired: Boolean` — milestone event guards

`syncStateFromWorld()` restores `drivePhase`, `driveMultiplier`, and `vesselDecayRate` from purchased upgrade state. Milestone flags restored from resource amounts (> 0 = already fired).

---

## GameViewModel Changes

### Epoch load routing

```kotlin
EpochType.INTERSTELLAR -> InterstellarSystem()
```
Added to `when (saved?.snapshot?.epoch)` block in `init`.

### `dismissEpochTransition()`

```kotlin
EpochType.CIVILIZATION -> engine.advanceEpoch(InterstellarSystem())
```

### Transition message (Civilization → Interstellar)

```kotlin
EpochType.CIVILIZATION -> "The great civilization looks to the stars. The interstellar age begins."
```

### `toCosmosState()` Interstellar branch

```kotlin
EpochType.INTERSTELLAR -> {
    val colonies = resources[ResourceType.COLONIES.name] ?: BigDouble.ZERO
    val ionPurchased = upgrades.find { it.id == InterstellarSystem.KEY_UPG_ION_DRIVE }?.purchased == true
    val hyperdrivePurchased = upgrades.find { it.id == InterstellarSystem.KEY_UPG_HYPERDRIVE }?.purchased == true
    val drivePhase = when {
        hyperdrivePurchased -> 2
        ionPurchased -> 1
        else -> 0
    }
    CosmosState(
        epoch = epoch,
        drivePhase = drivePhase,
        legacyLevel = (colonies.toDouble() / InterstellarSystem.COLONY_VISUAL_THRESHOLD)
            .toFloat().coerceIn(0f, 1f)
    )
}
```

`COLONY_VISUAL_THRESHOLD = 200.0` (constant in `InterstellarSystem.companion object`).

### `toUiState()` additions

```kotlin
interstellarPhaseName = run {
    val ionPurchased = upgrades.find { it.id == InterstellarSystem.KEY_UPG_ION_DRIVE }?.purchased == true
    val hyperdrivePurchased = upgrades.find { it.id == InterstellarSystem.KEY_UPG_HYPERDRIVE }?.purchased == true
    when {
        hyperdrivePurchased -> "Hyperdrive Era"
        ionPurchased -> "Ion Age"
        else -> "Sublight Era"
    }
},
vesselDecayRate = if (epoch == EpochType.INTERSTELLAR) snapshot.vesselDecayRate else 0f,
```

### `GameSnapshot` addition

Add one backwards-compatible field:
```kotlin
val vesselDecayRate: Float = 0f,
```

`InterstellarSystem.toSnapshot()` populates this from `vesselDecayRate`. Used by `GameViewModel` to pass to `GameUiState`.

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/InterstellarSystem.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/systems/InterstellarSystemTest.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt` — add RESEARCH, VESSELS, COLONIES, LEGACY
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt` — add `vesselDecayRate: Float = 0f`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — add `interstellarPhaseName`, `vesselDecayRate`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — add `drivePhase`, `legacyLevel`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch routing, `dismissEpochTransition()`, transition message, `toCosmosState()`, `toUiState()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Interstellar background + vessel particles + hyperspace streaks + tap burst

`EpochType.kt` — no changes (`INTERSTELLAR` already declared).
`Component.kt` — no changes (no new `UpgradeEffect` types required).
