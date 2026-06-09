# Canvas Tap + Tutorial Coach Marks — Design Spec

**Date:** 2026-06-09
**Scope:** Easier currency generation via canvas tap + 3-step first-launch tutorial with persistent re-enable
**Version:** 1.0

---

## 1. Problem Statement

The Quantum Fluctuation tap button is buried inside the Actions tab. New players have no indication that tapping generates Matter, nor any explanation of the passive Energy tick or the generator chain. This feature makes generation more accessible and teaches all three mechanics on first launch.

---

## 2. Changes Overview

| Area | Change |
|---|---|
| `CosmosCanvas` | Becomes tappable — each tap triggers Quantum Fluctuation + visual burst |
| `TutorialOverlay` | New composable — 3-step coach-mark overlay shown on first launch |
| `GameUiState` | Add `tutorialStep: Int` |
| `GameViewModel` | Add tutorial prefs read/write + `onTutorialNext()`, `onTutorialReset()` |
| `GameScreen` | Wire canvas tap + render tutorial overlay |
| `SettingsScreen` | Add "Show tutorial on next launch" toggle |

---

## 3. Canvas Tap

### Interface change

`CosmosCanvas` gains an optional tap callback:

```kotlin
@Composable
fun CosmosCanvas(
    state: CosmosState,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

### Implementation

The existing `Canvas` is wrapped in a `Box`. When `onTap != null`, a `pointerInput` modifier detects taps:

```kotlin
Box(
    modifier = modifier
        .then(if (onTap != null) Modifier.pointerInput(Unit) {
            detectTapGestures { offset -> onTap(); addBurst(offset) }
        } else Modifier)
) {
    Canvas(...) { /* existing draw code */ }
}
```

### Tap burst animation

Each tap spawns a `TapBurst(x: Float, y: Float, createdAt: Long)` added to a `remember { mutableStateListOf<TapBurst>() }`. A `LaunchedEffect(burst.createdAt)` per burst runs a 400ms animation (scale 0→1.5, alpha 1→0) drawing 6 circles radiating outward from the tap point, then removes the burst from the list. All burst logic lives inside `CosmosCanvas` — nothing leaks to the ViewModel.

### Fallback

The Quantum Fluctuation button in `ActionsTab` remains unchanged. Both entry points call the same `onQuantumFluctuationTap()` on the ViewModel.

---

## 4. Tutorial Coach Marks

### Steps

| Step | Target area | Message |
|---|---|---|
| 1 | TopBar Energy chip | "⚡ Energy flows every second, automatically. It powers your generators." |
| 2 | CosmosCanvas (center pulse ring) | "⬡ Tap anywhere in the cosmos to trigger a Quantum Fluctuation and generate Matter." |
| 3 | Actions tab + generator area | "Build generators in the Actions tab to automate production and climb the resource chain." |

### Visual design

Each step renders:
- Full-screen semi-opaque black scrim (`Color.Black.copy(alpha = 0.75f)`)
- A bright spotlight circle punched out over the target area (drawn via `Canvas` with `BlendMode.Clear` or a contrasting border ring)
- A `Card` below/above the spotlight with the step message
- A `TextButton("Got it →")` (step 3: `"Let's go!"`) that calls `onTutorialNext()`

Step 2 adds a pulsing ring at the canvas center to reinforce the tap target, driven by `infiniteTransition`.

### State

`GameUiState.tutorialStep: Int`:
- `0` — uninitialized default only; ViewModel overwrites this before the first frame
- `1`, `2`, `3` — active step (overlay visible)
- `4` — tutorial complete / dismissed (overlay hidden permanently this session)

The overlay renders when `tutorialStep in 1..3`. At `4` it is gone permanently (for this install).

### Persistence

`TutorialPrefs` (a plain class, not a ViewModel) wraps `SharedPreferences`:

```kotlin
class TutorialPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("yhwh_prefs", Context.MODE_PRIVATE)
    var completed: Boolean
        get() = prefs.getBoolean("tutorial_completed", false)
        set(v) = prefs.edit().putBoolean("tutorial_completed", v).apply()
    var enabledOnNextLaunch: Boolean
        get() = prefs.getBoolean("tutorial_enabled", true)
        set(v) = prefs.edit().putBoolean("tutorial_enabled", v).apply()
}
```

**On ViewModel init:**
- If `!completed || enabledOnNextLaunch` → set `tutorialStep = 1`, set `enabledOnNextLaunch = false`
- Else → `tutorialStep = 4`

**On `onTutorialNext()`:**
- Increment `tutorialStep`
- If `tutorialStep == 4` → set `completed = true`

**On `onTutorialReset()` (from Settings):**
- Set `enabledOnNextLaunch = true` in prefs
- Does NOT re-show the tutorial immediately — it shows on the **next** launch

---

## 5. ViewModel Methods

```kotlin
fun onTutorialNext() // advance step; mark complete at step 4
fun onTutorialReset(enabled: Boolean) // write enabledOnNextLaunch=enabled; true = show on next launch, false = cancel
```

No new StateFlow needed — `tutorialStep` lives in `GameUiState`.

---

## 6. Settings Screen

`SettingsScreen` gains a single row:

```
[ Show tutorial on next launch ]  [Switch]
```

The switch reads `tutorialPrefs.enabledOnNextLaunch` for its checked state and calls `onTutorialReset(enabled: Boolean)` on every toggle — passing `true` schedules the tutorial for next launch, passing `false` cancels the pending re-show. `SettingsScreen` receives the ViewModel as a parameter from `AppNavigation`.

---

## 7. File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/TutorialOverlay.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`

---

## 8. Out of Scope

- Tutorial steps for Upgrades, Epoch progression, or Offline earnings
- Animated character / narrator
- Skippable mid-tutorial (each step requires "Got it" — no skip-all button)
- Localisation / string resources (strings stay inline for now)
