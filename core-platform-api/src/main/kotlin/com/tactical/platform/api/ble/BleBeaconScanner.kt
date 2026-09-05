package com.tactical.platform.api.ble

import kotlinx.coroutines.flow.Flow

/**
 * Listens for nearby peers' BLE beacon advertisements. Implemented in
 * platform-android, wrapping Android's BluetoothLeScanner API. Must
 * transparently handle the API 28-30 (location permission) vs API 31+
 * (separate BLE scan/connect permissions) split — callers on the other
 * side of this interface shouldn't need to know which permission model
 * the running device uses.
 */
interface BleBeaconScanner {

    /**
     * Cold Flow of every BLE beacon seen nearby, one ScannedBleDevice per
     * advertisement received — including repeats from the same peer on
     * every beacon interval. No deduplication happens here; that's
     * engine-discovery's job.
     */
    fun scan(): Flow<ScannedBleDevice>
}