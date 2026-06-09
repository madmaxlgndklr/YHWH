# Conflict Resolution — Design Spec

**Date:** 2026-06-09
**Scope:** ConflictDialog composable, GameViewModel.resolveConflict(useCloud), GameViewModel.performSync(), conflictState StateFlow
**Version:** 1.0
**Dependencies:** SubProject 2 (ProfileScreen) and SubProject 3 (SyncRepository) — authRepository and SyncRepository must exist

---

## 1. Overview

Add a ConflictDialog composable that displays two save cards (local vs cloud) with epoch, tick count, and timestamp. When a player signs in and both local + cloud saves exist, show this dialog. On button tap, call `GameViewModel.resolveConflict(useCloud: Boolean)` to restore the chosen save and update the cloud. After this project, sign-in conflicts are handled with player choice.

---

## 2. Architecture

```
GameViewModel.performSync()
    ├── Call syncRepository.syncOnOpen()
    ├── If ConflictDetected:
    │   └── Emit ConflictState.Pending to _conflictState StateFlow
    │
GameScreen
    ├── Observe conflictState
    ├── If Pending, render ConflictDialog
    │
ConflictDialog
    ├── Display two SaveCard components (local vs cloud)
    ├── Each with epoch, tick, formatted timestamp, "Use this" button
    │
GameViewModel.resolveConflict(useCloud: Boolean)
    ├── If useCloud: restore cloud save to engine + local file
    ├── If !useCloud: push local save to cloud
    └── Emit ConflictState.Resolved
```

---

## 3. Types (in GameViewModel or SyncRepository)

**ConflictState** (already defined in SubProject 3):
```kotlin
sealed class ConflictState {
    object None : ConflictState()
    data class Pending(val local: SaveData, val cloud: RemoteSaveRow) : ConflictState()
    object Resolved : ConflictState()
}
```

---

## 4. GameViewModel Methods

```kotlin
private suspend fun performSync() {
    when (val result = syncRepository.syncOnOpen()) {
        is SyncResult.CloudRestoreAvailable -> {
            // Restore cloud save to engine + local file
            withContext(Dispatchers.Main) {
                engine.stop()
                saveManager.save(result.savedData.snapshot, result.savedData.lastTickTimestamp)
                engine.restore(result.savedData.snapshot, 0L)
                engine.start()
            }
        }
        is SyncResult.ConflictDetected -> {
            // Show conflict dialog
            withContext(Dispatchers.Main) {
                _conflictState.value = ConflictState.Pending(result.local, result.cloud)
            }
        }
        else -> { /* NoAction or PushedToCloud — do nothing */ }
    }
}

fun resolveConflict(useCloud: Boolean) {
    val pending = (_conflictState.value as? ConflictState.Pending) ?: return
    viewModelScope.launch(Dispatchers.IO) {
        if (useCloud) {
            val decoded = syncRepository.decodeSaveData(pending.cloud.saveJson)
            saveManager.save(decoded.snapshot, decoded.lastTickTimestamp)
            withContext(Dispatchers.Main) {
                engine.stop()
                engine.restore(decoded.snapshot, 0L)
                engine.start()
            }
        } else {
            // Use local — push to cloud to overwrite
            syncRepository.pushSave(pending.local)
        }
        withContext(Dispatchers.Main) {
            _conflictState.value = ConflictState.Resolved
        }
    }
}
```

Add to `GameViewModel.signInWithEmail/signUpWithEmail/onGoogleSignInSuccess()` after successful auth:
```kotlin
updateAuthState()
performSync()
```

---

## 5. ConflictDialog Composable

Full-screen AlertDialog showing two SaveCard components side-by-side:

```kotlin
@Composable
fun ConflictDialog(
    local: SaveData,
    cloud: RemoteSaveRow,
    onUseLocal: () -> Unit,
    onUseCloud: () -> Unit
)
```

**SaveCard** (nested composable):
- Label ("This Device" or "Cloud")
- Epoch name
- Tick count (formatted with comma separator)
- Timestamp (formatted as "MMM d HH:mm")
- "Use this" button

Example display:
```
    Two saves found

┌──────────────┐  ┌──────────────┐
│ This Device  │  │    Cloud     │
│  Cosmology   │  │   Biology    │
│ Tick 4,821   │  │ Tick 12,003  │
│ Jun 8 12:04  │  │ Jun 9 09:17  │
│ [Use this]   │  │ [Use this]   │
└──────────────┘  └──────────────┘
```

---

## 6. GameScreen Integration

Add to GameScreen:

```kotlin
val conflictState by viewModel.conflictState.collectAsStateWithLifecycle()

if (conflictState is ConflictState.Pending) {
    val pending = conflictState as ConflictState.Pending
    ConflictDialog(
        local = pending.local,
        cloud = pending.cloud,
        onUseLocal = { viewModel.resolveConflict(useCloud = false) },
        onUseCloud = { viewModel.resolveConflict(useCloud = true) }
    )
}
```

Render dialog on top of everything else (after tutorial overlay, outside main Scaffold).

---

## 7. Files

**New:**
- None (ConflictDialog is a new composable added to GameScreen or a dedicated file)

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `_conflictState`, `performSync()`, `resolveConflict()`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt` — add ConflictDialog rendering + imports
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt` — call `viewModel.performSync()` after successful sign-in/sign-up/Google

---

## 8. Testing

- Unit tests:
  - `resolveConflict(useCloud = true)` restores cloud save
  - `resolveConflict(useCloud = false)` pushes local to cloud
  - Conflict state is cleared after resolution
- Composable tests:
  - ConflictDialog renders two cards
  - Cards display correct epoch, tick, timestamp
  - Buttons call the correct callbacks
- Integration tests:
  - Sign in → detect conflict → show dialog → tap "Use Cloud" → engine restored

---

## 9. Out of Scope

- Persistent cloud push on every periodic save (SubProject 5)
- Auto-resolution strategies (always use cloud, always use local, etc.)
- Conflict history / logging
