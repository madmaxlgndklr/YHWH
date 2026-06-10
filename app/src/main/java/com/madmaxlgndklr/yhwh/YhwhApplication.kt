package com.madmaxlgndklr.yhwh

import android.app.Application
import android.util.Log
import com.madmaxlgndklr.yhwh.data.remote.SupabaseModule

class YhwhApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting YHWH — ${BuildConfig.VERSION_NAME}")
        }

        // Runtime guard: build.gradle.kts should catch blank credentials at build time,
        // but log loudly if they somehow reach runtime as blank strings.
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            Log.e(TAG,
                "Supabase credentials are blank — auth and sync will not function. " +
                "Ensure SUPABASE_URL and SUPABASE_ANON_KEY are set in local.properties."
            )
        }

        // Eagerly initialize the Supabase client so any misconfiguration surfaces
        // at startup rather than on the first network call mid-session.
        try {
            SupabaseModule.client
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Supabase client initialized (${BuildConfig.SUPABASE_URL})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase client initialization failed — proceeding offline", e)
        }
    }

    companion object {
        private const val TAG = "YhwhApplication"
    }
}
