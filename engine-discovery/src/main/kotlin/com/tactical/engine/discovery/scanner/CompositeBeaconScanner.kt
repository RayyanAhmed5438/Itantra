package com.tactical.engine.discovery.scanner

import com.tactical.domain.identity.DeviceNode
import com.tactical.domain.identity.LinkType
import com.tactical.domain.packet.BeaconPacket
import com.tactical.platform.api.ble.BleBeaconPayloadCodec
import com.tactical.platform.api.ble.BleBeaconScanner
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.protocol.serialization.PacketSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import java.time.Instant

class CompositeBeaconScanner(
    private val bleScanner: BleBeaconScanner,
    private val radioTransport: RadioTransport,
    private val serializer: PacketSerializer
) : BeaconScanner {

    override fun scan(): Flow<DeviceNode> {
        val bleFlow = bleScanner.scan()
            .mapNotNull { scanned ->
                val payload = scanned.advertisementPayload ?: return@mapNotNull null

                // decode() checks the magic bytes first and returns null
                // immediately for anything that isn't this app's format —
                // this is what actually filters out unrelated nearby BLE
                // devices (headphones, trackers, etc.) instead of trying
                // to interpret their random bytes as a beacon.
                val packet = BleBeaconPayloadCodec.decode(payload) ?: return@mapNotNull null

                DeviceNode(
                    id = packet.sender,
                    callsign = packet.callsign,
                    rssi = scanned.rssi,
                    lastSeen = Instant.now(),
                    hopCount = 0,
                    link = LinkType.DIRECT
                )
            }

        val radioFlow = radioTransport.incoming()
            .mapNotNull { raw ->
                try {
                    val packet = serializer.deserialize(raw.data)
                    if (packet !is BeaconPacket) return@mapNotNull null

                    DeviceNode(
                        id = packet.sender,
                        callsign = packet.callsign,
                        rssi = raw.rssi,
                        lastSeen = Instant.ofEpochMilli(raw.timestamp),
                        hopCount = 0,
                        link = LinkType.DIRECT
                    )
                } catch (_: Exception) {
                    null
                }
            }

        return merge(bleFlow, radioFlow)
    }
}