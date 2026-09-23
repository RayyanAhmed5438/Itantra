package com.tactical.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists whether the user wants manual PTT or continuous call mode. */
@Singleton
class PttModePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val isPttEnabled: Boolean
        get() = preferences.getBoolean(KEY_PTT_ENABLED, true)

    fun setPttEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_PTT_ENABLED, enabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "ptt_mode_preferences"
        private const val KEY_PTT_ENABLED = "ptt_enabled"
    }
}
