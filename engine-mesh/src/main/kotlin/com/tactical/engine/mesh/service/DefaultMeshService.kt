package com.tactical.engine.mesh.service

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.MeshRelayPacket
import com.tactical.domain.packet.Packet
import com.tactical.domain.result.TacticalResult
import com.tactical.engine.mesh.forwarding.ForwardDecision
import com.tactical.engine.mesh.router.MeshRouter
import com.tactical.engine.mesh.quality.LinkQualityMonitor
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import com.tactical.protocol.serialization.PacketSerializer
import com.tactical.protocol.constants.ProtocolConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Coordinates routing, deduplication, and transport for the mesh network.
 */
class DefaultMeshService(
    private val localDeviceId: DeviceId,
    private val router: MeshRouter,
    private val serializer: PacketSerializer,
    private val transport: RadioTransport,
    private val qualityMonitor: LinkQualityMonitor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : MeshService {

    private val _incomingPackets = MutableSharedFlow<Packet>()
    
    init {
        // Start processing incoming radio traffic
        transport.incoming()
            .onEach { handleIncomingRaw(it) }
            .launchIn(scope)
    }

    override suspend fun send(packet: Packet): TacticalResult<Unit> {
        val relayPacket = MeshRelayPacket(
            originalSender = localDeviceId,
            immediateSender = localDeviceId,
            ttl = ProtocolConstants.DEFAULT_TTL,
            hopCount = 0,
            payload = packet
        )
        return broadcastRelay(relayPacket)
    }

    override fun receive(): Flow<Packet> = _incomingPackets.asSharedFlow()

    private suspend fun handleIncomingRaw(raw: RawPacket) {
        try {
            val relayPacket = serializer.deserializeRelay(raw.data)
            
            // Track link quality
            qualityMonitor.recordSample(relayPacket.immediateSender, raw.rssi)
            
            // Route the packet
            when (val decision = router.handle(relayPacket)) {
                is ForwardDecision.AcceptLocal -> {
                    _incomingPackets.emit(relayPacket.payload)
                }
                is ForwardDecision.Rebroadcast -> {
                    broadcastRelay(decision.relayPacket.copy(immediateSender = localDeviceId))
                }
                is ForwardDecision.AcceptAndRebroadcast -> {
                    _incomingPackets.emit(relayPacket.payload)
                    broadcastRelay(decision.relayPacket.copy(immediateSender = localDeviceId))
                }
                is ForwardDecision.Drop -> {
                    // Log or ignore
                }
            }
        } catch (e: Exception) {
            // Malformed packet, skip
        }
    }

    private suspend fun broadcastRelay(relay: MeshRelayPacket): TacticalResult<Unit> {
        val bytes = serializer.serializeRelay(relay)
        val raw = RawPacket(bytes, 0, System.currentTimeMillis())
        return transport.broadcast(raw)
    }
}
