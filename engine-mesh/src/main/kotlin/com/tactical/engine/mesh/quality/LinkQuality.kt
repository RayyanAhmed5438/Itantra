package com.tactical.engine.mesh.quality

import com.tactical.domain.identity.DeviceId

/**
 * Signal strength metrics for a specific peer link.
 */
data class LinkQuality(
    val peerId: DeviceId,
    val averageRssi: Double,
    val lastUpdate: Long
)
