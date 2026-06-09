package com.madmaxlgndklr.yhwh.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo

class AuthRepository {
    private val auth = SupabaseModule.client.auth

    suspend fun signInAnonymously() {
        if (auth.currentUserOrNull() == null) {
            auth.signInAnonymously()
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUpWithEmail(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUser(): UserInfo? = auth.currentUserOrNull()

    /** Returns true when there is no session or the user is anonymous (role == "anon"). */
    fun isAnonymous(): Boolean {
        val user = auth.currentUserOrNull() ?: return true
        return user.role == "anon"
    }

    fun currentUserId(): String? = auth.currentUserOrNull()?.id
}
