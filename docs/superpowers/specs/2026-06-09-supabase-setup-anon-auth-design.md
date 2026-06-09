# Supabase Setup + Anonymous Auth — Design Spec

**Date:** 2026-06-09
**Scope:** Gradle dependencies, BuildConfig, SupabaseModule singleton, AuthRepository, silent anonymous sign-in on app launch
**Version:** 1.0
**Dependency:** None (foundation for all other Supabase work)

---

## 1. Overview

Set up Supabase SDK, configure credentials via BuildConfig, create `SupabaseModule` singleton and `AuthRepository` wrapper, and integrate silent anonymous sign-in into `GameViewModel.init()`. After this project, the app compiles and players are authenticated as anonymous users on launch (no sign-in UI yet).

---

## 2. Architecture

```
BuildConfig (gradle)
    ↓
SupabaseModule (singleton client)
    ↓
AuthRepository (suspend funs: signInAnonymously, signInWithEmail, signUpWithEmail, signOut, etc.)
    ↓
GameViewModel (calls authRepository.signInAnonymously() in init; stores authRepository field)
```

Credentials (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_SERVER_CLIENT_ID`) are stored in `local.properties` (never committed) and exposed to BuildConfig via `app/build.gradle.kts`. SupabaseModule uses these at runtime.

---

## 3. Files

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/SupabaseModule.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/data/remote/AuthRepository.kt`

**Modified:**
- `gradle/libs.versions.toml` — add Supabase + Ktor versions
- `app/build.gradle.kts` — add Supabase dependencies, read credentials from local.properties, expose to BuildConfig
- `app/src/main/AndroidManifest.xml` — add `INTERNET` permission
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `authRepository` field, call `signInAnonymously()` in init

---

## 4. BuildConfig Credentials

In `local.properties` (user's machine, never committed):
```properties
SUPABASE_URL=https://qwresuyroqzyxbqrvrdh.supabase.co
SUPABASE_ANON_KEY=sb_publishable_wjOQSsIfe7GVZheX2UTc2g_SITcXNzX
GOOGLE_SERVER_CLIENT_ID=<from Google Cloud Console>
```

In `app/build.gradle.kts`:
```kotlin
defaultConfig {
    buildConfigField("String", "SUPABASE_URL", "\"${properties["SUPABASE_URL"] ?: ""}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"${properties["SUPABASE_ANON_KEY"] ?: ""}\"")
    buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${properties["GOOGLE_SERVER_CLIENT_ID"] ?: ""}\"")
}
```

---

## 5. SupabaseModule

Singleton that creates the Supabase client once at app startup:

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

Installs: Auth (session + sign-in/up), Postgrest (database queries), ComposeAuth (Google native login).

---

## 6. AuthRepository

Wrapper around `SupabaseModule.client.auth` providing suspend functions:

```kotlin
class AuthRepository {
    suspend fun signInAnonymously()  // silent, no UI
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signUpWithEmail(email: String, password: String)
    suspend fun signOut()
    fun currentUser(): UserInfo?
    fun isAnonymous(): Boolean
    fun currentUserId(): String?
}
```

All auth operations are non-blocking and return/throw cleanly.

---

## 7. GameViewModel Integration

Add to `GameViewModel.init()` after `engine.start()`:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    try {
        authRepository.signInAnonymously()
        // Success — user is now in an anonymous session
    } catch (e: Exception) {
        Log.e("GameViewModel", "Anonymous sign-in failed", e)
        // Game is still playable offline; next session will retry
    }
}
```

No UI shown. If sign-in fails, it's swallowed and the game continues. On next launch, it retries.

---

## 8. Testing

- Unit tests for `AuthRepository` methods (mock Supabase client)
- Integration test: sign in anonymously, verify `currentUser() != null` and `isAnonymous() == true`
- Build test: project compiles with `assembleDebug`

---

## 9. Out of Scope

- Email/Google sign-in UI (SubProject 2)
- Database schema (SubProject 3)
- Cloud save sync (SubProject 3+)
- Sign-out (SubProject 2)
