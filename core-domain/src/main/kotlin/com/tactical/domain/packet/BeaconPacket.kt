package com.tactical.domain.packet

import com.tactical.domain.identity.DeviceId

/**
 * Presence heartbeat. Emitted periodically (~every 2s) so peers know a
 * device is alive and in range. Consumed by engine-discovery to build/
 * refresh DeviceNode entries.
 */
data class BeaconPacket(
    val sender: DeviceId,
    val callsign: String,
    val listenPort: Int? = null,
    val timestamp: Long
) : Packet {

    init {
        require(callsign.isNotBlank()) { "callsign must not be blank" }
        require(listenPort == null || listenPort in 1..65535) {
            "listenPort must be a valid port number if present"
        }
    }
}