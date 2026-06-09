# Persistent Cloud Sync — Design Spec

**Date:** 2026-06-09
**Scope:** Wire `syncRepository.pushSave()` into `GameViewModel.engine.onSaveDue` for periodic cloud updates, expose `authState` to UI
**Version:** 1.0
**Dependencies:** All SubProjects 1-4 — all Supabase infrastructure must be in place

---

## 1. Overview

Whenever `GameViewModel.engine.onSaveDue` fires (every 30 game ticks or on shutdown), push the current game save to the cloud if the player is signed in. After this project, game progress is continuously synced to the cloud whenever the engine saves locally.

---

## 2. Architecture

```
GameEngine.onSaveDue (every 30 ticks, on shutdown)
    ↓
GameViewModel.engine.onSaveDue callback
    ├── saveManager.save(snapshot)  [existing local save]
    └── if (!authRepository.isAnonymous()):
        └── syncRepository.pushSave(data)  [async, fire-and-forget]
```

Push is fire-and-forget (wrapped in `runCatching` with logging). The local save always succeeds; the cloud push is best-effort.

---

## 3. GameViewModel Changes

Update the `engine.onSaveDue` callback in `GameViewModel.init()`:

```kotlin
engine.onSaveDue = { snapshot ->
    withContext(Dispatchers.IO) {
        val savedData = saveManager.save(snapshot)
        // Push to cloud if signed in (non-blocking)
        if (!authRepository.isAnonymous()) {
            runCatching {
                syncRepository.pushSave(savedData)
            }.onFailure {
                Log.e("GameViewModel", "Cloud push failed", it)
            }
        }
    }
}
```

Assumptions:
- `saveManager.save(snapshot)` returns the `SaveData` object (if it doesn't, adjust to call `saveManager.load()` instead)
- `syncRepository.pushSave()` is a suspend function

---

## 4. AuthState Exposure

`authState` is already exposed as a `StateFlow<AuthState>` from SubProject 2. Keep it in GameViewModel and continue exposing it so SettingsScreen + ProfileScreen can read the current sign-in state.

No changes needed here — just verify the StateFlow is wired.

---

## 5. Implementation Notes

**Fire-and-forget semantics:**
- The local save is always written (game cannot block on network)
- Cloud push is wrapped in `runCatching` to prevent exception from propagating to engine
- Failures are logged but not surfaced to the player (no toast, no dialog)
- Next periodic save will retry the push

**No real-time subscriptions:**
- This project only pushes. There's no listening for remote changes (that's out of scope)
- Single-player game: no other device will overwrite this player's cloud save while the game is open

**Timing:**
- Push happens on the same `Dispatchers.IO` thread as the local save
- Should complete in <100ms for a typical save (100KB JSON)
- If push is slow, the next engine tick isn't delayed (it's queued and runs after)

---

## 6. Files

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — update `engine.onSaveDue` callback to call `syncRepository.pushSave()`

---

## 7. Testing

- Unit tests:
  - Verify `engine.onSaveDue` calls `syncRepository.pushSave()` when signed in
  - Verify `engine.onSaveDue` does NOT call `syncRepository.pushSave()` when anonymous
  - Verify exception in `pushSave()` doesn't crash the engine (wrapped in `runCatching`)
- Integration tests:
  - Sign in → generate resources → trigger engine save → verify cloud save row updated
  - Network failure during push → verify local save still succeeds + next push retries

---

## 8. Out of Scope

- Automatic conflict resolution on periodic push (keeps last-write-wins semantics)
- Retry strategies (manual retry on next app launch is sufficient)
- Bandwidth optimization (no diffing, always push full save)
- Real-time two-way sync (pull remote changes)
- Offline queue (saves are best-effort, not queued)
