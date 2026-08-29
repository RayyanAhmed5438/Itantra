package com.tactical.engine.mesh.forwarding

import com.tactical.domain.identity.DeviceId

/**
 * Always returns null — used when no specific routing table is needed (e.g. pure flooding).
 */
class EmptyForwardTable : ForwardTable {
    override fun nextHop(destination: DeviceId): DeviceId? = null
}
