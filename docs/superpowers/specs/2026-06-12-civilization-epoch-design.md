# Civilization Epoch — Design Spec

**Date:** 2026-06-12
**Status:** Approved
**Epoch:** Civilization (4th of 5 — COSMOLOGY → BIOLOGY → EVOLUTION → **CIVILIZATION** → INTERSTELLAR)

---

## Overview

Civilization is the fourth playable epoch. Players guide humanity from scattered survivors shaped by Evolution's natural dominance through cultural and intellectual growth to a fully realized civilization. It introduces two new mechanics: an **Unrest meter** (a deterministic pressure the player actively manages) and **Era Advancement** (three progressive tiers that double production but accelerate Unrest). Together they create a risk/reward loop absent from prior epochs — advancing eras is always powerful but demands better management.

---

## Resource Chain

**Followers → Culture → Knowledge → Civilization**

| Resource | Symbol | Thematic role |
|---|---|---|
| Followers | 🧑 | People drawn to a higher purpose — tapped and passively generated |
| Culture | 🎭 | Shared identity and belief — fuel for intellectual growth |
| Knowledge | 📚 | Accumulated understanding — drives civilization itself |
| Civilization | 🏛️ | The win resource — the measure of a fully realized society |

---

## Seeding from Evolution

Every 100 Dominance the player finished Evolution with grants +10% Follower production rate on `gen_early_settlements`.

- Minimum Evolution win: 1,000 Dominance → **+100% (2× base)** Follower production at Civilization start
- Formula: `floor(dominance / 100.0) * 10%` — e.g., 1,500 Dominance = +150%
- Read from `world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)` in `CivilizationSystem.initialize()`
- Mirrors the Organisms → Genes seeding pattern from Evolution

---

## Win Condition

**1,000 Civilization** — consistent with all prior epochs.

`epochProgress = min(civilization / 1000.0, 1.0).toFloat()`

---

## Generators

Four generators. Gen 1 and Gen 3 start unlocked. Gen 2 unlocks when `upg_medieval_era` is purchased; Gen 4 unlocks when `upg_industrial_era` is purchased.

| Key | Display Name | Produces | Costs | Default |
|---|---|---|---|---|
| `gen_early_settlements` | Early Settlements | Followers | Dominance | Unlocked |
| `gen_cultural_exchange` | Cultural Exchange | Culture | Followers | **Locked** (Medieval) |
| `gen_scholars_guild` | Scholars Guild | Knowledge | Culture | Unlocked |
| `gen_enlightened_senate` | Enlightened Senate | Civilization | Knowledge | **Locked** (Industrial) |

**Initial costs/rates:**

| Key | `costAmount` | `productionRate` |
|---|---|---|
| `gen_early_settlements` | 1.0 Dominance | seeding bonus (see above) |
| `gen_cultural_exchange` | 2.0 Followers | 1.0 |
| `gen_scholars_guild` | 10.0 Culture | 1.0 |
| `gen_enlightened_senate` | 5.0 Knowledge | 1.0 |

Gen 3 starts unlocked but cannot produce until Gen 2 runs (no Culture exists yet) — the cost guard in `runGenerator()` prevents premature production naturally.

All generator output is multiplied by `eraMultiplier` (see Era Advancement).

---

## Upgrades

Six upgrades. `upg_industrial_era` is blocked until `upg_medieval_era` is purchased. `upg_public_works` and both era upgrades use no-op sentinel effects handled behaviorally in `CivilizationSystem.purchaseUpgrade()`.

| # | Key | Display Name | Effect | Cost Type | Cost |
|---|---|---|---|---|---|
| 1 | `upg_divine_calling` | Divine Calling | ×2 tap Followers | Followers | 50 |
| 2 | `upg_social_order` | Social Order | Halve Unrest accumulation rate | Culture | 30 |
| 3 | `upg_public_works` | Public Works | Repeatable: 50 Culture → −25 Unrest | Culture | 50 |
| 4 | `upg_cultural_renaissance` | Cultural Renaissance | ×2 Culture production | Knowledge | 50 |
| 5 | `upg_medieval_era` | Medieval Era | Unlock Gen 2 + ×2 all gen production | Civilization | 50 |
| 6 | `upg_industrial_era` | Industrial Era | Unlock Gen 4 + ×2 all gen production (×4 total) | Civilization | 200 |

### Upgrade effect mapping

