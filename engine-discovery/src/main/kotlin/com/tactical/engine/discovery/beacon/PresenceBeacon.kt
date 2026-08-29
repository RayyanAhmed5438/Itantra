package com.tactical.engine.discovery.beacon

import com.tactical.domain.identity.DeviceId

/**
 * Internal representation of a heartbeat payload.
 */
data class PresenceBeacon(
    val deviceId: DeviceId,
    val callsign: String,
    val listenPort: Int? = null
)
