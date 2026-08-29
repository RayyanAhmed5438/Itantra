package com.tactical.engine.mesh.ttl

import com.tactical.domain.packet.MeshRelayPacket

class DecrementingTtlTracker : TtlTracker {
    override fun decrement(packet: MeshRelayPacket): MeshRelayPacket? {
        if (packet.ttl <= 0) return null
        
        return packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1
        )
    }
}
