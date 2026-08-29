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
        result = 31 * result + codec.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}


