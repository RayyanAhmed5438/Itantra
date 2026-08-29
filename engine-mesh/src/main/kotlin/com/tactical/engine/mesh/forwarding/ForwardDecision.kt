package com.tactical.engine.mesh.forwarding

import com.tactical.domain.packet.MeshRelayPacket

/**
 * Result of a routing decision by the MeshRouter.
 */
sealed interface ForwardDecision {
    /** The packet is for this node, consume it. */
    object AcceptLocal : ForwardDecision

    /** The packet should be rebroadcast as part of the mesh. */
    data class Rebroadcast(val relayPacket: MeshRelayPacket) : ForwardDecision

    /** The packet is for this node AND should be rebroadcast. */
    data class AcceptAndRebroadcast(val relayPacket: MeshRelayPacket) : ForwardDecision

    /** The packet should be discarded. */
    data class Drop(val reason: String) : ForwardDecision
}
