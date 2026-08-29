package com.tactical.engine.mesh.forwarding

import com.tactical.domain.identity.DeviceId

/**
 * Interface for looking up the next hop for a destination.
 * In a flooding mesh, this might be largely unused.
 */
interface ForwardTable {
    /** Returns the next hop [DeviceId] for a given destination, or null if unknown. */
    fun nextHop(destination: DeviceId): DeviceId?
}
