package com.tactical.protocol.hashing

import com.tactical.domain.packet.Packet

/**
 * Fast non-cryptographic hash implementation. 
 * For this initial implementation, we use Kotlin's hashCode combined with 
 * a simple bit mix to simulate a 64-bit hash.
 */
class XxHashPacketHasher : PacketHasher {
    override fun hash(packet: Packet): Long {
        // In a real implementation, this would use a proper xxHash on the serialized payload.
        // Using hashCode() as a placeholder that satisfies the interface.
        val base = packet.hashCode().toLong()
        return base xor (base ushr 32)
    }
}
