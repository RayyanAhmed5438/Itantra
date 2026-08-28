package com.tactical.platform.api.radio

/**
 * Data class wrapping received bytes from any radio bearer, plus the
 * signal strength and time they were received at. Bearer-agnostic and
 * content-agnostic — RadioTransport doesn't know or care what's inside
 * `data`, that's core-protocol's job once it's handed off.
 */
data class RawPacket(
    val data: ByteArray,
    val rssi: Int,
    val timestamp: Long
) {
    init {
        require(data.isNotEmpty()) { "data must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawPacket) return false
        return data.contentEquals(other.data) && rssi == other.rssi && timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + rssi
        result = 31 * result + timestamp.hashCode()
        return result
    }
}