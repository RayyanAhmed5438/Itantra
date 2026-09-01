package com.tactical.emergency.message

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.location.GeoFix
import com.tactical.domain.packet.EmergencyPacket
import com.tactical.domain.packet.Severity

class EmergencyMessageBuilder {

    /** Falls back to a generic description if the user didn't type one,
     *  per data-flow doc §5 step 2 ("user-provided text or a default"). */
    fun build(
        sender: DeviceId,
        severity: Severity,
        description: String?,
        location: GeoFix?,
        languageCode: String
    ): EmergencyPacket =
        EmergencyPacket(
            sender = sender,
            severity = severity,
            description = description?.takeIf { it.isNotBlank() } ?: "SOS at this location",
            location = location,
            languageCode = languageCode,
            timestamp = System.currentTimeMillis()
        )
}