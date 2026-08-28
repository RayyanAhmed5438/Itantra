package com.tactical.domain.location

/**
 * GPS coordinates with accuracy. Attached to EmergencyPacket when
 * available — never required, since an alert must be able to go out even
 * without a location lock.
 */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long
) {
    init {
        require(latitude in -90.0..90.0) { "latitude must be in -90..90" }
        require(longitude in -180.0..180.0) { "longitude must be in -180..180" }
        require(accuracyMeters == null || accuracyMeters >= 0f) {
            "accuracyMeters cannot be negative if present"
        }
    }
}