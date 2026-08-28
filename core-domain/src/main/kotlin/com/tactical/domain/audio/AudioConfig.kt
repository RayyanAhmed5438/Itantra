package com.tactical.domain.audio

/**
 * Configuration for audio capture/playback — the PCM format used across
 * mic capture, playback, and STT/TTS inference. Implemented against by
 * AudioRecorder/AudioPlayer in core-platform-api.
 */
data class AudioConfig(
    val sampleRate: Int = 16_000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val chunkDurationMs: Long = 20L
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels in 1..2) { "channels must be 1 (mono) or 2 (stereo)" }
        require(bitDepth == 8 || bitDepth == 16 || bitDepth == 24 || bitDepth == 32) {
            "bitDepth must be one of 8, 16, 24, 32"
        }
        require(chunkDurationMs > 0) { "chunkDurationMs must be positive" }
    }

    /**
     * Derived, not stored, so it can never drift out of sync with the
     * fields it's computed from.
     */
    val frameSizeInBytes: Int
        get() = (sampleRate * channels * (bitDepth / 8) * chunkDurationMs / 1000).toInt()
}