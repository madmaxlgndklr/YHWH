# Sync Infrastructure — Design Spec

**Date:** 2026-06-09
**Scope:** Supabase database schema, SyncRepository (pull/push game saves, conflict detection), remote DTO types
**Version:** 1.0
**Dependency:** SubProject 1 (Supabase Setup) — SupabaseModule must exist

---

## 1. Overview

Create the `game_saves` table in Supabase Postgres with RLS policies. Implement `SyncRepository` to pull/push game saves and detect conflicts (when both local and cloud saves exist). After this project, the sync layer can detect when a player signs in with conflicting saves, but the conflict resolution UI and game integration come later.

---

## 2. Database Schema

Run once in Supabase SQL editor (project ref: `qwresuyroqzyxbqrvrdh`):

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

Fields:
- `user_id` — PK, foreign key to `auth.users` (Supabase Auth), cascade delete
- `save_json` — full `SaveData` JSON (same blob written by `SaveManager`)
- `tick` — denormalized for conflict dialog (avoid deserializing huge JSON)
- `epoch` — denormalized for conflict dialog
- `last_saved_at` — millisecond timestamp

RLS: Players can only read/write their own row.

---

## 3. Architecture

```
SyncRepository
    ├── syncOnOpen(): SyncResult
    │   ├── Pull cloud save for user_id
    │   ├── Load local save from SaveManager
    │   └── Return: NoAction | CloudRestoreAvailable | PushedToCloud | ConflictDetected
    │
    ├── pushSave(data: SaveData): Unit
    │   └── Upsert to game_saves, fire-and-forget
    │
    └── decodeSaveData(rawJson: String): SaveData
        └── Deserialize JSON → SaveData for engine restoration
```

No StateFlow for conflicts here — `SyncRepository` just detects and returns the conflict. GameViewModel will handle the StateFlow and UI rendering (SubProject 4).

---

## 4. Types

**RemoteSaveRow** (DTO from Postgres):
```kotlin
@Serializable
data class RemoteSaveRow(
    @SerialName("user_id")       val userId: String,
    @SerialName("save_json")     val saveJson: String,
    val tick: Long,
    val epoch: String,
    @SerialName("last_saved_at") val lastSavedAt: Long
)
```

**SyncResult** (return type from `syncOnOpen()`):
```kotlin
sealed class SyncResult {
    object NoAction : SyncResult()
    data class CloudRestoreAvailable(val savedData: SaveData) : SyncResult()
    object PushedToCloud : SyncResult()
    data class ConflictDetected(val local: SaveData, val cloud: RemoteSaveRow) : SyncResult()
}
```

**ConflictState** (moved to SyncRepository for SubProject 3, will be used by GameViewModel in SubProject 4):
```kotlin
sealed class ConflictState {
    object None : ConflictState()
    data class Pending(val local: SaveData, val cloud: RemoteSaveRow) : ConflictState()
    object Resolved : ConflictState()
}
```

---

## 5. SyncRepository Logic

```kotlin
suspend fun syncOnOpen(): SyncResult {
    val uid = authRepository.currentUserId() ?: return SyncResult.NoAction
    
    val cloudRow = pg.from("game_saves")
        .select(...) { filter { eq("user_id", uid) } }
        .decodeSingleOrNull<RemoteSaveRow>()
    
    val localSave = saveManager.load()
    
    return when {
        cloudRow == null && localSave == null -> SyncResult.NoAction
        cloudRow == null && localSave != null -> {
            pushSave(localSave)
            SyncResult.PushedToCloud
        }
        cloudRow != null && localSave == null -> {
            SyncResult.CloudRestoreAvailable(decodeSaveData(cloudRow.saveJson))
        }
        else -> SyncResult.ConflictDetected(localSave!!, cloudRow!!)
    }
}

suspend fun pushSave(data: SaveData) {
    val uid = authRepository.currentUserId() ?: return
    val encoded = json.encodeToString(data)
    pg.from("game_saves").upsert(buildJsonObject {
        put("user_id", uid)
        put("save_json", encoded)
        put("tick", data.snapshot.tick)
        put("epoch", data.snapshot.epoch.name)
        put("last_saved_at", data.lastTickTimestamp)
    })
}

fun decodeSaveData(rawJson: String): SaveData =
    json.decodeFromString<SaveData>(rawJson)
```

---

## 6. Error Handling

- `syncOnOpen()` catches network exceptions, logs, returns `SyncResult.NoAction`
- `pushSave()` catches exceptions, logs, returns silently (game is never blocked on network)
- No retries (sync will happen again on next app launch or next sign-in)

---

## 7. Files

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt` (includes `RemoteSaveRow`, `SyncResult`, `ConflictState` types)

**Modified:**
- None (SyncRepository is standalone and doesn't modify existing files)

---

## 8. Testing

- Unit tests:
  - `syncOnOpen()` returns `NoAction` when uid is null
  - `syncOnOpen()` returns `CloudRestoreAvailable` when only cloud save exists
  - `syncOnOpen()` returns `PushedToCloud` when only local save exists
  - `syncOnOpen()` returns `ConflictDetected` when both exist
  - `pushSave()` encodes `SaveData` to JSON correctly
  - `decodeSaveData()` deserializes JSON back to `SaveData`
- Integration tests (mock Supabase Postgrest client)

---

## 9. Out of Scope

- Conflict resolution UI (SubProject 4)
- Persistent cloud push on periodic saves (SubProject 5)
- GameViewModel integration
- Real-time sync subscriptions
