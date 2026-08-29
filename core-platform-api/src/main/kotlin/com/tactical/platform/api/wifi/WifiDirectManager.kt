package com.tactical.platform.api.wifi

import kotlinx.coroutines.flow.Flow
import com.tactical.domain.result.TacticalResult

/**
 * Manages Wi-Fi Direct (P2P) discovery and connection — the
 * higher-throughput bearer used for relay traffic and emergency audio.
 * Implemented in platform-android, wrapping Android's WifiP2pManager API.
 * Per core.md, this is consumed by platform-android's own RadioTransport
 * implementation internally — engine-mesh talks to RadioTransport, not
 * necessarily to this interface directly.
 */
interface WifiDirectManager {

    /**
     * Discovers nearby Wi-Fi Direct peers, emitting the full current peer
     * list on each update (matches how Android's WifiP2pManager.
     * PeerListListener actually reports peers — a snapshot list, not
     * one-at-a-time events).
     */
    suspend fun discoverPeers(): Flow<List<WifiDirectPeer>>

    /**
     * Initiates a connection to the peer with the given deviceId. Failure
     * (peer declined, timeout, group negotiation failure) is expected to
     * throw rather than return a result type, per this signature having
     * no return value.
     */
    suspend fun connect(deviceId: String): TacticalResult<Unit>
}