- `upg_divine_calling` → `UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))` (existing type)
- `upg_social_order` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel — effect applied behaviorally in `tick()` by checking `purchased` flag
- `upg_public_works` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel, `repeatable = true` — effect applied in `purchaseUpgrade()` by spending Culture and subtracting 25 from `unrestLevel`
- `upg_cultural_renaissance` → `UpgradeEffect.MultiplyProduction(KEY_GEN_CULTURAL_EXCHANGE, BigDouble.of(2.0))` (existing type)
- `upg_medieval_era` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel — in `purchaseUpgrade()`: sets `eraLevel = 1`, `eraMultiplier = BigDouble.of(2.0)`, calls `world.get<GeneratorComponent>(KEY_GEN_CULTURAL_EXCHANGE)?.unlocked = true`
- `upg_industrial_era` → `UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)` sentinel — in `purchaseUpgrade()`: guarded by `eraLevel >= 1`; sets `eraLevel = 2`, `eraMultiplier = BigDouble.of(4.0)`, calls `world.get<GeneratorComponent>(KEY_GEN_ENLIGHTENED_SENATE)?.unlocked = true`; in `toSnapshot()`, `upg_industrial_era.available = false` when `eraLevel < 1` (locked until Medieval purchased — parallel to how Evolution's fork marks the unchosen path unavailable)

### `currentTapProduction()` note

Like Evolution's `currentTapProduction()`, this method checks only `upg_divine_calling` by key — not all purchased `MultiplyTapProduction` effects — because the three sentinel upgrades also use that effect type with `multiplier = 1.0`.

---

## Unrest Mechanic

`unrestLevel: Float` in `CivilizationSystem` (range 0–100), starts at 0.

### Accumulation rates

| Era | Rate (per tick) | With Social Order |
|---|---|---|
| Ancient (era 0) | +0.5 | +0.25 |
| Medieval (era 1) | +0.75 | +0.375 |
| Industrial (era 2) | +1.0 | +0.5 |

### Civil Unrest crisis

When `unrestLevel` reaches 100.0:
1. `civilUnrestActive = true`, `civilUnrestTicks = 30`
2. Reset `unrestLevel = 0.0f`
3. Fire `GameEvent(0, "Civil unrest erupts across the lands!", isMilestone = true)`
4. While `civilUnrestActive`, `gen_cultural_exchange` production is halved (×0.5 multiplier in `runGenerator()`)
5. Each tick: decrement `civilUnrestTicks`; when it hits 0, `civilUnrestActive = false`, fire `GameEvent(0, "Order restored.", isMilestone = false)`

### Public Works (repeatable mitigation)

In `purchaseUpgrade()`, `KEY_UPG_PUBLIC_WORKS` handler:
- Consumes 50 Culture from world
- Reduces `unrestLevel` by 25 (floor at 0)
- Can be used multiple times; does not need `purchased = true` gate (repeatable flag handles UI availability)

### Design tension

Advancing eras doubles production but increases unrest rate. The player must choose when to advance — advancing during an active crisis is valid but risky. Social Order (passive) and Public Works (active) are complementary, not competing.

---

## Era Advancement

Tracked in `CivilizationSystem`:

```kotlin
private var eraLevel: Int = 0       // 0=Ancient, 1=Medieval, 2=Industrial
private var eraMultiplier: BigDouble = BigDouble.ONE  // 1×, 2×, or 4×
```

`eraMultiplier` is applied in `runGenerator()` — all generators multiply their output by it. This mirrors how `globalMultiplier` works in `CosmologySystem`.

**Restore (`syncStateFromWorld()`):**
```kotlin
val medievalPurchased = world.get<UpgradeComponent>(KEY_UPG_MEDIEVAL_ERA)?.purchased == true
val industrialPurchased = world.get<UpgradeComponent>(KEY_UPG_INDUSTRIAL_ERA)?.purchased == true
eraLevel = when {
    industrialPurchased -> 2
    medievalPurchased -> 1
    else -> 0
}
eraMultiplier = when (eraLevel) {
    2 -> BigDouble.of(4.0)
    1 -> BigDouble.of(2.0)
    else -> BigDouble.ONE
}
```

No new `GameSnapshot` field needed for era — fully derivable from purchased upgrade state.

---

## Data Model Changes

### Modified: `ResourceType.kt`

Add four entries (after DOMINANCE):
```kotlin
FOLLOWERS("Followers", "🧑"),
CULTURE("Culture", "🎭"),
KNOWLEDGE("Knowledge", "📚"),
CIVILIZATION("Civilization", "🏛️"),
```

### Modified: `GameSnapshot.kt`

Add two backwards-compatible fields with defaults (parallel to `activeEvent`/`eventTicksRemaining` for Evolution):
```kotlin
val unrestLevel: Float = 0f,
val civilUnrestActive: Boolean = false,
```

`CivilizationSystem.toSnapshot()` must populate both fields. `unrestLevel` is the raw meter value (0–100, already reset to 0 when crisis is active). `civilUnrestActive` indicates an ongoing crisis.

### Modified: `GameUiState.kt`

Add two fields:
```kotlin
val unrestLevel: Float = 0f,
val civilizationEraName: String = "",
```

### Modified: `CosmosState.kt`

Add three fields:
```kotlin
val civEraLevel: Int = 0,              // 0/1/2 — drives background + particle style
val civilizationLevel: Float = 0f,    // 0..1, drives city density (from Knowledge)
val civilUnrestActive: Boolean = false // drives crisis overlay
```

---

## Canvas Visuals

### Background

Transitions across three palette bands driven by `civEraLevel`:

| Era | Background |
|---|---|
| Ancient (0) | Warm amber/ochre — open savanna, earthen tones |
| Medieval (1) | Stone gray with amber accents — walled towns |
| Industrial (2) | Dark charcoal with orange glow — factory smoke and firelight |

Transition between bands is a lerp driven by `civilizationLevel` (0→1 across the current era's resource threshold).

### Particles

| Era | Particle style |
|---|---|
| Ancient | Small warm dots, slow drift (campfire sparks) |
| Medieval | Larger clustered shapes (building silhouettes), medium drift |
| Industrial | Dense rectangular grid (factories), faster movement |

`civilizationLevel` drives density — near win condition the screen feels dense and structured.

### Civil Unrest overlay

When `civilUnrestActive = true`: red-orange wash over the canvas; particles scatter and speed up (rioting feel). Overlay fades out over the 30-tick crisis duration.

### Tap feedback

Each tap spawns a small burst of humanoid sparks at the tap point — symbolizing Followers gathering to a higher call. Consistent with prior epoch tap bursts (amino acid burst, gene burst).

---

## Architecture

`CivilizationSystem` mirrors `EvolutionSystem` exactly in structure: implements `GameSystem`, `PlayerActionHandler`, and `Restorable`.

Private state:
- `eraLevel: Int` and `eraMultiplier: BigDouble` — era tracking
- `unrestLevel: Float` — current unrest
- `civilUnrestActive: Boolean` and `civilUnrestTicks: Int` — active crisis
- `firstCivilizationFired: Boolean` — milestone event guard

`syncStateFromWorld()` restores `eraLevel`/`eraMultiplier` from purchased upgrade state (see Era Advancement section). `unrestLevel` restores from `GameSnapshot.unrestLevel`. `civilUnrestActive` resets to false on restore (like Evolution's event reset).

---

## GameViewModel Changes

### Epoch load routing

```kotlin
EpochType.CIVILIZATION -> CivilizationSystem()
```
Added to `when (saved?.snapshot?.epoch)` block in `init`.

### `dismissEpochTransition()`

```kotlin
EpochType.EVOLUTION -> engine.advanceEpoch(CivilizationSystem())
```

### Transition message

```kotlin
EpochType.EVOLUTION -> "Survivors of a million years rise from the wilderness. Civilization begins."
```

### `toCosmosState()` Civilization branch

```kotlin
EpochType.CIVILIZATION -> {
    val knowledge = resources[ResourceType.KNOWLEDGE.name] ?: BigDouble.ZERO
    val medievalPurchased = upgrades.find { it.id == CivilizationSystem.KEY_UPG_MEDIEVAL_ERA }?.purchased == true
    val industrialPurchased = upgrades.find { it.id == CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA }?.purchased == true
    val eraLevel = when {
        industrialPurchased -> 2
        medievalPurchased -> 1
        else -> 0
    }
    CosmosState(
        epoch = epoch,
        civEraLevel = eraLevel,
        civilizationLevel = (knowledge.toDouble() / CivilizationSystem.KNOWLEDGE_VISUAL_THRESHOLD)
            .toFloat().coerceIn(0f, 1f),
        civilUnrestActive = civilUnrestActive
    )
}
```

`KNOWLEDGE_VISUAL_THRESHOLD = 200.0` (constant in `CivilizationSystem.companion object`).

### `toUiState()` additions

```kotlin
unrestLevel = unrestLevel,   // from snapshot.unrestLevel
civilizationEraName = run {
    val medievalPurchased = upgrades.find { it.id == CivilizationSystem.KEY_UPG_MEDIEVAL_ERA }?.purchased == true
    val industrialPurchased = upgrades.find { it.id == CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA }?.purchased == true
    when {
        industrialPurchased -> "Industrial Era"
        medievalPurchased -> "Medieval Era"
        else -> "Ancient Era"
    }
},
```

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CivilizationSystem.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/systems/CivilizationSystemTest.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt` — add FOLLOWERS, CULTURE, KNOWLEDGE, CIVILIZATION
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt` — add `unrestLevel: Float = 0f`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — add `unrestLevel`, `civilizationEraName`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — add `civEraLevel`, `civilizationLevel`, `civilUnrestActive`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch routing, `dismissEpochTransition()`, `toCosmosState()`, `toUiState()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Civilization background + city particles + crisis overlay + tap burst

`EpochType.kt` — no changes (`CIVILIZATION` already declared).
`Component.kt` — no changes (no new `UpgradeEffect` types required).
