package com.tactical.platform.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tactical.platform.api.haptics.HapticEngine
import com.tactical.platform.api.haptics.HapticPattern

/**
 * Implements HapticEngine wrapping Android's Vibrator / VibratorManager —
 * VibratorManager on API 31+ (the pre-31 VIBRATOR_SERVICE is deprecated
 * there), falling back to the legacy Vibrator service below that, to
 * match minSdk 28's actual API split.
 */
class AndroidHapticEngine(private val context: Context) : HapticEngine {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override suspend fun perform(pattern: HapticPattern) {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, NO_REPEAT))
    }

    companion object {
        // -1 = play the waveform once, no loop-back index. HapticPattern
        // callers build the full waveform themselves (per its own kdoc),
        // so any repetition is on them, not this engine.
        private const val NO_REPEAT = -1
    }
}