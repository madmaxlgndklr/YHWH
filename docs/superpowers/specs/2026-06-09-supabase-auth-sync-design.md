# Supabase Auth + Cloud Save Sync — Design Spec

**Date:** 2026-06-09
**Scope:** Supabase authentication (email + Google) and cloud save sync for YHWH God Simulator
**Version:** 1.0
**Pattern:** Mirrors Pokedex Supabase implementation (`/Git/sandbox/Pokedex/app/src/main/java/com/madmaxlgndklr/pokedex/data/remote/`)

---

## 1. Overview

Players authenticate anonymously on first launch (silent, no UI). They can upgrade to a real account (email or Google) via Settings → Account. On sign-in, game saves are synced between local storage and Supabase Postgres. When both a local and cloud save exist, the player is shown a conflict dialog with timestamps and chooses which to keep.

---

## 2. Architecture

```
SupabaseModule          — singleton client (Auth, Postgrest, ComposeAuth/Google)
AuthRepository          — sign in/up/out, anonymous, currentUser, isAnonymous
SyncRepository          — push/pull game_saves, conflict resolution StateFlow
ProfileScreen           — email + Google UI, sign-out
GameViewModel           — calls syncOnOpen() on restore, exposes conflictState
GameScreen              — renders ConflictDialog when conflictState is Pending
SettingsScreen          — Account row → ProfileScreen, tutorial toggle unchanged
AppNavigation           — adds PROFILE route
```

**New files:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SupabaseModule.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/AuthRepository.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SyncRepository.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt`

**Modified files:**
- `gradle/libs.versions.toml` — Supabase SDK entries
- `app/build.gradle.kts` — Supabase dependencies
- `app/src/main/AndroidManifest.xml` — `INTERNET` permission
- `app/src/main/java/com/madmaxlgndklr/yhwh/YhwhApplication.kt` — Supabase client init
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — sync + conflictState
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt` — ConflictDialog
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt` — Account row
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt` — PROFILE route

---

## 3. Supabase Database Schema

Run once in the Supabase SQL editor (project ref: `qwresuyroqzyxbqrvrdh`):

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

`save_json` — full `SaveData` JSON (same blob written by `SaveManager`).
`tick` and `epoch` — denormalized for conflict dialog display without deserializing the blob.

---

## 4. Supabase Client Config

`SupabaseModule.kt` — identical structure to Pokedex's `SupabaseModule`:

```kotlin
object SupabaseModule {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(ComposeAuth) {
            googleNativeLogin(serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID)
        }
    }
}
```

Credentials stored in `local.properties` (never committed):
```
SUPABASE_URL=https://qwresuyroqzyxbqrvrdh.supabase.co
SUPABASE_ANON_KEY=sb_publishable_wjOQSsIfe7GVZheX2UTc2g_SITcXNzX
GOOGLE_SERVER_CLIENT_ID=<from Google Cloud Console>
```

Exposed to `BuildConfig` via `app/build.gradle.kts`:
```kotlin
defaultConfig {
    buildConfigField("String", "SUPABASE_URL", "\"${properties["SUPABASE_URL"]}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"${properties["SUPABASE_ANON_KEY"]}\"")
    buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${properties["GOOGLE_SERVER_CLIENT_ID"]}\"")
}
```

**Supabase SDK version:** `2.6.1` (matches Pokedex).

---

## 5. Auth Flow

**Launch:** `GameViewModel.init()` → `authRepository.signInAnonymously()` if no current session. Silent, no UI.

**Sign in/up:** Settings → Account → `ProfileScreen`. Email/password fields + Google button. On success → `syncRepository.syncOnOpen()`.

**Anonymous → linked:** Supabase upgrades the anonymous session transparently. `user_id` is preserved; existing `game_saves` row carries over.

**Sign out:** `authRepository.signOut()` → next launch creates a new anonymous session. Local save untouched. Game remains fully playable offline.

**`AuthRepository` interface:**
```kotlin
suspend fun signInAnonymously()
suspend fun signInWithEmail(email: String, password: String)
suspend fun signUpWithEmail(email: String, password: String)
suspend fun signOut()
fun currentUser(): UserInfo?
fun isAnonymous(): Boolean
fun currentUserId(): String?
```

---

## 6. Sync Flow

### On sign-in / app open (`syncOnOpen`)

```
1. Pull cloud save row for user_id (if exists)
2. Load local save (SaveManager.load())
3. If only cloud → restore from cloud, write to local file
4. If only local → push local to cloud
5. If both exist:
   → Emit ConflictState.Pending(local: SaveData, cloud: RemoteSaveRow)
   → Player sees ConflictDialog (Section 8)
   → Player chooses → winner written to both local and cloud
6. If neither → initNewGame() as normal
```

