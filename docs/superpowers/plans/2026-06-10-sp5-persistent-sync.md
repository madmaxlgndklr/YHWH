# Persistent Cloud Sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After each local save, push the same `SaveData` to the cloud if the player is signed in with a real (non-anonymous) account. Fire-and-forget — a network failure never blocks the game loop.

**Architecture:** `engine.onSaveDue` in `GameViewModel` already calls `saveManager.save(snapshot)`. Extend it to also call `syncRepository.pushSave(SaveData(...))` when `!authRepository.isAnonymous()`. Use the same `ts` timestamp for both so local file and cloud row are consistent.

**Tech Stack:** Kotlin Coroutines (`Dispatchers.IO`, `runCatching`), existing `SyncRepository.pushSave()`, `SaveData` data class

---

## Prerequisites

- SubProjects 1–4 complete: `SyncRepository`, `AuthRepository`, `syncRepository` field on `GameViewModel` all exist
- `game_saves` table exists in Supabase (SP3 prerequisite)

---

## File Map

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `SaveData` import, extend `engine.onSaveDue` callback to push to cloud when signed in

---

## Task 1: Wire Cloud Push into engine.onSaveDue

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

- [ ] **Step 1: Add `SaveData` import**

In `GameViewModel.kt`, add one import after the existing `SaveManager` import:

```kotlin
import com.madmaxlgndklr.yhwh.persistence.SaveData
```

- [ ] **Step 2: Replace the `engine.onSaveDue` callback**

Find this block in `init` (currently lines ~73–75):

```kotlin
engine.onSaveDue = { snapshot ->
    withContext(Dispatchers.IO) { saveManager.save(snapshot) }
}
```

Replace it with:

```kotlin
engine.onSaveDue = { snapshot ->
    withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        saveManager.save(snapshot, overrideTimestamp = ts)
        if (!authRepository.isAnonymous()) {
            runCatching {
                syncRepository.pushSave(SaveData(lastTickTimestamp = ts, snapshot = snapshot))
            }.onFailure { Log.e("GameViewModel", "periodic cloud push failed", it) }
        }
    }
}
```

By capturing `ts` before both calls, the local save and the cloud upsert share the exact same `last_saved_at` timestamp — no millisecond drift between them.

- [ ] **Step 3: Run existing tests — must all pass**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: all tests pass (32 total).

- [ ] **Step 4: Full build**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: push save to cloud on every periodic engine save when signed in"
```

- [ ] **Step 6: Connect device and install**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
```

If no device listed, check Developer Options → Wireless Debugging on device for current IP:port:
```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb connect <IP:PORT>
```

Install:
```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb install -r \
  /home/madmaxlgndklr/Git/sandbox/YHWH/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 7: Manual verification**

Sign in with email → play for 30+ seconds (engine saves every 30 ticks) → open the Supabase Table Editor at https://supabase.com/dashboard/project/qwresuyroqzyxbqrvrdh/editor → check that `game_saves` has a row for your user with an updated `tick` and `last_saved_at`.

- [ ] **Step 8: Push to GitHub**

```bash
git push origin main
```

---

## Self-Review

- [x] **Spec §2 push on periodic save** — `engine.onSaveDue` now calls `syncRepository.pushSave(...)` after the local save (Step 2)
- [x] **Spec §2 anonymous guard** — `if (!authRepository.isAnonymous())` — anonymous sessions never push (Step 2)
- [x] **Spec §2 fire-and-forget** — `runCatching { ... }.onFailure { Log.e(...) }` — network failure is swallowed, game loop never blocked (Step 2)
- [x] **Spec §2 consistent timestamp** — `ts` captured once, used for both `saveManager.save(overrideTimestamp = ts)` and `SaveData(lastTickTimestamp = ts, ...)` (Step 2)
- [x] **No SaveManager changes** — existing `save()` interface unchanged; `overrideTimestamp` param already exists (Step 2)
- [x] **No new StateFlows** — SP5 adds no UI state; the push is invisible to the player
