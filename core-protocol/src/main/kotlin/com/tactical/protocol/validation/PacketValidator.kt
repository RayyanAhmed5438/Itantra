package com.tactical.protocol.validation

interface PacketValidator {
    /**
     * Validates whether the given byte array represents a valid protocol packet.
     */
    fun validate(bytes: ByteArray): Boolean
}