### On periodic save

`SaveManager` is unchanged. `GameViewModel.engine.onSaveDue` already runs on `Dispatchers.IO`. After the existing `saveManager.save(snapshot)` call, if `!authRepository.isAnonymous()`, it also calls `syncRepository.pushSave(savedData)`. Fire-and-forget via `runCatching`; failures are logged and swallowed — the game is never blocked on network.

### `SyncRepository` public interface

```kotlin
suspend fun syncOnOpen()
suspend fun pushSave(data: SaveData)
fun resolveConflict(useCloud: Boolean)
val conflictState: StateFlow<ConflictState>
```

### Remote DTO

```kotlin
@Serializable
data class RemoteSaveRow(
    @SerialName("user_id")     val userId: String,
    @SerialName("save_json")   val saveJson: String,
    val tick: Long,
    val epoch: String,
    @SerialName("last_saved_at") val lastSavedAt: Long
)
```

### `ConflictState` sealed class

```kotlin
sealed class ConflictState {
    object None : ConflictState()
    data class Pending(val local: SaveData, val cloud: RemoteSaveRow) : ConflictState()
    object Resolved : ConflictState()
}
```

---

## 7. GameViewModel Changes

**`AuthState` sealed class** (defined in `GameViewModel.kt` or a shared state file):
```kotlin
sealed class AuthState {
    object Anonymous : AuthState()
    data class SignedIn(val email: String?) : AuthState()
}
```

New fields:
```kotlin
private val authRepository = AuthRepository()
private val syncRepository = SyncRepository(authRepository)
val conflictState: StateFlow<ConflictState> = syncRepository.conflictState
private val _authState = MutableStateFlow<AuthState>(AuthState.Anonymous)
val authState: StateFlow<AuthState> = _authState.asStateFlow()
```

New methods:
```kotlin
fun resolveConflict(useCloud: Boolean)   // delegates to SyncRepository, calls engine.restore() if useCloud
```

`init` block addition — after `engine.restore()` / `engine.initNewGame()`:
```kotlin
viewModelScope.launch(Dispatchers.IO) {
    authRepository.signInAnonymously()
    if (!authRepository.isAnonymous()) {
        syncRepository.syncOnOpen()
    }
}
```

`SaveManager.save()` wrapper in ViewModel is updated to also call `syncRepository.pushSave(data)` when signed in.

---

## 8. ConflictDialog (GameScreen)

Shown when `conflictState is ConflictState.Pending`. Two side-by-side cards:

```
┌─────────────────────────────────────┐
│         Two saves found             │
│                                     │
│  ┌──────────────┐  ┌──────────────┐ │
│  │  This Device │  │    Cloud     │ │
│  │  Cosmology   │  │  Cosmology   │ │
│  │  Tick 4,821  │  │  Tick 12,003 │ │
│  │  Jun 8 12:04 │  │  Jun 9 09:17 │ │
│  │ [Use Local]  │  │ [Use Cloud]  │ │
│  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────┘
```

Timestamps formatted as `"MMM d HH:mm"`. Tapping either button calls `viewModel.resolveConflict(useCloud = ...)`.

---

## 9. ProfileScreen

Matches Pokedex `ProfileScreen` structure adapted to YHWH dark cosmic theme:

- If anonymous: Sign In / Create Account tabs, email + password fields, Google button, loading indicator, error text
- If signed in: email display (or "Google Account"), cloud save summary (epoch + last saved timestamp), Sign Out button
- Navigation: back arrow → SettingsScreen

---

## 10. SettingsScreen Changes

Adds one section above the tutorial toggle:

```
ACCOUNT
[ Account ]  ›  max@example.com  (or "Not signed in")
```

Tapping navigates to ProfileScreen. The subtitle line reads `authRepository.currentUser()?.email ?: "Not signed in"` via `GameViewModel.authState`.

---

## 11. Dependencies to Add

```toml
# gradle/libs.versions.toml
supabase = "2.6.1"
ktor = "3.0.3"

supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt", version.ref = "supabase" }
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt", version.ref = "supabase" }
supabase-compose-auth = { group = "io.github.jan-tennert.supabase", name = "compose-auth", version.ref = "supabase" }
supabase-compose-auth-ui = { group = "io.github.jan-tennert.supabase", name = "compose-auth-ui", version.ref = "supabase" }
ktor-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
```

---

## 12. Out of Scope

- Realtime sync (no need for single-player idle game)
- Leaderboards / epoch ranking
- Password reset flow
- Account deletion
- Multi-device simultaneous play conflict (last push wins after initial conflict resolution)
