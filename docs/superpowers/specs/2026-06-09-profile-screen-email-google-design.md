# Profile Screen + Email/Google Sign-In — Design Spec

**Date:** 2026-06-09
**Scope:** ProfileScreen composable UI, SettingsScreen Account row, AppNavigation PROFILE route, email/password sign-in/up, Google native sign-in
**Version:** 1.0
**Dependency:** SubProject 1 (Supabase Setup + Anonymous Auth) — AuthRepository must exist

---

## 1. Overview

Add a ProfileScreen composable that allows players to sign in or create an account via email/password or Google. Wire it into SettingsScreen as an Account row that navigates to ProfileScreen. After this project, players can sign in with a real account (though saves aren't synced yet).

---

## 2. Architecture

```
SettingsScreen
    ↓ (Account row click)
AppNavigation (PROFILE route)
    ↓
ProfileScreen (email/password fields, Google button, sign in/up/out UI)
    ↓ (on success or Google sign-in)
GameViewModel.signInWithEmail(email, password)
GameViewModel.signUpWithEmail(email, password)
GameViewModel.onGoogleSignInSuccess()
GameViewModel.signOut()
```

ProfileScreen displays different UI based on `GameViewModel.authState: StateFlow<AuthState>`:
- `AuthState.Anonymous` — shows Sign In / Create Account tabs, email + password fields, Google button
- `AuthState.SignedIn(email)` — shows email address, Sign Out button

---

## 3. Files

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/ProfileScreen.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — add `signInWithEmail`, `signUpWithEmail`, `signOut`, `onGoogleSignInSuccess`, `updateAuthState`, auth loading/error StateFlows, `AuthState` sealed class
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt` — add Account row with email display + clickable navigation
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt` — add PROFILE route, pass viewModel to both GameScreen and SettingsScreen

---

## 4. AuthState

New sealed class in GameViewModel.kt:

```kotlin
sealed class AuthState {
    object Anonymous : AuthState()
    data class SignedIn(val email: String?) : AuthState()
}
```

Exposed via `val authState: StateFlow<AuthState>` on GameViewModel.

---

## 5. GameViewModel Methods

```kotlin
fun signInWithEmail(email: String, password: String)  // coroutine, sets loading/error
fun signUpWithEmail(email: String, password: String)  // coroutine, sets loading/error
fun signOut()                                           // coroutine
fun onGoogleSignInSuccess()                             // called after Google flow completes
fun clearAuthError()                                    // clears error message for retry
private fun updateAuthState()                           // reads authRepository, updates _authState
```

Sign-in/up methods:
- Set `_authLoading = true`, `_authError = null`
- Call `authRepository.signInWithEmail/signUpWithEmail`
- On success: call `updateAuthState()`
- On failure: set `_authError` to exception message
- Finally: set `_authLoading = false`

---

## 6. ProfileScreen UI

Two states:

**Anonymous (Sign In / Create Account tabs visible):**
- Tabs for "Sign In" vs "Create Account"
- Email text field
- Password text field
- Loading indicator (while sign-in in progress)
- Error message (if sign-in failed)
- "Sign In" / "Create" button (calls `viewModel.signInWithEmail` or `viewModel.signUpWithEmail`)
- "Google" button (triggers Google sign-in flow via `SupabaseModule.client.composeAuth.rememberSignInWithGoogle`)

**Signed In (email + Sign Out visible):**
- Card showing email address (or "Google Account" if null)
- "Sign Out" button (calls `viewModel.signOut()`)

Back arrow at top navigates back to SettingsScreen.

---

## 7. SettingsScreen Account Row

Add above the tutorial toggle:

```
[ Account ]  ›  max@example.com  (or "Not signed in")
```

Clickable Row that:
- Reads `authState` to display email or "Not signed in"
- Navigates to PROFILE route on click

---

## 8. AppNavigation

Hoist `GameViewModel` to nav level via `viewModel()`. Pass to both GameScreen and SettingsScreen:

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()
    
    NavHost(navController, startDestination = Routes.GAME) {
        composable(Routes.GAME) {
            GameScreen(viewModel = gameViewModel, onNavigateToSettings = { ... })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = gameViewModel, onNavigateToProfile = { ... })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(viewModel = gameViewModel, onBack = { navController.popBackStack() })
        }
    }
}
```

---

## 9. Testing

- Unit tests: `AuthState` serialization, `authState` flow changes
- Composable tests: ProfileScreen renders anonymous UI, signed-in UI
- Integration: tap "Sign In", enter credentials, verify `authState` updates
- Google flow: verify `onGoogleSignInSuccess()` is called after Google login

---

## 10. Out of Scope

- Cloud save sync (SubProject 3+)
- Sign-in persistence across app restarts (Supabase handles this, no extra work needed)
- Password reset
- Account deletion
