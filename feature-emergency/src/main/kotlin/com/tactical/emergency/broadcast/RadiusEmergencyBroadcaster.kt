package com.tactical.emergency.broadcast

import com.tactical.domain.packet.EmergencyPacket
import com.tactical.domain.result.TacticalResult
import com.tactical.engine.mesh.service.MeshService

/**
 * ARCHITECTURE GAP FLAGGED (raise with whoever owns engine-mesh): per the
 * real DefaultMeshService.kt, send() always wraps packets with
 * ProtocolConstants.DEFAULT_TTL (5) — there is no parameter to request
 * MAX_HOPS (10) for this packet specifically. That means, as things stand
 * today, an emergency alert gets the SAME flood radius as ordinary chat,
 * not the wider radius the architecture handbook §6.9 requires ("ttl =
 * ProtocolConstants.MAX_HOPS ... ensures it floods the entire squad").
 *
 * This class calls send(packet) as-is because that's the only entry
 * point MeshService currently exposes — it does NOT construct its own
 * MeshRelayPacket (that would double-wrap; DefaultMeshService already
 * does the wrapping internally). Fixing the TTL gap requires either:
 *   (a) MeshService.send() gaining an optional ttl parameter, or
 *   (b) DefaultMeshService special-casing `packet is EmergencyPacket`
 *       internally to use MAX_HOPS.
 * Until one of those lands, treat emergency flood radius as UNVERIFIED.
 */
class RadiusEmergencyBroadcaster(
    private val meshService: MeshService
) : EmergencyBroadcaster {

    override suspend fun broadcastSos(packet: EmergencyPacket): TacticalResult<Unit> =
        meshService.send(packet)
}