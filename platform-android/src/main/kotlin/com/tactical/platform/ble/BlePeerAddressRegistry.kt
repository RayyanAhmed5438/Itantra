package com.tactical.platform.ble

import java.util.concurrent.ConcurrentHashMap

/** Maps the stable iTantra application UUID to the Android BLE radio address seen during scanning. */
object BlePeerAddressRegistry {
    private val appIdToAddress = ConcurrentHashMap<String, String>()
    private val addressToAppId = ConcurrentHashMap<String, String>()
    private val appIdToCallsign = ConcurrentHashMap<String, String>()
    private val activeAddresses = ConcurrentHashMap.newKeySet<String>()

    fun remember(
        appDeviceId: String,
        bluetoothAddress: String,
        callsign: String? = null
    ) {
        if (appDeviceId.isBlank() || bluetoothAddress.isBlank()) return
        if (!callsign.isNullOrBlank()) {
            appIdToCallsign[appDeviceId] = callsign
        }

        val currentAddress = appIdToAddress[appDeviceId]
        val addressCanMove = currentAddress == null ||
            currentAddress == bluetoothAddress ||
            currentAddress !in activeAddresses

        if (addressCanMove) {
            if (currentAddress != null && currentAddress != bluetoothAddress) {
                addressToAppId.remove(currentAddress, appDeviceId)
            }
            appIdToAddress[appDeviceId] = bluetoothAddress
            addressToAppId[bluetoothAddress] = appDeviceId
        }
    }

    fun addressFor(identifier: String): String? {
        if (isBluetoothAddress(identifier)) return identifier
        return appIdToAddress[identifier]
    }

    fun applicationIdFor(bluetoothAddress: String): String? = addressToAppId[bluetoothAddress]

    fun callsignFor(appDeviceId: String): String? = appIdToCallsign[appDeviceId]

    fun markConnectionActive(bluetoothAddress: String) {
        if (bluetoothAddress.isNotBlank()) activeAddresses.add(bluetoothAddress)
    }

    fun markConnectionInactive(bluetoothAddress: String) {
        if (bluetoothAddress.isNotBlank()) activeAddresses.remove(bluetoothAddress)
    }

    fun updateCallsign(appDeviceId: String, callsign: String) {
        if (appDeviceId.isNotBlank() && callsign.isNotBlank()) {
            appIdToCallsign[appDeviceId] = callsign
        }
    }

    private fun isBluetoothAddress(value: String): Boolean =
        value.matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
}
