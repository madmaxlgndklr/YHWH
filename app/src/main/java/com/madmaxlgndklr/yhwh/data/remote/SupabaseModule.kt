package com.madmaxlgndklr.yhwh.data.remote

import com.madmaxlgndklr.yhwh.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

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
