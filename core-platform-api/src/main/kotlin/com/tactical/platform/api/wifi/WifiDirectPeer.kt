package com.tactical.platform.api.wifi

data class WifiDirectPeer(
    val deviceAddress: String,
    val deviceName: String
) {
    init {
        require(deviceAddress.isNotBlank()) { "deviceAddress must not be blank" }
    }
}