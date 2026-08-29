package com.tactical.ptt.relay

import com.tactical.domain.packet.TextPacket
import com.tactical.domain.result.TacticalResult
import com.tactical.engine.mesh.service.MeshService

/**
 * Dispatches built PTT packets through the mesh network routing layer.
 *
 * @param meshService Tactical mesh service handling flood routing and deduplication.
 */
class PttMeshDispatcher(
    private val meshService: MeshService
) {
    /**
     * Sends the packet to adjacent peers and propagates the transmission outcome.
     *
     * @param packet The text packet to transmit.
     * @return [TacticalResult.Success] on successful transmission or [TacticalResult.Failure] on transport failure.
     */
    suspend fun dispatch(packet: TextPacket): TacticalResult<Unit> {
        return meshService.send(packet)
    }
}