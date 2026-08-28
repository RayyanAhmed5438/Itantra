package com.tactical.platform.api.ble

/**
 * Broadcasts this device's own presence beacon over BLE advertising.
 * Implemented in platform-android, wrapping Android's
 * BluetoothLeAdvertiser API.
 */
interface BleBeaconAdvertiser {

    /**
     * Starts (or replaces, if already advertising) broadcasting the given
     * payload as this device's beacon.
     */
    suspend fun advertise(payload: ByteArray)

    /**
     * Stops advertising. Safe to call when not currently advertising.
     */
    suspend fun stopAdvertising()
}