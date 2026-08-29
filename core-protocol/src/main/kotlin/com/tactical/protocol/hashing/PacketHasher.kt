package com.tactical.protocol.hashing

import com.tactical.domain.packet.Packet

interface PacketHasher {
    /**
     * Generates a stable hash for the given packet, used for deduplication.
     * The hash should be based on the packet's unique content.
     */
    fun hash(packet: Packet): Long
}
