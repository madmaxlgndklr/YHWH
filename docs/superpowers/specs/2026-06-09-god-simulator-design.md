# YHWH — God Simulator: Phase 1 Design Spec

**Date:** 2026-06-09
**Scope:** Phase 1 — bedrock architecture + Cosmology epoch (playable prototype)
**Version:** 1.0

---

## 1. Project Overview

YHWH is a continuous, incremental "God Simulator" for Android. The player guides reality from the Big Bang through five distinct epochs: Cosmology, Biology, Evolution, Civilization, and Interstellar. Phase 1 delivers the bedrock architecture and a fully playable Cosmology epoch, establishing every pattern that future epochs will extend.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + ECS simulation engine |
| Concurrency | Kotlin Coroutines & Flows |
| Persistence | `kotlinx.serialization` → JSON file |
| Numbers | Custom `BigDouble` (mantissa + exponent as `Double` pair) |
| Navigation | Compose Navigation |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 |
| Package name | `com.madmaxlgndklr.yhwh` |

---

## 3. Architecture

### Layer Map

```
┌─────────────────────────────────────────┐
│           Compose UI Layer              │
│  CosmosCanvas  TopBar  ActionPanel      │
│         ↑ observes StateFlow            │
├─────────────────────────────────────────┤
│           GameViewModel                 │
│  Exposes: UiState, CosmosState flows    │
│  Handles: user actions → engine calls   │
│         ↑ observes GameSnapshot         │
├─────────────────────────────────────────┤
│           GameEngine                    │
│  Owns: ECS World, tick coroutine        │
│  Emits: GameSnapshot (StateFlow)        │
│  Calls: EpochSystem.tick() each frame   │
├─────────────────────────────────────────┤
│        ECS World / Systems              │
│  Entities: generators, upgrades         │
│  Systems: CosmologySystem, (future...)  │
│  Components: ResourceComponent,         │
│              GeneratorComponent, etc.   │
├─────────────────────────────────────────┤
│        Persistence Layer                │
│  SaveManager: JSON ↔ GameSnapshot       │
│  Timestamps offline delta on restore    │
└─────────────────────────────────────────┘
```

### Key Contracts

- `GameEngine` is the **only** class that mutates ECS state. Nothing outside it writes to the World.
- `GameSnapshot` is an **immutable** data class emitted after every tick. The ViewModel never reaches into the ECS directly.
- `CosmosState` is a **separate lightweight flow** derived from `GameSnapshot`, feeding only the canvas. Decoupled from gameplay UI state so the two can evolve independently, and a future live-entity canvas view can be swapped in without touching the canvas interface.

---

## 4. Core Engine

### Tick Loop

`GameEngine` starts a `CoroutineScope(Dispatchers.Default)` and launches the tick loop on `start()` at 1 TPS using `delay(1000L)`:

```
while (active):
    val delta = calculateDelta()       // 1 on normal tick, N on offline restore
    systems.forEach { it.tick(world, resources, delta) }
    _snapshot.emit(world.toSnapshot())
    saveManager.saveIfDue()            // every 30 ticks + always on pause
```

All systems must be `delta`-aware — production values are multiplied by `delta`.

### Offline Progress

`SaveManager` writes `lastTickTimestamp: Long` on every save. On cold start:

1. Deserialize save file
2. Compute `missedTicks = (now - lastTimestamp) / tickIntervalMs`
3. Cap at configurable max (default: 8 hours = 28,800 ticks)
4. Call `GameEngine.restore(snapshot, missedTicks)` — runs one batch tick with `delta = missedTicks`

### ECS Structure

| Concept | Implementation |
|---|---|
| `World` | `Map<EntityId, MutableMap<ComponentType, Component>>` |
| `Entity` | `typealias EntityId = Long` |
| `Component` | Sealed interface; implementations are data classes |
| `System` | Interface: `fun tick(world: World, delta: Long)` |

