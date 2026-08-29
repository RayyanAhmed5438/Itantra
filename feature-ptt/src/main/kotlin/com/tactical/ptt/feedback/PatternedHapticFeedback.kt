package com.tactical.ptt.feedback

import com.tactical.platform.api.haptics.HapticEngine
import com.tactical.platform.api.haptics.HapticPattern

/**
 * Delegates PTT haptic events to the platform HapticEngine using preconfigured patterns.
 */
class PatternedHapticFeedback(
    private val hapticEngine: HapticEngine
) : PttHapticFeedback {

    override suspend fun onPress() {
        hapticEngine.perform(HapticPattern.Click)
    }

    override suspend fun onRelease() {
        hapticEngine.perform(HapticPattern.Tick)
    }

    override suspend fun onTransmitComplete() {
        hapticEngine.perform(HapticPattern.Success)
    }
}