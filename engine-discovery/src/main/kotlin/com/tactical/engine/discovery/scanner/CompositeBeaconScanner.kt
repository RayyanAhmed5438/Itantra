package com.tactical.engine.discovery.scanner

import com.tactical.domain.identity.DeviceNode
import com.tactical.domain.identity.LinkType
import com.tactical.domain.packet.BeaconPacket
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.protocol.serialization.PacketSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant

/**
 * Listens for BeaconPackets across all radio transports and converts them 
 * to DeviceNodes. Prioritizes DeviceId over hardware MAC.
 */
class CompositeBeaconScanner(
    private val transports: List<RadioTransport>,
    private val serializer: PacketSerializer
) : BeaconScanner {

    override fun scan(): Flow<DeviceNode> {
        // In this implementation, we merge all transport flows into one
        // For simplicity, we'll implement this by listening to incoming traffic
        // that matches BeaconPacket type.
        
        // Note: Real merging of multiple flows usually involves 'merge()' from kotlinx-coroutines-core
        // but since we only have one interface for all transports in RadioTransport.kt, 
        // we'll assume the provided transports cover the physical layer.
        
        // Let's assume for now we take the first transport or aggregate them.
        // For the sake of the task, I'll combine them using a flow merge.
        
        return kotlinx.coroutines.flow.merge(
            *transports.map { transport ->
                transport.incoming().mapNotNull { raw ->
                    try {
                        val packet = serializer.deserialize(raw.data)
                        if (packet is BeaconPacket) {
                            DeviceNode(
                                id = packet.sender,
                                callsign = packet.callsign,
                                rssi = raw.rssi,
                                lastSeen = Instant.ofEpochMilli(raw.timestamp),
                                hopCount = 0, // Direct beacons have 0 hops
                                link = LinkType.DIRECT
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }.toTypedArray()
        )
    }
}
