package com.tactical.platform.api.radio

import kotlinx.coroutines.flow.Flow
import com.tactical.domain.result.TacticalResult

/**
 * Bearer-agnostic send/receive over whatever radio link is active (BLE or
 * Wi-Fi Direct) — engine-mesh doesn't need to know which. By the time
 * bytes reach this interface, a link is assumed already established (see
 * ble/ and wifi/ for discovery/connection).
 */
interface RadioTransport {

    /**
     * Cold Flow of every received packet, with RSSI and timestamp
     * attached, from any connected peer over this transport.
     */
    fun incoming(): Flow<RawPacket>

    /**
     * Sends raw bytes out over this transport to all nearby/connected
     * nodes.
     */
    suspend fun broadcast(raw: RawPacket): TacticalResult<Unit>
}