**Component types (Phase 1):**
- `ResourceComponent` — holds a `BigDouble` amount for a `ResourceType`
- `GeneratorComponent` — production rate, cost, unlocked flag
- `UpgradeComponent` — cost, effect, purchased flag
- `EpochComponent` — current epoch, progress float

**System registration** (open/closed principle — add epochs without touching core loop):
```kotlin
engine.registerSystem(CosmologySystem())
// Future: engine.registerSystem(BiologySystem())
```

---

## 5. Data Layer

### BigDouble

A `value class` in `engine/math/BigDouble.kt` with two `Double` fields: `mantissa` (normalized to 1.0–9.999…) and `exponent`. Zero Android dependencies — fully unit-testable.

Required operations: `+`, `-`, `*`, `/`, `compareTo`, `toDisplayString()` (e.g. `"1.23e45"` or suffix form `"1.23 Qd"`).

### GameSnapshot

```kotlin
data class GameSnapshot(
    val tick: Long,
    val epoch: EpochType,
    val resources: Map<ResourceType, BigDouble>,
    val generators: List<GeneratorSnapshot>,
    val upgrades: List<UpgradeSnapshot>,
    val epochProgress: Float,       // 0f–1f
    val events: List<GameEvent>     // new events this tick
)
```

### CosmosState

Minimal data class derived from `GameSnapshot` in the ViewModel. Contains only what the canvas needs:

```kotlin
data class CosmosState(
    val epoch: EpochType,
    val matterLevel: Float,         // normalized 0f–1f via min(matter / MATTER_VISUAL_THRESHOLD, 1f)
    val starLevel: Float,           // normalized 0f–1f via min(stars / STAR_VISUAL_THRESHOLD, 1f)
    val starsFormed: Boolean,
    val planetsFormed: Boolean
)
```
Visual thresholds (`MATTER_VISUAL_THRESHOLD`, `STAR_VISUAL_THRESHOLD`) are constants defined in `CosmologySystem` and passed into the snapshot mapping — not hardcoded in the ViewModel.

### SaveManager

- Serializes `GameSnapshot` + `lastTickTimestamp: Long` to a single JSON file in `Context.filesDir`
- Uses `kotlinx.serialization`
- Saves every 30 ticks and always on `onPause`
- Reads `version: Int` field for future migration support

---

## 6. UI Architecture

### Navigation

Single `Activity` → `NavHost` with two destinations: `GameScreen`, `SettingsScreen`. Epoch changes recompose `GameScreen` reactively — no destination change.

### GameViewModel

```kotlin
val uiState: StateFlow<GameUiState>     // TopBar + ActionPanel
val cosmosState: StateFlow<CosmosState> // Canvas only
```

User actions (`onGeneratorTap`, `onUpgradePurchase`) are ViewModel methods. UI never calls `GameEngine` directly.

### Screen Layout

```
┌──────────────────────────────┐
│  TopBar                      │  Epoch · Tick · Energy · Matter
├──────────────────────────────┤
│  CosmosCanvas (weight 1f)    │  Animated atmospheric backdrop
├──────────────────────────────┤
│  TabRow: Actions|Upgrades|Stats │
│  (content area below tabs)   │
└──────────────────────────────┘
```

### CosmosCanvas

Compose `Canvas` composable driven by `CosmosState` only. No custom render loop — uses `infiniteTransition` and `animateFloatAsState`.

**Cosmology layers (toggled by `CosmosState` milestone flags):**
1. Deep space star-field (always visible)
2. Matter particle clusters (density scales with `matterLevel`)
3. Warm stellar glow (intensity scales with `starLevel`)
4. Slow orbital ring (visible when `starsFormed == true`)
5. Blue-green fade + ocean particles (transition animation when `planetsFormed == true`, epoch shifts to BIOLOGY)

> **Future iteration:** a live entity-view canvas (1:1 rendering of ECS entities as particles) is architecturally supported via `CosmosState` — swap the data source without touching the composable interface.

