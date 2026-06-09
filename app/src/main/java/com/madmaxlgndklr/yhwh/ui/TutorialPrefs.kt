package com.madmaxlgndklr.yhwh.ui

import android.content.Context

/**
 * Persists tutorial completion state and the "show on next launch" flag
 * via SharedPreferences. All writes are applied immediately (apply()).
 */
class TutorialPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("yhwh_prefs", Context.MODE_PRIVATE)

    /** True once the player has dismissed all 3 steps at least once. */
    var completed: Boolean
        get() = prefs.getBoolean("tutorial_completed", false)
        set(v) = prefs.edit().putBoolean("tutorial_completed", v).apply()

    /**
     * When true, the tutorial will re-show on the next launch even if [completed].
     * The ViewModel resets this to false immediately on launch so it only fires once.
     */
    var enabledOnNextLaunch: Boolean
        get() = prefs.getBoolean("tutorial_enabled", true)
        set(v) = prefs.edit().putBoolean("tutorial_enabled", v).apply()
}
