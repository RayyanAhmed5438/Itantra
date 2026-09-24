package com.tactical.platform.api.ble

import com.tactical.domain.result.TacticalResult
import kotlinx.coroutines.flow.Flow

/**
 * BLE physical-link management plus app-level squad membership.
 *
 * Discovery may call connect() automatically. Squad membership is an
 * application decision and is deliberately independent from Android
 * Bluetooth bonding/pairing.
 */
interface BleConnectionManager {
    /** Add an iTantra peer to this device's application-level squad. */
    suspend fun addToSquad(deviceAddress: String): TacticalResult<Unit>

    /** Remove an iTantra peer from the application-level squad. */
    suspend fun removeFromSquad(deviceAddress: String)

    /** Establish a GATT session; Android bonding is never required. */
    suspend fun connect(deviceAddress: String): TacticalResult<Unit>

    suspend fun disconnect(deviceAddress: String)
    fun state(deviceAddress: String): Flow<BleLinkState>

    /** Emits the latest RSSI measured from the active GATT connection. */
    fun rssi(deviceAddress: String): Flow<Int?>

    /** Reconnect an application-level squad member after a transient link loss. */
    suspend fun reconnectSquadMember(deviceAddress: String): TacticalResult<Unit>

    /** Force a fresh GATT connection without performing Bluetooth pairing. */
    suspend fun repairAndReconnect(deviceAddress: String): TacticalResult<Unit>

    /** Returns stable iTantra IDs currently selected for the local squad. */
    fun squadDeviceIds(): Set<String>

    fun diagnostics(): Flow<BleDiagnostics>
}

data class BleDiagnostics(
    val advertisingOk: Boolean,
    val scanningOk: Boolean,
    val connectedPeerCount: Int,
    val consecutiveEmptyCycles: Int,
    val consecutiveScanFailures: Int,
    val lastRecoveryEpochMs: Long = 0L,
    val issue: String? = null
)

enum class BleLinkState {
    AVAILABLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}
