package com.tactical.platform.speech.mms

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class MmsTtsPlaybackMode {
    ONE_BY_ONE,
    OVERLAPPING
}

@Singleton
class MmsTtsPlaybackPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val playbackMode: MmsTtsPlaybackMode
        get() = when (preferences.getString(KEY_PLAYBACK_MODE, DEFAULT_PLAYBACK_MODE)) {
            ONE_BY_ONE_VALUE -> MmsTtsPlaybackMode.ONE_BY_ONE
            else -> MmsTtsPlaybackMode.OVERLAPPING
        }

    fun setPlaybackMode(mode: MmsTtsPlaybackMode) {
        preferences.edit()
            .putString(
                KEY_PLAYBACK_MODE,
                when (mode) {
                    MmsTtsPlaybackMode.ONE_BY_ONE -> ONE_BY_ONE_VALUE
                    MmsTtsPlaybackMode.OVERLAPPING -> OVERLAPPING_VALUE
                }
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "speech_preferences"
        private const val KEY_PLAYBACK_MODE = "incoming_tts_playback_mode"
        private const val ONE_BY_ONE_VALUE = "one_by_one"
        private const val OVERLAPPING_VALUE = "overlapping"
        private const val DEFAULT_PLAYBACK_MODE = OVERLAPPING_VALUE
    }
}
