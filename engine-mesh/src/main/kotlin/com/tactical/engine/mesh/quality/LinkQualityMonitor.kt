package com.tactical.engine.mesh.quality

import com.tactical.domain.identity.DeviceId
import kotlinx.coroutines.flow.Flow

interface LinkQualityMonitor {
    /**
     * Observes real-time link quality updates for all known peers.
     */
    fun observe(): Flow<List<LinkQuality>>

    /**
     * Records a new RSSI sample for a peer.
     */
    fun recordSample(peerId: DeviceId, rssi: Int)
}
