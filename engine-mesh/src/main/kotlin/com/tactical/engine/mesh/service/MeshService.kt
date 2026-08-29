package com.tactical.engine.mesh.service

import com.tactical.domain.packet.Packet
import com.tactical.domain.result.TacticalResult
import kotlinx.coroutines.flow.Flow

/**
 * High-level entry point for sending and receiving data over the mesh.
 */
interface MeshService {
    /**
     * Broadcasts a packet to the mesh network.
     */
    suspend fun send(packet: Packet): TacticalResult<Unit>

    /**
     * Flow of packets received from the mesh that are addressed to or 
     * relevant for this node.
     */
    fun receive(): Flow<Packet>
}
