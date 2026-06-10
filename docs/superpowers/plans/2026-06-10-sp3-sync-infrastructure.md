# Sync Infrastructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `game_saves` Postgres table with RLS, and implement `SyncRepository` with `RemoteSaveRow`, `SyncResult`, `ConflictState` types and pull/push/decode logic. No GameViewModel wiring yet — that's SubProject 4.

**Architecture:** `SyncRepository(authRepository, saveManager)` exposes three suspend/pure methods: `syncOnOpen(): SyncResult`, `pushSave(data: SaveData)`, `decodeSaveData(json: String): SaveData`. All network calls are fire-and-forget or return a sealed result; callers never throw.

**Tech Stack:** Supabase Kotlin SDK 3.6.0 (`postgrest-kt`), `kotlinx.serialization`, `kotlinx.serialization.json.buildJsonObject`

---

## Prerequisites (Manual — Before Task 2)

Run the following SQL in the Supabase SQL editor at https://supabase.com/dashboard/project/qwresuyroqzyxbqrvrdh/sql/new:

```sql
create table game_saves (
  user_id       uuid references auth.users(id) on delete cascade primary key,
  save_json     text    not null,
  tick          bigint  not null default 0,
  epoch         text    not null default 'COSMOLOGY',
  last_saved_at bigint  not null
);

alter table game_saves enable row level security;

create policy "Users manage own save"
  on game_saves for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

Verify in the Table Editor that `game_saves` appears with the correct columns and RLS enabled.

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt`
- `app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTypesTest.kt`

**Modified:** None.

---

## Task 1: SyncRepository + Unit Tests

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt`
- Create: `app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTypesTest.kt`

### Step 1 — Write the failing tests first

`app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTypesTest.kt`:

```kotlin
package com.madmaxlgndklr.yhwh.data.remote

