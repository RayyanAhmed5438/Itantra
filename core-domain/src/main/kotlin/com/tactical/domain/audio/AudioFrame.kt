package com.tactical.domain.audio

/**
 * One PCM audio chunk with metadata. Produced by AudioRecorder, consumed
 * by SpeechToText and AudioPlayer.
 */
class AudioFrame(
    val data: ByteArray,
    val timestamp: Long,
    val durationMs: Long
) {
    init {
        require(data.isNotEmpty()) { "data must not be empty" }
        require(durationMs > 0) { "durationMs must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return data.contentEquals(other.data) &&
                timestamp == other.timestamp &&
                durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }

    override fun toString(): String =
        "AudioFrame(bytes=${data.size}, timestamp=$timestamp, durationMs=$durationMs)"
}