package com.tactical.platform.api.ble

/**
 * One BLE scan result. Produced by BleBeaconScanner, consumed by
 * engine-discovery to build/refresh DeviceNode entries.
 */
data class ScannedBleDevice(
    val deviceId: String,
    val rssi: Int,
    val advertisementPayload: ByteArray? = null
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScannedBleDevice) return false
        if (deviceId != other.deviceId || rssi != other.rssi) return false
        val a = advertisementPayload
        val b = other.advertisementPayload
        return if (a == null || b == null) a == b else a.contentEquals(b)
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + rssi
        result = 31 * result + (advertisementPayload?.contentHashCode() ?: 0)
        return result
    }
}