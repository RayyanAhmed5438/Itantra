package com.tactical.domain.packet

import com.tactical.domain.identity.DeviceId

/**
 * Raw encoded audio packet. Used ONLY for the emergency raw-audio path —
 * every other case sends text (TextPacket) instead.
 */
data class VoicePacket(
    val sender: DeviceId,
    val audioData: ByteArray,
    val codec: AudioCodec,
    val timestamp: Long
) : Packet {

    init {
        require(audioData.isNotEmpty()) { "audioData must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoicePacket) return false
        return sender == other.sender &&
                audioData.contentEquals(other.audioData) &&
                codec == other.codec &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = sender.hashCode()
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + (codec?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * ASSUMPTION FLAGGED: core.md references `codec: AudioCodec?` on VoicePacket
 * but never defines AudioCodec anywhere in the file tree or spec. Sketched
 * here as a minimal enum covering the two realistic options — raw PCM
 * (no compression, largest payload) and a compressed option for when
 * bandwidth actually matters. `codec` being nullable on VoicePacket
 * suggests null means "raw PCM, no codec applied" is a valid, expected
 * case, not an error — worth confirming that reading is correct with
 * whoever specced this, along with which real codec(s) OPUS should
 * concretely mean once encoding is implemented in platform-android.
 */
enum class AudioCodec {
    PCM_RAW,
    OPUS
}