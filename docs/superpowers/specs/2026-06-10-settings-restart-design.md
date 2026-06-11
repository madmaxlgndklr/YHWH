# Settings Menu & New Game+ Restart — Design Spec

**Date:** 2026-06-10
**Status:** Approved

---

## Overview

Adds a dedicated Settings navigation entry point inside the Stats tab, a Restart Game option with a computed seed bonus, and a persistent restart counter displayed in Stats. The seed bonus carries epoch progress and lifetime resource totals into the next run, compounding across multiple restarts.

---

## Scope

- `MetaSave` data class + `MetaSaveManager` (new)
- `SeedBonus` data class (new)
- `CosmologySystem` — apply seed multiplier and starting resources
- `GameEngine.initNewGame()` — accept optional `SeedBonus`
- `GameViewModel` — load meta, `restartGame()`, `computedSeedBonus()`
- `GameUiState` — add `restartCount`, `activeSeedMultiplier`
- `SettingsScreen` — add Restart Game row + confirmation dialog
- `ActionPanel` / `StatsTab` — add Settings row, restart counter
- `GameScreen` / `AppNavigation` — thread `onNavigateToSettings` into ActionPanel

No changes to auth flow, ProfileScreen, or epoch systems beyond CosmologySystem.

---

## Seed Bonus Formula

The bonus is computed at the moment of restart from two components.

### Epoch multiplier

Based on the current epoch at restart time (each epoch represents one completed prior epoch):

| Epoch at restart | Cumulative global multiplier |
|---|---|
| COSMOLOGY | ×1.00 (no completed epochs) |
| BIOLOGY | ×1.15 |
| EVOLUTION | ×1.35 |
| CIVILIZATION | ×1.65 |
| INTERSTELLAR | ×2.00 |

### Resource top-up

From `GameSnapshot.lifetimeTotals` at restart time:

| Lifetime total | Starting bonus |
|---|---|
| Every 1,000 Energy | +10 starting Energy |
| Every 1,000 Matter | +5 starting Matter |

Caps: 500 starting Energy, 250 starting Matter.

Formula:
```
startingEnergy = min(floor(lifetimeEnergy / 1000.0) * 10.0, 500.0)
startingMatter = min(floor(lifetimeMatter / 1000.0) * 5.0, 250.0)
```

### Stacking across restarts

Each restart adds its newly computed bonus on top of the existing `MetaSave.seedBonus`:
```
newMultiplier = existingMultiplier + (epochMultiplier - 1.0f)
newStartingEnergy = min(existing + computed, 500.0)
newStartingMatter = min(existing + computed, 250.0)
```

The multiplier floor is always 1.0 (never below baseline).

---

## Data Model

### New: `SeedBonus`

```kotlin
@Serializable
data class SeedBonus(
    val globalMultiplier: Float = 1.0f,
    val startingEnergy: Double = 0.0,
    val startingMatter: Double = 0.0
)
```

### New: `MetaSave`

```kotlin
@Serializable
data class MetaSave(
    val restartCount: Int = 0,
    val seedBonus: SeedBonus = SeedBonus()
)
```

Stored at `yhwh_meta.json` in `application.filesDir`, alongside `yhwh_save.json`.

### New: `MetaSaveManager`

Lightweight class mirroring `SaveManager`. Two methods: `load(): MetaSave` (returns default if file absent), `save(meta: MetaSave)`. Uses `kotlinx.serialization` JSON — same pattern as `SaveManager`.

### Modified: `GameUiState`

Two new fields with defaults:
```kotlin
val restartCount: Int = 0,
val activeSeedMultiplier: Float = 1.0f
```

---

## CosmologySystem Changes

`initialize(world: World)` gains an optional `seedBonus: SeedBonus?` parameter (default `null`).

When `seedBonus != null`:
- Store `globalMultiplier` as a private field
- Write `startingEnergy` and `startingMatter` directly to the Energy and Matter `ResourceComponent` amounts after creating them

The `globalMultiplier` is applied in `tick()` to all production and tap output by multiplying the result:
```kotlin
prodAmount = baseAmount * BigDouble.of(globalMultiplier.toDouble())
```

The multiplier also applies to `onTap()` — `currentTapProduction()` is multiplied by `globalMultiplier`.

---

## GameEngine Changes

```kotlin
fun initNewGame(seedBonus: SeedBonus? = null) {
    systems.forEach { it.initialize(world, seedBonus) }
    emitSnapshot()
}
```

