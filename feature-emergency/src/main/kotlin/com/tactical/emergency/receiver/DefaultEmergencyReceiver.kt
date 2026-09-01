package com.tactical.emergency.receiver

import com.tactical.domain.packet.EmergencyPacket
import com.tactical.engine.mesh.service.MeshService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance

/**
 * ASSUMPTION FLAGGED: the module map describes this as "Filters
 * PacketReceived events for EmergencyPacket instances," implying a
 * DomainEvent bus (PacketReceived(packet) does exist in
 * core-domain/events/). But the real MeshService interface exposes
 * `receive(): Flow<Packet>` directly, with no event-bus wiring visible
 * anywhere in the repo that turns MeshService's output into published
 * PacketReceived events. Filtering MeshService.receive() directly
 * achieves the same practical result without an event bus that doesn't
 * appear to exist yet — worth confirming with whoever owns engine-mesh
 * or app whether a DomainEvent bus is actually meant to sit in between.
 */
class DefaultEmergencyReceiver(
    private val meshService: MeshService
) : EmergencyReceiver {

    override fun incoming(): Flow<EmergencyPacket> =
        meshService.receive().filterIsInstance<EmergencyPacket>()
}