### Bottom Tabs

| Tab | Content |
|---|---|
| **Actions** | Generator cards; Quantum Fluctuation tap button; auto-generator slots |
| **Upgrades** | Scrollable upgrade list with cost, effect, purchased state |
| **Stats** | Lifetime totals, epoch progress bar, offline earnings summary on resume |

---

## 7. Cosmology Epoch

### Resources

| Resource | Symbol | Source |
|---|---|---|
| Energy | ⚡ | Passive, generated each tick |
| Matter | ⬡ | Quantum Fluctuation taps + Nebula Condenser |
| Hydrogen | H | Hydrogen Fusion generator |
| Stars | ★ | Stellar Nursery generator |
| Accretion Disks | ◎ | Accretion Engine generator |
| Planets | ♁ | Gravitational Collapse upgrade (manual trigger) |

### Generator Chain

```
[Tap] Quantum Fluctuation  →  +Matter             (manual tap)
      Nebula Condenser      →  +Matter/tick        (costs Energy)
      Hydrogen Fusion       →  +Hydrogen/tick      (costs Matter)   [unlocked by upgrade]
      Stellar Nursery       →  +Stars/tick         (costs Hydrogen)
      Accretion Engine      →  +Disks/tick         (costs Stars)
```

### Upgrades

| # | Name | Effect | Cost |
|---|---|---|---|
| 1 | Particle Density | ×2 Matter per tap | Energy |
| 2 | Nuclear Ignition | Unlocks Hydrogen Fusion generator | Matter |
| 3 | Stellar Compression | ×2 Stars/tick | Hydrogen |
| 4 | Protoplanetary Disk | Unlocks Accretion Engine | Stars |
| 5 | Gravitational Collapse | 100 Disks → 1 Planet (repeatable, manual) | 100 Accretion Disks (consumed) |
| 6 | Tectonic Stabilization | −50% Planet formation cost | Stars |

### Win Condition / Epoch Transition

- `epochProgress` reaches `1.0` when `planets >= 1`
- `GameEngine` sets `epoch = BIOLOGY`, serializes, emits snapshot
- Full-screen overlay: *"A world has formed. Life stirs in the primordial ocean."*
- Canvas fades to blue-green palette with ocean particle effect
- Biology tab content visible but stubbed: *"Coming Soon — Epoch 2: Biology"*

### CosmologySystem Responsibilities

Each tick:
1. Apply generator production × delta to resource totals
2. Check upgrade purchase conditions (affordability gates)
3. Check win condition (`planets >= 1`)
4. Append `GameEvent` entries for milestones (first star, first planet, etc.)

---

## 8. File Structure (Phase 1)

```
app/
  src/main/
    java/com/madmaxlgndklr/yhwh/
      MainActivity.kt
      navigation/
        AppNavigation.kt
      engine/
        math/
          BigDouble.kt
        GameEngine.kt
        World.kt
        Entity.kt
        Component.kt          // sealed interface + all implementations
        System.kt             // interface
        GameSnapshot.kt
        EpochType.kt
        ResourceType.kt
      systems/
        CosmologySystem.kt
      persistence/
        SaveManager.kt
        SaveData.kt           // wrapper: GameSnapshot + lastTickTimestamp
      ui/
        GameViewModel.kt
        screen/
          GameScreen.kt
          SettingsScreen.kt
        components/
          TopBar.kt
          CosmosCanvas.kt
          ActionPanel.kt      // tabs + content
        state/
          GameUiState.kt
          CosmosState.kt
  build.gradle.kts
build.gradle.kts
settings.gradle.kts
```

---

## 9. Out of Scope (Phase 1)

- Epochs 2–5 (Biology, Evolution, Civilization, Interstellar)
- Cloud save / Google Play Games integration
- Monetization, ads, IAP
- Sound / music
- Achievements
- Live entity-view canvas (noted for future iteration)
- Prestige / reset mechanics
