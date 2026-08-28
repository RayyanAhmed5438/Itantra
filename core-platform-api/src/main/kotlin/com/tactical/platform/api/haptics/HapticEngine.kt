package com.tactical.platform.api.haptics

/**
 * Triggers device vibration. Implemented in platform-android, wrapping
 * Android's Vibrator / VibratorManager API.
 */
interface HapticEngine {

    /**
     * Performs the given pattern.
     */
    suspend fun perform(pattern: HapticPattern)
}