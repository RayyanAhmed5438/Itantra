package com.tactical.ptt.feedback

import com.tactical.platform.api.haptics.HapticEngine
import com.tactical.platform.api.haptics.HapticPattern

class PatternedHapticFeedback(
    private val hapticEngine: HapticEngine
) : PttHapticFeedback {

    override suspend fun onPress() {
        hapticEngine.perform(
            HapticPattern(timings = longArrayOf(0, 40), amplitudes = intArrayOf(0, 180))
        )
    }

    override suspend fun onRelease() {
        hapticEngine.perform(
            HapticPattern(timings = longArrayOf(0, 25), amplitudes = intArrayOf(0, 120))
        )
    }

    override suspend fun onTransmitComplete() {
        hapticEngine.perform(
            HapticPattern(timings = longArrayOf(0, 30, 60, 30), amplitudes = intArrayOf(0, 200, 0, 200))
        )
    }

    /** Longer, lower-amplitude single buzz — deliberately distinct from
     *  the two-pulse success pattern above. Placeholder feel, worth
     *  having someone tune it. */
    override suspend fun onTransmitFailed() {
        hapticEngine.perform(
            HapticPattern(timings = longArrayOf(0, 200), amplitudes = intArrayOf(0, 255))
        )
    }
}
