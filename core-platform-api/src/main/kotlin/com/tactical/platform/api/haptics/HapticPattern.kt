package com.tactical.platform.api.haptics

/**
 * Raw vibration waveform — timings (ms) paired with amplitudes, index for
 * index. Simple data holder, per spec: this is NOT a named enum of fixed
 * patterns (EMERGENCY_INCOMING, etc.) as our earlier version had it —
 * callers construct the actual waveform data themselves.
 */
data class HapticPattern(
    val timings: LongArray,
    val amplitudes: IntArray
) {
    init {
        require(timings.isNotEmpty()) { "timings must not be empty" }
        require(timings.size == amplitudes.size) {
            "timings and amplitudes must be the same length"
        }
        require(amplitudes.all { it in 0..255 }) {
            "amplitudes must be in 0..255 (Android VibrationEffect's amplitude range)"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HapticPattern) return false
        return timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)
    }

    override fun hashCode(): Int {
        var result = timings.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        return result
    }
}