package com.tactical.platform.ble

import java.util.concurrent.ConcurrentHashMap

/** Maps the stable iTantra application UUID to the Android BLE radio address seen during scanning. */
object BlePeerAddressRegistry {
    private val appIdToAddress = ConcurrentHashMap<String, String>()
    private val addressToAppId = ConcurrentHashMap<String, String>()

    fun remember(appDeviceId: String, bluetoothAddress: String) {
        if (appDeviceId.isBlank() || bluetoothAddress.isBlank()) return
        appIdToAddress[appDeviceId] = bluetoothAddress
        addressToAppId[bluetoothAddress] = appDeviceId
    }

    fun addressFor(identifier: String): String? {
        if (isBluetoothAddress(identifier)) return identifier
        return appIdToAddress[identifier]
    }

    fun applicationIdFor(bluetoothAddress: String): String? = addressToAppId[bluetoothAddress]

    private fun isBluetoothAddress(value: String): Boolean =
        value.matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
}
