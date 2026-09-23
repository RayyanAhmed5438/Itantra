package com.tactical.domain.packet

import com.tactical.domain.identity.DeviceId

data class TextPacket(
    val sender: DeviceId,
    val text: String,
    val languageCode: String,
    val timestamp: Long,
    /** True when this sentence came from continuous Call Mode. */
    val isCallMode: Boolean = false
) : Packet {
    init {
        require(text.isNotBlank()) { "text must not be blank" }
        require(languageCode.isNotBlank()) { "languageCode must not be blank" }
    }
}
