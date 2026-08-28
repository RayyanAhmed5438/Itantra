package com.tactical.domain.packet

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.location.GeoFix

/**
 * High-priority alert. Built from user input + current location, this is
 * the one exception to text-over-mesh — SystemSquelchBreaker always ends
 * in a spoken TTS readout on receive, regardless of literacy.
 */
data class EmergencyPacket(
    val sender: DeviceId,
    val severity: Severity,
    val description: String,
    val location: GeoFix? = null,
    val languageCode: String,
    val timestamp: Long
) : Packet {

    init {
        require(description.isNotBlank()) { "description must not be blank" }
        require(languageCode.isNotBlank()) { "languageCode must not be blank" }
    }
}

/**
 * ASSUMPTION FLAGGED: core.md says "Sealed Severity enum inside or
 * separate" without listing actual levels — same gap as our original
 * EmergencySeverity. Kept the same three-level placeholder as before
 * (CAUTION/URGENT/CRITICAL) pending confirmation from whoever owns
 * feature-emergency's escalation logic (louder/DND-bypass/flashlight
 * thresholds per level).
 */
enum class Severity { CAUTION, URGENT, CRITICAL }