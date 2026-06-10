# Evolution Epoch — Design Spec

**Date:** 2026-06-10
**Status:** Approved
**Epoch:** Evolution (3rd of 5 — COSMOLOGY → BIOLOGY → **EVOLUTION** → CIVILIZATION → INTERSTELLAR)

---

## Overview

Evolution is the third playable epoch. Players guide organisms through natural selection, genetic mutation, and ecosystem competition to achieve total ecosystem control. It introduces three new mechanics not present in prior epochs: extinction pressure (passive decay), environmental events (periodic debuffs), and a permanent upgrade fork (mutually exclusive adaptation paths).

---

## Resource Chain

**Genes → Mutations → Species → Dominance**

| Resource | Symbol | Thematic role |
|---|---|---|
| Genes | 🧬 | Raw genetic material — tapped and passively generated |
| Mutations | 🔀 | Genetic variation — fuel for speciation |
| Species | 🦎 | Distinct life forms — the core currency, also decays |
| Dominance | 👑 | Ecosystem control — the win resource |

---

## Seeding from Biology

Every 100 Organisms the player finished Biology with grants +10% Gene production rate.

- Minimum Biology win: 1,000 Organisms → **+100% (2× base)** Gene production at Evolution start
- Formula: `floor(organisms / 100) * 10%` — e.g., 1,500 Organisms = +150%
- Read from `world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)` in `EvolutionSystem.initialize()`
- Mirrors the Biology seeding pattern (Planets → +10%/planet on Prebiotic Soup)

---

## Win Condition

**1,000 Dominance** — consistent with Biology's 1,000 Organisms target.

`epochProgress = min(dominance / 1000.0, 1.0).toFloat()`

---

## Generators

| Key | Display Name | Produces | Costs | Default |
|---|---|---|---|---|
| `gen_primordial_gene_pool` | Primordial Gene Pool | Genes | Organisms | Unlocked |
| `gen_mutation_engine` | Mutation Engine | Mutations | Genes | Locked |
| `gen_natural_selection_chamber` | Natural Selection Chamber | Species | Mutations | Unlocked |
| `gen_ecosystem_architect` | Ecosystem Architect | Dominance | Species | Locked |

The first generator costs Organisms — the Biology resource still in the world after epoch transition — grounding the handoff thematically. The two locked generators are unlocked via upgrades.

---

## Upgrades

Six upgrades total. Upgrades 4A and 4B are a permanent fork — buying either one locks the other for the rest of the run.

| # | Key | Display Name | Effect | Cost Type | Cost Amount | Fork |
|---|---|---|---|---|---|---|
| 1 | `upg_genetic_drift` | Genetic Drift | ×2 tap Gene production | Genes | 50 | — |
| 2 | `upg_rna_replication` | RNA Replication | Unlock Mutation Engine | Genes | 30 | — |
| 3 | `upg_niche_colonization` | Niche Colonization | Unlock Ecosystem Architect | Species | 30 | — |
| 4A | `upg_adaptive_immunity` | Adaptive Immunity | Halve Species decay rate + halve event duration | Mutations | 50 | **Locks 4B** |
| 4B | `upg_hypermutation` | Hypermutation | ×2 Mutation Engine production | Genes | 50 | **Locks 4A** |
| 5 | `upg_apex_dominance` | Apex Dominance | Repeatable: 100 Species → 1 Dominance (starting cost) | Species | 100 | — |

### Fork mechanic

The fork is enforced in `EvolutionSystem.purchaseUpgrade()`: buying 4A or 4B immediately sets `forked = true` and records which path was chosen. In `toSnapshot()`, the unchosen fork upgrade has `available = false` and its `purchased` flag remains false permanently. The UI already handles `available = false` by graying out an upgrade card — no new UI component needed.

### Upgrade effects mapping

- `upg_genetic_drift` → `UpgradeEffect.MultiplyTapProduction(2.0)` (existing type)
- `upg_rna_replication` → `UpgradeEffect.UnlockGenerator("gen_mutation_engine")` (existing type)
- `upg_niche_colonization` → `UpgradeEffect.UnlockGenerator("gen_ecosystem_architect")` (existing type)
- `upg_adaptive_immunity` → handled entirely in `EvolutionSystem.tick()` by checking `purchased` flag; no new UpgradeEffect type needed
- `upg_hypermutation` → `UpgradeEffect.MultiplyProduction("gen_mutation_engine", 2.0)` (existing type)
- `upg_apex_dominance` → `UpgradeEffect.ManualConversion(Species, cost, Dominance, 1.0)` repeatable (existing type)

---

## Extinction Pressure

