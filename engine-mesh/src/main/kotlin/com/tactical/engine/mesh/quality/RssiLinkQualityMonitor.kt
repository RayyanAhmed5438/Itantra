package com.tactical.engine.mesh.quality

import com.tactical.domain.identity.DeviceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks link health using a rolling average of RSSI values.
 */
class RssiLinkQualityMonitor(
    private val alpha: Double = 0.2 // Smoothing factor for EMA
) : LinkQualityMonitor {

    private val qualities = ConcurrentHashMap<DeviceId, LinkQuality>()
    private val _qualityFlow = MutableStateFlow<List<LinkQuality>>(emptyList())

    override fun observe(): Flow<List<LinkQuality>> = _qualityFlow.asStateFlow()

    override fun recordSample(peerId: DeviceId, rssi: Int) {
        val now = System.currentTimeMillis()
        qualities.compute(peerId) { _, existing ->
            if (existing == null) {
                LinkQuality(peerId, rssi.toDouble(), now)
            } else {
                val newAvg = (existing.averageRssi * (1.0 - alpha)) + (rssi.toDouble() * alpha)
                existing.copy(averageRssi = newAvg, lastUpdate = now)
            }
        }
        _qualityFlow.value = qualities.values.toList()
    }
}