The `GameSystem` interface `initialize(world: World)` is unchanged. `GameEngine` passes the bonus by setting a property on `CosmologySystem` before the generic initialize loop runs:

```kotlin
fun initNewGame(seedBonus: SeedBonus? = null) {
    val cosmo = systems.filterIsInstance<CosmologySystem>().firstOrNull()
    cosmo?.seedBonus = seedBonus   // set before initialize
    systems.forEach { it.initialize(world) }
    emitSnapshot()
}
```

`CosmologySystem` gains a `var seedBonus: SeedBonus? = null` property read during `initialize()`.

---

## GameViewModel Changes

### Init

```kotlin
private val metaFile = File(application.filesDir, "yhwh_meta.json")
private val metaSaveManager = MetaSaveManager(metaFile)
private var meta = metaSaveManager.load()
```

`initNewGame()` call passes `meta.seedBonus` when applicable.

### `computedSeedBonus(): SeedBonus`

Pure function. Reads current snapshot + existing `meta.seedBonus`, returns the `SeedBonus` that would result from a restart now. Called by the Settings confirmation dialog (preview) and by `restartGame()`.

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

### `restartGame()`

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
    // Re-register fresh CosmologySystem as the active system
    engine.resetAndRegister(CosmologySystem())
    engine.initNewGame(newBonus)
    engine.start()
}
```

Note: `GameEngine.registerSystem()` needs to support re-registration (set `activeSystem` to the new system). Currently it appends — this needs a `resetAndRegister` or clearing the systems list first.

### `toUiState()` update

Populate new fields:
```kotlin
restartCount = meta.restartCount,
activeSeedMultiplier = meta.seedBonus.globalMultiplier
```

---

## Settings Screen Changes

New **Restart Game** row added below the Tutorial toggle in `SettingsScreen.kt`.

Row displays:
- Title: "Restart Game"
- Subtitle: computed preview from `viewModel.computedSeedBonus()` — e.g., "+35% production · 50 Energy · 25 Matter"
- Tapping opens a `AlertDialog` confirmation

Confirmation dialog:
- Title: "Start a new game?"
- Body: "Your progress earns: [bonus preview]. This cannot be undone."
- Buttons: Cancel | Restart
- On Restart: `viewModel.restartGame()` + `onNavigateToGame()` (new callback, pops back to game)

`SettingsScreen` gains an `onNavigateToGame: () -> Unit` callback parameter.

---

## Stats Tab Changes

Two additions to `StatsTab` in `ActionPanel.kt`:

### Settings row (top of tab)

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onNavigateToSettings)
        .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Settings", fontSize = 15.sp)
    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
}
HorizontalDivider()
```

### Restart counter (below lifetime resources)

Only shown when `state.restartCount > 0`:
```kotlin
if (state.restartCount > 0) {
    Text(
        "Restarts: ${state.restartCount}  " +
        "(×${"%.2f".format(state.activeSeedMultiplier)} production seed)",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

`StatsTab` gains an `onNavigateToSettings: () -> Unit` parameter, threaded from `ActionPanel` → `GameScreen`.

---

## Navigation Changes

`AppNavigation.kt` — `SettingsScreen` composable call gains `onNavigateToGame = { navController.popBackStack() }`.

`GameScreen.kt` — `ActionPanel` call gains `onNavigateToSettings = onNavigateToSettings` (already passed in as a parameter but not yet forwarded to ActionPanel).

`ActionPanel` composable signature gains `onNavigateToSettings: () -> Unit`, forwarded to `StatsTab`.

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSave.kt` — `MetaSave`, `SeedBonus` data classes
- `app/src/main/java/com/madmaxlgndklr/yhwh/persistence/MetaSaveManager.kt` — read/write `yhwh_meta.json`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt` — `seedBonus` property, apply in `initialize()` and `tick()`/`onTap()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt` — `initNewGame(seedBonus)`, `resetAndRegister(system)`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — `MetaSaveManager`, `computedSeedBonus()`, `restartGame()`, `toUiState()` update
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — `restartCount`, `activeSeedMultiplier`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt` — Restart Game row + dialog, `onNavigateToGame` param
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/ActionPanel.kt` — `onNavigateToSettings` param, thread to StatsTab
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt` — `onNavigateToGame` for SettingsScreen
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt` — thread `onNavigateToSettings` into ActionPanel