Species decay passively at **-0.5 Species/tick**. This is permanent and unavoidable — the player must generate Species faster than they decay.

- Applied at the start of each `tick()` before generator runs
- Species floor: 0 (never goes negative)
- Buying Adaptive Immunity (`upg_adaptive_immunity`) halves the rate to **-0.25 Species/tick**

---

## Environmental Events

An event fires every **60 ticks**, chosen randomly from three types. Only one event can be active at a time. No event fires in the first **30 ticks** after epoch start (grace period). Events are tracked in `EvolutionSystem` private state and surfaced via `GameSnapshot`.

| Event | Enum value | Debuff | Duration |
|---|---|---|---|
| Ice Age | `ICE_AGE` | Primordial Gene Pool production -50% | 30 ticks |
| Asteroid Impact | `ASTEROID_IMPACT` | Mutation Engine production -75% | 20 ticks |
| Volcanic Winter | `VOLCANIC_WINTER` | Natural Selection Chamber production -50% | 45 ticks |

If Adaptive Immunity is purchased, all event durations are halved (15 / 10 / 22 ticks respectively).

Events are announced in the event log (`GameEvent` with `isMilestone = false`) when they begin and when they end. The UI displays an active event indicator (see GameUiState below).

---

## Data Model Changes

### New: `EvolutionEvent` enum (`engine/EvolutionEvent.kt`)

```kotlin
@Serializable
enum class EvolutionEvent(val displayName: String) {
    ICE_AGE("Ice Age"),
    ASTEROID_IMPACT("Asteroid Impact"),
    VOLCANIC_WINTER("Volcanic Winter")
}
```

### Modified: `GameSnapshot`

Add two nullable fields with defaults (backwards-compatible with existing saves):

```kotlin
val activeEvent: EvolutionEvent? = null,
val eventTicksRemaining: Int = 0
```

### Modified: `GameUiState`

```kotlin
val activeEvent: EvolutionEvent? = null,
val eventTicksRemaining: Int = 0
```

### Modified: `CosmosState`

```kotlin
val mutationLevel: Float = 0f,   // 0..1, drives background color lerp
val speciesLevel: Float = 0f,    // 0..1, drives particle diversification
val activeEvent: EvolutionEvent? = null  // drives event overlay
```

---

## Canvas Visuals

### Background

Lerps from Biology's ocean blue → warm green → lush green, driven by `mutationLevel`.

- `mutationLevel = 0`: deep ocean blue (Biology handoff feel)
- `mutationLevel = 0.5`: earthy green-brown (primordial land)
- `mutationLevel = 1.0`: rich lush green (thriving biosphere)

### Particles

Organism silhouettes — simple convex shapes (ovals, elongated forms).

- Below `speciesLevel` threshold (~0.3): small uniform particles, slow drift
- Above threshold: larger and more varied shapes appear, mixed speeds
- Near win condition: screen feels dense and alive

### Event overlay

Rendered as a semi-transparent veil over the canvas when `activeEvent != null`:

| Event | Overlay |
|---|---|
| `ICE_AGE` | Cool blue wash; particles slow to 50% speed |
| `ASTEROID_IMPACT` | Brief red flash on trigger tick, then dim haze |
| `VOLCANIC_WINTER` | Gray-brown fog; particles dim to 50% opacity |

### Tap feedback

Each tap spawns a small burst of gene particles (small bright dots) at the tap point — consistent with Biology's amino acid tap burst pattern.

---

## Architecture

`EvolutionSystem` mirrors `BiologySystem` exactly in structure: implements `GameSystem`, `PlayerActionHandler`, and `Restorable`. Private state tracks:

- `forked: Boolean` and `chosenPath: String?` — fork tracking
- `activeEvent: EvolutionEvent?` and `eventTicksRemaining: Int` — event tracking
- `eventCooldown: Int` — counts ticks since last event (fires at 60)
- `firstDominanceFired: Boolean` — milestone event guard

`syncStateFromWorld()` restores `forked`/`chosenPath` by reading the purchased state of `upg_adaptive_immunity` and `upg_hypermutation` on restore.

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/EvolutionEvent.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystem.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/systems/EvolutionSystemTest.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameSnapshot.kt` — add `activeEvent`, `eventTicksRemaining`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — add `activeEvent`, `eventTicksRemaining`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — add `mutationLevel`, `speciesLevel`, `activeEvent`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch-aware init + `toCosmosState()` Evolution branch + `toGameUiState()` event fields
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Evolution background + organism particles + event overlay

`EpochType.kt` — no changes needed (`EVOLUTION` already declared).
`Component.kt` — no changes needed (no new `UpgradeEffect` types required).
