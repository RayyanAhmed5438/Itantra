package com.tactical.emergency.broadcast

import com.tactical.domain.packet.EmergencyPacket
import com.tactical.domain.result.TacticalResult

/**
 * ASSUMPTION FLAGGED: the module map's one-line comment for this
 * interface doesn't show a return type ("suspend fun broadcastSos(packet:
 * EmergencyPacket)."). Given architecture principle #8 ("every
 * transmission path returns a TacticalResult") and that the map's note
 * for the implementation says it "handles TacticalResult," returning
 * TacticalResult<Unit> here — rather than swallowing the failure
 * silently — seems like the intended contract. Worth confirming.
 */
interface EmergencyBroadcaster {
    suspend fun broadcastSos(packet: EmergencyPacket): TacticalResult<Unit>
}