import com.madmaxlgndklr.yhwh.engine.EpochType
import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import com.madmaxlgndklr.yhwh.engine.ResourceType
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.persistence.SaveData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SyncRepositoryTypesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun minimalSaveData(tick: Long = 1L) = SaveData(
        lastTickTimestamp = 9999L,
        snapshot = GameSnapshot(
            tick = tick,
            epoch = EpochType.COSMOLOGY,
            resources = mapOf(ResourceType.ENERGY.name to BigDouble.of(1.0)),
            generators = emptyList(),
            upgrades = emptyList(),
            epochProgress = 0f,
            events = emptyList()
        )
    )

    @Test fun `ConflictState None is not Pending or Resolved`() {
        val state: ConflictState = ConflictState.None
        assertTrue(state is ConflictState.None)
        assertFalse(state is ConflictState.Resolved)
    }

    @Test fun `ConflictState Resolved is not None`() {
        val state: ConflictState = ConflictState.Resolved
        assertTrue(state is ConflictState.Resolved)
        assertFalse(state is ConflictState.None)
    }

    @Test fun `SyncResult types are distinct`() {
        val noAction: SyncResult = SyncResult.NoAction
        val pushed: SyncResult = SyncResult.PushedToCloud
        assertTrue(noAction is SyncResult.NoAction)
        assertTrue(pushed is SyncResult.PushedToCloud)
        assertFalse(noAction is SyncResult.PushedToCloud)
    }

    @Test fun `SyncResult CloudRestoreAvailable holds SaveData`() {
        val data = minimalSaveData(tick = 42L)
        val result: SyncResult = SyncResult.CloudRestoreAvailable(data)
        assertTrue(result is SyncResult.CloudRestoreAvailable)
        assertEquals(42L, (result as SyncResult.CloudRestoreAvailable).savedData.snapshot.tick)
    }

    @Test fun `RemoteSaveRow holds expected fields`() {
        val row = RemoteSaveRow(
            userId = "uid-123",
            saveJson = "{}",
            tick = 500L,
            epoch = "COSMOLOGY",
            lastSavedAt = 100000L
        )
        assertEquals("uid-123", row.userId)
        assertEquals(500L, row.tick)
        assertEquals("COSMOLOGY", row.epoch)
    }

    @Test fun `SaveData round-trips through JSON correctly`() {
        val original = minimalSaveData(tick = 77L)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SaveData>(encoded)
        assertEquals(original.lastTickTimestamp, decoded.lastTickTimestamp)
        assertEquals(original.snapshot.tick, decoded.snapshot.tick)
        assertEquals(original.snapshot.epoch, decoded.snapshot.epoch)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (types don't exist yet)**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest \
  --tests "com.madmaxlgndklr.yhwh.data.remote.SyncRepositoryTypesTest" 2>&1 | tail -10
```

Expected: compilation error — `ConflictState`, `SyncResult`, `RemoteSaveRow` not found.

- [ ] **Step 3: Create SyncRepository.kt**

```kotlin
package com.madmaxlgndklr.yhwh.data.remote

import android.util.Log
import com.madmaxlgndklr.yhwh.persistence.SaveData
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── Remote DTO ────────────────────────────────────────────────────────────────

@Serializable
data class RemoteSaveRow(
    @SerialName("user_id")       val userId: String,
    @SerialName("save_json")     val saveJson: String,
    val tick: Long,
    val epoch: String,
    @SerialName("last_saved_at") val lastSavedAt: Long
)

// ── State types ───────────────────────────────────────────────────────────────

sealed class ConflictState {
    object None : ConflictState()
    data class Pending(val local: SaveData, val cloud: RemoteSaveRow) : ConflictState()
    object Resolved : ConflictState()
}

sealed class SyncResult {
    object NoAction : SyncResult()
    data class CloudRestoreAvailable(val savedData: SaveData) : SyncResult()
    object PushedToCloud : SyncResult()
    data class ConflictDetected(val local: SaveData, val cloud: RemoteSaveRow) : SyncResult()
}

// ── Repository ────────────────────────────────────────────────────────────────

class SyncRepository(
    private val authRepository: AuthRepository,
    private val saveManager: SaveManager
) {
    private val pg = SupabaseModule.client.postgrest
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Pull cloud save, compare with local, and return what the caller should do.
     * Never throws — returns [SyncResult.NoAction] on network failure.
     */
    suspend fun syncOnOpen(): SyncResult {
        val uid = authRepository.currentUserId() ?: return SyncResult.NoAction
        return try {
            val cloudRow = pg.from("game_saves")
                .select { filter { eq("user_id", uid) } }
                .decodeSingleOrNull<RemoteSaveRow>()

            val localSave = saveManager.load()

            when {
                cloudRow == null && localSave == null -> SyncResult.NoAction
                cloudRow == null && localSave != null -> {
                    pushSave(localSave)
                    SyncResult.PushedToCloud
                }
                cloudRow != null && localSave == null ->
                    SyncResult.CloudRestoreAvailable(decodeSaveData(cloudRow.saveJson))
                else ->
                    SyncResult.ConflictDetected(localSave!!, cloudRow!!)
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "syncOnOpen failed", e)
            SyncResult.NoAction
        }
    }

    /**
     * Upsert [data] to the cloud. Swallows network errors — the local save is
     * always the source of truth; the cloud copy is best-effort.
     */
    suspend fun pushSave(data: SaveData) {
        val uid = authRepository.currentUserId() ?: return
        try {
            val encoded = json.encodeToString(data)
            pg.from("game_saves").upsert(buildJsonObject {
                put("user_id", uid)
                put("save_json", encoded)
                put("tick", data.snapshot.tick)
                put("epoch", data.snapshot.epoch.name)
                put("last_saved_at", data.lastTickTimestamp)
            })
        } catch (e: Exception) {
            Log.e("SyncRepository", "pushSave failed", e)
        }
    }

    /** Deserialize a raw JSON string from the cloud into a [SaveData]. */
    fun decodeSaveData(rawJson: String): SaveData =
        json.decodeFromString<SaveData>(rawJson)
}
```

- [ ] **Step 4: Run tests — must all pass**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, all tests pass (previous 26 + 6 new = 32 total).

- [ ] **Step 5: Verify full compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt \
        app/src/test/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepositoryTypesTest.kt
git commit -m "feat: add SyncRepository with ConflictState, SyncResult, and remote DTO types"
```

- [ ] **Step 7: Push to GitHub**

```bash
git push origin main
```

---

## Self-Review

- [x] **Spec §2 game_saves schema** — `user_id` PK with cascade, `save_json TEXT`, `tick BIGINT`, `epoch TEXT`, `last_saved_at BIGINT`; RLS policy "Users manage own save" (Task 0 manual step)
- [x] **Spec §3 RemoteSaveRow** — `@Serializable` with `@SerialName` for snake_case fields (Task 1 Step 3)
- [x] **Spec §3 ConflictState** — `None`, `Pending(local, cloud)`, `Resolved` (Task 1 Step 3)
- [x] **Spec §3 SyncResult** — `NoAction`, `CloudRestoreAvailable(savedData)`, `PushedToCloud`, `ConflictDetected(local, cloud)` (Task 1 Step 3)
- [x] **Spec §4 syncOnOpen()** — all 4 branches: no saves → NoAction; cloud only → CloudRestoreAvailable; local only → pushSave + PushedToCloud; both → ConflictDetected (Task 1 Step 3)
- [x] **Spec §5 pushSave()** — upsert with all 5 columns; catches + logs exceptions (Task 1 Step 3)
- [x] **Spec §5 decodeSaveData()** — pure function, `json.decodeFromString<SaveData>(rawJson)` (Task 1 Step 3)
- [x] **Spec §6 error handling** — `syncOnOpen` catches and returns `NoAction`; `pushSave` catches and returns silently (Task 1 Step 3)
- [x] **Tests cover all type branches** — 6 tests covering `ConflictState`, `SyncResult`, `RemoteSaveRow`, JSON round-trip (Task 1 Steps 1–4)
- [x] **No GameViewModel changes** — SubProject 4 wires SyncRepository into the ViewModel
