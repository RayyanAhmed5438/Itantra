package com.tactical.platform.api.wifi

data class WifiDirectPeer(
    val deviceAddress: String,
    val deviceName: String,
    val appDeviceId: String? = null,
    val callsign: String? = null
) {
    init {
        require(deviceAddress.isNotBlank()) { "deviceAddress must not be blank" }
    }
}
