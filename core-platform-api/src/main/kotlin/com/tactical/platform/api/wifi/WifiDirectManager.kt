package com.tactical.platform.api.wifi

import com.tactical.domain.result.TacticalResult
import kotlinx.coroutines.flow.Flow

interface WifiDirectManager {
    /** Advertise this installation as an iTantra Wi-Fi Direct service. */
    suspend fun advertisePresence(
        deviceId: String,
        callsign: String
    ): TacticalResult<Unit>

    /**
     * Finds only Wi-Fi Direct devices that advertise the iTantra service.
     * Ordinary Wi-Fi Direct peers are deliberately ignored.
     */
    suspend fun discoverPeers(): Flow<List<WifiDirectPeer>>

    suspend fun disconnect(): TacticalResult<Unit>

    suspend fun connect(deviceId: String): TacticalResult<Unit>
}
