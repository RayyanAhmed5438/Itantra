package com.tactical.domain.events

import com.tactical.domain.packet.Packet

/**
 * Any Packet successfully received off the mesh. Published once
 * engine-mesh finishes routing/dedup and delivers the unwrapped payload.
 */
data class PacketReceived(val packet: Packet) : DomainEvent