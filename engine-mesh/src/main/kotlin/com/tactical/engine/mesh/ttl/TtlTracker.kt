package com.tactical.engine.mesh.ttl

import com.tactical.domain.packet.MeshRelayPacket

/**
 * Handles time-to-live logic for relayed packets.
 */
interface TtlTracker {
    /**
     * Decrements the TTL and increments hop count. 
     * Returns the updated packet, or null if TTL is exhausted.
     */
    fun decrement(packet: MeshRelayPacket): MeshRelayPacket?
}
