package com.tactical.engine.mesh.router

import com.tactical.domain.packet.MeshRelayPacket
import com.tactical.engine.mesh.forwarding.ForwardDecision

/**
 * Core routing logic for the mesh network.
 */
interface MeshRouter {
    /**
     * Determines the [ForwardDecision] for an incoming [MeshRelayPacket].
     */
    fun handle(relayPacket: MeshRelayPacket): ForwardDecision
}
