package com.tactical.platform.api.ble

import com.tactical.domain.result.TacticalResult
import kotlinx.coroutines.flow.Flow

/**
 * User-controlled BLE pairing and session connection.
 *
 * Discovery does not call these methods automatically.
 */
interface BleConnectionManager {
    suspend fun pair(deviceAddress: String): TacticalResult<Unit>
    suspend fun connect(deviceAddress: String): TacticalResult<Unit>
    suspend fun disconnect(deviceAddress: String)
    fun state(deviceAddress: String): Flow<BleLinkState>
    suspend fun reconnectPaired(deviceAddress: String): TacticalResult<Unit>
    suspend fun repairAndReconnect(deviceAddress: String): TacticalResult<Unit>
}

enum class BleLinkState {
    NOT_PAIRED,
    PAIRING,
    PAIRED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}
