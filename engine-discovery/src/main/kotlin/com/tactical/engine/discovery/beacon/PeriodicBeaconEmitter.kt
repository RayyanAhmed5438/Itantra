package com.tactical.engine.discovery.beacon

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.BeaconPacket
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import com.tactical.protocol.serialization.PacketSerializer
import kotlinx.coroutines.*

/**
 * Broadcasts a BeaconPacket every 2 seconds via RadioTransport.
 */
class PeriodicBeaconEmitter(
    private val localDeviceId: DeviceId,
    private val callsign: String,
    private val transport: RadioTransport,
    private val serializer: PacketSerializer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BeaconEmitter {

    private var job: Job? = null

    override fun start() {
        if (job != null) return
        
        job = scope.launch {
            while (isActive) {
                emit()
                delay(2000)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun emit() {
        val beacon = BeaconPacket(
            sender = localDeviceId,
            callsign = callsign,
            timestamp = System.currentTimeMillis()
        )
        val bytes = serializer.serialize(beacon)
        val raw = RawPacket(bytes, 0, System.currentTimeMillis())
        transport.broadcast(raw)
    }
}
